package com.nationswaw.commands;

import com.nationswaw.NationsWaW;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TopCommand implements CommandExecutor {
    private final NationsWaW plugin;
    public TopCommand(NationsWaW plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        plugin.getTopGUI().openMain(p);
        return true;
    }
}
