package com.nationswaw.commands;

import com.nationswaw.NationsWaW;
import com.nationswaw.gui.Theme;
import com.nationswaw.model.Nation;
import com.nationswaw.model.PendingConversion;
import com.nationswaw.utils.ChatFormatter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class MoedaCommand implements CommandExecutor {
    private final NationsWaW plugin;
    private static final Map<String, String> CODE_TO_ID = new HashMap<>();
    static {
        CODE_TO_ID.put("BRL", "brasil"); CODE_TO_ID.put("REAL", "brasil"); CODE_TO_ID.put("BRASIL", "brasil");
        CODE_TO_ID.put("JPY", "japao"); CODE_TO_ID.put("IENE", "japao"); CODE_TO_ID.put("JAPAO", "japao");
        CODE_TO_ID.put("USD", "eua"); CODE_TO_ID.put("DOLAR", "eua"); CODE_TO_ID.put("EUA", "eua");
        CODE_TO_ID.put("RUB", "russia"); CODE_TO_ID.put("RUBLO", "russia"); CODE_TO_ID.put("RUSSIA", "russia");
    }

    public MoedaCommand(NationsWaW plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        if (args.length >= 4 && args[0].equalsIgnoreCase("converter")) {
            String fromCode = args[1].toUpperCase();
            String toCode = args[3].toUpperCase();
            String fromId = CODE_TO_ID.getOrDefault(fromCode, args[1].toLowerCase());
            String toId = CODE_TO_ID.getOrDefault(toCode, args[3].toLowerCase());
            double amount;
            try { amount = Double.parseDouble(args[2]); }
            catch (Exception e) {
                p.sendMessage(Theme.MM.deserialize("<red>Valor inválido.</red>"));
                return true;
            }
            Nation fromN = plugin.getNationManager().getNation(fromId);
            Nation toN = plugin.getNationManager().getNation(toId);
            if (fromN == null || toN == null) {
                p.sendMessage(Theme.MM.deserialize("<red>Moeda invalida. Use: BRL, JPY, USD, RUB ou brasil/japao/eua/russia</red>"));
                return true;
            }
            double minA = plugin.getConfigManager().getMinAmount();
            double maxA = plugin.getConfigManager().getMaxAmount();
            if (amount < minA || amount > maxA) {
                p.sendMessage(Theme.MM.deserialize(
                        "<red>[X] Valor fora dos limites. Minimo: "
                        + String.format("%,.0f", minA) + " | Maximo: "
                        + String.format("%,.0f", maxA) + "</red>"));
                return true;
            }
            if (plugin.getEconomyManager().getBalance(p.getUniqueId(), fromId) < amount) {
                p.sendMessage(Theme.MM.deserialize("<red>Saldo insuficiente.</red>"));
                return true;
            }
            double rate = plugin.getExchangeManager().getRate(fromId, toId);
            double issp = amount * plugin.getConfigManager().getIssp();
            double isdn = amount * plugin.getConfigManager().getIsdn();
            double effective = amount - issp - isdn;
            double receive = effective * rate;

            plugin.getPendingManager().put(new PendingConversion(p.getUniqueId(), fromId, toId, amount, 30_000));
            p.sendMessage(ChatFormatter.conversionPreview(
                    fromN.getCurrencySymbol(), amount, toN.getCurrencySymbol(),
                    rate, issp, isdn, effective, receive,
                    fromN.getCurrencyName(), toN.getCurrencyName()));
            return true;
        }

        // default: show balances
        Nation n = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
        if (n == null) {
            p.sendMessage(Theme.MM.deserialize("<yellow>Imigrante. Use /moeda converter BRL 100 USD</yellow>"));
            return true;
        }
        double bal = plugin.getEconomyManager().getBalance(p.getUniqueId(), n.getId());
        p.sendMessage(Theme.MM.deserialize(plugin.getConfigManager().getPrefix() +
                "<gold>" + n.getCurrencyName() + " (" + n.getCurrencySymbol() + ")</gold>\n" +
                "<gray>Saldo: <green>" + String.format("%,.2f", bal) + "</green>\n" +
                "<gray>Tesouro: <aqua>" + String.format("%,.0f", n.getTreasury()) + "</aqua>\n" +
                "<gray>Banco Central: <yellow>" + String.format("%,.2f", n.getCentralBankBalance()) + "</yellow>\n" +
                "<dark_gray>/moeda converter BRL 100 USD</dark_gray>"));
        return true;
    }
}
