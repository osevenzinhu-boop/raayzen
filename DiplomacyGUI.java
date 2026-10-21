package com.nationswaw.gui;

import com.nationswaw.NationsWaW;
import com.nationswaw.model.DiplomaticRelation;
import com.nationswaw.model.Nation;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

/** Diplomacia somente leitura no GUI. Acoes via chat: /nation aliado */
public class DiplomacyGUI implements Listener {
    private final NationsWaW plugin;

    public DiplomacyGUI(NationsWaW plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player p) {
        Nation my = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 36, Theme.title("DIPLOMACIA"));

        if (my == null) {
            inv.setItem(13, Theme.item(Material.BARRIER, "<red>Sem nação</red>"));
        } else {
            inv.setItem(4, Theme.item(Material.SHIELD,
                    "<gradient:#FFD700:#FFA000><bold>" + my.getDisplayName().toUpperCase() + "</bold></gradient>",
                    Theme.DIV,
                    "<gray>Relacoes:</gray> <green>Aliado</green> <gray>· Neutro ·</gray> <red>Guerra</red>",
                    Theme.DIV,
                    "<gray>Aliancas sao propostas pelo chat.</gray>",
                    "<gray>Painel apenas informativo.</gray>"));

            int[] slots = {20, 22, 24};
            int i = 0;
            for (Nation other : plugin.getNationManager().getAllNations()) {
                if (other.getId().equals(my.getId())) continue;
                if (i >= slots.length) break;
                DiplomaticRelation rel = plugin.getDiplomacyManager().getRelation(my.getId(), other.getId());
                Material mat = switch (rel) {
                    case ALLY -> Material.LIME_BANNER;
                    case WAR -> Material.BLACK_BANNER;
                    default -> Material.WHITE_BANNER;
                };
                boolean emb = plugin.getEmbargoManager().hasEmbargo(other.getId());
                inv.setItem(slots[i++], Theme.item(mat,
                        "<white><bold>" + other.getDisplayName().toUpperCase() + "</bold></white>",
                        Theme.DIV,
                        "<gray>Status:</gray> " + rel.getColoredName(),
                        emb ? "<red>Embargo: +" + String.format("%.0f", plugin.getEmbargoManager().getEmbargoPercent(other.getId())) + "%</red>"
                                : "<gray>Embargo:</gray> <green>Nenhum</green>",
                        Theme.DIV,
                        "<dark_gray>Somente visualizacao</dark_gray>"));
            }
        }
        Theme.fillAll(inv);
        Theme.fillFooter(inv, 27, 35);
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String t = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(e.getView().title());
        if (!t.contains("DIPLOMACIA")) return;
        e.setCancelled(true);
        if (e.getSlot() == 27) plugin.getNationGUI().open(p);
        else if (e.getSlot() == 35) p.closeInventory();
        // sem acoes de diplomacia no GUI
    }
}
