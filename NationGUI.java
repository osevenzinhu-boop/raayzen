package com.nationswaw.gui;

import com.nationswaw.NationsWaW;
import com.nationswaw.model.DiplomaticRelation;
import com.nationswaw.model.Nation;
import com.nationswaw.model.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class NationGUI implements Listener {
    private final NationsWaW plugin;

    public NationGUI(NationsWaW plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Nation nation = plugin.getNationManager().getNationByPlayer(player.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54,
                Theme.title(nation != null ? "NACAO · " + nation.getDisplayName().toUpperCase() : "IMIGRANTE"));

        if (nation == null) {
            inv.setItem(4, Theme.item(Material.COMPASS,
                    "<gradient:#FFD700:#FFA000><bold>VOCÊ É UM IMIGRANTE</bold></gradient>",
                    Theme.DIV,
                    "<gray>Solicite cidadania a um Governador</gray>",
                    "<gray>ou Prefeito de uma das nações.</gray>",
                    Theme.DIV,
                    "<dark_gray>NationsWaW Premium</dark_gray>"));
            int[] slots = {20, 22, 24, 30};
            int i = 0;
            for (Nation n : plugin.getNationManager().getAllNations()) {
                if (i >= slots.length) break;
                inv.setItem(slots[i++], Theme.item(
                        Material.valueOf(n.getFlagMaterial() != null ? n.getFlagMaterial() : "WHITE_BANNER"),
                        "<gold><bold>" + n.getDisplayName().toUpperCase() + "</bold></gold>",
                        Theme.DIV,
                        "<gray>Moeda:</gray> <yellow>" + n.getCurrencyName() + "</yellow>",
                        "<gray>Símbolo:</gray> <yellow>" + n.getCurrencySymbol() + "</yellow>",
                        "<gray>População:</gray> <white>" + n.getPopulation() + "</white>",
                        Theme.DIV,
                        "<gray>Tesouro Nacional:</gray> <gold>" + n.getCurrencySymbol() + " " + Theme.fmtInt(n.getTreasury()) + "</gold>",
                        "<gray>Banco Central:</gray> <gold>" + n.getCurrencySymbol() + " " + Theme.fmtInt(n.getCentralBankBalance()) + "</gold>",
                        Theme.DIV,
                        "<dark_gray>Nação padrão do servidor</dark_gray>"));
            }
        } else {
            PlayerData data = plugin.getRoleManager().getPlayerData(player.getUniqueId());
            int allies = 0, wars = 0;
            for (Nation o : plugin.getNationManager().getAllNations()) {
                if (o.getId().equals(nation.getId())) continue;
                DiplomaticRelation r = plugin.getDiplomacyManager().getRelation(nation.getId(), o.getId());
                if (r == DiplomaticRelation.ALLY) allies++;
                if (r == DiplomaticRelation.WAR) wars++;
            }
            int votes = plugin.getVotingManager().getActiveProposals().size();
            String diploStatus = plugin.getWarManager().isWorldWarActive()
                    ? "<dark_red>GUERRA MUNDIAL</dark_red>"
                    : (wars > 0 ? "<red>Em Guerra</red>" : (votes > 0 ? "<yellow>Votação Ativa</yellow>" : "<green>Em Paz</green>"));
            java.util.List<String> warLines = new java.util.ArrayList<>();
            if (plugin.getWarManager().isWorldWarActive()) {
                warLines.add("<dark_red>[■] GUERRA MUNDIAL ATIVA</dark_red>");
            }
            for (String enemyId : plugin.getWarManager().getWarsFor(nation.getId())) {
                com.nationswaw.model.Nation en = plugin.getNationManager().getNation(enemyId);
                String ename = en != null ? en.getDisplayName() : enemyId;
                String left = plugin.getWarManager().formatRemaining(nation.getId(), enemyId);
                warLines.add("<red>[■] Guerra vs " + ename + "</red>");
                warLines.add("<gray>[■] Tempo restante: <white>" + left + "</white></gray>");
            }
            String presName = nation.getPresident() != null
                    ? (Bukkit.getOfflinePlayer(nation.getPresident()).getName() != null
                    ? Bukkit.getOfflinePlayer(nation.getPresident()).getName() : "?") : "Vago";
            String emoji = plugin.getTreasuryImpactManager().getStatusEmoji(nation.getId());
            String status = plugin.getTreasuryImpactManager().getStatusLabel(nation.getId());
            double bal = plugin.getEconomyManager().getBalance(player.getUniqueId(), nation.getId());

            // Cabeçalho
            java.util.List<String> headLore = new java.util.ArrayList<>();
            headLore.add(Theme.DIV);
            headLore.add("<gray>Presidente:</gray> <white>" + presName + "</white>");
            headLore.add("<gray>Seu cargo:</gray> <green>" + data.getRole().getDisplayName() + "</green>");
            headLore.add("<gray>População:</gray> <white>" + nation.getPopulation() + "</white>");
            headLore.add(Theme.DIV);
            headLore.add("<gray>Aliados:</gray> <green>" + allies + "</green>");
            headLore.add("<gray>Guerras:</gray> <red>" + wars + "</red>");
            headLore.add("<gray>Status diplomático:</gray> " + diploStatus);
            if (!warLines.isEmpty()) {
                headLore.add(Theme.DIV);
                headLore.addAll(warLines);
            }
            headLore.add(Theme.DIV);
            headLore.add("<gold>◆ Salarios Politicos (tempo online)</gold>");
            headLore.add("<gray>Presidente:</gray> <white>50.000 / 3h</white> <dark_gray>(BC)</dark_gray>");
            headLore.add("<gray>Governador:</gray> <white>20.000 / 1h30m</white> <dark_gray>(gerado)</dark_gray>");
            headLore.add("<gray>Prefeito:</gray> <white>7.500 / 45min</white> <dark_gray>(gerado)</dark_gray>");
            headLore.add(Theme.DIV);
            headLore.add("<dark_gray>Menu principal da nação</dark_gray>");
            inv.setItem(4, Theme.item(Material.GOLDEN_HELMET,
                    "<gradient:#FFD700:#FFA000><bold>" + nation.getDisplayName().toUpperCase() + "</bold></gradient>",
                    headLore.toArray(new String[0])));

            // Tesouro
                        // Missoes de Infraestrutura (acima do Cambio)
            inv.setItem(13, Theme.item(Material.ARMOR_STAND,
                    "<gradient:#FFD700:#FFA000><bold>◆ Missoes de Infraestrutura</bold></gradient>",
                    Theme.DIV,
                    "<gray>Territorio · Economia · Cidade</gray>",
                    "<gray>Recompensas automaticas no Tesouro</gray>",
                    Theme.DIV,
                    "<yellow>Clique para abrir</yellow>"));

            inv.setItem(20, Theme.item(Material.GOLD_BLOCK,
                    "<gradient:#FFD700:#FFA000><bold>TESOURO NACIONAL</bold></gradient>",
                    Theme.DIV,
                    "<gray>Tesouro Nacional:</gray> <gold>" + nation.getCurrencySymbol() + " " + Theme.fmt(nation.getTreasury()) + "</gold>",
                    "<gray>Banco Central:</gray> <gold>" + nation.getCurrencySymbol() + " " + Theme.fmt(nation.getCentralBankBalance()) + "</gold>",
                    Theme.DIV,
                    "<gray>Saldo Total da União:</gray> <green><bold>" + nation.getCurrencySymbol() + " "
                            + Theme.fmt(nation.getTreasury() + nation.getCentralBankBalance()) + "</bold></green>",
                    Theme.DIV,
                    "<gray>Status da Moeda:</gray> " + emoji + " <green>" + status + "</green>",
                    "<gray>Investimentos debitam do:</gray> <yellow>Banco Central</yellow>"));

            // Cambio
            inv.setItem(22, Theme.item(Material.GOLD_INGOT,
                    "<gradient:#FFD700:#FFA000><bold>[◆] CAMBIO</bold></gradient>",
                    Theme.DIV,
                    "<gray>Paridade inicial:</gray> <yellow>1:1</yellow>",
                    "<gray>ISSP:</gray> <red>3%</red> <dark_gray>(Tesouro destino)</dark_gray>",
                    "<gray>ISDN:</gray> <red>2%</red> <dark_gray>(BC destino)</dark_gray>",
                    Theme.DIV,
                    "<gray>Conversoes sao feitas pelo chat.</gray>",
                    "<gray>Painel apenas informativo.</gray>"));

            // Diplomacia
            inv.setItem(24, Theme.item(Material.SHIELD,
                    "<gradient:#1565C0:#42A5F5><bold>DIPLOMACIA</bold></gradient>",
                    Theme.DIV,
                    "<gray>Relações possíveis:</gray>",
                    "<green>Aliado</green>",
                    "<gray>Neutro</gray>",
                    "<red>Guerra</red>",
                    Theme.DIV,
                    "<gray>Guerra exige votação mútua de 70%.</gray>"));

            // Guerra
            inv.setItem(29, Theme.item(Material.IRON_SWORD,
                    "<gradient:#D32F2F:#FF5252><bold>[◆] GUERRA</bold></gradient>",
                    Theme.DIV,
                    "<gray>Aprovacao necessaria:</gray> <yellow>70%</yellow>",
                    "<gray>Votacao:</gray> <white>Indefinida</white>",
                    "<gray>Declaracao:</gray> <white>Mutua</white>",
                    Theme.DIV,
                    "<gray>Embargo progressivo ate confirmacao.</gray>",
                    Theme.DIV,
                    "<yellow>Clique para abrir a votacao</yellow>"));

            // Loja
            inv.setItem(31, Theme.item(Material.CHEST,
                    "<gradient:#00C853:#00BFA5><bold>LOJA DA NAÇÃO</bold></gradient>",
                    Theme.DIV,
                    "<gray>Status:</gray> <green>Aberta ao Público</green>",
                    "<gray>Taxa de Venda Local:</gray> <yellow>15%</yellow>",
                    "<gray>Taxa de Exportação:</gray> <red>45%</red>",
                    Theme.DIV,
                    "<gray>Clique para acessar o mercado.</gray>"));

            // Empregos
            inv.setItem(40, Theme.item(Material.IRON_PICKAXE,
                    "<gradient:#8D6E63:#A1887F><bold>[◆] EMPREGOS</bold></gradient>",
                    Theme.DIV,
                    "<gray>5 categorias de trabalho</gray>",
                    "<gray>Ativacao pelo Presidente (BC)</gray>",
                    "<gray>Pagamento de salário a cada 1 hora online</gray>",
                    Theme.DIV,
                    "<yellow>Clique para abrir</yellow>"));

            // Territorio
            inv.setItem(33, Theme.item(Material.MAP,
                    "<gradient:#7B1FA2:#CE93D8><bold>TERRITÓRIO</bold></gradient>",
                    Theme.DIV,
                    "<gray>Chunks claimados:</gray> <white>" + plugin.getClaimManager().countClaims(nation.getId()) + "</white>",
                    Theme.DIV,
                    "<gray>Claims debitam do Banco Central.</gray>"));

            // Rodapé - saldo do jogador
            inv.setItem(49, Theme.item(Material.EMERALD,
                    "<green><bold>Seu Saldo</bold></green>",
                    Theme.DIV,
                    "<gray>Moeda local:</gray> <yellow>" + nation.getCurrencyName() + "</yellow>",
                    "<gray>Saldo:</gray> <gold>" + nation.getCurrencySymbol() + " " + Theme.fmt(bal) + "</gold>"));
        }

        Theme.fillAll(inv);
        Theme.fillFooter(inv, 45, 53);
        player.openInventory(inv);
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.4f);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(e.getView().title());
        // Apenas menu principal (NACAO · / IMIGRANTE). Nunca Loja, LOJAS GLOBAIS, etc.
        String up = title.toUpperCase();
        if (up.contains("LOJA") || up.contains("LOJAS") || up.contains("MERCADO")
                || up.contains("TOP ") || up.contains("RANKING") || up.contains("GLOBAL")) {
            return;
        }
        if (!up.contains("NACAO") && !up.contains("IMIGRANTE")) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == null) return;
        // Ignora cliques no inventario do jogador (baixo)
        if (e.getClickedInventory().equals(p.getInventory())) return;

        int s = e.getSlot();
        ItemStack cur = e.getCurrentItem();
        if (cur == null || cur.getType().isAir()) return;
        String mat = cur.getType().name();
        if (mat.contains("GLASS") || mat.contains("PANE")) return; // decoracao

        if (s == 53 || s == 45) { p.closeInventory(); return; }
        Nation nation = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
        if (nation == null) return;
        if (s == 13 && cur.getType() == Material.ARMOR_STAND) plugin.getInfrastructureGUI().openMain(p);
        else if (s == 20) plugin.getTreasuryGUI().open(p);
        else if (s == 22) plugin.getExchangeGUI().openMain(p);
        else if (s == 24) plugin.getDiplomacyGUI().open(p);
        else if (s == 29) {
            if (!plugin.getRoleManager().hasRoleAtLeast(p.getUniqueId(), com.nationswaw.model.RoleType.PRESIDENT)) {
                p.sendMessage(Theme.MM.deserialize("<red>Apenas o Presidente pode abrir o menu de Guerra.</red>"));
            } else {
                plugin.getWarVoteGUI().open(p);
            }
        }
        else if (s == 31) plugin.getShopGUI().open(p, nation.getId());
        else if (s == 40) plugin.getJobsGUI().openMain(p);
        else if (s == 33) plugin.getClaimManager().openClaimsGUI(p);
    }
}
