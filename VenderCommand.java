package com.nationswaw.commands;

import com.nationswaw.NationsWaW;
import com.nationswaw.gui.Theme;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class VenderCommand implements CommandExecutor {
    private final NationsWaW plugin;
    public VenderCommand(NationsWaW plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (args.length < 1) {
            p.sendMessage(Theme.MM.deserialize(
                    "<yellow>/vender <valor_total></yellow>\n" +
                    "<gray>Segure o item. O valor informado e o TOTAL do stack.</gray>\n" +
                    "<gray>Preco/unidade = valor / quantidade de itens na mao.</gray>\n" +
                    "<dark_gray>Ex: 64x STONE + /vender 640 → 10,00 por unidade</dark_gray>"));
            return true;
        }
        try {
            double totalPrice = Double.parseDouble(args[0].replace(",", "."));
            if (totalPrice <= 0) {
                p.sendMessage(Theme.MM.deserialize("<red>Valor deve ser maior que zero.</red>"));
                return true;
            }
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand == null || hand.getType() == Material.AIR || hand.getAmount() <= 0) {
                p.sendMessage(Theme.MM.deserialize("<red>Segure o item na mao.</red>"));
                return true;
            }
            int amount = hand.getAmount();
            // Valor informado e o TOTAL do stack → divide pela quantidade
            double pricePerUnit = totalPrice / amount;
            plugin.getShopManager().listItem(p, pricePerUnit);
        } catch (NumberFormatException e) {
            p.sendMessage(Theme.MM.deserialize("<red>Valor inválido.</red>"));
        }
        return true;
    }
}
