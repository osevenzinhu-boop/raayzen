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

public class TreasuryGUI implements Listener {
    private final NationsWaW plugin;

    public TreasuryGUI(NationsWaW plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player p) {
        Nation n = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 27, Theme.title("TESOURO NACIONAL"));

        if (n == null) {
            inv.setItem(13, Theme.item(Material.BARRIER, "<red>Sem nação</red>"));
        } else {
            String status = plugin.getTreasuryImpactManager().getStatusLabel(n.getId());
            String emoji = plugin.getTreasuryImpactManager().getStatusEmoji(n.getId());
            String color = plugin.getTreasuryImpactManager().getStatusColor(n.getId());
            double pct = plugin.getTreasuryImpactManager().getIndexPercent(n.getId());
            String sym = n.getCurrencySymbol();
            double total = n.getTreasury() + n.getCentralBankBalance();

            inv.setItem(4, Theme.item(Material.GOLD_BLOCK,
                    "<gradient:#FFD700:#FFA000><bold>[◆] TESOURO NACIONAL</bold></gradient>",
                    Theme.DIV,
                    "<gray>Tesouro Nacional:</gray> <gold>" + sym + " " + Theme.fmt(n.getTreasury()) + "</gold>",
                    "<gray>Banco Central:</gray> <gold>" + sym + " " + Theme.fmt(n.getCentralBankBalance()) + "</gold>",
                    Theme.DIV,
                    "<gray>Saldo Total da Uniao:</gray> <green><bold>" + sym + " " + Theme.fmt(total) + "</bold></green>",
                    Theme.DIV,
                    "<gray>Status da Moeda:</gray> <" + color + ">" + emoji + " " + status + "</" + color + ">",
                    "<gray>Investimentos debitam do:</gray> <yellow>Banco Central</yellow>"));

            inv.setItem(11, Theme.item(Material.GOLD_INGOT,
                    "<yellow><bold>TESOURO NACIONAL</bold></yellow>",
                    Theme.DIV,
                    "<gray>Funcao:</gray> <white>Reserva estrategica</white>",
                    "<gray>Uso:</gray> <white>Valorizacao da moeda</white>",
                    "<gray>Uso:</gray> <white>Emergencias nacionais</white>",
                    Theme.DIV,
                    "<gray>Saldo atual:</gray> <gold>" + sym + " " + Theme.fmt(n.getTreasury()) + "</gold>"));

            inv.setItem(13, Theme.item(Material.EMERALD,
                    "<green><bold>BANCO CENTRAL</bold></green>",
                    Theme.DIV,
                    "<gray>Funcao:</gray> <white>Conta operacional</white>",
                    "<gray>Uso:</gray> <white>Claims e infraestrutura</white>",
                    "<gray>Uso:</gray> <white>Subsidios e investimentos</white>",
                    Theme.DIV,
                    "<gray>Saldo atual:</gray> <gold>" + sym + " " + Theme.fmt(n.getCentralBankBalance()) + "</gold>"));

            inv.setItem(15, Theme.item(Material.PAPER,
                    "<gradient:#00BCD4:#00E5FF><bold>[◆] IMPACTO NA MOEDA</bold></gradient>",
                    Theme.DIV,
                    "<gray>Base de Calculo:</gray> <white>Saldo Total da Uniao (Tesouro + BC)</white>",
                    Theme.DIV,
                    "<gray>Acima de 10.000.000:</gray> <green>Valoriza (+0,05% por 1M)</green>",
                    "<gray>Entre 2M e 10M:</gray> <yellow>Estavel</yellow>",
                    "<gray>Abaixo de 2.000.000:</gray> <red>Desvaloriza (Exponencial ate -95%)</red>",
                    Theme.DIV,
                    "<gray>Saldo Total atual:</gray> <aqua>" + sym + " " + Theme.fmt(total) + "</aqua>",
                    "<gray>Status atual:</gray> <" + color + "><bold>" + status + "</bold></" + color + ">"));
        }

        Theme.fillAll(inv);
        Theme.fillFooter(inv, 18, 26);
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String t = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(e.getView().title());
        if (!t.contains("TESOURO")) return;
        e.setCancelled(true);
        if (e.getSlot() == 18) plugin.getNationGUI().open(p);
        else if (e.getSlot() == 26) p.closeInventory();
    }
}
