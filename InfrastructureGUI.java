package com.nationswaw.gui;

import com.nationswaw.NationsWaW;
import com.nationswaw.model.JobType;
import com.nationswaw.model.Nation;
import com.nationswaw.model.NationJob;
import com.nationswaw.systems.InfrastructureMissionManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public class InfrastructureGUI implements Listener {
    private final NationsWaW plugin;

    public InfrastructureGUI(NationsWaW plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openMain(Player p) {
        Nation n = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
        if (n == null) {
            p.sendMessage(Theme.MM.deserialize("<red>Sem nacao.</red>"));
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 54, Theme.title("MISSOES DE INFRAESTRUTURA"));
        Theme.fillAll(inv);

        inv.setItem(4, Theme.item(Material.ARMOR_STAND,
                "<gradient:#FFD700:#FFA000><bold>◆ Missoes de Infraestrutura</bold></gradient>",
                Theme.DIV,
                "<gray>Desenvolva o territorio, a economia</gray>",
                "<gray>e a cidade da sua nacao.</gray>",
                Theme.DIV,
                "<dark_gray>Recompensas vao ao Tesouro / BC</dark_gray>"));

        inv.setItem(20, Theme.item(Material.FILLED_MAP,
                "<green><bold>Expansao de Territorio</bold></green>",
                Theme.DIV,
                "<gray>Claims atuais:</gray> <white>"
                        + plugin.getClaimManager().countClaims(n.getId()) + "/1500</white>",
                "<gray>Marcos:</gray> <yellow>5 a 250 claims</yellow>",
                "<gray>Recompensa:</gray> <gold>" + n.getCurrencySymbol() + " 100.000</gold> <gray>por marco</gray>",
                Theme.DIV,
                "<yellow>» Clique para ver progresso</yellow>"));

        inv.setItem(22, Theme.item(Material.EXPERIENCE_BOTTLE,
                "<aqua><bold>Sistema Economico</bold></aqua>",
                Theme.DIV,
                "<gray>Melhorar Vagas + Salario de cada emprego</gray>",
                "<gray>Recompensa:</gray> <gold>" + n.getCurrencySymbol() + " 2.500.000</gold> <gray>por emprego</gray>",
                Theme.DIV,
                "<yellow>» Clique para ver progresso</yellow>"));

        boolean city = plugin.getInfrastructureMissionManager().isCityDone(n.getId());
        inv.setItem(24, Theme.item(city ? Material.BEACON : Material.BRICKS,
                city ? "<green><bold>[✔] Construcao da Cidade</bold></green>"
                        : "<gold><bold>Construcao da Cidade</bold></gold>",
                Theme.DIV,
                "<gray>[◆] Construa uma cidade/nacao digna.</gray>",
                "<gray>[■] A avaliacao e aprovacao serao feitas</gray>",
                "<gray>  por um Administrador.</gray>",
                Theme.DIV,
                "<gray>[■] Recompensa Base:</gray>",
                "<gold>  " + n.getCurrencySymbol() + " 10.000.000,00 (Tesouro)</gold>",
                "<aqua>  " + n.getCurrencySymbol() + " 10.000.000,00 (BC)</aqua>",
                Theme.DIV,
                city ? "<green>[✔] Missao concluida!</green>" : "<yellow>Aguardando avaliacao admin</yellow>"));

        Theme.fillFooter(inv, 45, 53);
        inv.setItem(45, Theme.item(Material.ARROW, "<yellow>« Voltar</yellow>"));
        inv.setItem(53, Theme.item(Material.BARRIER, "<red>Fechar</red>"));
        p.openInventory(inv);
        try { p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f); } catch (Exception ignored) {}
    }

    public void openClaims(Player p, int page) {
        Nation n = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
        if (n == null) return;
        int claims = plugin.getClaimManager().countClaims(n.getId());
        Inventory inv = Bukkit.createInventory(null, 54, Theme.title("TERRITORIO · Pag " + (page + 1)));
        Theme.fillAll(inv);

        inv.setItem(4, Theme.item(Material.MAP,
                "<gradient:#00C853:#69F0AE><bold>Expansao de Territorio</bold></gradient>",
                "<gray>Claims:</gray> <white>" + claims + "</white>",
                "<gray>Cada marco de 5 claims = "
                        + n.getCurrencySymbol() + " 100.000 no Tesouro</gray>"));

        // 45 content slots max; 50 milestones across pages of 28 (slots 10-43 skip borders)
        int perPage = 28;
        int startIdx = page * perPage;
        int slot = 10;
        int shown = 0;
        for (int i = startIdx; i < 50 && shown < perPage; i++) {
            int milestone = (i + 1) * InfrastructureMissionManager.CLAIM_STEP;
            if (milestone > InfrastructureMissionManager.CLAIM_MAX) break;
            while (slot % 9 == 0 || slot % 9 == 8 || slot >= 44) slot++;
            if (slot >= 44) break;

            boolean done = plugin.getInfrastructureMissionManager().isClaimMilestoneDone(n.getId(), milestone)
                    || claims >= milestone;
            // auto-mark visual if reached even if not yet rewarded (check triggers reward)
            if (claims >= milestone && !plugin.getInfrastructureMissionManager().isClaimMilestoneDone(n.getId(), milestone)) {
                plugin.getInfrastructureMissionManager().onClaimCountChanged(n.getId());
                done = true;
            }

            if (done) {
                inv.setItem(slot, Theme.item(Material.EMERALD,
                        "<green><bold>[✔] Marco " + milestone + " claims</bold></green>",
                        Theme.DIV,
                        "<green>[✔] Concluido!</green>",
                        "<green>Recompensa entregue ao Tesouro Nacional.</green>",
                        Theme.DIV,
                        "<gray>" + n.getCurrencySymbol() + " 100.000</gray>"));
            } else {
                inv.setItem(slot, Theme.item(Material.CHEST,
                        "<yellow><bold>Marco " + milestone + " claims</bold></yellow>",
                        Theme.DIV,
                        "<gray>[■] Progresso:</gray> <white>" + claims + "/" + milestone + "</white>",
                        "<gray>[■] Recompensa:</gray> <gold>" + n.getCurrencySymbol() + " 100.000</gold> <gray>no Tesouro</gray>",
                        Theme.DIV,
                        claims >= milestone ? "<green>Pronto para resgatar!</green>"
                                : "<dark_gray>Faltam " + (milestone - claims) + " claims</dark_gray>"));
            }
            slot++;
            shown++;
        }

        if (page > 0) inv.setItem(45, Theme.item(Material.ARROW, "<yellow>« Pagina anterior</yellow>"));
        else inv.setItem(45, Theme.item(Material.ARROW, "<yellow>« Voltar</yellow>"));
        if (startIdx + perPage < 50) inv.setItem(49, Theme.item(Material.PAPER, "<aqua>Proxima pagina »</aqua>"));
        inv.setItem(53, Theme.item(Material.BARRIER, "<red>Fechar</red>"));
        p.openInventory(inv);
    }

    public void openJobs(Player p) {
        Nation n = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
        if (n == null) return;
        Inventory inv = Bukkit.createInventory(null, 54, Theme.title("ECONOMIA · EMPREGOS"));
        Theme.fillAll(inv);

        inv.setItem(4, Theme.item(Material.EXPERIENCE_BOTTLE,
                "<gradient:#1565C0:#42A5F5><bold>Sistema Economico</bold></gradient>",
                "<gray>Melhore Vagas (5) e Salario (5) de cada emprego</gray>",
                "<gray>Recompensa:</gray> <gold>2.500.000</gold> <gray>por emprego no nivel maximo</gray>"));

        JobType[] types = JobType.values();
        int[] slots = {20, 22, 24, 30, 32};
        for (int i = 0; i < types.length; i++) {
            JobType t = types[i];
            NationJob job = plugin.getJobManager().getJob(n.getId(), t);
            int slotLvl = job == null ? 0 : job.slotLevel;
            int salLvl = job == null ? 0 : job.salaryLevel;
            boolean rewarded = plugin.getInfrastructureMissionManager().isJobMaxedRewarded(n.getId(), t);
            boolean maxed = job != null && job.active
                    && slotLvl >= NationJob.MAX_SLOT_UPGRADES
                    && salLvl >= NationJob.MAX_SALARY_UPGRADES;

            if (maxed && !rewarded) {
                plugin.getInfrastructureMissionManager().checkJobMax(n.getId(), t);
                rewarded = true;
            }

            if (rewarded || maxed) {
                inv.setItem(slots[i], Theme.item(Material.DIAMOND_BLOCK,
                        "<green><bold>[✔] " + t.getDisplayName() + "</bold></green>",
                        Theme.DIV,
                        "<green>[✔] Emprego no Nivel Maximo!</green>",
                        "<gray>Vagas:</gray> <white>8/8</white>  <gray>Salario:</gray> <white>+25%</white>",
                        "<gold>+" + n.getCurrencySymbol() + " 2.500.000</gold> <gray>Tesouro</gray>"));
            } else {
                inv.setItem(slots[i], Theme.item(t.getIcon(),
                        "<yellow><bold>" + t.getDisplayName() + "</bold></yellow>",
                        Theme.DIV,
                        job != null && job.active ? "<green>● Ativo</green>" : "<red>○ Inativo</red>",
                        "<gray>Upgrades de Vaga:</gray> <white>" + slotLvl + "/" + NationJob.MAX_SLOT_UPGRADES + "</white>",
                        "<gray>Upgrades de Salario:</gray> <white>" + salLvl + "/" + NationJob.MAX_SALARY_UPGRADES + "</white>",
                        Theme.DIV,
                        "<gray>Recompensa ao melhorar:</gray> <gold>" + n.getCurrencySymbol() + " 2.500.000</gold>"));
            }
        }

        Theme.fillFooter(inv, 45, 53);
        inv.setItem(45, Theme.item(Material.ARROW, "<yellow>« Voltar</yellow>"));
        inv.setItem(53, Theme.item(Material.BARRIER, "<red>Fechar</red>"));
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(e.getView().title());
        boolean main = title.contains("MISSOES DE INFRAESTRUTURA");
        boolean claims = title.contains("TERRITORIO");
        boolean jobs = title.contains("ECONOMIA");
        if (!main && !claims && !jobs) return;
        e.setCancelled(true);
        int s = e.getSlot();
        if (s == 53) { p.closeInventory(); return; }

        if (main) {
            if (s == 45) { plugin.getNationGUI().open(p); return; }
            if (s == 20) openClaims(p, 0);
            else if (s == 22) openJobs(p);
            else if (s == 24) {
                p.sendMessage(Theme.MM.deserialize(
                        "<gold>[◆] Missao de Cidade:</gold> <gray>Construa uma cidade digna. A avaliacao e aprovacao serao feitas por um Administrador.</gray>"));
            }
            return;
        }
        if (claims) {
            if (s == 45) {
                if (title.contains("Pag 1") || !title.contains("Pag")) openMain(p);
                else {
                    // previous page
                    try {
                        int page = Integer.parseInt(title.replaceAll(".*Pag\\s*", "").trim()) - 1;
                        openClaims(p, Math.max(0, page - 1));
                    } catch (Exception ex) { openMain(p); }
                }
                return;
            }
            if (s == 49) {
                try {
                    int page = Integer.parseInt(title.replaceAll(".*Pag\\s*", "").trim()) - 1;
                    openClaims(p, page + 1);
                } catch (Exception ex) { openClaims(p, 1); }
            }
            return;
        }
        if (jobs) {
            if (s == 45) openMain(p);
        }
    }
}
