package com.nationswaw.commands;

import com.nationswaw.NationsWaW;
import com.nationswaw.gui.Theme;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CancelarCommand implements CommandExecutor {
    private final NationsWaW plugin;
    public CancelarCommand(NationsWaW plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (plugin.getPendingManager().remove(p.getUniqueId()) != null) {
            p.sendMessage(Theme.MM.deserialize("<yellow>Operacao cancelada.</yellow>"));
        } else {
            p.sendMessage(Theme.MM.deserialize("<gray>Nada para cancelar.</gray>"));
        }
        return true;
    }
}
