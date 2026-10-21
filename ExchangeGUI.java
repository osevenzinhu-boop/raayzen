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

/** Cotacoes com base na moeda da nação do jogador */
public class ExchangeGUI implements Listener {
    private final NationsWaW plugin;

    public ExchangeGUI(NationsWaW plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openMain(Player p) {
        Nation my = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54, Theme.title("COTACOES DE CAMBIO"));

        if (my == null) {
            inv.setItem(4, Theme.item(Material.BARRIER,
                    "<red><bold>SEM NACAO</bold></red>",
                    Theme.DIV,
                    "<gray>Voce precisa pertencer a uma nação</gray>",
                    "<gray>para ver cotacoes locais.</gray>",
                    Theme.DIV,
                    "<gray>Use /economia status para o mercado global.</gray>"));
            Theme.fillAll(inv);
            Theme.fillFooter(inv, 45, 53);
            p.openInventory(inv);
            return;
        }

        String baseId = my.getId();
        String baseName = my.getCurrencyName();
        String baseSym = my.getCurrencySymbol();
        double bal = plugin.getEconomyManager().getBalance(p.getUniqueId(), baseId);

        inv.setItem(4, Theme.item(Material.GOLD_INGOT,
                "<gradient:#FFD700:#FFA000><bold>CAMBIO - " + baseName.toUpperCase() + "</bold></gradient>",
                Theme.DIV,
                "<gray>Moeda base:</gray> <yellow>" + baseName + " (" + baseSym + ")</yellow>",
                "<gray>Seu saldo:</gray> <green>" + baseSym + " " + Theme.fmt(bal) + "</green>",
                Theme.DIV,
                "<gray>ISSP:</gray> <red>3%</red> <dark_gray>(Tesouro destino)</dark_gray>",
                "<gray>ISDN:</gray> <red>2%</red> <dark_gray>(BC destino)</dark_gray>",
                Theme.DIV,
                "<gray>Painel informativo. Conversao pelo chat.</gray>"));

        // Outras 3 moedas
        Material[] mats = {Material.LIME_BANNER, Material.RED_BANNER, Material.BLUE_BANNER, Material.WHITE_BANNER};
        int slotIdx = 0;
        int[] slots = {20, 22, 24};
        String[] all = {"brasil", "japao", "eua", "russia"};
        for (int i = 0; i < all.length; i++) {
            if (all[i].equalsIgnoreCase(baseId)) continue;
            if (slotIdx >= slots.length) break;
            Nation other = plugin.getNationManager().getNation(all[i]);
            if (other == null) continue;
            double rate = plugin.getExchangeManager().getRate(baseId, all[i]);
            String emoji = plugin.getTreasuryImpactManager().getStatusEmoji(all[i]);
            String status = plugin.getTreasuryImpactManager().getStatusLabel(all[i]);
            inv.setItem(slots[slotIdx++], Theme.item(mats[i],
                    emoji + " <aqua><bold>" + other.getCurrencyName().toUpperCase() + "</bold></aqua>",
                    Theme.DIV,
                    "<gray>1 " + baseName + " =</gray>",
                    "<yellow><bold>" + String.format("%.4f", rate) + " " + other.getCurrencyName() + "</bold></yellow>",
                    Theme.DIV,
                    "<gray>Simbolo:</gray> <white>" + other.getCurrencySymbol() + "</white>",
                    "<gray>Status:</gray> " + emoji + " <white>" + status + "</white>",
                    Theme.DIV,
                    "<dark_gray>Base: " + baseName + " da sua nação</dark_gray>"));
        }

        inv.setItem(31, Theme.item(Material.PAPER,
                "<yellow><bold>TABELA COMPLETA</bold></yellow>",
                Theme.DIV,
                "<gray>Para ver todas as paridades</gray>",
                "<gray>entre as 4 moedas, use no chat:</gray>",
                Theme.DIV,
                "<aqua>/economia status</aqua>"));

        inv.setItem(40, Theme.item(Material.WRITABLE_BOOK,
                "<green><bold>COMO CONVERTER</bold></green>",
                Theme.DIV,
                "<gray>1. /moeda converter BRL 1000 USD</gray>",
                "<gray>2. Confira ISSP 3% e ISDN 2%</gray>",
                "<gray>3. /confirmar em ate 30s</gray>",
                Theme.DIV,
                "<gray>Voce recebe 95% do valor convertido.</gray>"));

        Theme.fillAll(inv);
        Theme.fillFooter(inv, 45, 53);
        inv.setItem(49, Theme.item(Material.EMERALD,
                "<gold><bold>CARTEIRA</bold></gold>",
                Theme.DIV,
                "<gray>Abre sua carteira completa.</gray>"));
        p.openInventory(inv);
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(e.getView().title());
        if (!title.contains("COTACOES") && !title.contains("CAMBIO") && !title.contains("CÂMBIO")) return;
        e.setCancelled(true);
        int s = e.getSlot();
        if (s == 45) { plugin.getNationGUI().open(p); return; }
        if (s == 53) { p.closeInventory(); return; }
        if (s == 49) { plugin.getCarteiraGUI().open(p); return; }
    }
}
