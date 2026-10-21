package com.nationswaw.systems;

import com.nationswaw.NationsWaW;
import com.nationswaw.gui.Theme;
import com.nationswaw.model.Nation;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * /mercado — venda de recursos com divisao jogador/tesouro.
 */
public class MarketManager implements Listener {
    private final NationsWaW plugin;
    private final Map<UUID, Boolean> openMarket = new HashMap<>();

    public record Offer(double total, double playerPct) {
        public double playerShare() { return total * playerPct; }
        public double treasuryShare() { return total * (1.0 - playerPct); }
    }

    public MarketManager(NationsWaW plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public static Offer offerFor(Material mat) {
        if (mat == null) return null;
        String name = mat.name();
        if (mat == Material.GOLD_INGOT) return new Offer(12_000, 0.60);
        if (mat == Material.DIAMOND) return new Offer(20_000, 0.45);
        if (mat == Material.IRON_INGOT) return new Offer(8_000, 0.20);
        if (name.endsWith("_LOG") || name.equals("LOG") || name.equals("LOG_2")) return new Offer(2_000, 0.45);
        return null;
    }

    public void open(Player p) {
        Nation n = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
        if (n == null) {
            p.sendMessage(Theme.MM.deserialize("<red>Voce precisa pertencer a uma nacao para usar o mercado.</red>"));
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 54, Theme.title("MERCADO NACIONAL"));
        openMarket.put(p.getUniqueId(), true);
        p.openInventory(inv);
        p.sendMessage(Theme.MM.deserialize(
                "<gold>[◆] Mercado:</gold> <gray>Coloque itens validos e feche o bau para vender.</gray>\n"
                        + "<gray>Ouro 60/40 · Diamante 45/55 · Madeira 45/55 · Ferro 20/80</gray>"));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!Boolean.TRUE.equals(openMarket.get(p.getUniqueId()))) return;
        // permite drag no mercado
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!Boolean.TRUE.equals(openMarket.get(p.getUniqueId()))) return;
        // permite mover itens livremente
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        if (!Boolean.TRUE.equals(openMarket.remove(p.getUniqueId()))) return;

        Nation n = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
        if (n == null) {
            returnItems(p, e.getInventory());
            return;
        }

        int soldUnits = 0;
        double gross = 0;
        double playerTotal = 0;
        double treasuryTotal = 0;

        for (ItemStack stack : e.getInventory().getContents()) {
            if (stack == null || stack.getType().isAir()) continue;
            Offer offer = offerFor(stack.getType());
            if (offer == null) {
                giveOrDrop(p, stack);
                continue;
            }
            int amount = stack.getAmount();
            double unit = offer.total;
            double pShare = offer.playerShare() * amount;
            double tShare = offer.treasuryShare() * amount;
            soldUnits += amount;
            gross += unit * amount;
            playerTotal += pShare;
            treasuryTotal += tShare;
        }
        e.getInventory().clear();

        if (soldUnits <= 0) {
            p.sendMessage(Theme.MM.deserialize("<yellow>[◆] Nenhum item valido vendido.</yellow>"));
            return;
        }

        plugin.getEconomyManager().addBalance(p.getUniqueId(), n.getId(), playerTotal);
        n.addTreasury(treasuryTotal);
        try { plugin.getNationManager().saveAll(); } catch (Exception ignored) {}

        double pPct = gross > 0 ? (playerTotal / gross) * 100 : 0;
        double tPct = gross > 0 ? (treasuryTotal / gross) * 100 : 0;
        String sym = n.getCurrencySymbol();

        p.sendMessage(Theme.MM.deserialize(
                "<gold>==========================================</gold>\n"
                        + "<gradient:#FFD700:#FFA000><bold>[◆] MERCADO NACIONAL - VENDA CONCLUIDA</bold></gradient>\n"
                        + "<gray>[■] Itens Vendidos:</gray> <white>" + soldUnits + "</white>\n"
                        + "<gray>[■] Valor Bruto:</gray> <yellow>" + sym + " " + Theme.fmt(gross) + "</yellow>\n"
                        + "<gray>[■] Seu Lucro:</gray> <green>" + sym + " " + Theme.fmt(playerTotal)
                        + "</green> <dark_gray>(" + String.format("%.0f", pPct) + "%)</dark_gray>\n"
                        + "<gray>[■] Imposto para o Tesouro:</gray> <aqua>" + sym + " " + Theme.fmt(treasuryTotal)
                        + "</aqua> <dark_gray>(" + String.format("%.0f", tPct) + "%)</dark_gray>\n"
                        + "<gold>==========================================</gold>"));
    }

    private void returnItems(Player p, Inventory inv) {
        for (ItemStack stack : inv.getContents()) {
            if (stack != null && !stack.getType().isAir()) giveOrDrop(p, stack);
        }
        inv.clear();
    }

    private void giveOrDrop(Player p, ItemStack stack) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(stack);
        for (ItemStack rest : left.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
    }
}
