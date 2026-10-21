package com.nationswaw.commands;

import com.nationswaw.NationsWaW;
import com.nationswaw.gui.Theme;
import com.nationswaw.model.RoleType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /claim e /claim info — apenas Presidente. */
public class ClaimCommand implements CommandExecutor {
    private final NationsWaW plugin;
    public ClaimCommand(NationsWaW plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        if (!plugin.getRoleManager().hasRoleAtLeast(p.getUniqueId(), RoleType.PRESIDENT)) {
            p.sendMessage(Theme.MM.deserialize("<red>Apenas o Presidente pode fazer isto!</red>"));
            return true;
        }

        var n = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
        if (n == null) {
            p.sendMessage(Theme.MM.deserialize("<red>Sem nação.</red>"));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("info")) {
            int c = plugin.getClaimManager().countClaims(n.getId());
            p.sendMessage(Theme.MM.deserialize(
                    "<gold>[◆] Territorio " + n.getDisplayName() + "</gold>\n" +
                    "<gray>Claims:</gray> <white>" + c + "/1500</white>\n" +
                    "<gray>Proximo custo:</gray> <yellow>" +
                    Theme.fmt(plugin.getClaimManager().nextClaimCost(n.getId())) + "</yellow>"));
            plugin.getClaimManager().showNearbyClaims(p);
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("unclaim")) {
            plugin.getClaimManager().unclaim(p, p.getLocation().getChunk());
            return true;
        }

        plugin.getClaimManager().claim(p, p.getLocation().getChunk());
        return true;
    }
}
