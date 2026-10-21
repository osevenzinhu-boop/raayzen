package com.nationswaw.commands;

import com.nationswaw.NationsWaW;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class NacaoCommand implements CommandExecutor {
    private final NationsWaW plugin;
    public NacaoCommand(NationsWaW plugin) { this.plugin = plugin; }
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        plugin.getNationGUI().open(p);
        return true;
    }
}
