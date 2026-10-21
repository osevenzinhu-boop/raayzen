package com.nationswaw.systems;

import com.nationswaw.NationsWaW;
import com.nationswaw.gui.Theme;
import com.nationswaw.model.JobType;
import com.nationswaw.model.Nation;
import com.nationswaw.model.NationJob;
import com.nationswaw.model.RoleType;
import com.nationswaw.utils.Ledger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Empregos: apenas Civis.
 * Salário a cada 1h ONLINE (timer pausa offline).
 * Missões com cooldown 10 min. Vagas 3..8. Upgrades debitam BC.
 */
public class JobManager implements Listener {
    private final NationsWaW plugin;
    private final Ledger ledger;
    private final Map<String, Map<JobType, NationJob>> jobs = new ConcurrentHashMap<>();

    private static final long QUIT_MS = 3L * 24 * 60 * 60 * 1000;
    private static final long MISSION_COOLDOWN_MS = 10L * 60 * 1000;
    private static final long SALARY_MS = 60L * 60 * 1000; // 1h online

    /** Progresso de missão ativa */
    public static class MissionState {
        public int progress;
        public long cooldownUntil; // 0 = sem cooldown
        public MissionState() { this.progress = 0; this.cooldownUntil = 0; }
    }

    private final Map<UUID, MissionState> missions = new ConcurrentHashMap<>();
    /** Tempo online acumulado para salário (ms) desde ultimo pagamento */
    private final Map<UUID, Long> onlineAccumMs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> sessionStartMs = new ConcurrentHashMap<>();
    /** nationId -> nivel 0..5 de reducao de tempo de salário (-5 min por nivel) */

    public JobManager(NationsWaW plugin) {
        this.plugin = plugin;
        this.ledger = new Ledger(plugin);
        for (String id : plugin.getConfigManager().getNationIds()) {
            Map<JobType, NationJob> map = new EnumMap<>(JobType.class);
            for (JobType t : JobType.values()) map.put(t, new NationJob(t));
            jobs.put(id, map);
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        // Tick a cada 30s para checar salarios
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickSalaries, 20L * 30, 20L * 30);
        plugin.getLogger().info("JobManager: salarios 1h online, missoes ativas.");
    }

    public NationJob getJob(String nationId, JobType type) {
        return jobs.getOrDefault(nationId, Map.of()).get(type);
    }

    public Collection<NationJob> getJobs(String nationId) {
        Map<JobType, NationJob> m = jobs.get(nationId);
        return m == null ? List.of() : m.values();
    }

    public NationJob getPlayerJob(UUID uuid, String nationId) {
        for (NationJob j : getJobs(nationId)) {
            if (j.workers.contains(uuid)) return j;
        }
        return null;
    }

    public MissionState getMission(UUID uuid) {
        return missions.computeIfAbsent(uuid, u -> new MissionState());
    }


    public int getSalaryTimeLevel(String nationId, JobType type) {
        NationJob job = getJob(nationId, type);
        return job == null ? 0 : job.timeLevel;
    }

    /** @deprecated use getSalaryIntervalMs(nationId, type) */
    public long getSalaryIntervalMs(String nationId) {
        return 60L * 60_000L;
    }

    public long getSalaryIntervalMs(String nationId, JobType type) {
        NationJob job = getJob(nationId, type);
        if (job == null) return 60L * 60_000L;
        return job.salaryIntervalMs();
    }

    public static double timeUpgradeCost(int nextLevel) {
        return switch (nextLevel) {
            case 1 -> 1_000_000;
            case 2 -> 2_500_000;
            case 3 -> 4_000_000;
            case 4 -> 6_000_000;
            case 5 -> 10_000_000;
            default -> Double.MAX_VALUE;
        };
    }

