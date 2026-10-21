package com.nationswaw.gui;

import com.nationswaw.NationsWaW;
import com.nationswaw.model.Nation;
import com.nationswaw.systems.DebtManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DebtGUI implements Listener {
    private final NationsWaW plugin;
    /** viewer -> ordered debt ids for open offers menu */
    private final Map<UUID, List<Integer>> offerIds = new HashMap<>();
    private final Map<UUID, String> mode = new HashMap<>(); // "offers" | "active"

    public DebtGUI(NationsWaW plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player p) {
        openOffers(p);
    }

    public void openOffers(Player p) {
        mode.put(p.getUniqueId(), "offers");
        Inventory inv = Bukkit.createInventory(null, 54, Theme.title("TITULOS PUBLICOS"));
        Theme.fillAll(inv);

        Nation viewer = plugin.getNationManager().getNationByPlayer(p.getUniqueId());

        inv.setItem(4, Theme.item(Material.PAPER,
                "<gradient:#FFD700:#FFA000><bold>Ofertas de Titulos</bold></gradient>",
                "<gray>Clique para comprar (apenas Presidente)</gray>",
                "<gray>Pagamento exclusivo do Banco Central</gray>",
                "<gray>Juros 10% + Amortizacao 10% a cada 3h</gray>"));

        inv.setItem(8, Theme.item(Material.CHEST,
                "<aqua><bold>Dividas Ativas / Compradas</bold></aqua>",
                "<gray>Ver titulos que sua nacao paga ou recebe</gray>",
                "",
                "<yellow>» Clique para abrir</yellow>"));

        List<Integer> ids = new ArrayList<>();
        int slot = 19;
        for (DebtManager.Debt d : plugin.getDebtManager().getOpenOffers()) {
            if (slot >= 44) break;
            if (slot % 9 == 0 || slot % 9 == 8) { slot++; continue; }
            Nation debtor = plugin.getNationManager().getNation(d.debtorId);
            if (debtor == null || d.remaining <= 0) continue;

            double risk = d.riskPercent(debtor);
            String riskColor = risk < 20 ? "green" : (risk <= 60 ? "yellow" : "red");

            double rate = 1.0;
            String costLine = "<gray>Custo para voce:</gray> <green>" + debtor.getCurrencySymbol()
                    + " " + Theme.fmt(d.remaining) + "</green>";
            String rateLine = "<gray>Cambio Atual:</gray> <yellow>1:1</yellow>";
            if (viewer != null && !viewer.getId().equalsIgnoreCase(d.debtorId)) {
                rate = plugin.getExchangeManager().getRate(d.debtorId, viewer.getId());
                double cost = d.remaining * rate;
                rateLine = "<gray>Cambio Atual:</gray> <yellow>1 " + currencyCode(debtor) + " = "
                        + String.format("%.4f", rate) + " " + currencyCode(viewer) + "</yellow>";
                costLine = "<gray>Custo para voce:</gray> <green>" + viewer.getCurrencySymbol()
                        + " " + Theme.fmt(cost) + " (" + currencyCode(viewer) + ")</green>";
            }

            Material icon = bannerFor(d.debtorId);
            inv.setItem(slot, Theme.item(icon,
                    "<gold>◆ Titulo da Divida: " + debtor.getDisplayName() + "</gold>",
                    "<gray>Valor Original:</gray> <gold>" + debtor.getCurrencySymbol() + " "
                            + Theme.fmt(d.remaining) + " (" + currencyCode(debtor) + ")</gold>",
                    rateLine,
                    costLine,
                    "<gray>Juros:</gray> <yellow>10% + 10% Amortizacao a cada 3h</yellow>",
                    "<gray>Tesouro de Garantia:</gray> <white>" + debtor.getCurrencySymbol() + " "
                            + Theme.fmt(debtor.getTreasury()) + "</white>",
                    "<gray>Risco de Calote:</gray> <" + riskColor + ">"
                            + String.format("%.1f", risk) + "%</" + riskColor + ">",
                    "<aqua>OFERTA ABERTA</aqua>",
                    "",
                    "<yellow>» Clique para comprar (Presidente)</yellow>"));
            ids.add(d.id);
            slot++;
        }
        offerIds.put(p.getUniqueId(), ids);

        inv.setItem(45, Theme.item(Material.ARROW, "<yellow>« Voltar</yellow>"));
        inv.setItem(53, Theme.item(Material.BARRIER, "<red>Fechar</red>"));
        p.openInventory(inv);
    }

    public void openActive(Player p) {
        mode.put(p.getUniqueId(), "active");
        Inventory inv = Bukkit.createInventory(null, 54, Theme.title("DIVIDAS ATIVAS"));
        Theme.fillAll(inv);

        Nation my = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
        inv.setItem(4, Theme.item(Material.BOOK,
                "<gradient:#1565C0:#42A5F5><bold>Dividas Ativas / Compradas</bold></gradient>",
                "<gray>Titulos em que sua nacao e credora ou devedora</gray>"));

        int slot = 19;
        if (my != null) {
            for (DebtManager.Debt d : plugin.getDebtManager().getAll()) {
                if (d.creditorId == null || d.remaining <= 0) continue;
                boolean isDebtor = d.debtorId.equalsIgnoreCase(my.getId());
                boolean isCreditor = d.creditorId.equalsIgnoreCase(my.getId());
                if (!isDebtor && !isCreditor) continue;
                if (slot >= 44) break;
                if (slot % 9 == 0 || slot % 9 == 8) { slot++; continue; }

                Nation debtor = plugin.getNationManager().getNation(d.debtorId);
                Nation creditor = plugin.getNationManager().getNation(d.creditorId);
                double nextPay = d.remaining * 0.20;
                String role = isDebtor ? "<red>VOCE E DEVEDOR</red>" : "<green>VOCE E CREDOR</green>";
                String status = d.taxRedirect ? "<red>Impostos redirecionados</red>" : "<green>Pagamento normal</green>";

                inv.setItem(slot, Theme.item(bannerFor(d.debtorId),
                        "<gold>◆ Titulo #" + d.id + " - "
                                + (debtor != null ? debtor.getDisplayName() : d.debtorId) + "</gold>",
                        role,
                        "<gray>Saldo restante:</gray> <white>"
                                + (debtor != null ? debtor.getCurrencySymbol() : "") + " "
                                + Theme.fmt(d.remaining) + "</white>",
                        "<gray>Proxima parcela (3h):</gray> <yellow>"
                                + Theme.fmt(nextPay) + "</yellow> <dark_gray>(10% juros + 10% amort)</dark_gray>",
                        "<gray>Credor:</gray> <aqua>"
                                + (creditor != null ? creditor.getDisplayName() : d.creditorId) + "</aqua>",
                        "<gray>Status:</gray> " + status));
                slot++;
            }
        }

        inv.setItem(45, Theme.item(Material.ARROW, "<yellow>« Voltar as ofertas</yellow>"));
        inv.setItem(53, Theme.item(Material.BARRIER, "<red>Fechar</red>"));
        p.openInventory(inv);
    }

    private static String currencyCode(Nation n) {
        if (n == null) return "?";
        return switch (n.getId().toLowerCase()) {
            case "brasil" -> "BRL";
            case "eua" -> "USD";
            case "japao" -> "JPY";
            case "russia" -> "RUB";
            default -> n.getId().toUpperCase();
        };
    }

    private static Material bannerFor(String id) {
        return switch (id == null ? "" : id.toLowerCase()) {
            case "brasil" -> Material.LIME_BANNER;
            case "eua" -> Material.BLUE_BANNER;
            case "japao" -> Material.WHITE_BANNER;
            case "russia" -> Material.RED_BANNER;
            default -> Material.PAPER;
        };
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(e.getView().title());
        boolean offers = title.contains("TITULOS") || title.contains("TÍTULOS");
        boolean active = title.contains("DIVIDAS ATIVAS") || title.contains("DÍVIDAS ATIVAS");
        if (!offers && !active) return;
        e.setCancelled(true);

        if (e.getSlot() == 53) { p.closeInventory(); return; }

        if (offers) {
            if (e.getSlot() == 45) { plugin.getNationGUI().open(p); return; }
            if (e.getSlot() == 8) { openActive(p); return; }
            List<Integer> ids = offerIds.get(p.getUniqueId());
            if (ids == null || ids.isEmpty()) return;
            int idx = 0;
            int target = e.getSlot();
            for (int s = 19; s < 44; s++) {
                if (s % 9 == 0 || s % 9 == 8) continue;
                if (s == target) {
                    if (idx < ids.size()) {
                        plugin.getDebtManager().buy(p, ids.get(idx));
                        openOffers(p);
                    }
                    return;
                }
                idx++;
            }
        } else if (active) {
            if (e.getSlot() == 45) { openOffers(p); }
        }
    }
}
