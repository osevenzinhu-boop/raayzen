package com.nationswaw.gui;

import com.nationswaw.NationsWaW;
import com.nationswaw.model.Nation;
import com.nationswaw.model.RoleType;
import com.nationswaw.systems.MonopolyManager;
import com.nationswaw.systems.MonopolyManager.Category;
import com.nationswaw.systems.MonopolyManager.ShopItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MonopolyGUI implements Listener {
    private final NationsWaW plugin;
    private final Map<UUID, Category> viewing = new HashMap<>();
    private final Map<UUID, Map<Integer, Material>> slotItems = new HashMap<>();

    public MonopolyGUI(NationsWaW plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openMain(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, Theme.title("LOJA GLOBAL"));
        Theme.fillAll(inv);

        int[] slots = {11, 13, 15};
        Category[] cats = Category.values();
        for (int i = 0; i < cats.length; i++) {
            Category cat = cats[i];
            String ownerId = plugin.getMonopolyManager().getOwner(cat);
            Nation owner = ownerId != null ? plugin.getNationManager().getNation(ownerId) : null;
            String mono = owner != null ? owner.getDisplayName() : "Nenhum";
            String currency = owner != null ? owner.getCurrencyName() : "Nenhum";
            inv.setItem(slots[i], Theme.item(cat.icon,
                    "<gold><bold>◆ Loja de " + cat.display + "</bold></gold>",
                    Theme.DIV,
                    "<gray>Monopolio:</gray> <white>" + mono + "</white>",
                    "<gray>Moeda dos Itens:</gray> <yellow>" + currency + "</yellow>",
                    Theme.DIV,
                    ownerId == null
                            ? "<gray>Adquirir por:</gray> <aqua>"
                            + Theme.fmt(MonopolyManager.costOf(cat)) + "</aqua> <dark_gray>(BC)</dark_gray>"
                            : "<green>Loja aberta — clique para ver itens</green>",
                    Theme.DIV,
                    "<yellow>» Clique para abrir</yellow>"));
        }
        inv.setItem(22, Theme.item(Material.BARRIER, "<red>Fechar</red>"));
        viewing.remove(p.getUniqueId());
        p.openInventory(inv);
    }

    public void openCategory(Player p, Category cat) {
        Inventory inv = Bukkit.createInventory(null, 54, Theme.title("LOJA · " + cat.display.toUpperCase()));
        Theme.fillAll(inv);

        String ownerId = plugin.getMonopolyManager().getOwner(cat);
        Nation owner = ownerId != null ? plugin.getNationManager().getNation(ownerId) : null;
        Nation my = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
        boolean isPres = plugin.getRoleManager().hasRoleAtLeast(p.getUniqueId(), RoleType.PRESIDENT);

        inv.setItem(4, Theme.item(cat.icon,
                "<gold><bold>◆ " + cat.display + "</bold></gold>",
                Theme.DIV,
                "<gray>Monopolio:</gray> <white>"
                        + (owner != null ? owner.getDisplayName() : "Nenhum") + "</white>",
                "<gray>Moeda:</gray> <yellow>"
                        + (owner != null ? owner.getCurrencyName() : "—") + "</yellow>"));

        if (ownerId == null && isPres && my != null
                && plugin.getMonopolyManager().getCategoryOwnedBy(my.getId()) == null) {
            inv.setItem(49, Theme.item(Material.EMERALD_BLOCK,
                    "<green><bold>Adquirir Monopolio</bold></green>",
                    Theme.DIV,
                    "<gray>Custo:</gray> <yellow>" + my.getCurrencySymbol() + " "
                            + Theme.fmt(MonopolyManager.costOf(cat)) + "</yellow>",
                    "<gray>Debitado do Banco Central</gray>",
                    "<red>Apenas 1 monopólio por nacao</red>",
                    Theme.DIV,
                    "<yellow>» Clique para comprar</yellow>"));
        }

        Map<Integer, Material> map = new HashMap<>();
        if (ownerId != null) {
            List<ShopItem> items = MonopolyManager.itemsOf(cat);
            int[] itemSlots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
            for (int i = 0; i < items.size() && i < itemSlots.length; i++) {
                ShopItem si = items.get(i);
                double price = plugin.getMonopolyManager().getPrice(cat, si.material);
                String priceLine;
                if (my != null && !my.getId().equalsIgnoreCase(ownerId) && owner != null) {
                    double rate = plugin.getExchangeManager().getRate(ownerId, my.getId());
                    double converted = price * rate;
                    double tax = converted * (plugin.getConfigManager().getIssp() + plugin.getConfigManager().getIsdn());
                    priceLine = "<gray>Preco:</gray> <yellow>" + owner.getCurrencySymbol() + " "
                            + Theme.fmt(price) + "</yellow>\n"
                            + "<gray>Para voce:</gray> <aqua>~" + my.getCurrencySymbol() + " "
                            + Theme.fmt(converted + tax) + "</aqua> <dark_gray>(+impostos)</dark_gray>";
                } else if (owner != null) {
                    priceLine = "<gray>Preco:</gray> <yellow>" + owner.getCurrencySymbol() + " "
                            + Theme.fmt(price) + "</yellow>";
                } else {
                    priceLine = "<gray>Preco:</gray> <yellow>" + Theme.fmt(price) + "</yellow>";
                }
                // Theme.item doesn't support multiline in one string well - split
                inv.setItem(itemSlots[i], Theme.item(si.material,
                        "<white><bold>" + si.name + "</bold></white>",
                        Theme.DIV,
                        priceLine.contains("\n") ? priceLine.split("\n")[0] : priceLine,
                        priceLine.contains("\n") ? priceLine.split("\n")[1] : "",
                        Theme.DIV,
                        "<yellow>» Clique para comprar</yellow>"));
                map.put(itemSlots[i], si.material);
            }
        } else {
            inv.setItem(22, Theme.item(Material.BARRIER,
                    "<red>Loja fechada</red>",
                    "<gray>Nenhuma nacao detem este monopólio.</gray>"));
        }

        inv.setItem(45, Theme.item(Material.ARROW, "<yellow>« Voltar</yellow>"));
        inv.setItem(53, Theme.item(Material.BARRIER, "<red>Fechar</red>"));
        viewing.put(p.getUniqueId(), cat);
        slotItems.put(p.getUniqueId(), map);
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(e.getView().title());
        boolean isMain = title.contains("LOJA GLOBAL") || title.contains("LOJAS GLOBAIS");
        boolean isCat = title.contains("LOJA ·");
        if (!isMain && !isCat) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == null || e.getClickedInventory().equals(p.getInventory())) return;
        ItemStack cur = e.getCurrentItem();
        if (cur == null || cur.getType().isAir()) return;
        String mat = cur.getType().name();
        if (mat.contains("GLASS") || mat.contains("PANE")) return;

        int s = e.getSlot();
        if (isMain) {
            if (s == 22 && cur.getType() == Material.BARRIER) { p.closeInventory(); return; }
            if (s == 11) openCategory(p, Category.PROTECTION);
            else if (s == 13) openCategory(p, Category.ARMAMENT);
            else if (s == 15) openCategory(p, Category.FOOD);
            return;
        }

        Category cat = viewing.get(p.getUniqueId());
        if (cat == null) return;
        if (s == 45 && cur.getType() == Material.ARROW) { openMain(p); return; }
        if (s == 53 && cur.getType() == Material.BARRIER) { p.closeInventory(); return; }
        if (s == 49 && cur.getType() == Material.EMERALD_BLOCK) {
            plugin.getMonopolyManager().buyMonopoly(p, cat);
            openCategory(p, cat);
            return;
        }
        Map<Integer, Material> map = slotItems.get(p.getUniqueId());
        if (map != null && map.containsKey(s)) {
            plugin.getMonopolyManager().purchaseItem(p, cat, map.get(s));
        }
    }
}
