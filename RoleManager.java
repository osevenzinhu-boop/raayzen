package com.nationswaw.systems;

import com.nationswaw.NationsWaW;
import com.nationswaw.model.Nation;
import com.nationswaw.model.PlayerData;
import com.nationswaw.model.RoleType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RoleManager {
    private final NationsWaW plugin;
    private final Map<UUID, PlayerData> players = new ConcurrentHashMap<>();
    private static final int MAX_GOVERNORS = 1;
    private static final int MAX_MAYORS = 3;
    private static final int MAX_CITIZENS = 25;

    public RoleManager(NationsWaW plugin) { this.plugin = plugin; }

    public void loadRoles() {
        plugin.getDatabaseManager().queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM players")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    PlayerData d = new PlayerData(uuid);
                    d.setNationId(rs.getString("nation_id"));
                    try { d.setRole(RoleType.valueOf(rs.getString("role"))); } catch (Exception e) { d.setRole(RoleType.NONE); }
                    d.setDailyExchangeVolume(rs.getDouble("daily_exchange_volume"));
                    d.setLastExchangeResetDay(rs.getLong("last_exchange_reset_day"));
                    players.put(uuid, d);
                }
            } catch (Exception e) { plugin.getLogger().warning("Load players: " + e.getMessage()); }
            return null;
        }).join();
    }

    public void saveAll() {
        plugin.getDatabaseManager().executeAsync(conn -> {
            try {
                for (PlayerData d : players.values()) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR REPLACE INTO players (uuid, nation_id, role, daily_exchange_volume, last_exchange_reset_day) VALUES (?,?,?,?,?)")) {
                        ps.setString(1, d.getUuid().toString());
                        ps.setString(2, d.getNationId());
                        ps.setString(3, d.getRole().name());
                        ps.setDouble(4, d.getDailyExchangeVolume());
                        ps.setLong(5, d.getLastExchangeResetDay());
                        ps.executeUpdate();
                    }
                }
            } catch (Exception e) { plugin.getLogger().severe("Save players: " + e.getMessage()); }
        });
    }

    public PlayerData getPlayerData(UUID uuid) { return players.computeIfAbsent(uuid, PlayerData::new); }
    public Map<UUID, PlayerData> getAllPlayers() { return players; }

    /** overload usado por NationCommand */
    public void setRole(UUID uuid, RoleType role, String nationId) {
        setRole(uuid, nationId, role);
    }

    /**
     * @return mensagem de erro ou null se OK
     */
    public String setRole(UUID uuid, String nationId, RoleType role) {
        Nation nation = nationId != null ? plugin.getNationManager().getNation(nationId) : null;
        if (role != RoleType.NONE && nation != null) {
            if (role == RoleType.GOVERNOR && nation.getGovernors().size() >= MAX_GOVERNORS
                    && !nation.getGovernors().contains(uuid)) {
                return "Limite de Governadores atingido (max " + MAX_GOVERNORS + ").";
            }
            if (role == RoleType.MAYOR && nation.getMayors().size() >= MAX_MAYORS
                    && !nation.getMayors().contains(uuid)) {
                return "Limite de Prefeitos atingido (max " + MAX_MAYORS + ").";
            }
            if (role == RoleType.PRESIDENT && nation.getPresident() != null
                    && !nation.getPresident().equals(uuid)) {
                return "Ja existe um Presidente nesta nação.";
            }
            int pop = nation.getPopulation();
            if (!nation.getCitizens().contains(uuid) && !uuid.equals(nation.getPresident())
                    && !nation.getGovernors().contains(uuid) && !nation.getMayors().contains(uuid)
                    && pop >= MAX_CITIZENS) {
                return "Nação cheia (max " + MAX_CITIZENS + " civis).";
            }
        }
        PlayerData data = getPlayerData(uuid);
        data.setNationId(nationId);
        data.setRole(role == null ? RoleType.NONE : role);
        plugin.getNationManager().removeFromNation(uuid);
        if (nation != null && role != null && role != RoleType.NONE) {
            switch (role) {
                case PRESIDENT -> nation.setPresident(uuid);
                case GOVERNOR -> { nation.getGovernors().add(uuid); nation.getCitizens().add(uuid); }
                case MAYOR -> { nation.getMayors().add(uuid); nation.getCitizens().add(uuid); }
                case CITIZEN -> nation.getCitizens().add(uuid);
                default -> {}
            }
            // Politicos nao podem ter emprego: demissão automatica
            if (role == RoleType.MAYOR || role == RoleType.GOVERNOR || role == RoleType.PRESIDENT) {
                try {
                    plugin.getJobManager().forceFire(uuid, "Promocao a " + role.getDisplayName());
                } catch (Exception ignored) {}
            }
        }
        try {
            org.bukkit.entity.Player pl = org.bukkit.Bukkit.getPlayer(uuid);
            if (pl != null) plugin.getDisplayManager().updatePlayerDisplay(pl);
        } catch (Exception ignored) {}
        return null;
    }

    public RoleType getRole(UUID uuid) { return getPlayerData(uuid).getRole(); }
    public boolean hasRoleAtLeast(UUID uuid, RoleType min) { return getRole(uuid).hasPowerOf(min); }
}
