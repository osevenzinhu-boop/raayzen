package com.nationswaw.systems;

import com.nationswaw.NationsWaW;
import com.nationswaw.gui.Theme;
import com.nationswaw.model.Nation;
import com.nationswaw.model.RoleType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Monopolio global de 3 lojas. 1 monopólio por nação.
 */
public class MonopolyManager {
    private final NationsWaW plugin;
    public static final double MONOPOLY_COST_PROTECTION = 7_500_000;
    public static final double MONOPOLY_COST_ARMAMENT = 15_500_000;
    public static final double MONOPOLY_COST_FOOD = 5_500_000;
    /** @deprecated use costOf(Category) */
    public static final double MONOPOLY_COST = 7_500_000;

    public static double costOf(Category cat) {
        return switch (cat) {
            case PROTECTION -> MONOPOLY_COST_PROTECTION;
            case ARMAMENT -> MONOPOLY_COST_ARMAMENT;
            case FOOD -> MONOPOLY_COST_FOOD;
        };
    }

    public enum Category {
        PROTECTION("Protecao", Material.DIAMOND_CHESTPLATE),
        ARMAMENT("Armamento", Material.DIAMOND_SWORD),
        FOOD("Alimentos", Material.COOKED_BEEF);

        public final String display;
        public final Material icon;
        Category(String d, Material i) { this.display = d; this.icon = i; }
    }

    public static class ShopItem {
        public final Material material;
        public final String name;
        public final double defaultPrice;
        public ShopItem(Material m, String n, double p) {
            this.material = m; this.name = n; this.defaultPrice = p;
        }
    }

    /** category -> owner nation id */
    private final Map<Category, String> owners = new ConcurrentHashMap<>();
    /** category -> material -> custom price (optional) */
    private final Map<Category, Map<Material, Double>> prices = new ConcurrentHashMap<>();

    public MonopolyManager(NationsWaW plugin) {
        this.plugin = plugin;
        load();
    }

    public static List<ShopItem> itemsOf(Category cat) {
        return switch (cat) {
            case PROTECTION -> List.of(
                    new ShopItem(Material.DIAMOND_HELMET, "Capacete de Diamante", 200_000),
                    new ShopItem(Material.DIAMOND_CHESTPLATE, "Peitoral de Diamante", 200_000),
                    new ShopItem(Material.DIAMOND_LEGGINGS, "Calcas de Diamante", 200_000),
                    new ShopItem(Material.DIAMOND_BOOTS, "Botas de Diamante", 200_000),
                    new ShopItem(Material.SHIELD, "Escudo", 40_000),
                    new ShopItem(Material.NETHERITE_HELMET, "Capacete de Netherite", 800_000),
                    new ShopItem(Material.NETHERITE_CHESTPLATE, "Peitoral de Netherite", 800_000),
                    new ShopItem(Material.NETHERITE_LEGGINGS, "Calcas de Netherite", 800_000),
                    new ShopItem(Material.NETHERITE_BOOTS, "Botas de Netherite", 800_000)
            );
            case ARMAMENT -> List.of(
                    new ShopItem(Material.DIAMOND_SWORD, "Espada de Diamante", 250_000),
                    new ShopItem(Material.DIAMOND_AXE, "Machado de Diamante", 250_000),
                    new ShopItem(Material.TRIDENT, "Lanca de Diamante", 250_000),
                    new ShopItem(Material.NETHERITE_SWORD, "Espada de Netherite", 900_000),
                    new ShopItem(Material.NETHERITE_AXE, "Machado de Netherite", 900_000),
                    new ShopItem(Material.BOW, "Arco", 50_000),
                    new ShopItem(Material.CROSSBOW, "Crossbow", 50_000),
                    new ShopItem(Material.ARROW, "Flecha", 5_500)
            );
            case FOOD -> List.of(
                    new ShopItem(Material.COOKED_BEEF, "Carne Assada (Vaca)", 9_000),
                    new ShopItem(Material.COOKED_PORKCHOP, "Carne Assada (Porco)", 9_000),
                    new ShopItem(Material.GOLDEN_APPLE, "Maca Dourada", 90_000),
                    new ShopItem(Material.GOLDEN_CARROT, "Cenoura Dourada", 110_000),
                    new ShopItem(Material.CHORUS_FRUIT, "Chorus Fruit", 35_000)
            );
        };
    }

    public String getOwner(Category cat) {
        return owners.get(cat);
    }