    public boolean upgradeSalaryTime(Player president, String nationId, JobType type) {
        if (!plugin.getRoleManager().hasRoleAtLeast(president.getUniqueId(), RoleType.PRESIDENT)) {
            president.sendMessage(Theme.MM.deserialize("<red>Apenas o Presidente pode fazer este upgrade.</red>"));
            return false;
        }
        Nation n = plugin.getNationManager().getNation(nationId);
        NationJob job = getJob(nationId, type);
        if (n == null || job == null || !job.active) {
            president.sendMessage(Theme.MM.deserialize("<red>Emprego inativo.</red>"));
            return false;
        }
        if (job.timeLevel >= NationJob.MAX_TIME_UPGRADES) {
            president.sendMessage(Theme.MM.deserialize("<red>Limite de upgrades de tempo (5 / min 35 min) neste emprego.</red>"));
            return false;
        }
        int next = job.timeLevel + 1;
        double cost = timeUpgradeCost(next);
        if (!n.removeFromCentralBank(cost)) {
            president.sendMessage(Theme.MM.deserialize("<red>BC insuficiente. Necessario: "
                    + n.getCurrencySymbol() + " " + Theme.fmt(cost) + "</red>"));
            return false;
        }
        job.timeLevel = next;
        ledger.record(nationId, "JOB_TIME_" + type.name(), cost, president.getUniqueId(), "Upgrade tempo salario");
        plugin.getNationManager().saveAll();
        president.sendMessage(Theme.MM.deserialize("<green>Tempo de salario de " + type.getDisplayName()
                + ": " + job.salaryIntervalMinutes() + " min. Custo: "
                + n.getCurrencySymbol() + " " + Theme.fmt(cost) + "</green>"));
        return true;
    }

    // ─── Ativacao / Upgrades (Presidente) ───────────────────────────

    public boolean activate(Player president, String nationId, JobType type) {
        if (!plugin.getRoleManager().hasRoleAtLeast(president.getUniqueId(), RoleType.PRESIDENT)) {
            president.sendMessage(Theme.MM.deserialize("<red>Apenas o Presidente pode ativar empregos.</red>"));
            return false;
        }
        Nation n = plugin.getNationManager().getNation(nationId);
        NationJob job = getJob(nationId, type);
        if (n == null || job == null) return false;
        if (job.active) {
            president.sendMessage(Theme.MM.deserialize("<yellow>Emprego ja ativo.</yellow>"));
            return false;
        }
        double cost = type.getActivationCost();
        if (!n.removeFromCentralBank(cost)) {
            president.sendMessage(Theme.MM.deserialize("<red>Banco Central insuficiente. Necessario: "
                    + n.getCurrencySymbol() + " " + Theme.fmt(cost) + "</red>"));
            return false;
        }
        job.active = true;
        ledger.record(nationId, "JOB_ACTIVATE_" + type.name(), cost, president.getUniqueId(), "Ativacao emprego");
        plugin.getNationManager().saveAll();
        president.sendMessage(Theme.MM.deserialize("<green>Emprego " + type.getDisplayName()
                + " ativado! Debitado: " + n.getCurrencySymbol() + " " + Theme.fmt(cost) + "</green>"));
        return true;
    }

    public boolean upgradeSlots(Player president, String nationId, JobType type) {
        if (!plugin.getRoleManager().hasRoleAtLeast(president.getUniqueId(), RoleType.PRESIDENT)) {
            president.sendMessage(Theme.MM.deserialize("<red>Apenas o Presidente pode fazer upgrades.</red>"));
            return false;
        }
        Nation n = plugin.getNationManager().getNation(nationId);
        NationJob job = getJob(nationId, type);
        if (n == null || job == null || !job.active) {
            president.sendMessage(Theme.MM.deserialize("<red>Emprego inativo.</red>"));
            return false;
        }
        if (job.slotLevel >= NationJob.MAX_SLOT_UPGRADES) {
            president.sendMessage(Theme.MM.deserialize("<red>Limite de upgrades de vaga (5 / max 8 vagas).</red>"));
            return false;
        }
        int next = job.slotLevel + 1;
        double cost = JobType.slotUpgradeCost(next);
        if (!n.removeFromCentralBank(cost)) {
            president.sendMessage(Theme.MM.deserialize("<red>BC insuficiente. Necessario: "
                    + n.getCurrencySymbol() + " " + Theme.fmt(cost) + "</red>"));
            return false;
        }
        job.slotLevel = next;
        try { plugin.getInfrastructureMissionManager().checkJobMax(nationId, type); } catch (Exception ignored) {}
        ledger.record(nationId, "JOB_SLOT_" + type.name(), cost, president.getUniqueId(), "Upgrade vaga");
        plugin.getNationManager().saveAll();
        president.sendMessage(Theme.MM.deserialize("<green>Vagas de " + type.getDisplayName()
                + ": " + job.maxSlots() + "/8. Custo: " + n.getCurrencySymbol() + " " + Theme.fmt(cost) + "</green>"));
        return true;
    }

    /** @deprecated use upgradeSlots */
    public boolean upgradeIncome(Player president, String nationId, JobType type) {
        return upgradeSlots(president, nationId, type);
    }

