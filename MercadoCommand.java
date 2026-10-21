package com.nationswaw.commands;

import com.nationswaw.NationsWaW;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MercadoCommand implements CommandExecutor {
    private final NationsWaW plugin;

    public MercadoCommand(NationsWaW plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        plugin.getMarketManager().open(p);
        return true;
    }
}