    public Category getCategoryOwnedBy(String nationId) {
        for (var e : owners.entrySet()) {
            if (e.getValue().equalsIgnoreCase(nationId)) return e.getKey();
        }
        return null;
    }

    public double getPrice(Category cat, Material mat) {
        Map<Material, Double> map = prices.get(cat);
        if (map != null && map.containsKey(mat)) return map.get(mat);
        for (ShopItem si : itemsOf(cat)) {
            if (si.material == mat) return si.defaultPrice;
        }
        return -1;
    }

    public boolean buyMonopoly(Player president, Category cat) {
        if (!plugin.getRoleManager().hasRoleAtLeast(president.getUniqueId(), RoleType.PRESIDENT)) {
            president.sendMessage(Theme.MM.deserialize("<red>Apenas o Presidente pode adquirir monopólio.</red>"));
            return false;
        }
        Nation n = plugin.getNationManager().getNationByPlayer(president.getUniqueId());
        if (n == null) return false;

        if (getCategoryOwnedBy(n.getId()) != null) {
            president.sendMessage(Theme.MM.deserialize(
                    "<red>Sua nacao ja possui um monopólio. Apenas 1 por nacao.</red>"));
            return false;
        }
        if (owners.containsKey(cat)) {
            Nation own = plugin.getNationManager().getNation(owners.get(cat));
            president.sendMessage(Theme.MM.deserialize(
                    "<red>Monopolio de " + cat.display + " ja pertence a "
                            + (own != null ? own.getDisplayName() : owners.get(cat)) + ".</red>"));
            return false;
        }
        double cost = costOf(cat);
        if (!n.removeFromCentralBank(cost)) {
            president.sendMessage(Theme.MM.deserialize(
                    "<red>BC insuficiente. Necessario: " + n.getCurrencySymbol() + " "
                            + Theme.fmt(cost) + "</red>"));
            return false;
        }
        owners.put(cat, n.getId().toLowerCase());
        save();
        try { plugin.getNationManager().saveAll(); } catch (Exception ignored) {}

        president.sendMessage(Theme.MM.deserialize(
                "<gold>==========================================</gold>\n"
                        + "<green><bold>[◆] MONOPOLIO ADQUIRIDO</bold></green>\n"
                        + "<gray>[■] Categoria:</gray> <white>" + cat.display + "</white>\n"
                        + "<gray>[■] Custo:</gray> <yellow>" + n.getCurrencySymbol() + " "
                        + Theme.fmt(cost) + "</yellow> <dark_gray>(BC)</dark_gray>\n"
                        + "<gray>[■] Itens desta loja agora usam a moeda de "
                        + n.getDisplayName() + ".</gray>\n"
                        + "<gold>==========================================</gold>"));
        return true;
    }

