package com.nationswaw.commands;

import com.nationswaw.NationsWaW;
import com.nationswaw.gui.Theme;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class VotarCommand implements CommandExecutor {
    private final NationsWaW plugin;

    public VotarCommand(NationsWaW plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        // /votar [id] [sim|nao] still works; bare /votar opens GUI
        if (args.length == 0) {
            plugin.getVotesGUI().open(p);
            return true;
        }
        if (args.length >= 2) {
            try {
                int id = Integer.parseInt(args[0]);
                boolean yes = args[1].equalsIgnoreCase("sim") || args[1].equalsIgnoreCase("yes")
                        || args[1].equalsIgnoreCase("s") || args[1].equalsIgnoreCase("favor");
                boolean ok = plugin.getVotingManager().castVote(p.getUniqueId(), id, yes);
                p.sendMessage(Theme.MM.deserialize(ok
                        ? (yes ? "<green>Voto A FAVOR registrado!</green>" : "<yellow>Voto CONTRA registrado!</yellow>")
                        : "<red>Nao foi possivel votar.</red>"));
            } catch (NumberFormatException e) {
                p.sendMessage(Theme.MM.deserialize("<yellow>/votar</yellow> <gray>abre o menu</gray>"));
                p.sendMessage(Theme.MM.deserialize("<yellow>/votar [id] sim|nao</yellow>"));
            }
            return true;
        }
        // /votar <id> -> open vote submenu
        try {
            int id = Integer.parseInt(args[0]);
            plugin.getVotesGUI().openVote(p, id);
        } catch (NumberFormatException e) {
            plugin.getVotesGUI().open(p);
        }
        return true;
    }
}
