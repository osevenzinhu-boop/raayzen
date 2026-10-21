package com.nationswaw.commands;

import com.nationswaw.NationsWaW;
import com.nationswaw.gui.Theme;
import com.nationswaw.model.Nation;
import com.nationswaw.model.RoleType;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CargoCommand implements CommandExecutor {
    private final NationsWaW plugin;
    public CargoCommand(NationsWaW plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (args.length >= 3 && args[0].equalsIgnoreCase("nomear")) {
            if (!plugin.getRoleManager().hasRoleAtLeast(p.getUniqueId(), RoleType.GOVERNOR)) {
                p.sendMessage(Theme.MM.deserialize("<red>Sem permissão.</red>"));
                return true;
            }
            Nation n = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
            if (n == null) return true;
            Player t = Bukkit.getPlayerExact(args[1]);
            if (t == null) {
                p.sendMessage(Theme.MM.deserialize("<red>Jogador offline.</red>"));
                return true;
            }
            try {
                RoleType role = RoleType.valueOf(args[2].toUpperCase());
                if (role == RoleType.PRESIDENT && !plugin.getRoleManager().hasRoleAtLeast(p.getUniqueId(), RoleType.PRESIDENT)) {
                    p.sendMessage(Theme.MM.deserialize("<red>Apenas Presidente pode nomear outro Presidente.</red>"));
                    return true;
                }
                String err = plugin.getRoleManager().setRole(t.getUniqueId(), n.getId(), role);
                if (err != null) {
                    p.sendMessage(Theme.MM.deserialize("<red>" + err + "</red>"));
                    return true;
                }
                plugin.getNationManager().saveAll();
                plugin.getRoleManager().saveAll();
                p.sendMessage(Theme.MM.deserialize("<green>" + t.getName() + " nomeado como " + role.getDisplayName() + "</green>"));
                t.sendMessage(Theme.MM.deserialize("<green>Voce foi nomeado " + role.getDisplayName() + " de " + n.getDisplayName() + "</green>"));
            } catch (Exception e) {
                p.sendMessage(Theme.MM.deserialize("<red>Cargos: GOVERNOR, MAYOR, CITIZEN (limites: 1 Gov, 3 Pref, 15 total)</red>"));
            }
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("listar")) {
            Nation n = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
            if (n == null) {
                p.sendMessage(Theme.MM.deserialize("<red>Sem nação.</red>"));
                return true;
            }
            p.sendMessage(Theme.MM.deserialize(
                    "<gold>Cargos de " + n.getDisplayName() + "</gold>\n" +
                    "<gray>Presidente:</gray> <white>" + nameOf(n.getPresident()) + "</white>\n" +
                    "<gray>Governadores (" + n.getGovernors().size() + "/1):</gray> <white>" + n.getGovernors().size() + "</white>\n" +
                    "<gray>Prefeitos (" + n.getMayors().size() + "/3):</gray> <white>" + n.getMayors().size() + "</white>\n" +
                    "<gray>Civils:</gray> <white>" + n.getPopulation() + "/15</white>"));
            return true;
        }
        p.sendMessage(Theme.MM.deserialize("<yellow>/cargo nomear [jogador] [ROLE]\n/cargo listar</yellow>"));
        return true;
    }

    private String nameOf(java.util.UUID u) {
        if (u == null) return "Vago";
        String n = Bukkit.getOfflinePlayer(u).getName();
        return n != null ? n : "?";
    }
}