    /**
     * Compra item da loja de monopólio com conversao automatica + ISSP/ISDN.
     */
    public boolean purchaseItem(Player buyer, Category cat, Material mat) {
        String ownerId = owners.get(cat);
        if (ownerId == null) {
            buyer.sendMessage(Theme.MM.deserialize("<red>Esta loja esta fechada (sem monopólio).</red>"));
            return false;
        }
        double price = getPrice(cat, mat);
        if (price <= 0) {
            buyer.sendMessage(Theme.MM.deserialize("<red>Item invalido.</red>"));
            return false;
        }
        Nation mono = plugin.getNationManager().getNation(ownerId);
        Nation buyerNation = plugin.getNationManager().getNationByPlayer(buyer.getUniqueId());
        if (mono == null) {
            buyer.sendMessage(Theme.MM.deserialize("<red>Nacao monopolista invalida.</red>"));
            return false;
        }

        String itemName = mat.name();
        for (ShopItem si : itemsOf(cat)) {
            if (si.material == mat) { itemName = si.name; break; }
        }

        ItemStack give = new ItemStack(mat, mat == Material.ARROW ? 16 : 1);

        // mesma nacao: paga direto na moeda local
        if (buyerNation != null && buyerNation.getId().equalsIgnoreCase(ownerId)) {
            if (!plugin.getEconomyManager().removeBalance(buyer.getUniqueId(), ownerId, price)) {
                buyer.sendMessage(Theme.MM.deserialize("<red>Saldo insuficiente.</red>"));
                return false;
            }
            mono.addToCentralBank(price);
            giveItem(buyer, give);
            try { plugin.getNationManager().saveAll(); } catch (Exception ignored) {}
            buyer.sendMessage(Theme.MM.deserialize(
                    "<green>[◆] Comprou " + itemName + " por "
                            + mono.getCurrencySymbol() + " " + Theme.fmt(price) + "</green>"));
            return true;
        }

        // internacional
        String payCurrency = buyerNation != null ? buyerNation.getId() : ownerId;
        double rate = plugin.getExchangeManager().getRate(ownerId, payCurrency);
        double converted = price * rate;
        double isspPct = plugin.getConfigManager().getIssp();
        double isdnPct = plugin.getConfigManager().getIsdn();
        double issp = converted * isspPct;
        double isdn = converted * isdnPct;
        double total = converted + issp + isdn;

        if (buyerNation == null) {
            // imigrante: tenta pagar na moeda do monopolista
            if (!plugin.getEconomyManager().removeBalance(buyer.getUniqueId(), ownerId, price * (1 + isspPct + isdnPct))) {
                buyer.sendMessage(Theme.MM.deserialize("<red>Saldo insuficiente na moeda do monopolista.</red>"));
                return false;
            }
            mono.addToCentralBank(price);
            mono.addTreasury(price * isspPct);
            mono.addToCentralBank(price * isdnPct);
            giveItem(buyer, give);
            try { plugin.getNationManager().saveAll(); } catch (Exception ignored) {}
            buyer.sendMessage(Theme.MM.deserialize("<green>[◆] Compra realizada.</green>"));
            return true;
        }

        if (!plugin.getEconomyManager().removeBalance(buyer.getUniqueId(), payCurrency, total)) {
            buyer.sendMessage(Theme.MM.deserialize(
                    "<red>Saldo insuficiente. Necessario: "
                            + buyerNation.getCurrencySymbol() + " " + Theme.fmt(total) + "</red>"));
            return false;
        }

        // monopolista recebe preco base; impostos em moeda do mono
        mono.addToCentralBank(price);
        mono.addTreasury(price * isspPct);
        mono.addToCentralBank(price * isdnPct);
        giveItem(buyer, give);
        try { plugin.getNationManager().saveAll(); } catch (Exception ignored) {}

        buyer.sendMessage(Theme.MM.deserialize(
                "<gold>==========================================</gold>\n"
                        + "<gradient:#1565C0:#42A5F5><bold>[◆] COMPRA INTERNACIONAL</bold></gradient>\n"
                        + "<gray>[■] Voce comprou:</gray> <white>" + itemName + "</white>\n"
                        + "<gray>[■] Custo Original:</gray> <yellow>" + mono.getCurrencySymbol() + " "
                        + Theme.fmt(price) + " (" + mono.getCurrencyName() + ")</yellow>\n"
                        + "<gray>[■] Conversao (Taxa Atual):</gray> <aqua>"
                        + buyerNation.getCurrencySymbol() + " " + Theme.fmt(converted)
                        + " (" + buyerNation.getCurrencyName() + ")</aqua>\n"
                        + "<gray>[■] Impostos de Conversao pagos a "
                        + mono.getDisplayName() + ":</gray> <red>"
                        + buyerNation.getCurrencySymbol() + " " + Theme.fmt(issp + isdn) + "</red>\n"
                        + "<gray>[■] Total pago:</gray> <green>"
                        + buyerNation.getCurrencySymbol() + " " + Theme.fmt(total) + "</green>\n"
                        + "<gold>==========================================</gold>"));
        return true;
    }

    private void giveItem(Player p, ItemStack item) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(item);
        for (ItemStack rest : left.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
    }

    private void load() {
        plugin.getDatabaseManager().queryAsync(conn -> {
            try {
                try (var st = conn.createStatement()) {
                    st.executeUpdate("CREATE TABLE IF NOT EXISTS monopolies (category TEXT PRIMARY KEY, nation_id TEXT)");
                }
                try (PreparedStatement ps = conn.prepareStatement("SELECT category, nation_id FROM monopolies");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        try {
                            Category c = Category.valueOf(rs.getString(1));
                            owners.put(c, rs.getString(2).toLowerCase());
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Monopoly load: " + e.getMessage());
            }
            return null;
        }).join();
    }

    public void save() {
        plugin.getDatabaseManager().executeAsync(conn -> {
            try {
                try (PreparedStatement del = conn.prepareStatement("DELETE FROM monopolies")) {
                    del.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO monopolies (category, nation_id) VALUES (?,?)")) {
                    for (var e : owners.entrySet()) {
                        ps.setString(1, e.getKey().name());
                        ps.setString(2, e.getValue());
                        ps.executeUpdate();
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Monopoly save: " + e.getMessage());
            }
        });
    }
}
