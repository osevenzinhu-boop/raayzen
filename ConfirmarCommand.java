package com.nationswaw.commands;

import com.nationswaw.NationsWaW;
import com.nationswaw.gui.Theme;
import com.nationswaw.model.ExchangeResult;
import com.nationswaw.model.PendingConversion;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ConfirmarCommand implements CommandExecutor {
    private final NationsWaW plugin;
    public ConfirmarCommand(NationsWaW plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        PendingConversion pending = plugin.getPendingManager().remove(p.getUniqueId());
        if (pending == null) {
            p.sendMessage(Theme.MM.deserialize("<red>Nenhuma operacao pendente (ou expirou).</red>"));
            return true;
        }
        if (pending.isExpired()) {
            p.sendMessage(Theme.MM.deserialize("<red>Operacao expirada (30s).</red>"));
            return true;
        }
        ExchangeResult r = plugin.getExchangeManager().convert(p.getUniqueId(), pending.fromId, pending.toId, pending.amount);
        if (r.isSuccess()) {
            p.sendMessage(Theme.MM.deserialize(plugin.getConfigManager().getPrefix() +
                    "<green>Conversao confirmada! Recebeu <bold>" + String.format("%,.2f", r.getAmountTo()) +
                    " " + r.getToCurrency() + "</bold></green>\n" +
                    "<gray>ISSP (3%): -" + String.format("%.2f", r.getSpreadAmount()) +
                    " (Tesouro destino) | ISDN (2%): -" + String.format("%.2f", r.getIofAmount()) +
                    " (BC destino)</gray>"));
        } else {
            p.sendMessage(Theme.MM.deserialize("<red>" + r.getMessage() + "</red>"));
        }
        return true;
    }
}
