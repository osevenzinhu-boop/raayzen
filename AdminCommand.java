package com.nationswaw.commands;

import com.nationswaw.NationsWaW;
import com.nationswaw.gui.Theme;
import com.nationswaw.model.RoleType;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AdminCommand implements CommandExecutor {
    private final NationsWaW plugin;
    public AdminCommand(NationsWaW plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("forcesalary")) {
            plugin.getSalaryManager().forcePay();
            sender.sendMessage(com.nationswaw.gui.Theme.MM.deserialize("<green>Salarios forcados.</green>"));
            return true;
        }
        if (!sender.hasPermission("nationswaw.admin")) {
            sender.sendMessage(Theme.MM.deserialize("<red>Sem permissão.</red>")); return true;
        }
        if (args.length == 0) {
            sender.sendMessage(Theme.MM.deserialize("<yellow>/admin setpresident [jogador] [nacao]\n/admin setrole [jogador] [nacao] [ROLE]\n/admin givecurrency [jogador] [nacao] [qtd]\n/admin guerramundial [off]\n/admin reload</yellow>"));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "setpresident" -> {
                if (args.length < 3) { sender.sendMessage(Theme.MM.deserialize("<red>Uso: /admin setpresident <jogador> <nacao></red>")); return true; }
                Player t = Bukkit.getPlayer(args[1]);
                if (t == null) { sender.sendMessage(Theme.MM.deserialize("<red>Offline.</red>")); return true; }
                String nid = args[2].toLowerCase();
                if (plugin.getNationManager().getNation(nid) == null) { sender.sendMessage(Theme.MM.deserialize("<red>Nação invalida.</red>")); return true; }
                plugin.getRoleManager().setRole(t.getUniqueId(), nid, RoleType.PRESIDENT);
                plugin.getEconomyManager().giveStartingBalance(t.getUniqueId(), nid);
                sender.sendMessage(Theme.MM.deserialize("<green>" + t.getName() + " e Presidente de " + nid + "!</green>"));
                t.sendMessage(Theme.MM.deserialize(plugin.getConfigManager().getPrefix() + "<gold>Voce e Presidente de " + nid + "!</gold>"));
            }
            case "setrole" -> {
                if (args.length < 4) return true;
                Player t = Bukkit.getPlayer(args[1]);
                if (t == null) return true;
                try {
                    RoleType role = RoleType.valueOf(args[3].toUpperCase());
                    plugin.getRoleManager().setRole(t.getUniqueId(), args[2].toLowerCase(), role);
                    if (role != RoleType.NONE) plugin.getEconomyManager().giveStartingBalance(t.getUniqueId(), args[2].toLowerCase());
                    sender.sendMessage(Theme.MM.deserialize("<green>Role definido.</green>"));
                } catch (Exception e) { sender.sendMessage(Theme.MM.deserialize("<red>Role inválido.</red>")); }
            }
            case "givecurrency" -> {
                if (args.length < 4) return true;
                Player t = Bukkit.getPlayer(args[1]);
                if (t == null) return true;
                try {
                    plugin.getEconomyManager().addBalance(t.getUniqueId(), args[2].toLowerCase(), Double.parseDouble(args[3]));
                    sender.sendMessage(Theme.MM.deserialize("<green>Moeda adicionada.</green>"));
                } catch (Exception e) { sender.sendMessage(Theme.MM.deserialize("<red>Erro.</red>")); }
            }
            case "cidade" -> {
                if (args.length < 3 || !args[1].equalsIgnoreCase("feita")) {
                    sender.sendMessage(Theme.MM.deserialize("<yellow>/admin cidade feita [nacao] [bonus [valor]]</yellow>"));
                    return true;
                }
                // /admin cidade feita <nacao>
                // /admin cidade feita bonus <nacao> <valor>
                if (args[2].equalsIgnoreCase("bonus")) {
                    if (args.length < 5) {
                        sender.sendMessage(Theme.MM.deserialize("<yellow>/admin cidade feita bonus [nacao] [valor]</yellow>"));
                        return true;
                    }
                    try {
                        double bonus = Double.parseDouble(args[4].replace(",", "."));
                        String nid = args[3].toLowerCase();
                        if (plugin.getInfrastructureMissionManager().completeCity(nid, bonus,
                                sender instanceof org.bukkit.entity.Player pl ? pl : null)) {
                            sender.sendMessage(Theme.MM.deserialize("<green>Cidade aprovada com bonus " + bonus + "!</green>"));
                        }
                    } catch (NumberFormatException ex) {
                        sender.sendMessage(Theme.MM.deserialize("<red>Valor invalido.</red>"));
                    }
                } else {
                    String nid = args[2].toLowerCase();
                    if (plugin.getInfrastructureMissionManager().completeCity(nid, 0,
                            sender instanceof org.bukkit.entity.Player pl ? pl : null)) {
                        sender.sendMessage(Theme.MM.deserialize("<green>Cidade de " + nid + " aprovada (10M + 10M)!</green>"));
                    }
                }
            }
            case "guerramundial", "worldwar", "ww" -> {
                if (args.length >= 2 && (args[1].equalsIgnoreCase("off") || args[1].equalsIgnoreCase("fim"))) {
                    plugin.getWarManager().endWorldWar();
                    sender.sendMessage(Theme.MM.deserialize("<green>Guerra Mundial desativada.</green>"));
                } else if (plugin.getWarManager().isWorldWarActive()) {
                    sender.sendMessage(Theme.MM.deserialize(
                            "<yellow>Guerra Mundial ja ativa. Use /admin guerramundial off</yellow>"));
                } else {
                    plugin.getWarManager().startWorldWar();
                    sender.sendMessage(Theme.MM.deserialize("<red><bold>Guerra Mundial ATIVADA!</bold></red>"));
                }
            }
            case "reload" -> { plugin.getConfigManager().load(); sender.sendMessage(Theme.MM.deserialize("<green>Config recarregada.</green>")); }
            default -> sender.sendMessage(Theme.MM.deserialize("<red>Subcomando desconhecido.</red>"));
        }
        return true;
    }
}
