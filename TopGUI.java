package com.nationswaw.gui;

import com.nationswaw.NationsWaW;
import com.nationswaw.model.Nation;
import com.nationswaw.model.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class TopGUI implements Listener {
    private final NationsWaW plugin;

    public TopGUI(NationsWaW plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openMain(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, Theme.title("TOP RANKINGS"));
        Theme.fillAll(inv);
        inv.setItem(11, Theme.item(Material.GOLD_BLOCK,
                "<gold><bold>◆ Top Nacoes</bold></gold>",
                Theme.DIV,
                "<gray>Ranking por riqueza total</gray>",
                "<gray>(Tesouro + BC + carteiras)</gray>",
                Theme.DIV,
                "<yellow>» Clique para abrir</yellow>"));
        inv.setItem(15, Theme.item(Material.DIAMOND_HELMET,
                "<aqua><bold>◆ Top Jogadores</bold></aqua>",
                Theme.DIV,
                "<gray>Ranking por riqueza pessoal</gray>",
                "<gray>(todas as moedas convertidas)</gray>",
                Theme.DIV,
                "<yellow>» Clique para abrir</yellow>"));
        inv.setItem(22, Theme.item(Material.BARRIER, "<red>Fechar</red>"));
        p.openInventory(inv);
    }

    public void openNations(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, Theme.title("TOP NACOES"));
        Theme.fillAll(inv);

        List<NationWealth> list = new ArrayList<>();
        for (Nation n : plugin.getNationManager().getAllNations()) {
            list.add(new NationWealth(n, calcNationWealth(n)));
        }
        list.sort((a, b) -> Double.compare(b.wealth, a.wealth));

        int[] slots = {19, 21, 23, 25};
        Material[] medals = {Material.GOLD_BLOCK, Material.IRON_BLOCK, Material.COPPER_BLOCK, Material.COAL_BLOCK};
        for (int i = 0; i < list.size() && i < slots.length; i++) {
            NationWealth nw = list.get(i);
            String place = (i + 1) + "o";
            inv.setItem(slots[i], Theme.item(medals[i],
                    "<gold><bold>[" + place + " Lugar]</bold></gold>",
                    Theme.DIV,
                    "<gray>Pais:</gray> <white>" + nw.nation.getDisplayName() + "</white>",
                    "",
                    "<gray>Riqueza Total:</gray> <green>"
                            + nw.nation.getCurrencySymbol() + " " + Theme.fmt(nw.wealth) + "</green>",
                    Theme.DIV,
                    "<dark_gray>Tesouro + BC + carteiras</dark_gray>"));
        }
        inv.setItem(45, Theme.item(Material.ARROW, "<yellow>« Voltar</yellow>"));
        inv.setItem(53, Theme.item(Material.BARRIER, "<red>Fechar</red>"));
        p.openInventory(inv);
    }

    public void openPlayers(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, Theme.title("TOP JOGADORES"));
        Theme.fillAll(inv);

        List<PlayerWealth> list = new ArrayList<>();
        for (Map.Entry<UUID, PlayerData> e : plugin.getRoleManager().getAllPlayers().entrySet()) {
            PlayerData d = e.getValue();
            if (d.getNationId() == null || d.getNationId().isEmpty()) continue;
            Nation n = plugin.getNationManager().getNation(d.getNationId());
            if (n == null) continue;
            double w = calcPlayerWealth(e.getKey(), n.getId());
            list.add(new PlayerWealth(e.getKey(), n, w));
        }
        list.sort((a, b) -> Double.compare(b.wealth, a.wealth));

        int[] slots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };
        for (int i = 0; i < list.size() && i < slots.length; i++) {
            PlayerWealth pw = list.get(i);
            String place = (i + 1) + "o";
            ItemStack head = playerHead(pw.uuid, pw.nation, place, pw.wealth);
            inv.setItem(slots[i], head);
        }
        inv.setItem(45, Theme.item(Material.ARROW, "<yellow>« Voltar</yellow>"));
        inv.setItem(53, Theme.item(Material.BARRIER, "<red>Fechar</red>"));
        p.openInventory(inv);
    }

    private double calcNationWealth(Nation n) {
        double total = n.getTreasury() + n.getCentralBankBalance();
        String nid = n.getId();
        for (Map.Entry<UUID, PlayerData> e : plugin.getRoleManager().getAllPlayers().entrySet()) {
            PlayerData d = e.getValue();
            if (d.getNationId() == null || !d.getNationId().equalsIgnoreCase(nid)) continue;
            total += calcPlayerWealth(e.getKey(), nid);
        }
        return total;
    }

    /** Soma todos os saldos convertidos para a moeda da nacao do jogador. */
    private double calcPlayerWealth(UUID uuid, String localNationId) {
        PlayerData d = plugin.getRoleManager().getPlayerData(uuid);
        double sum = 0;
        for (Map.Entry<String, Double> bal : d.getBalances().entrySet()) {
            if (bal.getValue() <= 0) continue;
            String cur = bal.getKey();
            if (cur.equalsIgnoreCase(localNationId)) {
                sum += bal.getValue();
            } else {
                double rate = plugin.getExchangeManager().getRate(cur, localNationId);
                sum += bal.getValue() * rate;
            }
        }
        return sum;
    }

    private ItemStack playerHead(UUID uuid, Nation nation, String place, double wealth) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        try {
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                try {
                    meta.setOwningPlayer(op);
                } catch (Exception ignored) {}
                String name = op.getName() != null ? op.getName() : uuid.toString().substring(0, 8);
                meta.displayName(Theme.MM.deserialize("<gold><bold>[" + place + " Lugar]</bold></gold> <white>" + name + "</white>"));
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                lore.add(Theme.MM.deserialize(Theme.DIV));
                lore.add(Theme.MM.deserialize("<gray>Nacionalidade:</gray> <white>" + nation.getDisplayName() + "</white>"));
                lore.add(Theme.MM.deserialize(""));
                lore.add(Theme.MM.deserialize("<gray>Riqueza do Jogador:</gray> <green>"
                        + nation.getCurrencySymbol() + " " + Theme.fmt(wealth) + "</green>"));
                meta.lore(lore);
                head.setItemMeta(meta);
            }
        } catch (Exception e) {
            return Theme.item(Material.PLAYER_HEAD,
                    "<gold><bold>[" + place + " Lugar]</bold></gold>",
                    Theme.DIV,
                    "<gray>Nacionalidade:</gray> <white>" + nation.getDisplayName() + "</white>",
                    "",
                    "<gray>Riqueza do Jogador:</gray> <green>"
                            + nation.getCurrencySymbol() + " " + Theme.fmt(wealth) + "</green>");
        }
        return head;
    }

    private record NationWealth(Nation nation, double wealth) {}
    private record PlayerWealth(UUID uuid, Nation nation, double wealth) {}

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(e.getView().title());
        String up = title.toUpperCase();
        if (!up.contains("TOP RANKINGS") && !up.contains("TOP NACOES") && !up.contains("TOP JOGADORES")) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == null || e.getClickedInventory().equals(p.getInventory())) return;
        ItemStack cur = e.getCurrentItem();
        if (cur == null || cur.getType().isAir()) return;
        if (cur.getType().name().contains("GLASS") || cur.getType().name().contains("PANE")) return;

        int s = e.getSlot();
        if (up.contains("TOP RANKINGS")) {
            if (s == 22) { p.closeInventory(); return; }
            if (s == 11 && cur.getType() == Material.GOLD_BLOCK) openNations(p);
            else if (s == 15 && cur.getType() == Material.DIAMOND_HELMET) openPlayers(p);
            return;
        }
        if (s == 45) openMain(p);
        else if (s == 53) p.closeInventory();
    }
}