    public boolean upgradeSalary(Player president, String nationId, JobType type) {
        if (!plugin.getRoleManager().hasRoleAtLeast(president.getUniqueId(), RoleType.PRESIDENT)) {
            president.sendMessage(Theme.MM.deserialize("<red>Apenas o Presidente pode fazer upgrades.</red>"));
            return false;
        }
        Nation n = plugin.getNationManager().getNation(nationId);
        NationJob job = getJob(nationId, type);
        if (n == null || job == null || !job.active) {
            president.sendMessage(Theme.MM.deserialize("<red>Emprego inativo.</red>"));
            return false;
        }
        if (job.salaryLevel >= NationJob.MAX_SALARY_UPGRADES) {
            president.sendMessage(Theme.MM.deserialize("<red>Limite de upgrades de salário (5).</red>"));
            return false;
        }
        int next = job.salaryLevel + 1;
        double cost = JobType.salaryUpgradeCost(next);
        if (!n.removeFromCentralBank(cost)) {
            president.sendMessage(Theme.MM.deserialize("<red>BC insuficiente. Necessario: "
                    + n.getCurrencySymbol() + " " + Theme.fmt(cost) + "</red>"));
            return false;
        }
        job.salaryLevel = next;
        try { plugin.getInfrastructureMissionManager().checkJobMax(nationId, type); } catch (Exception ignored) {}
        ledger.record(nationId, "JOB_SALARY_" + type.name(), cost, president.getUniqueId(), "Upgrade salario");
        plugin.getNationManager().saveAll();
        president.sendMessage(Theme.MM.deserialize("<green>Salário de " + type.getDisplayName()
                + " agora: " + n.getCurrencySymbol() + " " + Theme.fmt(job.grossSalary())
                + " (+15% composto x" + job.salaryLevel + "). Custo: " + Theme.fmt(cost) + "</green>"));
        return true;
    }

    // ─── Candidatura / Saida (apenas Civil) ──────────────────────────

    public boolean apply(Player player, String nationId, JobType type) {
        RoleType role = plugin.getRoleManager().getRole(player.getUniqueId());
        if (role != RoleType.CITIZEN) {
            player.sendMessage(Theme.MM.deserialize(
                    "<red>Apenas Civis podem ter empregos. Politicos nao podem trabalhar.</red>"));
            return false;
        }
        Nation n = plugin.getNationManager().getNation(nationId);
        NationJob job = getJob(nationId, type);
        if (n == null || job == null || !job.active) {
            player.sendMessage(Theme.MM.deserialize("<red>Emprego indisponível.</red>"));
            return false;
        }
        NationJob current = getPlayerJob(player.getUniqueId(), nationId);
        if (current != null) {
            player.sendMessage(Theme.MM.deserialize("<red>Voce ja tem emprego: " + current.type.getDisplayName() + "</red>"));
            return false;
        }
        Long lastQuit = job.quitCooldown.get(player.getUniqueId());
        if (lastQuit != null && System.currentTimeMillis() - lastQuit < QUIT_MS) {
            long left = QUIT_MS - (System.currentTimeMillis() - lastQuit);
            long h = left / 3_600_000;
            player.sendMessage(Theme.MM.deserialize("<red>Cooldown de saida: espere mais " + h + "h.</red>"));
            return false;
        }
        // Cooldown global de quit em qualquer emprego da nacao
        for (NationJob j : getJobs(nationId)) {
            Long q = j.quitCooldown.get(player.getUniqueId());
            if (q != null && System.currentTimeMillis() - q < QUIT_MS) {
                long left = QUIT_MS - (System.currentTimeMillis() - q);
                long h = left / 3_600_000;
                player.sendMessage(Theme.MM.deserialize("<red>Cooldown de saida de emprego: " + h + "h restantes.</red>"));
                return false;
            }
        }
        if (!job.hasVacancy()) {
            player.sendMessage(Theme.MM.deserialize("<red>Sem vagas (" + job.usedSlots() + "/" + job.maxSlots() + ").</red>"));
            return false;
        }
        job.workers.add(player.getUniqueId());
        missions.put(player.getUniqueId(), new MissionState());
        onlineAccumMs.put(player.getUniqueId(), 0L);
        sessionStartMs.put(player.getUniqueId(), System.currentTimeMillis());
        plugin.getNationManager().saveAll();
        player.sendMessage(Theme.MM.deserialize("<green>Voce foi contratado como "
                + type.getDisplayName() + "! Vagas: " + job.usedSlots() + "/" + job.maxSlots() + "</green>"));
        return true;
    }

