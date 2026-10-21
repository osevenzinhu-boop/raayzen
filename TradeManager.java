package com.nationswaw.systems;

import com.nationswaw.NationsWaW;
import com.nationswaw.gui.Theme;
import com.nationswaw.model.Nation;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TradeManager {
    private final NationsWaW plugin;
    private final Map<String, Double> tradeVolume = new ConcurrentHashMap<>();
    private double tariff = 0.05;

    public TradeManager(NationsWaW plugin) {
        this.plugin = plugin;
        for (String id : plugin.getConfigManager().getNationIds()) tradeVolume.put(id, 0.0);
    }

    public boolean exportItems(Player player, String targetId, Material mat, int amount, double price) {
        Nation from = plugin.getNationManager().getNationByPlayer(player.getUniqueId());
        Nation to = plugin.getNationManager().getNation(targetId);
        if (from == null || to == null || from.getId().equalsIgnoreCase(targetId)) {
            player.sendMessage(Theme.MM.deserialize("<red>Exportacao invalida.</red>")); return false;
        }
        int has = 0;
        for (ItemStack i : player.getInventory().getContents())
            if (i != null && i.getType() == mat) has += i.getAmount();
        if (has < amount) { player.sendMessage(Theme.MM.deserialize("<red>Itens insuficientes.</red>")); return false; }
        double total = price * amount;
        double tax = total * tariff;
        double net = total - tax;
        int left = amount;
        for (ItemStack i : player.getInventory().getContents()) {
            if (i != null && i.getType() == mat && left > 0) {
                int take = Math.min(left, i.getAmount());
                i.setAmount(i.getAmount() - take); left -= take;
            }
        }
        plugin.getEconomyManager().addBalance(player.getUniqueId(), targetId, net);
        from.addToCentralBank(tax);
        tradeVolume.merge(from.getId(), total, Double::sum);
        tradeVolume.merge(to.getId(), -total * 0.5, Double::sum);
        plugin.getExchangeManager().applyDynamicRates();
        player.sendMessage(Theme.MM.deserialize("<green>Exportou " + amount + "x " + mat + " por " +
                String.format("%.2f", net) + " " + to.getCurrencyName() + "</green>"));
        return true;
    }
}
