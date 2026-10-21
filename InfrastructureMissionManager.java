package com.nationswaw.systems;

import com.nationswaw.NationsWaW;
import com.nationswaw.gui.Theme;
import com.nationswaw.model.JobType;
import com.nationswaw.model.Nation;
import com.nationswaw.model.NationJob;
import com.nationswaw.utils.Ledger;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Missoes de infraestrutura: claims (5..250), empregos maxados, cidade (admin).
 */
public class InfrastructureMissionManager {
    private final NationsWaW plugin;
    private final Ledger ledger;
    /** nationId -> set of claim milestones claimed (5,10,...,250) */
    private final Map<String, Set<Integer>> claimMilestones = new ConcurrentHashMap<>();
    /** nationId -> set of JobType names fully maxed and rewarded */
    private final Map<String, Set<String>> jobMaxed = new ConcurrentHashMap<>();
    /** nationId -> city completed */
    private final Map<String, Boolean> cityDone = new ConcurrentHashMap<>();

    public static final int CLAIM_STEP = 5;
    public static final int CLAIM_MAX = 250;
    public static final double CLAIM_REWARD = 100_000;
    public static final double JOB_MAX_REWARD = 2_500_000;
    public static final double CITY_TREASURY = 10_000_000;
    public static final double CITY_BC = 10_000_000;

    public InfrastructureMissionManager(NationsWaW plugin) {
        this.plugin = plugin;
        this.ledger = new Ledger(plugin);
        for (String id : plugin.getConfigManager().getNationIds()) {
            claimMilestones.put(id, ConcurrentHashMap.newKeySet());
            jobMaxed.put(id, ConcurrentHashMap.newKeySet());
            cityDone.put(id, false);
        }
        load();
    }

