package com.nationswaw.utils;

import com.nationswaw.NationsWaW;

import java.sql.PreparedStatement;
import java.util.UUID;

/** Ledger imutavel para auditoria financeira. */
public class Ledger {
    private final NationsWaW plugin;

    public Ledger(NationsWaW plugin) { this.plugin = plugin; }

    public void record(String nationId, String type, double amount, UUID actor, String description) {
        plugin.getDatabaseManager().executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO ledger (nation_id, type, amount, actor, description, timestamp) VALUES (?,?,?,?,?,?)")) {
                ps.setString(1, nationId);
                ps.setString(2, type);
                ps.setDouble(3, amount);
                ps.setString(4, actor != null ? actor.toString() : "SYSTEM");
                ps.setString(5, description);
                ps.setLong(6, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (Exception e) {
                plugin.getLogger().warning("Ledger error: " + e.getMessage());
            }
        });
    }
}
