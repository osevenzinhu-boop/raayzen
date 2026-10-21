package com.nationswaw.systems;

import com.nationswaw.NationsWaW;
import com.nationswaw.gui.Theme;
import com.nationswaw.model.Nation;
import com.nationswaw.model.RoleType;
import com.nationswaw.utils.Ledger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Titulos publicos / dividas nacionais.
 * Emitir: credita BC. Garantia: Tesouro.
 * Compra: Presidente paga BC->Tesouro.
 * A cada 3h: 10% juros + 10% amortizacao do valor restante, so do Tesouro.
 * Se falhar: ISSP/ISDN da devedora redirecionados ao credor.
 */
public class DebtManager {
    private final NationsWaW plugin;
    private final Ledger ledger;
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final Map<Integer, Debt> debts = new ConcurrentHashMap<>();

    public static class Debt {
        public final int id;
        public final String debtorId;
        public String creditorId; // null = oferta aberta
        public final double original;
        public double remaining;
        public final double treasuryAtEmit;
        public final long createdAt;
        public boolean taxRedirect;

        public Debt(int id, String debtorId, double amount, double treasuryAtEmit) {
            this.id = id;
            this.debtorId = debtorId;
            this.creditorId = null;
            this.original = amount;
            this.remaining = amount;
            this.treasuryAtEmit = treasuryAtEmit;
            this.createdAt = System.currentTimeMillis();
            this.taxRedirect = false;
        }

        public double riskPercent(Nation debtor) {
            double treas = debtor == null ? 0 : debtor.getTreasury();
            if (treas <= 0) return 100.0;
            return Math.min(100.0, (remaining / treas) * 100.0);
        }
    }

    public DebtManager(NationsWaW plugin) {
        this.plugin = plugin;
        this.ledger = new Ledger(plugin);
        // ciclo 3h
        Bukkit.getScheduler().runTaskTimer(plugin, this::processPayments, 20L * 60 * 60 * 3, 20L * 60 * 60 * 3);
        plugin.getLogger().info("DebtManager: ciclo de pagamento 3h.");
    }

    public Collection<Debt> getAll() { return debts.values(); }
    public Collection<Debt> getOpenOffers() {
        List<Debt> out = new ArrayList<>();
        for (Debt d : debts.values()) if (d.creditorId == null && d.remaining > 0) out.add(d);
        return out;
    }
    public Debt get(int id) { return debts.get(id); }

    /** ISSP/ISDN da nação devem ir ao credor se taxRedirect ativo */
    public String getTaxRedirectTarget(String debtorNationId) {
        for (Debt d : debts.values()) {
            if (d.taxRedirect && d.creditorId != null && d.debtorId.equalsIgnoreCase(debtorNationId) && d.remaining > 0) {
                return d.creditorId;
            }
        }
        return null;
    }

    public boolean emit(Player president, double amount) {
        if (!plugin.getRoleManager().hasRoleAtLeast(president.getUniqueId(), RoleType.PRESIDENT)) {
            president.sendMessage(Theme.MM.deserialize("<red>Apenas o Presidente pode emitir divida.</red>"));
            return false;
        }
        Nation n = plugin.getNationManager().getNationByPlayer(president.getUniqueId());
        if (n == null) return false;
        if (amount <= 0) {
            president.sendMessage(Theme.MM.deserialize("<red>Valor inválido.</red>"));
            return false;
        }
        // NÃO credita BC na emissão — só ao ser comprado (anti-exploit)
        int id = nextId.getAndIncrement();
        debts.put(id, new Debt(id, n.getId(), amount, n.getTreasury()));
        ledger.record(n.getId(), "DEBT_EMIT", 0, president.getUniqueId(), "Titulo pendente #" + id + " valor=" + amount);
        plugin.getNationManager().saveAll();
        president.sendMessage(Theme.MM.deserialize(
                "<green>[◆] Titulo de divida de " + n.getCurrencySymbol() + " " + Theme.fmt(amount)
                        + " emitido com sucesso! Aguardando compradores.</green>"));
        president.sendMessage(Theme.MM.deserialize(
                "<gray>Tesouro de garantia: " + n.getCurrencySymbol() + " " + Theme.fmt(n.getTreasury())
                        + " · O valor so entra no BC quando outra nacao comprar.</gray>"));
        Bukkit.broadcast(Theme.MM.deserialize(
                plugin.getConfigManager().getPrefix() + "<gold>" + n.getDisplayName()
                        + " ofereceu titulo publico de " + n.getCurrencySymbol() + " " + Theme.fmt(amount)
                        + " (aguardando comprador)</gold>"));
        return true;
    }

