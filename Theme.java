package com.nationswaw.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Tema premium brasileiro - dourado, esmeralda, moldura completa */
public final class Theme {
    public static final MiniMessage MM = MiniMessage.miniMessage();
    public static final Material BORDER = Material.BLACK_STAINED_GLASS_PANE;
    public static final Material FILL = Material.GRAY_STAINED_GLASS_PANE;
    public static final Material GOLD = Material.YELLOW_STAINED_GLASS_PANE;
    public static final Material GREEN = Material.LIME_STAINED_GLASS_PANE;

    private Theme() {}

    public static ItemStack pane(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MM.deserialize("<black> </black>"));
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack item(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MM.deserialize(name));
            if (lore.length > 0) {
                List<Component> l = new ArrayList<>();
                for (String s : lore) {
                    if (s == null) continue;
                    l.add(MM.deserialize(s.isEmpty() ? "<black> </black>" : s));
                }
                meta.lore(l);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public static Component title(String text) {
        return MM.deserialize("<gradient:#FFD700:#FFA000><bold>◆ " + text + " ◆</bold></gradient>");
    }

    /** Preenche TODOS os slots vazios com vidro (moldura + fundo) */
    public static void fillAll(Inventory inv) {
        int size = inv.getSize();
        int rows = size / 9;
        for (int i = 0; i < size; i++) {
            if (inv.getItem(i) != null) continue;
            int row = i / 9;
            int col = i % 9;
            if (row == 0 || row == rows - 1 || col == 0 || col == 8) {
                inv.setItem(i, pane(i % 2 == 0 ? BORDER : GOLD));
            } else {
                inv.setItem(i, pane(FILL));
            }
        }
    }

    public static void fillBorder(Inventory inv) {
        fillAll(inv);
    }

    public static void fillFooter(Inventory inv, int backSlot, int closeSlot) {
        int size = inv.getSize();
        inv.setItem(backSlot, item(Material.ARROW,
                "<yellow><bold>« Voltar</bold></yellow>",
                "<dark_gray>▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬</dark_gray>",
                "<gray>Retorna ao menu anterior</gray>"));
        inv.setItem(closeSlot, item(Material.BARRIER,
                "<red><bold>✖ Fechar</bold></red>",
                "<dark_gray>▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬</dark_gray>",
                "<gray>Fecha este menu</gray>"));
        int mid = size - 5;
        if (inv.getItem(mid) == null || inv.getItem(mid).getType().name().contains("GLASS")) {
            inv.setItem(mid, item(Material.PAPER,
                    "<gray>[ <yellow>1</yellow> <gray>/</gray> <yellow>1</yellow> <gray>]</gray>",
                    "<dark_gray>NationsWaW Premium</dark_gray>"));
        }
    }

    public static String DIV = "<dark_gray>▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬</dark_gray>";
    public static String fmt(double v) { return String.format("%,.2f", v); }
    public static String fmtInt(double v) { return String.format("%,.0f", v); }
}
