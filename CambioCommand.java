package com.nationswaw.commands;

import com.nationswaw.NationsWaW;
import com.nationswaw.gui.Theme;
import com.nationswaw.model.ExchangeResult;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CambioCommand implements CommandExecutor {
    private final NationsWaW plugin;
    public CambioCommand(NationsWaW plugin) { this.plugin = plugin; }
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (args.length >= 4 && args[0].equalsIgnoreCase("converter")) {
            try {
                ExchangeResult r = plugin.getExchangeManager().convert(p.getUniqueId(), args[1].toLowerCase(), args[2].toLowerCase(), Double.parseDouble(args[3]));
                if (r.isSuccess())
                    p.sendMessage(Theme.MM.deserialize("<green>Convertido! Recebeu " + String.format("%.2f", r.getAmountTo()) + " " + r.getToCurrency() + "</green>"));
                else p.sendMessage(Theme.MM.deserialize("<red>" + r.getMessage() + "</red>"));
            } catch (Exception e) { p.sendMessage(Theme.MM.deserialize("<red>Uso: /cambio converter <origem> <destino> <qtd></red>")); }
            return true;
        }
        plugin.getExchangeGUI().openMain(p);
        return true;
    }
}
