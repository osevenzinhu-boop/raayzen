package com.nationswaw.commands;

import com.nationswaw.NationsWaW;
import com.nationswaw.gui.ShopGUI;
import com.nationswaw.gui.Theme;
import com.nationswaw.model.Nation;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ShopCommand implements CommandExecutor {
    private final NationsWaW plugin;
    public ShopCommand(NationsWaW plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        String nationId;
        if (args.length >= 1) {
            nationId = args[0].toLowerCase()
                    .replace("brasil", "brasil").replace("eua", "eua")
                    .replace("japao", "japao").replace("japão", "japao")
                    .replace("russia", "russia").replace("rússia", "russia");
            // also accept display names partially
            if (plugin.getNationManager().getNation(nationId) == null) {
                for (Nation n : plugin.getNationManager().getAllNations()) {
                    if (n.getDisplayName().equalsIgnoreCase(args[0]) || n.getId().equalsIgnoreCase(args[0])) {
                        nationId = n.getId();
                        break;
                    }
                }
            }
        } else {
            Nation my = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
            if (my == null) {
                p.sendMessage(Theme.MM.deserialize("<red>Informe a nacao: /shop brasil|eua|japao|russia</red>"));
                return true;
            }
            nationId = my.getId();
        }
        if (plugin.getNationManager().getNation(nationId) == null) {
            p.sendMessage(Theme.MM.deserialize("<red>Nação invalida.</red>"));
            return true;
        }
        plugin.getShopGUI().open(p, nationId);
        return true;
    }
}
