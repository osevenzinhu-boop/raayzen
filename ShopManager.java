package com.nationswaw.systems;

import com.nationswaw.NationsWaW;
import com.nationswaw.gui.Theme;
import com.nationswaw.model.Nation;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ShopManager {
    private final NationsWaW plugin;
    private final Map<Integer, ShopListing> listings = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);

    public static class ShopListing {
        public final int id;
        public final UUID seller;
        public final String nationId;
        public final Material material;
        public final int amount;
        public final double pricePerUnit;
        public final long createdAt;

        public ShopListing(int id, UUID seller, String nationId, Material material, int amount, double pricePerUnit) {
            this.id = id;
            this.seller = seller;
            this.nationId = nationId;
            this.material = material;
            this.amount = amount;
            this.pricePerUnit = pricePerUnit;
            this.createdAt = System.currentTimeMillis();
        }
    }

    public ShopManager(NationsWaW plugin) { this.plugin = plugin; }

    public int listItem(Player player, double pricePerUnit) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR || hand.getAmount() <= 0) {
            player.sendMessage(Theme.MM.deserialize("<red>Segure o item na mao.</red>"));
            return -1;
        }
        Nation n = plugin.getNationManager().getNationByPlayer(player.getUniqueId());
        if (n == null) {
            player.sendMessage(Theme.MM.deserialize("<red>Precisa ser civil de uma nação.</red>"));
            return -1;
        }
        if (pricePerUnit <= 0) {
            player.sendMessage(Theme.MM.deserialize("<red>Preco inválido.</red>"));
            return -1;
        }
        int amount = hand.getAmount();
        Material mat = hand.getType();
        player.getInventory().setItemInMainHand(null);
        int id = nextId.getAndIncrement();
        listings.put(id, new ShopListing(id, player.getUniqueId(), n.getId(), mat, amount, pricePerUnit));
        player.sendMessage(Theme.MM.deserialize(plugin.getConfigManager().getPrefix() +
                "<green>Listado: " + amount + "x " + mat.name() + "</green>"));
        player.sendMessage(Theme.MM.deserialize(
                "<gray>Preco unitario:</gray> <yellow>" + n.getCurrencySymbol() + " " +
                String.format("%.2f", pricePerUnit) + "/un</yellow> <dark_gray>(ID #" + id + ")</dark_gray>"));
        player.sendMessage(Theme.MM.deserialize(
                "<gray>Total do anuncio:</gray> <aqua>" + n.getCurrencySymbol() + " " +
                String.format("%.2f", pricePerUnit * amount) + "</aqua>"));
        return id;
    }

    public boolean buy(Player buyer, int listingId, int qty) {
        ShopListing list = listings.get(listingId);
        if (list == null) {
            buyer.sendMessage(Theme.MM.deserialize("<red>Anuncio nao encontrado.</red>"));
            return false;
        }
        if (qty <= 0 || qty > list.amount) {
            buyer.sendMessage(Theme.MM.deserialize("<red>Quantidade invalida (estoque: " + list.amount + ").</red>"));
            return false;
        }
        Nation sellerNation = plugin.getNationManager().getNation(list.nationId);
        Nation buyerNation = plugin.getNationManager().getNationByPlayer(buyer.getUniqueId());
        if (sellerNation == null) return false;

        boolean local = buyerNation != null && buyerNation.getId().equalsIgnoreCase(list.nationId);
        double taxRate = local ? 0.15 : 0.45;
        double playerShare = local ? 0.85 : 0.55;

        double unitPrice = list.pricePerUnit;
        // embargo on export prices
        if (!local) {
            unitPrice *= plugin.getEmbargoManager().getEmbargoMultiplier(list.nationId);
        }
        double total = unitPrice * qty;
        String payCurrency = local ? list.nationId :
                (buyerNation != null ? buyerNation.getId() : list.nationId);

        // if export, convert: buyer pays in own currency via exchange rate
        double payAmount = total;
        if (!local && buyerNation != null) {
            double rate = plugin.getExchangeManager().getRate(list.nationId, buyerNation.getId());
            // total is in seller currency; convert to buyer currency
            payAmount = total * rate;
            // apply spread+iof on conversion conceptually - simplify: use rate only + small fee
            double spread = payAmount * plugin.getConfigManager().getSpread();
            double iof = payAmount * plugin.getConfigManager().getIof();
            payAmount = payAmount + spread + iof; // buyer pays more
        }

        if (!plugin.getEconomyManager().removeBalance(buyer.getUniqueId(), payCurrency, payAmount)) {
            buyer.sendMessage(Theme.MM.deserialize("<red>Saldo insuficiente.</red>"));
            return false;
        }

        double toSeller = total * playerShare;
        double toBank = total * taxRate;
        plugin.getEconomyManager().addBalance(list.seller, list.nationId, toSeller);
        sellerNation.addToCentralBank(toBank);

        // give items
        ItemStack give = new ItemStack(list.material, qty);
        Map<Integer, ItemStack> overflow = buyer.getInventory().addItem(give);
        for (ItemStack left : overflow.values()) {
            buyer.getWorld().dropItemNaturally(buyer.getLocation(), left);
        }

        // update or remove listing
        if (qty >= list.amount) {
            listings.remove(listingId);
        } else {
            listings.put(listingId, new ShopListing(list.id, list.seller, list.nationId, list.material,
                    list.amount - qty, list.pricePerUnit));
        }
        plugin.getNationManager().saveAll();
        buyer.sendMessage(Theme.MM.deserialize(plugin.getConfigManager().getPrefix() +
                "<green>Comprou " + qty + "x " + list.material.name() + " por " +
                String.format("%.2f", payAmount) + (local ? " (taxa 15%)" : " (exportacao 45% + cambio)") + "</green>"));
        return true;
    }

    public Collection<ShopListing> getListings(String nationId) {
        List<ShopListing> out = new ArrayList<>();
        for (ShopListing l : listings.values()) {
            if (l.nationId.equalsIgnoreCase(nationId)) out.add(l);
        }
        return out;
    }

    public Collection<ShopListing> getAllListings() { return listings.values(); }
    public ShopListing getListing(int id) { return listings.get(id); }
}
