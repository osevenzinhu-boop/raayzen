package com.nationswaw.systems;

import com.nationswaw.NationsWaW;
import com.nationswaw.model.DiplomaticRelation;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DiplomacyManager {
    private final NationsWaW plugin;
    private final Map<String, DiplomaticRelation> relations = new ConcurrentHashMap<>();

    public DiplomacyManager(NationsWaW plugin) { this.plugin = plugin; }

    private String key(String a, String b) {
        return a.compareToIgnoreCase(b) < 0 ? a.toLowerCase()+":"+b.toLowerCase() : b.toLowerCase()+":"+a.toLowerCase();
    }

    public void loadRelations() {
        plugin.getDatabaseManager().queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM diplomacy")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    String relStr = rs.getString("relation");
                    DiplomaticRelation rel;
                    try {
                        if ("ENEMY".equalsIgnoreCase(relStr)) rel = DiplomaticRelation.NEUTRAL;
                        else rel = DiplomaticRelation.valueOf(relStr);
                    } catch (Exception e) { rel = DiplomaticRelation.NEUTRAL; }
                    relations.put(key(rs.getString("nation_a"), rs.getString("nation_b")), rel);
                }
            } catch (Exception e) { plugin.getLogger().warning("Load diplo: " + e.getMessage()); }
            return null;
        }).join();
    }

    public void saveRelations() {
        plugin.getDatabaseManager().executeAsync(conn -> {
            try {
                try (PreparedStatement del = conn.prepareStatement("DELETE FROM diplomacy")) { del.executeUpdate(); }
                for (var e : relations.entrySet()) {
                    String[] p = e.getKey().split(":");
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO diplomacy (nation_a, nation_b, relation) VALUES (?,?,?)")) {
                        ps.setString(1, p[0]); ps.setString(2, p[1]); ps.setString(3, e.getValue().name());
                        ps.executeUpdate();
                    }
                }
            } catch (Exception ex) { plugin.getLogger().warning("Save diplo: " + ex.getMessage()); }
        });
    }

    public DiplomaticRelation getRelation(String a, String b) {
        if (a.equalsIgnoreCase(b)) return DiplomaticRelation.ALLY;
        return relations.getOrDefault(key(a, b), DiplomaticRelation.NEUTRAL);
    }
    public void setRelation(String a, String b, DiplomaticRelation r) {
        relations.put(key(a, b), r); saveRelations();
    }
    public boolean isAtWar(String a, String b) { return getRelation(a, b) == DiplomaticRelation.WAR; }
}
