package com.nationswaw.commands;

import com.nationswaw.NationsWaW;
import com.nationswaw.gui.Theme;
import com.nationswaw.model.Nation;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class EconomiaCommand implements CommandExecutor {
    private final NationsWaW plugin;

    public EconomiaCommand(NationsWaW plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // /economia ou /economia status
        if (args.length == 0 || args[0].equalsIgnoreCase("status") || args[0].equalsIgnoreCase("cambio")
                || args[0].equalsIgnoreCase("taxas")) {
            sendGlobalTable(sender);
            return true;
        }
        sender.sendMessage(Theme.MM.deserialize(
                "<yellow>/economia status</yellow> <gray>- tabela completa de paridades</gray>"));
        return true;
    }

    private void sendGlobalTable(CommandSender sender) {
        List<Nation> nations = new ArrayList<>(plugin.getNationManager().getAllNations());
        StringBuilder sb = new StringBuilder();
        sb.append("<gold>==========================================</gold>\n");
        sb.append("<gradient:#FFD700:#FFA000><bold>[◆] MERCADO DE CAMBIO GLOBAL - NationsWaW</bold></gradient>\n");
        sb.append("<gold>==========================================</gold>\n");

        for (Nation from : nations) {
            for (Nation to : nations) {
                if (from.getId().equals(to.getId())) continue;
                double rate = plugin.getExchangeManager().getRate(from.getId(), to.getId());
                sb.append("<white>[■] 1 ").append(from.getCurrencyName())
                        .append(" (").append(codeOf(from)).append(")</white>")
                        .append("   <yellow>= ").append(String.format("%.4f", rate)).append(" ")
                        .append(to.getCurrencyName()).append(" (").append(codeOf(to)).append(")</yellow>\n");
            }
        }
        sb.append("<gold>==========================================</gold>\n");
        sb.append("<gray>[◆] Taxas dinamicas atualizadas em tempo real.</gray>\n");
        sb.append("<gray>[◆] ISSP 3% + ISDN 2% na conversao (95% liquido).</gray>\n");
        sb.append("<gold>==========================================</gold>");
        sender.sendMessage(Theme.MM.deserialize(sb.toString()));
    }

    private String codeOf(Nation n) {
        return switch (n.getId().toLowerCase()) {
            case "brasil" -> "BRL";
            case "eua" -> "USD";
            case "japao" -> "JPY";
            case "russia" -> "RUB";
            default -> n.getId().toUpperCase();
        };
    }
}
