package com.nationswaw.gui;

import com.nationswaw.NationsWaW;
import com.nationswaw.model.Nation;
import com.nationswaw.systems.VotingManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /votar — menu centralizado de propostas ativas da nacao.
 */
public class VotesGUI implements Listener {
    private final NationsWaW plugin;
    private final Map<UUID, List<Integer>> openIds = new HashMap<>();

    public VotesGUI(NationsWaW plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player p) {
        Nation my = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
        if (my == null) {
            p.sendMessage(Theme.MM.deserialize("<red>Voce precisa pertencer a uma nacao para votar.</red>"));
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 54, Theme.title("VOTACOES · " + my.getDisplayName()));
        Theme.fillAll(inv);

        inv.setItem(4, Theme.item(Material.BOOKSHELF,
                "<gradient:#FFD700:#FFA000><bold>Votacoes Ativas</bold></gradient>",
                Theme.DIV,
                "<gray>Clique em uma proposta para votar.</gray>",
                "<gray>Aprovacao: 70% · Votacao indefinida</gray>"));

        List<Integer> ids = new ArrayList<>();
        int slot = 19;
        for (VotingManager.WarProposal prop : plugin.getVotingManager().getActiveProposalsForNation(my.getId())) {
            if (slot >= 44) break;
            if (slot % 9 == 0 || slot % 9 == 8) { slot++; continue; }

            Nation target = plugin.getNationManager().getNation(
                    my.getId().equalsIgnoreCase(prop.proposerNation) ? prop.targetNation : prop.proposerNation);
            String title = my.getId().equalsIgnoreCase(prop.proposerNation)
                    ? "Declarar Guerra a " + (target != null ? target.getDisplayName() : prop.targetNation)
                    : "Defesa vs " + (target != null ? target.getDisplayName() : prop.proposerNation);

            Map<UUID, Boolean> votes = my.getId().equalsIgnoreCase(prop.proposerNation)
                    ? prop.proposerVotes : prop.targetVotes;
            int yes = 0, no = 0;
            for (Boolean b : votes.values()) {
                if (Boolean.TRUE.equals(b)) yes++;
                else no++;
            }
            int pop = Math.max(1, my.getPopulation());
            double pct = (yes * 100.0) / pop;

            inv.setItem(slot, Theme.item(Material.BOOK,
                    "<gold>◆ Proposta: " + title + "</gold>",
                    Theme.DIV,
                    "<gray>ID:</gray> <white>#" + prop.id + "</white>",
                    "<gray>Iniciada por:</gray> <yellow>" + prop.proposerNation + "</yellow>",
                    "<gray>Regra:</gray> <aqua>70% de aprovacao</aqua>",
                    Theme.DIV,
                    "<green>A Favor:</green> <white>" + yes + "</white>  <red>Contra:</red> <white>" + no + "</white>",
                    "<gray>Progresso:</gray> <yellow>" + String.format("%.0f", pct) + "%</yellow>",
                    prop.waitingTarget && my.getId().equalsIgnoreCase(prop.targetNation)
                            ? "<red>Votacao de DEFESA ativa</red>" : "",
                    Theme.DIV,
                    "<yellow>» Clique para votar</yellow>"));
            ids.add(prop.id);
            slot++;
        }

        if (ids.isEmpty()) {
            inv.setItem(22, Theme.item(Material.BARRIER,
                    "<gray>Nenhuma votacao ativa</gray>",
                    "<dark_gray>O Presidente pode iniciar guerra pelo menu Guerra.</dark_gray>"));
        }
        openIds.put(p.getUniqueId(), ids);

        Theme.fillFooter(inv, 45, 53);
        inv.setItem(45, Theme.item(Material.ARROW, "<yellow>« Fechar</yellow>"));
        inv.setItem(53, Theme.item(Material.BARRIER, "<red>Fechar</red>"));
        p.openInventory(inv);
    }

    public void openVote(Player p, int voteId) {
        Nation my = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
        VotingManager.WarProposal prop = plugin.getVotingManager().getProposal(voteId);
        if (my == null || prop == null || prop.finished) {
            p.sendMessage(Theme.MM.deserialize("<red>Votacao indisponivel.</red>"));
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 27, Theme.title("VOTAR · #" + voteId));
        Theme.fillAll(inv);

        inv.setItem(4, Theme.item(Material.BOOK,
                "<gold>◆ Votacao #" + voteId + "</gold>",
                "<gray>Escolha seu voto abaixo</gray>"));

        inv.setItem(11, Theme.item(Material.LIME_DYE,
                "<green><bold>A FAVOR</bold></green>",
                "",
                "<yellow>» Clique para votar A FAVOR</yellow>"));
        inv.setItem(15, Theme.item(Material.RED_DYE,
                "<red><bold>CONTRA</bold></red>",
                "",
                "<yellow>» Clique para votar CONTRA</yellow>"));
        inv.setItem(22, Theme.item(Material.ARROW, "<yellow>« Voltar</yellow>"));
        p.openInventory(inv);
        // store vote id in openIds as single element negative marker
        openIds.put(p.getUniqueId(), List.of(-voteId));
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(e.getView().title());
        boolean list = title.contains("VOTACOES");
        boolean sub = title.contains("VOTAR ·");
        if (!list && !sub) return;
        e.setCancelled(true);
        int s = e.getSlot();

        if (list) {
            if (s == 45 || s == 53) { p.closeInventory(); return; }
            List<Integer> ids = openIds.get(p.getUniqueId());
            if (ids == null) return;
            int idx = 0;
            for (int slot = 19; slot < 44; slot++) {
                if (slot % 9 == 0 || slot % 9 == 8) continue;
                if (slot == s && idx < ids.size()) {
                    openVote(p, ids.get(idx));
                    return;
                }
                idx++;
            }
            return;
        }

        if (sub) {
            List<Integer> ids = openIds.get(p.getUniqueId());
            if (ids == null || ids.isEmpty()) return;
            int voteId = Math.abs(ids.get(0));
            if (s == 22) { open(p); return; }
            if (s == 11) {
                boolean ok = plugin.getVotingManager().castVote(p.getUniqueId(), voteId, true);
                p.sendMessage(Theme.MM.deserialize(ok
                        ? "<green>Voto A FAVOR registrado!</green>"
                        : "<red>Nao foi possivel votar.</red>"));
                open(p);
            } else if (s == 15) {
                boolean ok = plugin.getVotingManager().castVote(p.getUniqueId(), voteId, false);
                p.sendMessage(Theme.MM.deserialize(ok
                        ? "<yellow>Voto CONTRA registrado!</yellow>"
                        : "<red>Nao foi possivel votar.</red>"));
                open(p);
            }
        }
    }
}
