package com.nationswaw.gui;

import com.nationswaw.NationsWaW;
import com.nationswaw.model.Nation;
import com.nationswaw.systems.ShopManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ShopGUI implements Listener {
    private final NationsWaW plugin;
    private final Map<UUID, String> openNation = new HashMap<>();

    public ShopGUI(NationsWaW plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player p, String nationId) {
        Nation n = plugin.getNationManager().getNation(nationId);
        if (n == null) return;
        openNation.put(p.getUniqueId(), nationId);

        Inventory inv = Bukkit.createInventory(null, 54, Theme.title("Loja · " + n.getDisplayName()));
        Theme.fillAll(inv);

        boolean embargo = plugin.getEmbargoManager().hasEmbargo(nationId);
        double embPct = plugin.getEmbargoManager().getEmbargoPercent(nationId);

        inv.setItem(4, Theme.item(Material.EMERALD,
                "<gradient:#00C853:#69F0AE><bold>Loja de " + n.getDisplayName() + "</bold></gradient>",
                "<gray>Moeda: " + n.getCurrencyName() + " (" + n.getCurrencySymbol() + ")</gray>",
                embargo ? "<red>Embargo ativo: +" + String.format("%.0f", embPct) + "% exportacao</red>" : "<green>Sem embargo</green>",
                "<gray>Taxa local: 15% | Exportacao: 45%</gray>",
                "",
                "<dark_gray>Clique para comprar 1 unidade</dark_gray>"));

        Nation viewerNation = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
        boolean viewerLocal = viewerNation != null && viewerNation.getId().equalsIgnoreCase(nationId);

        List<ShopManager.ShopListing> lists = new ArrayList<>(plugin.getShopManager().getListings(nationId));
        int slot = 19;
        for (ShopManager.ShopListing l : lists) {
            if (slot >= 44) break;
            if (slot % 9 == 0 || slot % 9 == 8) { slot++; continue; }

            double unit = l.pricePerUnit;
            double exportBase = unit * plugin.getEmbargoManager().getEmbargoMultiplier(nationId);

            // Linha Exportar: valor real que o visitante paga na MOEDA DELE (com cambio)
            String exportLine;
            if (viewerNation != null && !viewerLocal) {
                double rate = plugin.getExchangeManager().getRate(nationId, viewerNation.getId());
                double pay = exportBase * rate;
                double issp = pay * plugin.getConfigManager().getIssp();
                double isdn = pay * plugin.getConfigManager().getIsdn();
                double realPay = pay + issp + isdn;
                exportLine = "<gold>Exportar: " + viewerNation.getCurrencySymbol() + " "
                        + String.format("%.2f", realPay) + "/un</gold>";
            } else {
                // Mesma nação ou sem nação: mostra preco de exportacao na moeda da loja
                exportLine = "<gold>Exportar: " + n.getCurrencySymbol() + " "
                        + String.format("%.2f", exportBase) + "/un</gold> <dark_gray>(45% imposto)</dark_gray>";
            }

            String sellerName = Bukkit.getOfflinePlayer(l.seller).getName();
            if (sellerName == null) sellerName = "Desconhecido";

            inv.setItem(slot, Theme.item(l.material,
                    "<gold>" + l.material.name() + "</gold> <gray>x" + l.amount + "</gray>",
                    "<green>Preco: " + n.getCurrencySymbol() + " " + String.format("%.2f", unit) + "/un</green>",
                    exportLine,
                    "<gray>Estoque: " + l.amount + "</gray>",
                    "<gray>Vendedor: " + sellerName + "</gray>",
                    "<dark_gray>ID: #" + l.id + "</dark_gray>",
                    "",
                    "<yellow>Clique para comprar 1 unidade</yellow>"));
            slot++;
        }

        inv.setItem(45, Theme.item(Material.ARROW, "<yellow>Voltar</yellow>"));
        inv.setItem(49, Theme.item(Material.PAPER,
                "<aqua>/vender <valor></aqua>",
                "<gray>Segure o item e informe o valor TOTAL do stack.</gray>",
                "<gray>O preco por unidade sera valor / quantidade.</gray>",
                "<dark_gray>Ex: 64 pedras + /vender 640 = 10/un</dark_gray>"));
        inv.setItem(53, Theme.item(Material.BARRIER, "<red>Fechar</red>"));
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(e.getView().title());
        // Apenas loja por nacao (nao LOJAS GLOBAIS)
        if (!title.contains("Loja") || title.toUpperCase().contains("LOJAS GLOBAIS")
                || title.toUpperCase().contains("GLOBAL")) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == null || e.getClickedInventory().equals(p.getInventory())) return;
        int s = e.getSlot();
        if (s == 45) {
            plugin.getNationGUI().open(p);
            return;
        }
        if (s == 53) {
            p.closeInventory();
            return;
        }

        String nationId = openNation.get(p.getUniqueId());
        if (nationId == null) return;

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR
                || clicked.getType().name().contains("GLASS")
                || clicked.getType().name().contains("PANE")
                || clicked.getType() == Material.ARROW
                || clicked.getType() == Material.BARRIER
                || clicked.getType() == Material.PAPER
                || clicked.getType() == Material.EMERALD
                || clicked.getType() == Material.HOPPER) return;

        // Prioriza match por ID se houver varios do mesmo material
        ShopManager.ShopListing found = null;
        for (ShopManager.ShopListing l : plugin.getShopManager().getListings(nationId)) {
            if (l.material == clicked.getType()) {
                found = l;
                break;
            }
        }
        if (found == null) return;

        // Apenas 1 unidade por clique (nunca packs)
        plugin.getShopManager().buy(p, found.id, 1);
        open(p, nationId);
    }
}
