package com.nationswaw.gui;

import com.nationswaw.NationsWaW;
import com.nationswaw.model.Nation;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public class CarteiraGUI implements Listener {
    private final NationsWaW plugin;

    public CarteiraGUI(NationsWaW plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, Theme.title("CARTEIRA · " + p.getName().toUpperCase()));
        Nation my = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
        String baseId = my != null ? my.getId() : "brasil";
        String baseName = my != null ? my.getCurrencyName() : "Real";
        String baseSym = my != null ? my.getCurrencySymbol() : "R$";

        inv.setItem(4, Theme.item(Material.PLAYER_HEAD,
                "<gradient:#FFD700:#FFA000><bold>CARTEIRA - " + p.getName() + "</bold></gradient>",
                Theme.DIV,
                "<gray>Moeda base:</gray> <yellow>" + baseName + " (" + baseSym + ")</yellow>",
                "<gray>Saldos e cotacoes locais.</gray>",
                Theme.DIV,
                "<dark_gray>NationsWaW Premium</dark_gray>"));

        String[] ids = {"brasil", "japao", "eua", "russia"};
        Material[] mats = {Material.LIME_BANNER, Material.RED_BANNER, Material.BLUE_BANNER, Material.WHITE_BANNER};
        int[] slots = {19, 21, 23, 25};
        double totalLocal = 0;
        for (int i = 0; i < ids.length; i++) {
            Nation n = plugin.getNationManager().getNation(ids[i]);
            if (n == null) continue;
            double bal = plugin.getEconomyManager().getBalance(p.getUniqueId(), ids[i]);
            // converter saldo desta moeda para a moeda local do jogador
            double inLocal = bal * plugin.getExchangeManager().getRate(ids[i], baseId);
            totalLocal += inLocal;
            String emoji = plugin.getTreasuryImpactManager().getStatusEmoji(ids[i]);
            inv.setItem(slots[i], Theme.item(mats[i],
                    emoji + " <gold><bold>" + n.getCurrencyName().toUpperCase() + "</bold></gold>",
                    Theme.DIV,
                    "<gray>Simbolo:</gray> <yellow>" + n.getCurrencySymbol() + "</yellow>",
                    "<gray>Saldo:</gray> <green>" + n.getCurrencySymbol() + " " + Theme.fmt(bal) + "</green>",
                    "<gray>Equiv. em " + baseName + ":</gray> <aqua>" + baseSym + " " + Theme.fmt(inLocal) + "</aqua>",
                    Theme.DIV,
                    "<gray>Status:</gray> " + emoji + " <white>"
                            + plugin.getTreasuryImpactManager().getStatusLabel(ids[i]) + "</white>"));
        }

        inv.setItem(29, Theme.item(Material.GOLD_INGOT,
                "<yellow><bold>TOTAL APROXIMADO EM " + baseName.toUpperCase() + "</bold></yellow>",
                Theme.DIV,
                "<aqua><bold>" + baseSym + " " + Theme.fmt(totalLocal) + "</bold></aqua>",
                Theme.DIV,
                "<gray>Convertido pelas taxas atuais</gray>"));

        // Cotacoes apenas da moeda base do jogador
        java.util.List<String> rates = new java.util.ArrayList<>();
        rates.add(Theme.DIV);
        for (String id : ids) {
            if (id.equalsIgnoreCase(baseId)) continue;
            Nation t = plugin.getNationManager().getNation(id);
            if (t == null) continue;
            double r = plugin.getExchangeManager().getRate(baseId, id);
            rates.add("<gray>1 " + baseName + " =</gray> <yellow>"
                    + String.format("%.4f", r) + " " + t.getCurrencyName() + "</yellow>");
        }
        rates.add(Theme.DIV);
        rates.add("<dark_gray>Taxas dinamicas · Paridade inicial 1:1</dark_gray>");
        inv.setItem(31, Theme.item(Material.PAPER,
                "<aqua><bold>COTACOES ATUAIS</bold></aqua>",
                rates.toArray(new String[0])));

        if (my != null) {
            inv.setItem(33, Theme.item(Material.GOLD_BLOCK,
                    "<gold><bold>TESOURO DA SUA NACAO</bold></gold>",
                    Theme.DIV,
                    "<gray>Nacao:</gray> <white>" + my.getDisplayName() + "</white>",
                    "<gray>Tesouro Nacional:</gray> <gold>" + my.getCurrencySymbol() + " " + Theme.fmt(my.getTreasury()) + "</gold>",
                    "<gray>Banco Central:</gray> <gold>" + my.getCurrencySymbol() + " " + Theme.fmt(my.getCentralBankBalance()) + "</gold>",
                    Theme.DIV,
                    plugin.getTreasuryImpactManager().getStatusEmoji(my.getId()) + " <white>"
                            + plugin.getTreasuryImpactManager().getStatusLabel(my.getId()) + "</white>"));
        }

        Theme.fillAll(inv);
        Theme.fillFooter(inv, 45, 53);
        p.openInventory(inv);
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.0f);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String t = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(e.getView().title());
        if (!t.contains("CARTEIRA")) return;
        e.setCancelled(true);
        if (e.getSlot() == 45 || e.getSlot() == 53) p.closeInventory();
    }
}