    public boolean quit(Player player, String nationId) {
        NationJob job = getPlayerJob(player.getUniqueId(), nationId);
        if (job == null) {
            player.sendMessage(Theme.MM.deserialize("<red>Voce nao tem emprego.</red>"));
            return false;
        }
        job.workers.remove(player.getUniqueId());
        job.quitCooldown.put(player.getUniqueId(), System.currentTimeMillis());
        missions.remove(player.getUniqueId());
        flushOnlineTime(player.getUniqueId());
        onlineAccumMs.remove(player.getUniqueId());
        sessionStartMs.remove(player.getUniqueId());
        plugin.getNationManager().saveAll();
        player.sendMessage(Theme.MM.deserialize(
                "<yellow>Voce saiu do emprego " + job.type.getDisplayName()
                        + ". Cooldown de 3 dias para se candidatar novamente.</yellow>"));
        return true;
    }

    /** Demissão forcada (promocao politica). Sem cooldown de reentrada. */
    public void forceFire(UUID uuid, String reason) {
        for (Map<JobType, NationJob> map : jobs.values()) {
            for (NationJob job : map.values()) {
                if (job.workers.remove(uuid)) {
                    missions.remove(uuid);
                    flushOnlineTime(uuid);
                    onlineAccumMs.remove(uuid);
                    sessionStartMs.remove(uuid);
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        p.sendMessage(Theme.MM.deserialize(
                                "<gold>[◆] Voce foi demitido do emprego <bold>" + job.type.getDisplayName()
                                        + "</bold> automaticamente.</gold>"));
                        p.sendMessage(Theme.MM.deserialize("<gray>Motivo: " + reason + "</gray>"));
                    }
                }
            }
        }
    }

    // ─── Salário (1h online) ────────────────────────────────────────

    private void flushOnlineTime(UUID uuid) {
        Long start = sessionStartMs.remove(uuid);
        if (start == null) return;
        long add = System.currentTimeMillis() - start;
        onlineAccumMs.merge(uuid, add, Long::sum);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        Nation n = plugin.getNationManager().getNationByPlayer(u);
        if (n == null) return;
        if (getPlayerJob(u, n.getId()) != null) {
            sessionStartMs.put(u, System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        flushOnlineTime(e.getPlayer().getUniqueId());
    }

    private void tickSalaries() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Nation n = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
            if (n == null) continue;
            NationJob job = getPlayerJob(p.getUniqueId(), n.getId());
            if (job == null) continue;

            // acumula sessao atual
            long start = sessionStartMs.getOrDefault(p.getUniqueId(), now);
            long session = now - start;
            long accum = onlineAccumMs.getOrDefault(p.getUniqueId(), 0L) + session;

            long need = getSalaryIntervalMs(n.getId(), job.type);
            if (accum >= need) {
                // reseta contadores
                onlineAccumMs.put(p.getUniqueId(), accum % need);
                sessionStartMs.put(p.getUniqueId(), now);
                payOneSalary(p, n, job);
            }
        }
    }

    private void payOneSalary(Player p, Nation n, NationJob job) {
        double gross = job.grossSalary();
        double issp = gross * 0.10;
        double isdn = gross * 0.05;
        double net = gross - issp - isdn;
        plugin.getEconomyManager().addBalance(p.getUniqueId(), n.getId(), net);
        if (!plugin.getDebtManager().redirectTax(n.getId(), issp, isdn)) {
            n.addTreasury(issp);
            n.addToCentralBank(isdn);
        }
        ledger.record(n.getId(), "JOB_SALARY", net, p.getUniqueId(), job.type.name());
        plugin.getNationManager().saveAll();
        p.sendMessage(Theme.MM.deserialize(
                "<gold>==========================================</gold>\n" +
                "<gold>[◆] SALARIO - " + job.type.getDisplayName() + "</gold>\n" +
                "<gray>[■] Bruto:</gray> <white>" + n.getCurrencySymbol() + " " + Theme.fmt(gross) + "</white>\n" +
                "<gray>[■] ISSP 10% (Tesouro):</gray> <red>-" + Theme.fmt(issp) + "</red>\n" +
                "<gray>[■] ISDN 5% (BC):</gray> <red>-" + Theme.fmt(isdn) + "</red>\n" +
                "<green>[■] Liquido:</green> <yellow>" + n.getCurrencySymbol() + " " + Theme.fmt(net) + "</yellow>\n" +
                "<gold>==========================================</gold>"));
    }

    // ─── Missoes ────────────────────────────────────────────────────

    private void tryMissionProgress(Player p, JobType required, int amount, boolean requireClaim) {
        Nation n = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
        if (n == null) return;
        NationJob job = getPlayerJob(p.getUniqueId(), n.getId());
        if (job == null || job.type != required) return;

        if (requireClaim) {
            String owner = plugin.getClaimManager().getOwner(
                    p.getWorld().getName(),
                    p.getLocation().getChunk().getX(),
                    p.getLocation().getChunk().getZ());
            if (owner == null || !owner.equalsIgnoreCase(n.getId())) return;
        }

        MissionState ms = getMission(p.getUniqueId());
        long now = System.currentTimeMillis();
        if (ms.cooldownUntil > now) return;

        ms.progress += amount;
        if (ms.progress >= required.getMissionTarget()) {
            completeMission(p, n, job);
        }
    }

    private void completeMission(Player p, Nation n, NationJob job) {
        JobType t = job.type;
        double gross = t.getMissionReward();
        double issp = gross * t.getMissionIssp();
        double isdn = gross * t.getMissionIsdn();
        double net = gross - issp - isdn;

        plugin.getEconomyManager().addBalance(p.getUniqueId(), n.getId(), net);
        if (!plugin.getDebtManager().redirectTax(n.getId(), issp, isdn)) {
            n.addTreasury(issp);
            n.addToCentralBank(isdn);
        }
        ledger.record(n.getId(), "JOB_MISSION_" + t.name(), net, p.getUniqueId(), "Missão");

        MissionState ms = getMission(p.getUniqueId());
        ms.progress = 0;
        ms.cooldownUntil = System.currentTimeMillis() + MISSION_COOLDOWN_MS;
        plugin.getNationManager().saveAll();

        p.sendMessage(Theme.MM.deserialize(
                "<gold>==========================================</gold>\n" +
                "<gold>[◆] MISSAO CONCLUIDA - " + t.getDisplayName() + "</gold>\n" +
                "<gray>[■] Recompensa Bruta:</gray> <white>" + n.getCurrencySymbol() + " " + Theme.fmt(gross) + "</white>\n" +
                "<gray>[■] Impostos (ISSP/ISDN):</gray> <red>" + n.getCurrencySymbol() + " " + Theme.fmt(issp + isdn) + "</red>\n" +
                "<green>[■] Valor Liquido Recebido:</green> <yellow>" + n.getCurrencySymbol() + " " + Theme.fmt(net) + "</yellow>\n" +
                "<gray>[■] Proxima missão disponível em:</gray> <aqua>10:00 minutos</aqua>\n" +
                "<gold>==========================================</gold>"));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        tryMissionProgress(e.getPlayer(), JobType.ENGINEER, 1, true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        Material m = b.getType();

        // Lenhador: qualquer log
        if (Tag.LOGS.isTagged(m) || m.name().endsWith("_LOG") || m.name().endsWith("_WOOD")) {
            tryMissionProgress(p, JobType.LUMBERJACK, 1, false);
        }
        // Minerador: minerios
        if (m.name().endsWith("_ORE") || m == Material.ANCIENT_DEBRIS
                || m == Material.NETHER_QUARTZ_ORE || m == Material.GILDED_BLACKSTONE) {
            tryMissionProgress(p, JobType.MINER, 1, false);
        }
        // Agricultor: colheita (blocos de cultura madura / crops)
        if (isCrop(m)) {
            tryMissionProgress(p, JobType.FARMER, 1, true);
        }
    }

    private boolean isCrop(Material m) {
        return m == Material.WHEAT || m == Material.CARROTS || m == Material.POTATOES
                || m == Material.BEETROOTS || m == Material.NETHER_WART
                || m == Material.COCOA || m == Material.SUGAR_CANE
                || m == Material.MELON || m == Material.PUMPKIN
                || m == Material.SWEET_BERRY_BUSH || m == Material.CAVE_VINES
                || m == Material.CAVE_VINES_PLANT;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        if (!(e.getEntity() instanceof Monster) && e.getEntityType() != EntityType.SLIME
                && e.getEntityType() != EntityType.MAGMA_CUBE
                && e.getEntityType() != EntityType.PHANTOM
                && e.getEntityType() != EntityType.SHULKER) {
            // so hostis
            if (!(e.getEntity() instanceof org.bukkit.entity.Enemy)) return;
        }
        tryMissionProgress(killer, JobType.SECURITY, 1, true);
    }

    /** @deprecated vendas removidas */
    public void sellHand(Player p, String nationId) {
        p.sendMessage(Theme.MM.deserialize("<yellow>Sistema de vendas removido. Use Missoes.</yellow>"));
    }
}