    public boolean buy(Player president, int debtId) {
        if (!plugin.getRoleManager().hasRoleAtLeast(president.getUniqueId(), RoleType.PRESIDENT)) {
            president.sendMessage(Theme.MM.deserialize("<red>Apenas o Presidente pode comprar divida.</red>"));
            return false;
        }
        Nation buyer = plugin.getNationManager().getNationByPlayer(president.getUniqueId());
        Debt d = debts.get(debtId);
        if (buyer == null || d == null || d.creditorId != null) {
            president.sendMessage(Theme.MM.deserialize("<red>Titulo indisponível.</red>"));
            return false;
        }
        if (buyer.getId().equalsIgnoreCase(d.debtorId)) {
            president.sendMessage(Theme.MM.deserialize("<red>Nao pode comprar a propria divida.</red>"));
            return false;
        }
        Nation debtor = plugin.getNationManager().getNation(d.debtorId);
        // Converte valor da divida (moeda do emissor) para moeda do comprador
        double rate = 1.0;
        if (debtor != null && !debtor.getId().equalsIgnoreCase(buyer.getId())) {
            rate = plugin.getExchangeManager().getRate(d.debtorId, buyer.getId());
        }
        double priceLocal = d.remaining * rate; // custo no BC do comprador
        if (buyer.getCentralBankBalance() < priceLocal - 0.01) {
            president.sendMessage(Theme.MM.deserialize(
                    "<red>Banco Central insuficiente. Necessario: "
                            + buyer.getCurrencySymbol() + " " + Theme.fmt(priceLocal)
                            + " (apenas BC, Tesouro nao pode ser usado).</red>"));
            return false;
        }
        buyer.removeFromCentralBank(priceLocal);
        // Credita o valor ORIGINAL (moeda do emissor) no BC do emissor
        if (debtor != null) {
            debtor.addToCentralBank(d.remaining);
            ledger.record(debtor.getId(), "DEBT_FUND", d.remaining, president.getUniqueId(), "Titulo #" + d.id + " comprado");
        }
        d.creditorId = buyer.getId();
        ledger.record(buyer.getId(), "DEBT_BUY", priceLocal, president.getUniqueId(), "Compra titulo #" + d.id);
        plugin.getNationManager().saveAll();
        president.sendMessage(Theme.MM.deserialize(
                "<green>[◆] Titulo #" + d.id + " adquirido por " + buyer.getCurrencySymbol()
                        + " " + Theme.fmt(priceLocal) + "</green>"));
        if (debtor != null) {
            president.sendMessage(Theme.MM.deserialize(
                    "<gray>BC de " + debtor.getDisplayName() + " recebeu "
                            + debtor.getCurrencySymbol() + " " + Theme.fmt(d.remaining) + "</gray>"));
        }
        if (debtor != null && debtor.getPresident() != null) {
            Player dp = Bukkit.getPlayer(debtor.getPresident());
            if (dp != null) {
                dp.sendMessage(Theme.MM.deserialize(
                        "<gold>[◆] Sua divida #" + d.id + " foi comprada por " + buyer.getDisplayName() + "</gold>"));
            }
        }
        return true;
    }

    private void processPayments() {
        for (Debt d : new ArrayList<>(debts.values())) {
            if (d.creditorId == null || d.remaining <= 0) continue;
            Nation debtor = plugin.getNationManager().getNation(d.debtorId);
            Nation creditor = plugin.getNationManager().getNation(d.creditorId);
            if (debtor == null || creditor == null) continue;

            double interest = d.remaining * 0.10;
            double amort = d.remaining * 0.10;
            double due = interest + amort;

            if (debtor.getTreasury() >= due) {
                debtor.removeTreasury(due);
                creditor.addTreasury(due);
                d.remaining = Math.max(0, d.remaining - amort);
                d.taxRedirect = false;
                ledger.record(d.debtorId, "DEBT_PAY", due, null, "Pagamento titulo #" + d.id);
                if (d.remaining <= 0.01) {
                    d.remaining = 0;
                    debts.remove(d.id);
                }
            } else {
                // falencia parcial: redireciona impostos
                d.taxRedirect = true;
                Bukkit.broadcast(Theme.MM.deserialize(
                        plugin.getConfigManager().getPrefix() + "<red>" + debtor.getDisplayName()
                                + " nao pagou a divida #" + d.id + ". Impostos redirecionados a "
                                + creditor.getDisplayName() + ".</red>"));
            }
        }
        plugin.getNationManager().saveAll();
    }

    /** Aplica redirecionamento de imposto se ativo. Retorna true se redirecionou. */
    public boolean redirectTax(String debtorNationId, double issp, double isdn) {
        String target = getTaxRedirectTarget(debtorNationId);
        if (target == null) return false;
        Nation creditor = plugin.getNationManager().getNation(target);
        Nation debtor = plugin.getNationManager().getNation(debtorNationId);
        if (creditor == null) return false;
        // ISSP + ISDN vao ao tesouro do credor (abatimento da divida)
        double total = issp + isdn;
        creditor.addTreasury(total);
        for (Debt d : debts.values()) {
            if (d.taxRedirect && target.equals(d.creditorId) && d.debtorId.equalsIgnoreCase(debtorNationId)) {
                d.remaining = Math.max(0, d.remaining - total);
                if (d.remaining <= 0.01) {
                    d.remaining = 0;
                    d.taxRedirect = false;
                    debts.remove(d.id);
                }
                break;
            }
        }
        return true;
    }
}
