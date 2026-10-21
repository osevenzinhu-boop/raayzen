package com.nationswaw.gui;

import com.nationswaw.NationsWaW;
import com.nationswaw.model.Nation;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

public class JoinNationGUI implements Listener {
    private final NationsWaW plugin;

    public JoinNationGUI(NationsWaW plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player p) {
        if (plugin.getNationManager().getNationByPlayer(p.getUniqueId()) != null) {
            p.sendMessage(Theme.MM.deserialize("<red>Voce ja pertence a uma nacao.</red>"));
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 54, Theme.title("ENTRAR EM UMA NACAO"));
        Theme.fillAll(inv);

        inv.setItem(4, Theme.item(Material.COMPASS,
                "<gradient:#FFD700:#FFA000><bold>Solicitar Cidadania</bold></gradient>",
                Theme.DIV,
                "<gray>Escolha uma nacao e solicite entrada.</gray>",
                "<gray>Lideres precisam aceitar sua solicitacao.</gray>"));

        int[] slots = {20, 22, 24, 30};
        int i = 0;
        for (Nation n : plugin.getNationManager().getAllNations()) {
            if (i >= slots.length) break;
            String pres = nameOf(n.getPresident());
            String gov = "Nenhum";
            if (!n.getGovernors().isEmpty()) {
                UUID g = n.getGovernors().iterator().next();
                gov = nameOf(g);
            }
            int mayors = n.getMayors().size();
            int pop = n.getPopulation();
            Material icon = switch (n.getId().toLowerCase()) {
                case "brasil" -> Material.LIME_BANNER;
                case "eua" -> Material.BLUE_BANNER;
                case "japao" -> Material.WHITE_BANNER;
                case "russia" -> Material.RED_BANNER;
                default -> Material.WHITE_BANNER;
            };
            inv.setItem(slots[i++], Theme.item(icon,
                    "<gold>◆ " + n.getDisplayName() + "</gold>",
                    "<dark_gray>──────────────────</dark_gray>",
                    "<gray>Presidente:</gray> <white>" + pres + "</white>",
                    "<gray>Governador:</gray> <white>" + gov + "</white>",
                    "<gray>Prefeitos:</gray> <white>" + mayors + "</white>",
                    "<gray>Populacao Total:</gray> <aqua>" + pop + "</aqua>",
                    "<dark_gray>──────────────────</dark_gray>",
                    "",
                    "<yellow>Clique para solicitar entrada.</yellow>"));
        }

        Theme.fillFooter(inv, 45, 53);
        inv.setItem(53, Theme.item(Material.BARRIER, "<red>Fechar</red>"));
        p.openInventory(inv);
    }

    private String nameOf(UUID uuid) {
        if (uuid == null) return "Nenhum";
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        String n = op.getName();
        return n != null ? n : "Nenhum";
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(e.getView().title());
        if (!title.contains("ENTRAR EM UMA NACAO") && !title.contains("ENTRAR EM UMA")) return;
        e.setCancelled(true);
        if (e.getSlot() == 53) { p.closeInventory(); return; }

        Nation[] nations = plugin.getNationManager().getAllNations().toArray(new Nation[0]);
        int[] slots = {20, 22, 24, 30};
        for (int i = 0; i < slots.length && i < nations.length; i++) {
            if (e.getSlot() == slots[i]) {
                p.closeInventory();
                plugin.getJoinRequestManager().submit(p, nations[i].getId());
                return;
            }
        }
    }
}
