package com.nationswaw.systems;

import com.nationswaw.NationsWaW;
import com.nationswaw.model.PlayerData;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;

public class EconomyManager {
    private final NationsWaW plugin;
    public EconomyManager(NationsWaW plugin) { this.plugin = plugin; }

    public void loadBalances() {
        plugin.getDatabaseManager().queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM balances")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    plugin.getRoleManager().getPlayerData(uuid)
                            .setBalance(rs.getString("currency_nation_id"), rs.getDouble("balance"));
                }
            } catch (Exception e) { plugin.getLogger().warning("Load balances: " + e.getMessage()); }
            return null;
        }).join();
    }

    public void saveAll() {
        Map<UUID, PlayerData> all = plugin.getRoleManager().getAllPlayers();
        plugin.getDatabaseManager().executeAsync(conn -> {
            try {
                try (PreparedStatement del = conn.prepareStatement("DELETE FROM balances")) { del.executeUpdate(); }
                for (PlayerData d : all.values()) {
                    for (var e : d.getBalances().entrySet()) {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "INSERT INTO balances (uuid, currency_nation_id, balance) VALUES (?,?,?)")) {
                            ps.setString(1, d.getUuid().toString());
                            ps.setString(2, e.getKey());
                            ps.setDouble(3, e.getValue());
                            ps.executeUpdate();
                        }
                    }
                }
            } catch (Exception e) { plugin.getLogger().severe("Save balances: " + e.getMessage()); }
        });
    }

    public void savePlayerBalances(UUID uuid) {
        PlayerData data = plugin.getRoleManager().getPlayerData(uuid);
        plugin.getDatabaseManager().executeAsync(conn -> {
            try {
                try (PreparedStatement del = conn.prepareStatement("DELETE FROM balances WHERE uuid=?")) {
                    del.setString(1, uuid.toString()); del.executeUpdate();
                }
                for (var e : data.getBalances().entrySet()) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO balances (uuid, currency_nation_id, balance) VALUES (?,?,?)")) {
                        ps.setString(1, uuid.toString());
                        ps.setString(2, e.getKey());
                        ps.setDouble(3, e.getValue());
                        ps.executeUpdate();
                    }
                }
            } catch (Exception ex) { plugin.getLogger().warning("Save balance: " + ex.getMessage()); }
        });
    }

    public double getBalance(UUID uuid, String currency) {
        return plugin.getRoleManager().getPlayerData(uuid).getBalance(currency);
    }
    public void addBalance(UUID uuid, String currency, double amount) {
        plugin.getRoleManager().getPlayerData(uuid).addBalance(currency, amount);
        savePlayerBalances(uuid);
    }
    public boolean removeBalance(UUID uuid, String currency, double amount) {
        boolean ok = plugin.getRoleManager().getPlayerData(uuid).removeBalance(currency, amount);
        if (ok) savePlayerBalances(uuid);
        return ok;
    }
    public void giveStartingBalance(UUID uuid, String nationId) {
        if (getBalance(uuid, nationId) <= 0)
            addBalance(uuid, nationId, plugin.getConfigManager().getStartingBalance());
    }
}
