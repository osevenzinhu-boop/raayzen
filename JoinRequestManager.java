package com.nationswaw.systems;

import com.nationswaw.NationsWaW;
import com.nationswaw.gui.Theme;
import com.nationswaw.model.Nation;
import com.nationswaw.model.RoleType;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Solicitacoes de entrada persistentes (online + offline leaders).
 */
public class JoinRequestManager {
    private final NationsWaW plugin;
    /** nationId -> list of requester UUIDs */
    private final Map<String, Set<UUID>> pending = new ConcurrentHashMap<>();

    public JoinRequestManager(NationsWaW plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        plugin.getDatabaseManager().queryAsync(conn -> {
            try {
                try (var st = conn.createStatement()) {
                    st.executeUpdate("CREATE TABLE IF NOT EXISTS join_requests (uuid TEXT NOT NULL, nation_id TEXT NOT NULL, created_at INTEGER, PRIMARY KEY (uuid, nation_id))");
                }
                try (PreparedStatement ps = conn.prepareStatement("SELECT uuid, nation_id FROM join_requests");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        UUID u = UUID.fromString(rs.getString("uuid"));
                        String nid = rs.getString("nation_id");
                        pending.computeIfAbsent(nid, k -> ConcurrentHashMap.newKeySet()).add(u);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Load join_requests: " + e.getMessage());
            }
            return null;
        }).join();
    }

    public void saveRequest(UUID uuid, String nationId) {
        pending.computeIfAbsent(nationId, k -> ConcurrentHashMap.newKeySet()).add(uuid);
        plugin.getDatabaseManager().executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR REPLACE INTO join_requests (uuid, nation_id, created_at) VALUES (?,?,?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, nationId);
                ps.setLong(3, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (Exception e) {
                plugin.getLogger().warning("Save join request: " + e.getMessage());
            }
        });
    }

    public void removeRequest(UUID uuid, String nationId) {
        Set<UUID> set = pending.get(nationId);
        if (set != null) set.remove(uuid);
        plugin.getDatabaseManager().executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM join_requests WHERE uuid=? AND nation_id=?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, nationId);
                ps.executeUpdate();
            } catch (Exception e) {
                plugin.getLogger().warning("Del join request: " + e.getMessage());
            }
        });
    }

    public void removeAllForPlayer(UUID uuid) {
        for (String nid : new ArrayList<>(pending.keySet())) {
            removeRequest(uuid, nid);
        }
    }

    public Set<UUID> getPendingForNation(String nationId) {
        return pending.getOrDefault(nationId, Set.of());
    }

    public boolean hasRequest(UUID uuid, String nationId) {
        return pending.getOrDefault(nationId, Set.of()).contains(uuid);
    }

    public void submit(Player applicant, String nationId) {
        Nation n = plugin.getNationManager().getNation(nationId);
        if (n == null) return;
        if (plugin.getNationManager().getNationByPlayer(applicant.getUniqueId()) != null) {
            applicant.sendMessage(Theme.MM.deserialize("<red>Voce ja pertence a uma nacao.</red>"));
            return;
        }
        if (n.getPopulation() >= 25) {
            applicant.sendMessage(Theme.MM.deserialize("<red>Esta nacao esta cheia.</red>"));
            return;
        }
        saveRequest(applicant.getUniqueId(), nationId);
        applicant.sendMessage(Theme.MM.deserialize(
                "<green>[◆] Solicitacao enviada para " + n.getDisplayName() + "! Aguarde aprovacao dos lideres.</green>"));

        String msg = "<gold>[◆] O jogador <white>" + applicant.getName()
                + "</white> solicitou entrada na nacao!</gold>";
        boolean anyOnline = false;
        for (Player leader : onlineLeaders(n)) {
            anyOnline = true;
            leader.sendMessage(Theme.MM.deserialize(msg));
            sendAcceptButtons(leader, applicant.getName());
        }
        if (!anyOnline) {
            applicant.sendMessage(Theme.MM.deserialize(
                    "<gray>Nenhum lider online. Sua solicitacao foi salva e sera avisada quando entrarem.</gray>"));
        }
    }

    public void notifyLeaderOnJoin(Player leader) {
        Nation n = plugin.getNationManager().getNationByPlayer(leader.getUniqueId());
        if (n == null) return;
        RoleType role = plugin.getRoleManager().getRole(leader.getUniqueId());
        if (role != RoleType.PRESIDENT && role != RoleType.GOVERNOR && role != RoleType.MAYOR) return;
        Set<UUID> reqs = getPendingForNation(n.getId());
        if (reqs.isEmpty()) return;
        for (UUID u : reqs) {
            String name = Bukkit.getOfflinePlayer(u).getName();
            if (name == null) name = u.toString().substring(0, 8);
            leader.sendMessage(Theme.MM.deserialize(
                    "<gold>[◆] <white>" + name + "</white> quer entrar na sua nacao!</gold>"));
            sendAcceptButtons(leader, name);
        }
    }

    private void sendAcceptButtons(Player leader, String applicantName) {
        var accept = Theme.MM.deserialize("<green><bold>[ACEITAR]</bold></green>")
                .clickEvent(ClickEvent.runCommand("/nation aceitar " + applicantName))
                .hoverEvent(HoverEvent.showText(Theme.MM.deserialize("<green>Aceitar entrada</green>")));
        var refuse = Theme.MM.deserialize("<red><bold>[RECUSAR]</bold></red>")
                .clickEvent(ClickEvent.runCommand("/nation recusar " + applicantName))
                .hoverEvent(HoverEvent.showText(Theme.MM.deserialize("<red>Recusar entrada</red>")));
        leader.sendMessage(Theme.MM.deserialize("<gray>  </gray>").append(accept)
                .append(Theme.MM.deserialize("  ")).append(refuse));
    }

    private List<Player> onlineLeaders(Nation n) {
        List<Player> out = new ArrayList<>();
        if (n.getPresident() != null) {
            Player p = Bukkit.getPlayer(n.getPresident());
            if (p != null) out.add(p);
        }
        for (UUID u : n.getGovernors()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) out.add(p);
        }
        for (UUID u : n.getMayors()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) out.add(p);
        }
        return out;
    }
}
