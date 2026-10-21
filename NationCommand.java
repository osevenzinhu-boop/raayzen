package com.nationswaw.commands;

import com.nationswaw.NationsWaW;
import com.nationswaw.gui.Theme;
import com.nationswaw.model.*;
import com.nationswaw.utils.Ledger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NationCommand implements CommandExecutor {
    private final NationsWaW plugin;
    private final Ledger ledger;
    private final Map<UUID, PendingJoin> joinRequests = new ConcurrentHashMap<>();
    private final Map<String, PendingAlliance> allianceRequests = new ConcurrentHashMap<>();
    private static final int MAX_CITIZENS = 25;
    private static final int MAX_GOVERNORS = 1;
    private static final int MAX_MAYORS = 3;

    public NationCommand(NationsWaW plugin) {
        this.plugin = plugin;
        this.ledger = new Ledger(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        MiniMessage MM = Theme.MM;

        if (args.length == 0 || args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("menu")) {
            plugin.getNationGUI().open(p);
            return true;
        }

        String sub = args[0].toLowerCase();

        // /nation emitir <valor>
        if (sub.equals("emitir")) {
            if (args.length < 2) {
                p.sendMessage(MM.deserialize("<yellow>/nation emitir [valor]</yellow>"));
                return true;
            }
            try {
                double v = Double.parseDouble(args[1].replace(",", "."));
                plugin.getDebtManager().emit(p, v);
            } catch (NumberFormatException ex) {
                p.sendMessage(MM.deserialize("<red>Valor inválido.</red>"));
            }
            return true;
        }
        // /nation dividas
        if (sub.equals("dividas") || sub.equals("dívidas") || sub.equals("debts")) {
            plugin.getDebtGUI().open(p);
            return true;
        }

        if (sub.equals("claim")) {
            plugin.getClaimManager().claim(p, p.getLocation().getChunk());
            return true;
        }
        if (sub.equals("unclaim")) {
            plugin.getClaimManager().unclaim(p, p.getLocation().getChunk());
            return true;
        }


        // /nation reload (admin)
        if (sub.equals("reload")) {
            if (!p.hasPermission("nationswaw.admin") && !p.isOp()) {
                p.sendMessage(MM.deserialize("<red>Sem permissao.</red>"));
                return true;
            }
            plugin.reloadConfig();
            try { plugin.getDisplayManager().reload(); } catch (Exception ignored) {}
            try { plugin.getCacheService().invalidateAll(); } catch (Exception ignored) {}
            p.sendMessage(MM.deserialize("<green>Config, tab.yml e caches recarregados.</green>"));
            return true;
        }

        // /nation loja
        if (sub.equals("global") || sub.equals("loja") || sub.equals("monopólio") || sub.equals("monopolio") || sub.equals("shopglobal")) {
            plugin.getMonopolyGUI().openMain(p);
            return true;
        }

        // /nation tregua
        if (sub.equals("tregua") || sub.equals("truce") || sub.equals("paz")) {
            if (args.length >= 3 && args[1].equalsIgnoreCase("votar")) {
                boolean yes = args[2].equalsIgnoreCase("sim") || args[2].equalsIgnoreCase("yes")
                        || args[2].equalsIgnoreCase("s") || args[2].equalsIgnoreCase("favor");
                if (args.length < 4) {
                    p.sendMessage(MM.deserialize("<yellow>/nation tregua votar sim|nao [nacaoProponente]</yellow>"));
                    return true;
                }
                plugin.getWarManager().voteTruce(p, args[3], yes);
                return true;
            }
            plugin.getWarManager().proposeTruce(p);
            return true;
        }

        // /nation cidade
        if (sub.equals("cidade") || sub.equals("city") || sub.equals("spawn")) {
            plugin.getCityTeleportManager().requestTeleport(p);
            return true;
        }

        // /nation entrar [nacao] — sem args abre GUI
        if (sub.equals("entrar") || sub.equals("join")) {
            if (plugin.getNationManager().getNationByPlayer(p.getUniqueId()) != null) {
                p.sendMessage(MM.deserialize("<red>Voce ja pertence a uma nação. Saia antes de entrar em outra.</red>"));
                return true;
            }
            if (args.length < 2) {
                plugin.getJoinNationGUI().open(p);
                return true;
            }
            String nid = resolveNation(args[1]);
            Nation n = plugin.getNationManager().getNation(nid);
            if (n == null) {
                p.sendMessage(MM.deserialize("<red>Nação invalida. Use: brasil, japao, eua, russia</red>"));
                return true;
            }
            plugin.getJoinRequestManager().submit(p, nid);
            return true;
        }


        // /nation aceitar <jogador>  (atalho)
        if (sub.equals("aceitar") && args.length == 2 && !args[1].equalsIgnoreCase("entrar")
                && !args[1].equalsIgnoreCase("aliado")) {
            return handleAcceptJoin(p, args[1]);
        }
        // /nation recusar <jogador>
        if (sub.equals("recusar") && args.length == 2 && !args[1].equalsIgnoreCase("entrar")
                && !args[1].equalsIgnoreCase("aliado")) {
            return handleRefuseJoin(p, args[1]);
        }

        // /nation aceitar entrar <jogador>
        if (sub.equals("aceitar") && args.length >= 3 && args[1].equalsIgnoreCase("entrar")) {
            return handleAcceptJoin(p, args[2]);
        }
        // /nation recusar entrar <jogador>
        if (sub.equals("recusar") && args.length >= 3 && args[1].equalsIgnoreCase("entrar")) {
            return handleRefuseJoin(p, args[2]);
        }

        // /nation expulsar <jogador>
        if (sub.equals("expulsar") || sub.equals("kick")) {
            if (args.length < 2) {
                p.sendMessage(MM.deserialize("<yellow>/nation expulsar [jogador]</yellow>"));
                return true;
            }
            RoleType role = plugin.getRoleManager().getRole(p.getUniqueId());
            if (!role.hasPowerOf(RoleType.GOVERNOR)) {
                p.sendMessage(MM.deserialize("<red>Apenas Presidente ou Governador pode expulsar.</red>"));
                return true;
            }
            Nation my = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
            if (my == null) return true;
            Player target = Bukkit.getPlayerExact(args[1]);
            UUID targetUuid = target != null ? target.getUniqueId() : null;
            if (targetUuid == null) {
                // offline
                for (UUID u : my.getCitizens()) {
                    if (Bukkit.getOfflinePlayer(u).getName() != null
                            && Bukkit.getOfflinePlayer(u).getName().equalsIgnoreCase(args[1])) {
                        targetUuid = u; break;
                    }
                }
            }
            if (targetUuid == null || plugin.getNationManager().getNationByPlayer(targetUuid) == null
                    || !plugin.getNationManager().getNationByPlayer(targetUuid).getId().equals(my.getId())) {
                p.sendMessage(MM.deserialize("<red>Jogador nao encontrado na sua nação.</red>"));
                return true;
            }
            if (targetUuid.equals(my.getPresident())) {
                p.sendMessage(MM.deserialize("<red>Nao e possivel expulsar o Presidente.</red>"));
                return true;
            }
            plugin.getNationManager().removeFromNation(targetUuid);
            plugin.getRoleManager().setRole(targetUuid, RoleType.NONE, null);
            plugin.getNationManager().saveAll();
            plugin.getRoleManager().saveAll();
            p.sendMessage(MM.deserialize("<green>Jogador expulso.</green>"));
            if (target != null) target.sendMessage(MM.deserialize("<red>Voce foi expulso da nação " + my.getDisplayName() + ".</red>"));
            return true;
        }

        // /nation aliado <nacao>
        if (sub.equals("aliado") || sub.equals("ally")) {
            if (args.length < 2) {
                p.sendMessage(MM.deserialize("<yellow>/nation aliado [nacao]</yellow>"));
                return true;
            }
            if (!plugin.getRoleManager().hasRoleAtLeast(p.getUniqueId(), RoleType.PRESIDENT)) {
                p.sendMessage(MM.deserialize("<red>Apenas o Presidente pode propor alianca.</red>"));
                return true;
            }
            Nation my = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
            if (my == null) return true;
            String tid = resolveNation(args[1]);
            Nation target = plugin.getNationManager().getNation(tid);
            if (target == null || target.getId().equals(my.getId())) {
                p.sendMessage(MM.deserialize("<red>Nação invalida.</red>"));
                return true;
            }
            if (plugin.getDiplomacyManager().getRelation(my.getId(), tid) == DiplomaticRelation.ALLY) {
                p.sendMessage(MM.deserialize("<yellow>Ja sao aliados.</yellow>"));
                return true;
            }
            if (plugin.getDiplomacyManager().isAtWar(my.getId(), tid)) {
                p.sendMessage(MM.deserialize("<red>Em guerra. Nao e possivel aliar.</red>"));
                return true;
            }
            allianceRequests.put(my.getId() + ":" + tid, new PendingAlliance(my.getId(), tid, 10 * 60_000L));
            p.sendMessage(MM.deserialize("<green>Proposta de alianca enviada para " + target.getDisplayName() + ".</green>"));
            notifyPresident(target, my.getDisplayName() + " propôs alianca.",
                    "/nation aceitar aliado " + my.getId(),
                    "/nation recusar aliado " + my.getId());
            return true;
        }

        // /nation aceitar aliado <nacao>
        if (sub.equals("aceitar") && args.length >= 3 && args[1].equalsIgnoreCase("aliado")) {
            return handleAcceptAlly(p, resolveNation(args[2]));
        }
        if (sub.equals("recusar") && args.length >= 3 && args[1].equalsIgnoreCase("aliado")) {
            return handleRefuseAlly(p, resolveNation(args[2]));
        }

        // depositar / sacar
        Nation n = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
        if (n == null) {
            p.sendMessage(MM.deserialize("<red>Voce nao pertence a uma nação. Use /nation entrar [nacao]</red>"));
            return true;
        }
        if (args.length < 3) {
            p.sendMessage(MM.deserialize(
                    "<yellow>/nation info</yellow>\n" +
                    "<yellow>/nation entrar [nacao]</yellow>\n" +
                    "<yellow>/nation expulsar [jogador]</yellow>\n" +
                    "<yellow>/nation aliado [nacao]</yellow>\n" +
                    "<yellow>/nation depositar [tesouro|bc] [valor]</yellow>\n" +
                    "<yellow>/nation claim</yellow> <gray>(Presidente)</gray>\n" +
                    "<yellow>/nation sacar bc [valor]</yellow> <gray>(limite 100k/6h)</gray>"));
            return true;
        }
        return handleTreasuryOp(p, n, sub, args);
    }

    private boolean handleAcceptJoin(Player leader, String playerName) {
        RoleType role = plugin.getRoleManager().getRole(leader.getUniqueId());
        if (!role.hasPowerOf(RoleType.MAYOR)) {
            leader.sendMessage(Theme.MM.deserialize("<red>Apenas Presidente, Governador ou Prefeito pode aceitar.</red>"));
            return true;
        }
        Nation my = plugin.getNationManager().getNationByPlayer(leader.getUniqueId());
        if (my == null) return true;
        if (my.getPopulation() >= MAX_CITIZENS) {
            leader.sendMessage(Theme.MM.deserialize("<red>Nação cheia (" + MAX_CITIZENS + ").</red>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            // tenta offline via OfflinePlayer
            org.bukkit.OfflinePlayer off = Bukkit.getOfflinePlayer(playerName);
            if (off.getUniqueId() == null || (!off.hasPlayedBefore() && !off.isOnline())) {
                leader.sendMessage(Theme.MM.deserialize("<red>Jogador nao encontrado.</red>"));
                return true;
            }
            if (!plugin.getJoinRequestManager().hasRequest(off.getUniqueId(), my.getId())
                    && !joinRequests.containsKey(off.getUniqueId())) {
                leader.sendMessage(Theme.MM.deserialize("<red>Nenhuma solicitacao pendente deste jogador.</red>"));
                return true;
            }
            plugin.getJoinRequestManager().removeRequest(off.getUniqueId(), my.getId());
            joinRequests.remove(off.getUniqueId());
            plugin.getRoleManager().setRole(off.getUniqueId(), my.getId(), RoleType.CITIZEN);
            plugin.getNationManager().saveAll();
            leader.sendMessage(Theme.MM.deserialize("<green>" + playerName + " aceito na nacao (offline)!</green>"));
            return true;
        }
        boolean hasReq = plugin.getJoinRequestManager().hasRequest(target.getUniqueId(), my.getId());
        PendingJoin req = joinRequests.get(target.getUniqueId());
        if (!hasReq && (req == null || req.isExpired() || !req.nationId.equalsIgnoreCase(my.getId()))) {
            leader.sendMessage(Theme.MM.deserialize("<red>Nenhuma solicitacao pendente deste jogador.</red>"));
            return true;
        }
        joinRequests.remove(target.getUniqueId());
        plugin.getJoinRequestManager().removeRequest(target.getUniqueId(), my.getId());
        plugin.getNationManager().removeFromNation(target.getUniqueId());
        my.getCitizens().add(target.getUniqueId());
        plugin.getRoleManager().setRole(target.getUniqueId(), RoleType.CITIZEN, my.getId());
        plugin.getNationManager().saveAll();
        plugin.getRoleManager().saveAll();
        leader.sendMessage(Theme.MM.deserialize("<green>" + target.getName() + " aceito na nação!</green>"));
        target.sendMessage(Theme.MM.deserialize("<green>Sua solicitacao para " + my.getDisplayName() + " foi ACEITA!</green>"));
        target.showTitle(net.kyori.adventure.title.Title.title(
                Theme.MM.deserialize("<green><bold>BEM-VINDO</bold></green>"),
                Theme.MM.deserialize("<yellow>" + my.getDisplayName() + "</yellow>")));
        return true;
    }

    private boolean handleRefuseJoin(Player leader, String playerName) {
        RoleType role = plugin.getRoleManager().getRole(leader.getUniqueId());
        if (!role.hasPowerOf(RoleType.MAYOR)) {
            leader.sendMessage(Theme.MM.deserialize("<red>Sem permissão.</red>"));
            return true;
        }
        Nation my = plugin.getNationManager().getNationByPlayer(leader.getUniqueId());
        Player target = Bukkit.getPlayerExact(playerName);
        if (my != null) {
            org.bukkit.OfflinePlayer off = target != null ? target : Bukkit.getOfflinePlayer(playerName);
            if (off != null) {
                plugin.getJoinRequestManager().removeRequest(off.getUniqueId(), my.getId());
                joinRequests.remove(off.getUniqueId());
            }
        }
        if (target != null) {
            joinRequests.remove(target.getUniqueId());
            target.sendMessage(Theme.MM.deserialize("<red>Sua solicitacao para " + (my != null ? my.getDisplayName() : "a nacao") + " foi RECUSADA.</red>"));
        }
        leader.sendMessage(Theme.MM.deserialize("<yellow>Solicitacao recusada.</yellow>"));
        return true;
    }

    private boolean handleAcceptAlly(Player p, String fromId) {
        if (!plugin.getRoleManager().hasRoleAtLeast(p.getUniqueId(), RoleType.PRESIDENT)) {
            p.sendMessage(Theme.MM.deserialize("<red>Apenas o Presidente pode aceitar alianca.</red>"));
            return true;
        }
        Nation my = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
        if (my == null) return true;
        String key = fromId + ":" + my.getId();
        PendingAlliance req = allianceRequests.get(key);
        if (req == null || req.isExpired()) {
            p.sendMessage(Theme.MM.deserialize("<red>Nenhuma proposta pendente desta nação.</red>"));
            return true;
        }
        allianceRequests.remove(key);
        plugin.getDiplomacyManager().setRelation(fromId, my.getId(), DiplomaticRelation.ALLY);
        Nation from = plugin.getNationManager().getNation(fromId);
        String fname = from != null ? from.getDisplayName() : fromId;
        Bukkit.broadcast(Theme.MM.deserialize(plugin.getConfigManager().getPrefix() +
                "<green><bold>ALIANCA:</bold></green> <yellow>" + fname + "</yellow> e <yellow>" + my.getDisplayName() + "</yellow> agora sao aliados!"));
        for (Player pl : Bukkit.getOnlinePlayers()) {
            if (plugin.getNationManager().getNationByPlayer(pl.getUniqueId()) != null) {
                Nation pn = plugin.getNationManager().getNationByPlayer(pl.getUniqueId());
                if (pn.getId().equals(my.getId()) || pn.getId().equalsIgnoreCase(fromId)) {
                    pl.showTitle(net.kyori.adventure.title.Title.title(
                            Theme.MM.deserialize("<green><bold>ALIANCA FORMADA</bold></green>"),
                            Theme.MM.deserialize("<yellow>" + fname + " + " + my.getDisplayName() + "</yellow>")));
                }
            }
        }
        return true;
    }

    private boolean handleRefuseAlly(Player p, String fromId) {
        if (!plugin.getRoleManager().hasRoleAtLeast(p.getUniqueId(), RoleType.PRESIDENT)) return true;
        Nation my = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
        if (my == null) return true;
        allianceRequests.remove(fromId + ":" + my.getId());
        p.sendMessage(Theme.MM.deserialize("<yellow>Proposta de alianca recusada.</yellow>"));
        Nation from = plugin.getNationManager().getNation(fromId);
        if (from != null && from.getPresident() != null) {
            Player pres = Bukkit.getPlayer(from.getPresident());
            if (pres != null) pres.sendMessage(Theme.MM.deserialize("<red>" + my.getDisplayName() + " recusou a alianca.</red>"));
        }
        return true;
    }

    private boolean handleTreasuryOp(Player p, Nation n, String op, String[] args) {
        double amount;
        try { amount = Double.parseDouble(args[2]); }
        catch (Exception e) {
            p.sendMessage(Theme.MM.deserialize("<red>Valor inválido.</red>"));
            return true;
        }
        if (amount <= 0) {
            p.sendMessage(Theme.MM.deserialize("<red>Valor deve ser positivo.</red>"));
            return true;
        }
        String account = args[1].toLowerCase();
        boolean isTreasury = account.equals("tesouro") || account.equals("treasury");
        boolean isBc = account.equals("bc") || account.equals("banco") || account.equals("central");
        if (!isTreasury && !isBc) {
            p.sendMessage(Theme.MM.deserialize("<red>Conta invalida. Use: tesouro ou bc</red>"));
            return true;
        }
        RoleType role = plugin.getRoleManager().getRole(p.getUniqueId());
        if (op.equals("depositar") || op.equals("deposit")) {
            if (!role.hasPowerOf(RoleType.GOVERNOR)) {
                p.sendMessage(Theme.MM.deserialize("<red>Apenas Presidente ou Governador pode depositar.</red>"));
                return true;
            }
            if (!plugin.getEconomyManager().removeBalance(p.getUniqueId(), n.getId(), amount)) {
                p.sendMessage(Theme.MM.deserialize("<red>Saldo insuficiente na carteira.</red>"));
                return true;
            }
            double before = isTreasury ? n.getTreasury() : n.getCentralBankBalance();
            if (isTreasury) n.addTreasury(amount); else n.addToCentralBank(amount);
            double after = isTreasury ? n.getTreasury() : n.getCentralBankBalance();
            ledger.record(n.getId(), "DEPOSIT_" + (isTreasury ? "TREASURY" : "BC"), amount, p.getUniqueId(), "Deposito");
            plugin.getNationManager().saveAll();
            plugin.getTreasuryImpactManager().recalculateAllRates();
            sendReceipt(p, n, isTreasury ? "Tesouro Nacional" : "Banco Central", "Deposito", amount, before, after);
            return true;
        }
        if (op.equals("sacar") || op.equals("withdraw")) {
            if (isTreasury) {
                p.sendMessage(Theme.MM.deserialize(
                        "<red>Saque do Tesouro Nacional e proibido.</red>\n" +
                        "<gray>O Tesouro e reserva estrategica. Apenas deposito e permitido.</gray>\n" +
                        "<gray>Use: /nation sacar bc [valor] (limite 100.000 / 6h)</gray>"));
                return true;
            }
            if (!role.hasPowerOf(RoleType.PRESIDENT)) {
                p.sendMessage(Theme.MM.deserialize("<red>Apenas o Presidente pode sacar do Banco Central.</red>"));
                return true;
            }
            // Limite 100k / 6h
            var wl = plugin.getWithdrawLimitManager();
            if (!wl.canWithdraw(n.getId(), amount)) {
                double used = wl.getWithdrawnInWindow(n.getId());
                double rem = wl.getRemaining(n.getId());
                p.sendMessage(Theme.MM.deserialize(
                        "<red>Limite de saque do BC atingido.</red>\n" +
                        "<gray>Limite:</gray> <yellow>100.000</yellow> <gray>a cada 6 horas</gray>\n" +
                        "<gray>Ja sacado nesta janela:</gray> <red>" + Theme.fmt(used) + "</red>\n" +
                        "<gray>Disponível:</gray> <green>" + Theme.fmt(rem) + "</green>\n" +
                        "<gray>Reset em:</gray> <aqua>" + wl.formatRemainingTime(n.getId()) + "</aqua>"));
                return true;
            }
            double before = n.getCentralBankBalance();
            if (!n.removeFromCentralBank(amount)) {
                p.sendMessage(Theme.MM.deserialize("<red>Saldo insuficiente no Banco Central.</red>"));
                return true;
            }
            double after = n.getCentralBankBalance();
            plugin.getEconomyManager().addBalance(p.getUniqueId(), n.getId(), amount);
            wl.record(n.getId(), amount);
            ledger.record(n.getId(), "WITHDRAW_BC", amount, p.getUniqueId(), "Saque BC");
            plugin.getNationManager().saveAll();
            sendReceipt(p, n, "Banco Central", "Saque", amount, before, after);
            p.sendMessage(Theme.MM.deserialize(
                    "<gray>Limite restante na janela de 6h:</gray> <yellow>"
                    + Theme.fmt(wl.getRemaining(n.getId())) + "</yellow>\n" +
                    "<gray>Reset da janela em:</gray> <aqua>" + wl.formatRemainingTime(n.getId()) + "</aqua>"));
            return true;
        }
        p.sendMessage(Theme.MM.deserialize("<red>Subcomando desconhecido.</red>"));
        return true;
    }

    private void notifyLeaders(Nation n, String msg, String acceptCmd, String refuseCmd) {
        Component base = Theme.MM.deserialize(plugin.getConfigManager().getPrefix() + "<yellow>" + msg + "</yellow> ");
        Component accept = Theme.MM.deserialize("<green><bold>[ACEITAR]</bold></green>")
                .clickEvent(ClickEvent.runCommand(acceptCmd))
                .hoverEvent(HoverEvent.showText(Theme.MM.deserialize("<green>Clique para aceitar</green>")));
        Component refuse = Theme.MM.deserialize("<red><bold>[RECUSAR]</bold></red>")
                .clickEvent(ClickEvent.runCommand(refuseCmd))
                .hoverEvent(HoverEvent.showText(Theme.MM.deserialize("<red>Clique para recusar</red>")));
        Component full = base.append(accept).append(Component.text(" ")).append(refuse);
        notifyUuid(n.getPresident(), full);
        for (UUID u : n.getGovernors()) notifyUuid(u, full);
        for (UUID u : n.getMayors()) notifyUuid(u, full);
    }

    private void notifyPresident(Nation n, String msg, String acceptCmd, String refuseCmd) {
        if (n.getPresident() == null) return;
        Component base = Theme.MM.deserialize(plugin.getConfigManager().getPrefix() + "<yellow>" + msg + "</yellow> ");
        Component accept = Theme.MM.deserialize("<green><bold>[ACEITAR]</bold></green>")
                .clickEvent(ClickEvent.runCommand(acceptCmd));
        Component refuse = Theme.MM.deserialize("<red><bold>[RECUSAR]</bold></red>")
                .clickEvent(ClickEvent.runCommand(refuseCmd));
        notifyUuid(n.getPresident(), base.append(accept).append(Component.text(" ")).append(refuse));
    }

    private void notifyUuid(UUID uuid, Component c) {
        if (uuid == null) return;
        Player pl = Bukkit.getPlayer(uuid);
        if (pl != null) pl.sendMessage(c);
    }

    private String resolveNation(String raw) {
        String s = raw.toLowerCase()
                .replace("japão", "japao").replace("rússia", "russia")
                .replace("estados unidos", "eua").replace("usa", "eua");
        if (plugin.getNationManager().getNation(s) != null) return s;
        for (Nation n : plugin.getNationManager().getAllNations()) {
            if (n.getDisplayName().equalsIgnoreCase(raw) || n.getId().equalsIgnoreCase(raw)) return n.getId();
        }
        return s;
    }

    private void sendReceipt(Player p, Nation n, String account, String operation, double amount, double before, double after) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy - HH:mm"));
        String emoji = plugin.getTreasuryImpactManager().getStatusEmoji(n.getId());
        String status = plugin.getTreasuryImpactManager().getStatusLabel(n.getId());
        p.sendMessage(Theme.MM.deserialize(
                "<gold>------------------------------------------</gold>\n" +
                "<gradient:#FFD700:#FFA000><bold>  TRANSACAO - NationsWaW</bold></gradient>\n" +
                "<gold>------------------------------------------</gold>\n" +
                "<white>Conta:</white>        <yellow>" + account + "</yellow>\n" +
                "<white>Autor:</white>        <aqua>" + p.getName() + "</aqua>\n" +
                "<white>Operacao:</white>     <green>" + operation + "</green>\n" +
                "<white>Valor:</white>        <yellow>" + n.getCurrencySymbol() + " " + Theme.fmt(amount) + "</yellow>\n" +
                "<white>Saldo Anterior:</white> <gray>" + n.getCurrencySymbol() + " " + Theme.fmt(before) + "</gray>\n" +
                "<white>Saldo Novo:</white>    <green>" + n.getCurrencySymbol() + " " + Theme.fmt(after) + "</green>\n" +
                "<white>Data/Hora:</white>     <gray>" + time + "</gray>\n" +
                "<gold>------------------------------------------</gold>\n" +
                "<white>Status da moeda:</white> " + emoji + " <white>" + status + "</white>\n" +
                "<gold>------------------------------------------</gold>"
        ));
    }

    public int getMaxCitizens() { return MAX_CITIZENS; }
    public int getMaxGovernors() { return MAX_GOVERNORS; }
    public int getMaxMayors() { return MAX_MAYORS; }
}
