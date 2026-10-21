package com.nationswaw.commands;

import com.nationswaw.NationsWaW;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MapaCommand implements CommandExecutor {
    private final NationsWaW plugin;
    public MapaCommand(NationsWaW plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Apenas jogadores.");
            return true;
        }
        plugin.getTerritoryMapManager().giveMap(p);
        return true;
    }
}
