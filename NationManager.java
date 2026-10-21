package com.nationswaw.systems;

import com.nationswaw.NationsWaW;
import com.nationswaw.model.Nation;
import com.nationswaw.utils.ConfigManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NationManager {
    private final NationsWaW plugin;
    private final Map<String, Nation> nations = new ConcurrentHashMap<>();

    public NationManager(NationsWaW plugin) { this.plugin = plugin; }

    public void loadNations() {
        ConfigManager cfg = plugin.getConfigManager();
        for (String id : cfg.getNationIds()) {
            ConfigManager.NationConfig nc = cfg.getNationConfig(id);
            Nation n = new Nation(id);
            n.setDisplayName(nc.displayName);
            n.setCurrencyName(nc.currency);
            n.setCurrencySymbol(nc.symbol);
            n.setCentralBankName(nc.centralBank);
            n.setColor(nc.color);
            n.setFlagMaterial(nc.flagMaterial);
            n.setTreasury(nc.initialTreasury);
            n.setCentralBankBalance(nc.initialCentralBank);
            nations.put(id, n);
        }
        plugin.getDatabaseManager().queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM nations")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    Nation n = nations.get(rs.getString("id"));
                    if (n == null) continue;
                    String p = rs.getString("president");
                    if (p != null && !p.isEmpty()) n.setPresident(UUID.fromString(p));
                    n.setTreasury(rs.getDouble("treasury"));
                    n.setCentralBankBalance(rs.getDouble("central_bank_balance"));
                }
            } catch (Exception e) { plugin.getLogger().warning("Load nations: " + e.getMessage()); }
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM nation_roles")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    Nation n = nations.get(rs.getString("nation_id"));
                    if (n == null) continue;
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    switch (rs.getString("role")) {
                        case "PRESIDENT" -> n.setPresident(uuid);
                        case "GOVERNOR" -> n.getGovernors().add(uuid);
                        case "MAYOR" -> n.getMayors().add(uuid);
                        case "CITIZEN" -> n.getCitizens().add(uuid);
                    }
                }
            } catch (Exception e) { plugin.getLogger().warning("Load roles: " + e.getMessage()); }
            return null;
        }).join();

        // Correcao critica: se BC estiver zerado, aplicar 2.5M (migracao de versoes antigas)
        boolean needSave = false;
        for (Nation n : nations.values()) {
            var nc = cfg.getNationConfig(n.getId());
            if (nc == null) continue;
            if (n.getTreasury() <= 0) {
                n.setTreasury(nc.initialTreasury > 0 ? nc.initialTreasury : 2_500_000);
                needSave = true;
            }
            if (n.getCentralBankBalance() <= 0) {
                n.setCentralBankBalance(nc.initialCentralBank > 0 ? nc.initialCentralBank : 2_500_000);
                needSave = true;
                plugin.getLogger().info("Migracao: Banco Central de " + n.getId() + " definido para " + n.getCentralBankBalance());
            }
        }
        if (needSave) saveAll();
        for (Nation n : nations.values()) {
            plugin.getLogger().info("Nação " + n.getId() + " | Tesouro=" + String.format("%,.0f", n.getTreasury())
                    + " | BC=" + String.format("%,.0f", n.getCentralBankBalance()));
        }
    }

    public void saveAll() {
        plugin.getDatabaseManager().executeAsync(conn -> {
            try {
                for (Nation n : nations.values()) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR REPLACE INTO nations (id, president, treasury, central_bank_balance) VALUES (?,?,?,?)")) {
                        ps.setString(1, n.getId());
                        ps.setString(2, n.getPresident() != null ? n.getPresident().toString() : null);
                        ps.setDouble(3, n.getTreasury());
                        ps.setDouble(4, n.getCentralBankBalance());
                        ps.executeUpdate();
                    }
                    try (PreparedStatement del = conn.prepareStatement("DELETE FROM nation_roles WHERE nation_id=?")) {
                        del.setString(1, n.getId()); del.executeUpdate();
                    }
                    insertRole(conn, n.getId(), n.getPresident(), "PRESIDENT");
                    for (UUID u : n.getGovernors()) insertRole(conn, n.getId(), u, "GOVERNOR");
                    for (UUID u : n.getMayors()) insertRole(conn, n.getId(), u, "MAYOR");
                    for (UUID u : n.getCitizens()) insertRole(conn, n.getId(), u, "CITIZEN");
                }
            } catch (Exception e) { plugin.getLogger().severe("Save nations: " + e.getMessage()); }
        });
    }

    private void insertRole(java.sql.Connection conn, String nationId, UUID uuid, String role) throws Exception {
        if (uuid == null) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO nation_roles (nation_id, uuid, role) VALUES (?,?,?)")) {
            ps.setString(1, nationId); ps.setString(2, uuid.toString()); ps.setString(3, role);
            ps.executeUpdate();
        }
    }

    public Nation getNation(String id) { return nations.get(id == null ? null : id.toLowerCase()); }
    public Collection<Nation> getAllNations() { return nations.values(); }

    public Nation getNationByPlayer(UUID uuid) {
        for (Nation n : nations.values()) {
            if (uuid.equals(n.getPresident())) return n;
            if (n.getGovernors().contains(uuid)) return n;
            if (n.getMayors().contains(uuid)) return n;
            if (n.getCitizens().contains(uuid)) return n;
        }
        return null;
    }

    public void removeFromNation(UUID uuid) {
        for (Nation n : nations.values()) {
            if (uuid.equals(n.getPresident())) n.setPresident(null);
            n.getGovernors().remove(uuid);
            n.getMayors().remove(uuid);
            n.getCitizens().remove(uuid);
        }
    }

    public void addCitizen(String nationId, UUID uuid) {
        Nation n = getNation(nationId);
        if (n != null) n.getCitizens().add(uuid);
    }

}