    private void load() {
        plugin.getDatabaseManager().queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM infra_milestones")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    String nid = rs.getString("nation_id");
                    String type = rs.getString("type");
                    String key = rs.getString("key");
                    if (rs.getInt("claimed") != 1) continue;
                    if ("CLAIM".equals(type)) {
                        try {
                            claimMilestones.computeIfAbsent(nid, k -> ConcurrentHashMap.newKeySet())
                                    .add(Integer.parseInt(key));
                        } catch (NumberFormatException ignored) {}
                    } else if ("JOB".equals(type)) {
                        jobMaxed.computeIfAbsent(nid, k -> ConcurrentHashMap.newKeySet()).add(key);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Load infra: " + e.getMessage());
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM city_mission")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    if (rs.getInt("completed") == 1) {
                        cityDone.put(rs.getString("nation_id"), true);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Load city: " + e.getMessage());
            }
            return null;
        }).join();
    }

    public void save() {
        plugin.getDatabaseManager().executeAsync(conn -> {
            try {
                for (var e : claimMilestones.entrySet()) {
                    for (Integer m : e.getValue()) {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "INSERT INTO infra_milestones (nation_id, type, key, claimed) VALUES (?,?,?,1) "
                                        + "ON CONFLICT(nation_id, type, key) DO UPDATE SET claimed=1")) {
                            ps.setString(1, e.getKey());
                            ps.setString(2, "CLAIM");
                            ps.setString(3, String.valueOf(m));
                            ps.executeUpdate();
                        }
                    }
                }
                for (var e : jobMaxed.entrySet()) {
                    for (String job : e.getValue()) {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "INSERT INTO infra_milestones (nation_id, type, key, claimed) VALUES (?,?,?,1) "
                                        + "ON CONFLICT(nation_id, type, key) DO UPDATE SET claimed=1")) {
                            ps.setString(1, e.getKey());
                            ps.setString(2, "JOB");
                            ps.setString(3, job);
                            ps.executeUpdate();
                        }
                    }
                }
                for (var e : cityDone.entrySet()) {
                    if (!Boolean.TRUE.equals(e.getValue())) continue;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO city_mission (nation_id, completed) VALUES (?,1) "
                                    + "ON CONFLICT(nation_id) DO UPDATE SET completed=1")) {
                        ps.setString(1, e.getKey());
                        ps.executeUpdate();
                    }
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Save infra: " + ex.getMessage());
            }
        });
    }

    public void saveSync() {
        plugin.getDatabaseManager().executeSync(conn -> {
            try {
                for (var e : claimMilestones.entrySet()) {
                    for (Integer m : e.getValue()) {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "INSERT INTO infra_milestones (nation_id, type, key, claimed) VALUES (?,?,?,1) "
                                        + "ON CONFLICT(nation_id, type, key) DO UPDATE SET claimed=1")) {
                            ps.setString(1, e.getKey());
                            ps.setString(2, "CLAIM");
                            ps.setString(3, String.valueOf(m));
                            ps.executeUpdate();
                        }
                    }
                }
                for (var e : jobMaxed.entrySet()) {
                    for (String job : e.getValue()) {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "INSERT INTO infra_milestones (nation_id, type, key, claimed) VALUES (?,?,?,1) "
                                        + "ON CONFLICT(nation_id, type, key) DO UPDATE SET claimed=1")) {
                            ps.setString(1, e.getKey());
                            ps.setString(2, "JOB");
                            ps.setString(3, job);
                            ps.executeUpdate();
                        }
                    }
                }
            } catch (Exception ex) {
                plugin.getLogger().severe("SaveSync infra: " + ex.getMessage());
            }
        });
    }

    public boolean isClaimMilestoneDone(String nationId, int milestone) {
        return claimMilestones.getOrDefault(nationId, Set.of()).contains(milestone);
    }

    public boolean isJobMaxedRewarded(String nationId, JobType type) {
        return jobMaxed.getOrDefault(nationId, Set.of()).contains(type.name());
    }

    public boolean isCityDone(String nationId) {
        return Boolean.TRUE.equals(cityDone.get(nationId));
    }

    /** Chamado apos claim bem-sucedido */
    public void onClaimCountChanged(String nationId) {
        int count = plugin.getClaimManager().countClaims(nationId);
        Nation n = plugin.getNationManager().getNation(nationId);
        if (n == null) return;
        Set<Integer> done = claimMilestones.computeIfAbsent(nationId, k -> ConcurrentHashMap.newKeySet());
        for (int m = CLAIM_STEP; m <= CLAIM_MAX; m += CLAIM_STEP) {
            if (count >= m && !done.contains(m)) {
                done.add(m);
                n.addTreasury(CLAIM_REWARD);
                ledger.record(nationId, "INFRA_CLAIM_" + m, CLAIM_REWARD, null, "Marco " + m + " claims");
                String msg = plugin.getConfigManager().getPrefix()
                        + "<gold>[◆] Missao de Territorio:</gold> <green>" + m
                        + " claims!</green> <yellow>+" + n.getCurrencySymbol() + " "
                        + Theme.fmt(CLAIM_REWARD) + "</yellow> <gray>no Tesouro.</gray>";
                for (Player p : Bukkit.getOnlinePlayers()) {
                    Nation pn = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
                    if (pn != null && pn.getId().equalsIgnoreCase(nationId)) {
                        p.sendMessage(Theme.MM.deserialize(msg));
                        try { p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f); } catch (Exception ignored) {}
                    }
                }
            }
        }
        save();
        plugin.getNationManager().saveAll();
    }

    /** Chamado apos upgrade de vaga/salario */
    public void checkJobMax(String nationId, JobType type) {
        if (isJobMaxedRewarded(nationId, type)) return;
        NationJob job = plugin.getJobManager().getJob(nationId, type);
        if (job == null || !job.active) return;
        if (job.slotLevel < NationJob.MAX_SLOT_UPGRADES) return;
        if (job.salaryLevel < NationJob.MAX_SALARY_UPGRADES) return;
        Nation n = plugin.getNationManager().getNation(nationId);
        if (n == null) return;
        jobMaxed.computeIfAbsent(nationId, k -> ConcurrentHashMap.newKeySet()).add(type.name());
        n.addTreasury(JOB_MAX_REWARD);
        ledger.record(nationId, "INFRA_JOB_" + type.name(), JOB_MAX_REWARD, null, "Emprego maxado");
        String msg = plugin.getConfigManager().getPrefix()
                + "<gold>[◆] Missao Economica:</gold> <aqua>" + type.getDisplayName()
                + "</aqua> <green>totalmente desenvolvido!</green> <yellow>+"
                + n.getCurrencySymbol() + " " + Theme.fmt(JOB_MAX_REWARD) + "</yellow> <gray>Tesouro.</gray>";
        for (Player p : Bukkit.getOnlinePlayers()) {
            Nation pn = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
            if (pn != null && pn.getId().equalsIgnoreCase(nationId)) {
                p.sendMessage(Theme.MM.deserialize(msg));
                try { p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f); } catch (Exception ignored) {}
            }
        }
        save();
        plugin.getNationManager().saveAll();
    }

    public boolean completeCity(String nationId, double bonus, Player admin) {
        Nation n = plugin.getNationManager().getNation(nationId);
        if (n == null) {
            if (admin != null) admin.sendMessage(Theme.MM.deserialize("<red>Nacao invalida.</red>"));
            return false;
        }
        if (isCityDone(nationId)) {
            if (admin != null) admin.sendMessage(Theme.MM.deserialize("<yellow>Missao de cidade ja concluida para " + n.getDisplayName() + ".</yellow>"));
            return false;
        }
        cityDone.put(nationId, true);
        n.addTreasury(CITY_TREASURY + Math.max(0, bonus));
        n.addToCentralBank(CITY_BC);
        ledger.record(nationId, "INFRA_CITY", CITY_TREASURY + CITY_BC + bonus, admin != null ? admin.getUniqueId() : null, "Cidade aprovada");
        save();
        plugin.getNationManager().saveAll();
        Bukkit.broadcast(Theme.MM.deserialize(
                plugin.getConfigManager().getPrefix() + "<gold>[◆] " + n.getDisplayName()
                        + " concluiu a Missao de Construcao da Cidade!</gold> "
                        + "<green>+" + Theme.fmt(CITY_TREASURY + bonus) + " Tesouro</green> "
                        + "<aqua>+" + Theme.fmt(CITY_BC) + " BC</aqua>"));
        return true;
    }
}
