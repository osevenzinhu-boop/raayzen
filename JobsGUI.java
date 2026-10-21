package com.nationswaw.gui;

import com.nationswaw.NationsWaW;
import com.nationswaw.model.JobType;
import com.nationswaw.model.Nation;
import com.nationswaw.model.NationJob;
import com.nationswaw.model.RoleType;
import com.nationswaw.systems.JobManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class JobsGUI implements Listener {
    private final NationsWaW plugin;
    private final Map<UUID, JobType> viewing = new HashMap<>();

    public JobsGUI(NationsWaW plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openMain(Player p) {
        Nation n = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
        if (n == null) {
            p.sendMessage(Theme.MM.deserialize("<red>Sem nação.</red>"));
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 54, Theme.title("EMPREGOS · " + n.getDisplayName()));
        Theme.fillAll(inv);

        RoleType role = plugin.getRoleManager().getRole(p.getUniqueId());
        inv.setItem(4, Theme.item(Material.BOOK,
                "<gradient:#FFD700:#FFA000><bold>Sistema de Empregos</bold></gradient>",
                "<gray>Apenas <white>Civis</white> podem trabalhar.</gray>",
                "<gray>Politicos (Presidente/Governador/Prefeito) nao.</gray>",
                "<gray>Seu cargo:</gray> <yellow>" + role.getDisplayName() + "</yellow>"));

        JobType[] types = JobType.values();
        int[] slots = {20, 22, 24, 30, 32};
        for (int i = 0; i < types.length; i++) {
            JobType t = types[i];
            NationJob job = plugin.getJobManager().getJob(n.getId(), t);
            boolean active = job != null && job.active;
            int used = job == null ? 0 : job.usedSlots();
            int max = job == null ? 3 : job.maxSlots();
            double sal = job == null ? t.getBaseSalary() : job.grossSalary();
            inv.setItem(slots[i], Theme.item(t.getIcon(),
                    (active ? "<green>" : "<red>") + "<bold>" + t.getDisplayName() + "</bold>",
                    active ? "<green>● Ativo</green>" : "<red>○ Inativo</red>",
                    "<gray>Vagas:</gray> <white>" + used + "/" + max + "</white>",
                    "<gray>Salário:</gray> <yellow>" + n.getCurrencySymbol() + " " + Theme.fmt(sal) + "/h online</yellow>",
                    "<gray>Missão:</gray> <aqua>" + t.getMissionDesc() + "</aqua>",
                    "",
                    "<yellow>» Clique para gerenciar</yellow>"));
        }

        Theme.fillFooter(inv, 45, 53);
        inv.setItem(45, Theme.item(Material.ARROW, "<yellow>« Voltar</yellow>"));
        inv.setItem(53, Theme.item(Material.BARRIER, "<red>Fechar</red>"));
        p.openInventory(inv);
    }

    public void openJob(Player p, JobType type) {
        Nation n = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
        if (n == null) return;
        viewing.put(p.getUniqueId(), type);
        NationJob job = plugin.getJobManager().getJob(n.getId(), type);
        boolean active = job != null && job.active;
        boolean isPresident = plugin.getRoleManager().hasRoleAtLeast(p.getUniqueId(), RoleType.PRESIDENT);
        RoleType role = plugin.getRoleManager().getRole(p.getUniqueId());
        boolean isWorker = job != null && job.workers.contains(p.getUniqueId());
        boolean isCivil = role == RoleType.CITIZEN;

        Inventory inv = Bukkit.createInventory(null, 54, Theme.title(type.getDisplayName().toUpperCase()));
        Theme.fillAll(inv);

        double sal = job == null ? type.getBaseSalary() : job.grossSalary();
        int used = job == null ? 0 : job.usedSlots();
        int max = job == null ? 3 : job.maxSlots();
        int slotLvl = job == null ? 0 : job.slotLevel;
        int salLvl = job == null ? 0 : job.salaryLevel;

        inv.setItem(4, Theme.item(type.getIcon(),
                "<gradient:#FFD700:#FFA000><bold>" + type.getDisplayName() + "</bold></gradient>",
                active ? "<green>● Emprego ativo</green>" : "<red>○ Emprego inativo</red>",
                "<gray>Vagas:</gray> <white>" + used + "/" + max + "</white> <dark_gray>(base 3 + upgrades)</dark_gray>",
                "<gray>Salário atual:</gray> <yellow>" + n.getCurrencySymbol() + " " + Theme.fmt(sal) + "/h online</yellow>",
                "<gray>Upgrade salario:</gray> <aqua>" + salLvl + "x +15% composto</aqua> <dark_gray>(max 5)</dark_gray>",
                "",
                "<gray>Missão:</gray> <white>" + type.getMissionDesc() + "</white>",
                "<gray>Recompensa bruta:</gray> <green>" + n.getCurrencySymbol() + " " + Theme.fmt(type.getMissionReward()) + "</green>"));

        if (isPresident) {
            // Ativar
            if (!active) {
                inv.setItem(19, Theme.item(Material.LIME_DYE,
                        "<green><bold>Ativar Emprego</bold></green>",
                        "<gray>Custo:</gray> <yellow>" + n.getCurrencySymbol() + " "
                                + Theme.fmt(type.getActivationCost()) + "</yellow>",
                        "<gray>Debitado do Banco Central</gray>",
                        "",
                        "<yellow>» Clique para ativar</yellow>"));
            } else {
                inv.setItem(19, Theme.item(Material.LIME_CONCRETE,
                        "<green><bold>Emprego Ativo</bold></green>",
                        "<gray>Vagas: " + used + "/" + max + "</gray>"));
            }

            // Upgrade vaga
            if (active) {
                if (slotLvl < NationJob.MAX_SLOT_UPGRADES) {
                    double cost = JobType.slotUpgradeCost(slotLvl + 1);
                    inv.setItem(21, Theme.item(Material.CHEST,
                            "<aqua><bold>Upgrade de Vaga</bold></aqua>",
                            "<gray>Nivel atual:</gray> <white>" + slotLvl + "/" + NationJob.MAX_SLOT_UPGRADES + "</white>",
                            "<gray>Vagas:</gray> <white>" + max + " → " + (max + 1) + "</white>",
                            "<gray>Custo:</gray> <yellow>" + n.getCurrencySymbol() + " " + Theme.fmt(cost) + "</yellow>",
                            "<dark_gray>Escala: 20k + 25% por nivel</dark_gray>",
                            "",
                            "<yellow>» Clique para upgrade</yellow>"));
                } else {
                    inv.setItem(21, Theme.item(Material.CHEST,
                            "<gray>Vagas no maximo (8)</gray>"));
                }

                // Upgrade salario
                if (salLvl < NationJob.MAX_SALARY_UPGRADES) {
                    double cost = JobType.salaryUpgradeCost(salLvl + 1);
                    inv.setItem(23, Theme.item(Material.GOLD_INGOT,
                            "<gold><bold>Upgrade de Salario</bold></gold>",
                            "<gray>Nivel:</gray> <white>" + salLvl + "/" + NationJob.MAX_SALARY_UPGRADES + "</white>",
                            "<gray>Salario:</gray> <yellow>" + Theme.fmt(sal) + " → "
                                    + Theme.fmt(type.getBaseSalary() * (1 + 0.05 * (salLvl + 1))) + "</yellow>",
                            "<gray>Proximo:</gray> <aqua>+15% composto</aqua>",
                            "<gray>Custo:</gray> <yellow>" + n.getCurrencySymbol() + " " + Theme.fmt(cost) + "</yellow>",
                            "",
                            "<yellow>» Clique para upgrade</yellow>"));
                } else {
                    inv.setItem(23, Theme.item(Material.GOLD_BLOCK,
                            "<gray>Salário no maximo (+25%)</gray>"));
                }

                // Upgrade tempo de salário (global)
                int timeLvl = plugin.getJobManager().getSalaryTimeLevel(n.getId(), type);
                long curMin = job != null ? job.salaryIntervalMinutes() : 60;
                if (timeLvl < NationJob.MAX_TIME_UPGRADES) {
                    double tCost = com.nationswaw.systems.JobManager.timeUpgradeCost(timeLvl + 1);
                    inv.setItem(25, Theme.item(Material.CLOCK,
                            "<light_purple><bold>Upgrade de Tempo de Salario</bold></light_purple>",
                            "<gray>Apenas este emprego: " + type.getDisplayName() + "</gray>",
                            "<gray>Nivel:</gray> <white>" + timeLvl + "/5</white>",
                            "<gray>Intervalo:</gray> <aqua>" + curMin + " min → " + (curMin - 5) + " min</aqua>",
                            "<gray>Custo:</gray> <yellow>" + n.getCurrencySymbol() + " " + Theme.fmt(tCost) + "</yellow>",
                            "",
                            "<yellow>» Clique para upgrade</yellow>"));
                } else {
                    inv.setItem(25, Theme.item(Material.CLOCK,
                            "<gray>Tempo minimo neste emprego (35 min)</gray>"));
                }
            }
        }

        // Civil worker / candidate
        if (isCivil && active) {
            JobManager.MissionState ms = plugin.getJobManager().getMission(p.getUniqueId());
            long now = System.currentTimeMillis();
            if (isWorker) {
                String cool;
                if (ms.cooldownUntil > now) {
                    long sec = (ms.cooldownUntil - now) / 1000;
                    cool = (sec / 60) + "m " + (sec % 60) + "s";
                } else {
                    cool = "Disponível";
                }
                inv.setItem(30, Theme.item(Material.WRITABLE_BOOK,
                        "<light_purple><bold>Missoes</bold></light_purple>",
                        "<gray>" + type.getMissionDesc() + "</gray>",
                        "<gray>Progresso:</gray> <white>" + ms.progress + "/" + type.getMissionTarget() + "</white>",
                        "<gray>Cooldown:</gray> <aqua>" + cool + "</aqua>",
                        "<gray>Recompensa liquida ~</gray> <green>"
                                + Theme.fmt(type.getMissionReward() * (1 - type.getMissionIssp() - type.getMissionIsdn()))
                                + "</green>",
                        "",
                        "<dark_gray>Progresso automatico ao jogar</dark_gray>"));

                inv.setItem(32, Theme.item(Material.RED_DYE,
                        "<red><bold>Sair do Emprego</bold></red>",
                        "<gray>Cooldown de 3 dias para se candidatar de novo</gray>",
                        "",
                        "<yellow>» Clique para sair</yellow>"));
            } else {
                inv.setItem(30, Theme.item(Material.EMERALD,
                        "<green><bold>Candidatar-se</bold></green>",
                        "<gray>Vagas:</gray> <white>" + used + "/" + max + "</white>",
                        "<gray>Salário:</gray> <yellow>" + n.getCurrencySymbol() + " " + Theme.fmt(sal) + "/h online</yellow>",
                        "",
                        "<yellow>» Clique para se candidatar</yellow>"));
            }
        } else if (!isCivil) {
            inv.setItem(30, Theme.item(Material.BARRIER,
                    "<red>Politicos nao podem ter emprego</red>",
                    "<gray>Apenas o cargo Civil pode trabalhar</gray>"));
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
        boolean main = title.contains("EMPREGOS");
        boolean sub = false;
        JobType subType = null;
        for (JobType t : JobType.values()) {
            if (title.toUpperCase().contains(t.getDisplayName().toUpperCase())) {
                sub = true;
                subType = t;
                break;
            }
        }
        if (!main && !sub) return;
        e.setCancelled(true);
        int s = e.getSlot();
        if (s == 53) { p.closeInventory(); return; }
        if (s == 45) {
            if (sub) openMain(p);
            else plugin.getNationGUI().open(p);
            return;
        }

        Nation n = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
        if (n == null) return;

        if (main) {
            JobType[] types = JobType.values();
            int[] slots = {20, 22, 24, 30, 32};
            for (int i = 0; i < slots.length; i++) {
                if (s == slots[i]) { openJob(p, types[i]); return; }
            }
            return;
        }

        JobType type = viewing.getOrDefault(p.getUniqueId(), subType);
        if (type == null) return;

        if (s == 19) {
            plugin.getJobManager().activate(p, n.getId(), type);
            openJob(p, type);
        } else if (s == 21) {
            plugin.getJobManager().upgradeSlots(p, n.getId(), type);
            openJob(p, type);
        } else if (s == 23) {
            plugin.getJobManager().upgradeSalary(p, n.getId(), type);
            openJob(p, type);
        } else if (s == 25) {
            plugin.getJobManager().upgradeSalaryTime(p, n.getId(), type);
            openJob(p, type);
        } else if (s == 30) {
            NationJob job = plugin.getJobManager().getJob(n.getId(), type);
            if (job != null && job.workers.contains(p.getUniqueId())) {
                // missoes: so informativo
                p.sendMessage(Theme.MM.deserialize(
                        "<aqua>Missão em andamento. Complete as acoes no mundo para progredir.</aqua>"));
            } else {
                plugin.getJobManager().apply(p, n.getId(), type);
            }
            openJob(p, type);
        } else if (s == 32) {
            plugin.getJobManager().quit(p, n.getId());
            openJob(p, type);
        }
    }
}
