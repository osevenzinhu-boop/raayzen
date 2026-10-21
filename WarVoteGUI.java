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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class WarVoteGUI implements Listener {
    private final NationsWaW plugin;
    private final java.util.Map<java.util.UUID, java.util.Map<Integer, String>> targetSlots = new java.util.concurrent.ConcurrentHashMap<>();

    public WarVoteGUI(NationsWaW plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player p) {
        Nation my = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
        if (my == null) {
            p.sendMessage(Theme.MM.deserialize("<red>Voce nao pertence a uma nação.</red>"));
            return;
        }

        VotingManager.WarProposal prop = plugin.getVotingManager().getActiveProposalFor(my.getId());
        Inventory inv = Bukkit.createInventory(null, 54,
                Theme.title("VOTACAO DE GUERRA - " + my.getDisplayName().toUpperCase()));

        // Bordas vermelho/preto
        fillWarBorder(inv);

        if (prop == null) {
            inv.setItem(4, Theme.item(Material.IRON_SWORD,
                    "<red><bold>[◆] NENHUMA VOTACAO ATIVA</bold></red>",
                    Theme.DIV,
                    "<gray>Nao ha votacao de guerra em andamento.</gray>",
                    Theme.DIV,
                    "<gray>O Presidente inicia com:</gray>",
                    "<yellow>/guerra iniciar [nacao]</yellow>",
                    Theme.DIV,
                    "<gray>Regras:</gray>",
                    "<white>■ Aprovacao: 70%</white>",
                    "<white>■ Votacao: Indefinida</white>",
                    "<white>■ Declaracao: Mutua</white>",
                    "<white>■ Embargo progressivo ate confirmacao</white>"));

            // Lista de nações alvo
            boolean isPres = plugin.getRoleManager().hasRoleAtLeast(p.getUniqueId(), com.nationswaw.model.RoleType.PRESIDENT);
            java.util.Map<Integer, String> slotMap = new java.util.HashMap<>();
            int s = 20;
            for (Nation other : plugin.getNationManager().getAllNations()) {
                if (other.getId().equals(my.getId())) continue;
                boolean atWar = plugin.getDiplomacyManager().isAtWar(my.getId(), other.getId());
                String relation = atWar ? "<red>EM GUERRA</red>" : "<gray>Status: Neutro/Aliado</gray>";
                inv.setItem(s, Theme.item(
                        Material.valueOf(other.getFlagMaterial() != null ? other.getFlagMaterial() : "WHITE_BANNER"),
                        (atWar ? "<red>" : "<white>") + "<bold>" + other.getDisplayName().toUpperCase() + "</bold>",
                        Theme.DIV,
                        relation,
                        Theme.DIV,
                        isPres && !atWar
                                ? "<yellow>Clique para INICIAR votacao de guerra</yellow>"
                                : "<gray>Apenas visualizacao</gray>"));
                slotMap.put(s, other.getId());
                s += 2;
                if (s > 24) break;
            }
            targetSlots.put(p.getUniqueId(), slotMap);
        } else {
            openWithProposal(inv, p, my, prop);
        }

        Theme.fillFooter(inv, 45, 53);
        inv.setItem(49, Theme.item(Material.BOOK,
                "<yellow><bold>REGRAS</bold></yellow>",
                Theme.DIV,
                "<gray>■ Qualquer civil pode votar</gray>",
                "<gray>■ Votacao fica ativa ate todos votarem</gray>",
                "<gray>■ 70% a favor aprova o pedido</gray>",
                "<gray>■ Outra nação tambem precisa aprovar</gray>",
                "<gray>■ Embargo +10%/dia ate confirmacao</gray>"));
        p.openInventory(inv);
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 0.3f, 1.5f);
    }

    private void openWithProposal(Inventory inv, Player viewer, Nation my, VotingManager.WarProposal prop) {
        boolean isProposer = my.getId().equalsIgnoreCase(prop.proposerNation);
        String enemyId = isProposer ? prop.targetNation : prop.proposerNation;
        Nation enemy = plugin.getNationManager().getNation(enemyId);
        Map<UUID, Boolean> votes = plugin.getVotingManager().getVotesForViewer(prop, my.getId());

        // Membros da nação que votam
        Set<UUID> members = new LinkedHashSet<>();
        if (my.getPresident() != null) members.add(my.getPresident());
        members.addAll(my.getGovernors());
        members.addAll(my.getMayors());
        members.addAll(my.getCitizens());

        int pop = Math.max(1, members.size());
        long yes = votes.values().stream().filter(b -> b).count();
        long no = votes.values().stream().filter(b -> !b).count();
        double yesPct = (100.0 * yes) / pop;
        double noPct = (100.0 * no) / pop;

        Material flagMat;
        try {
            flagMat = Material.valueOf(enemy != null && enemy.getFlagMaterial() != null
                    ? enemy.getFlagMaterial() : "BLACK_BANNER");
        } catch (Exception e) {
            flagMat = Material.BLACK_BANNER;
        }

        String phase = prop.waitingTarget
                ? (isProposer ? "Aguardando resposta inimiga" : "Sua nação deve votar")
                : "Votacao em andamento";

        inv.setItem(4, Theme.item(flagMat,
                "<red><bold>[◆] ALVO: " + (enemy != null ? enemy.getDisplayName().toUpperCase() : enemyId) + "</bold></red>",
                Theme.DIV,
                "<gray>Proposta ID:</gray> <yellow>#" + prop.id + "</yellow>",
                "<gray>Fase:</gray> <white>" + phase + "</white>",
                Theme.DIV,
                "<gray>A favor:</gray> <green>" + yes + " (" + String.format("%.0f", yesPct) + "%)</green>",
                "<gray>Contra:</gray> <red>" + no + " (" + String.format("%.0f", noPct) + "%)</red>",
                "<gray>Participacao:</gray> <aqua>" + votes.size() + "/" + pop + "</aqua>",
                Theme.DIV,
                "<gray>Necessario:</gray> <yellow>70% a favor</yellow>"));

        inv.setItem(20, Theme.item(Material.LIME_DYE,
                "<green><bold>[◆] VOTAR A FAVOR</bold></green>",
                Theme.DIV,
                "<gray>Votos a favor:</gray> <green><bold>" + yes + "</bold></green>",
                "<gray>Porcentagem:</gray> <green>" + String.format("%.1f", yesPct) + "%</green>",
                Theme.DIV,
                "<yellow>Precisa de 70% para aprovar.</yellow>",
                Theme.DIV,
                "<green>Clique para votar A FAVOR da guerra</green>"));

        inv.setItem(24, Theme.item(Material.RED_DYE,
                "<red><bold>[◆] VOTAR CONTRA</bold></red>",
                Theme.DIV,
                "<gray>Votos contra:</gray> <red><bold>" + no + "</bold></red>",
                "<gray>Porcentagem:</gray> <red>" + String.format("%.1f", noPct) + "%</red>",
                Theme.DIV,
                "<gray>Clique para votar CONTRA a guerra</gray>"));

        // Cabecas dos jogadores slots 28-43
        int slot = 28;
        for (UUID uuid : members) {
            if (slot > 43) break;
            if (slot % 9 == 0 || slot % 9 == 8) { slot++; continue; }
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            if (name == null) name = "?";
            Boolean vote = votes.get(uuid);
            String voteStr = vote == null ? "<gray>NAO VOTOU</gray>"
                    : (vote ? "<green>A FAVOR</green>" : "<red>CONTRA</red>");
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                try {
                    meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
                } catch (Exception ignored) {
                    // timeout Mojang / offline - usa cabeca padrao
                }
                meta.displayName(Theme.MM.deserialize("<white><bold>" + name + "</bold></white>"));
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                lore.add(Theme.MM.deserialize(Theme.DIV));
                lore.add(Theme.MM.deserialize("<gray>[■] Voto:</gray> " + voteStr));
                lore.add(Theme.MM.deserialize(Theme.DIV));
                meta.lore(lore);
                head.setItemMeta(meta);
            }
            inv.setItem(slot++, head);
        }

        // Barra de progresso slots 46-52 (exceto 45 e 53 footer)
        // Use 48, 49, 50 as specified - expand to 46-52 for better bar
        int barSlots[] = {46, 47, 48, 49, 50, 51, 52};
        int greenCount = (int) Math.round(yesPct / 100.0 * barSlots.length);
        for (int i = 0; i < barSlots.length; i++) {
            Material mat = i < greenCount ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
            inv.setItem(barSlots[i], Theme.item(mat,
                    i < greenCount
                            ? "<green>A favor " + String.format("%.0f", yesPct) + "%</green>"
                            : "<red>Restante</red>",
                    "<dark_gray>Barra de progresso da votacao</dark_gray>"));
        }
    }

    private void fillWarBorder(Inventory inv) {
        int size = inv.getSize();
        int rows = size / 9;
        for (int i = 0; i < size; i++) {
            int row = i / 9;
            int col = i % 9;
            if (row == 0 || row == rows - 1 || col == 0 || col == 8) {
                inv.setItem(i, Theme.pane(i % 2 == 0 ? Material.BLACK_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE));
            } else if (inv.getItem(i) == null) {
                inv.setItem(i, Theme.pane(Material.GRAY_STAINED_GLASS_PANE));
            }
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(e.getView().title());
        if (!title.contains("VOTACAO DE GUERRA") && !title.contains("VOTAÇÃO DE GUERRA")) return;
        e.setCancelled(true);
        int s = e.getSlot();
        if (s == 45) { plugin.getNationGUI().open(p); return; }
        if (s == 53) { p.closeInventory(); return; }

        Nation my = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
        if (my == null) return;
        VotingManager.WarProposal prop = plugin.getVotingManager().getActiveProposalFor(my.getId());

        // Sem votacao ativa: presidente clica na bandeira para iniciar
        if (prop == null) {
            java.util.Map<Integer, String> slotMap = targetSlots.get(p.getUniqueId());
            if (slotMap != null && slotMap.containsKey(s)) {
                if (!plugin.getRoleManager().hasRoleAtLeast(p.getUniqueId(), com.nationswaw.model.RoleType.PRESIDENT)) {
                    p.sendMessage(Theme.MM.deserialize("<red>Apenas o Presidente pode iniciar votacao de guerra.</red>"));
                    return;
                }
                String targetId = slotMap.get(s);
                int id = plugin.getVotingManager().startWarProposal(my.getId(), targetId);
                if (id > 0) {
                    p.sendMessage(Theme.MM.deserialize("<green>Votacao de guerra #" + id + " iniciada!</green>"));
                    open(p);
                } else if (id == -4) {
                    p.sendMessage(Theme.MM.deserialize("<red>Sua nacao precisa de no minimo 5 membros para iniciar guerra.</red>"));
                } else if (id == -2) {
                    p.sendMessage(Theme.MM.deserialize("<red>Ja estao em guerra.</red>"));
                } else if (id == -3) {
                    p.sendMessage(Theme.MM.deserialize("<yellow>Ja existe votacao aberta contra essa nação.</yellow>"));
                }
            }
            return;
        }

        if (s == 20) {
            boolean ok = plugin.getVotingManager().castVote(p.getUniqueId(), prop.id, true);
            p.sendMessage(Theme.MM.deserialize(ok
                    ? "<green>Voto A FAVOR registrado!</green>"
                    : "<red>Nao foi possivel votar (ja votou ou nao elegivel).</red>"));
            open(p);
        } else if (s == 24) {
            boolean ok = plugin.getVotingManager().castVote(p.getUniqueId(), prop.id, false);
            p.sendMessage(Theme.MM.deserialize(ok
                    ? "<yellow>Voto CONTRA registrado!</yellow>"
                    : "<red>Nao foi possivel votar (ja votou ou nao elegivel).</red>"));
            open(p);
        }
    }
}
