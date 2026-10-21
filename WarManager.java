package com.nationswaw.systems;

import com.nationswaw.NationsWaW;
import com.nationswaw.gui.Theme;
import com.nationswaw.model.DiplomaticRelation;
import com.nationswaw.model.Nation;
import com.nationswaw.model.RoleType;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guerra Mundial, timer de 7 dias e sistema de tregua (70% presidentes).
 */
public class WarManager {
    private final NationsWaW plugin;

    public static final long WAR_DURATION_MS = 7L * 24 * 60 * 60 * 1000; // 7 dias
    public static final long TRUCE_COOLDOWN_MS = 24L * 60 * 60 * 1000;   // 24h

    private volatile boolean worldWarActive = false;

    /** key nationA:nationB (ordenado) -> endTimestamp */
    private final Map<String, Long> warEnds = new ConcurrentHashMap<>();

    /** key = proposerNationId -> TruceProposal */
    private final Map<String, TruceProposal> truceProposals = new ConcurrentHashMap<>();

    /** key warKey -> lastTruceProposeMs */
    private final Map<String, Long> truceCooldown = new ConcurrentHashMap<>();

    public static class TruceProposal {
        public final String proposerNation;
        public final Set<String> involvedNations = ConcurrentHashMap.newKeySet();
        public final Map<String, Boolean> presidentVotes = new ConcurrentHashMap<>(); // nationId -> yes
        public final long createdAt = System.currentTimeMillis();

        public TruceProposal(String proposer) {
            this.proposerNation = proposer;
        }
    }

    public WarManager(NationsWaW plugin) {
        this.plugin = plugin;
        load();
        // tick a cada 1 min
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L * 60, 20L * 60);
        plugin.getLogger().info("WarManager: guerras 7d, tregua 70%, guerra mundial.");
    }

    private String warKey(String a, String b) {
        return a.compareToIgnoreCase(b) < 0
                ? a.toLowerCase() + ":" + b.toLowerCase()
                : b.toLowerCase() + ":" + a.toLowerCase();
    }

    public boolean isWorldWarActive() {
        return worldWarActive;
    }

    public void startWorldWar() {
        worldWarActive = true;
        List<Nation> nations = new ArrayList<>(plugin.getNationManager().getAllNations());
        long end = System.currentTimeMillis() + WAR_DURATION_MS;
        for (int i = 0; i < nations.size(); i++) {
            for (int j = i + 1; j < nations.size(); j++) {
                String a = nations.get(i).getId();
                String b = nations.get(j).getId();
                plugin.getDiplomacyManager().setRelation(a, b, DiplomaticRelation.WAR);
                warEnds.put(warKey(a, b), end);
            }
        }
        save();
        broadcastWorldWarOn();
    }

    public void endWorldWar() {
        worldWarActive = false;
        List<Nation> nations = new ArrayList<>(plugin.getNationManager().getAllNations());
        for (int i = 0; i < nations.size(); i++) {
            for (int j = i + 1; j < nations.size(); j++) {
                String a = nations.get(i).getId();
                String b = nations.get(j).getId();
                if (plugin.getDiplomacyManager().isAtWar(a, b)) {
                    plugin.getDiplomacyManager().setRelation(a, b, DiplomaticRelation.NEUTRAL);
                }
                warEnds.remove(warKey(a, b));
            }
        }
        truceProposals.clear();
        save();
        broadcastWorldWarOff();
    }

    /** Chamado quando uma guerra bilateral e declarada por votacao. */
    public void startWar(String a, String b) {
        long end = System.currentTimeMillis() + WAR_DURATION_MS;
        warEnds.put(warKey(a, b), end);
        save();
    }

    public void endWar(String a, String b) {
        plugin.getDiplomacyManager().setRelation(a, b, DiplomaticRelation.NEUTRAL);
        warEnds.remove(warKey(a, b));
        // limpa propostas de tregua ligadas
        truceProposals.entrySet().removeIf(e -> {
            TruceProposal tp = e.getValue();
            return tp.involvedNations.contains(a.toLowerCase()) && tp.involvedNations.contains(b.toLowerCase())
                    || tp.proposerNation.equalsIgnoreCase(a) || tp.proposerNation.equalsIgnoreCase(b);
        });
        save();
    }

    public Long getWarEndMs(String a, String b) {
        return warEnds.get(warKey(a, b));
    }

    public String formatRemaining(String a, String b) {
        Long end = getWarEndMs(a, b);
        if (end == null) {
            if (worldWarActive) return "Guerra Mundial";
            return "-";
        }
        long left = end - System.currentTimeMillis();
        if (left <= 0) return "0d 00:00";
        long days = left / (24 * 3600_000L);
        long hours = (left % (24 * 3600_000L)) / 3600_000L;
        long mins = (left % 3600_000L) / 60_000L;
        return String.format("%dd %02d:%02d", days, hours, mins);
    }

    public List<String> getWarsFor(String nationId) {
        List<String> out = new ArrayList<>();
        for (Nation n : plugin.getNationManager().getAllNations()) {
            if (n.getId().equalsIgnoreCase(nationId)) continue;
            if (plugin.getDiplomacyManager().isAtWar(nationId, n.getId())) {
                out.add(n.getId());
            }
        }
        return out;
    }

    /** Durante guerra mundial OU guerra bilateral, permite interacao em claim inimigo. */
    public boolean canModifyClaim(Player player, String claimOwnerId) {
        if (player.hasPermission("nationswaw.admin")) return true;
        if (worldWarActive) return true;
        if (claimOwnerId == null) return true;
        Nation my = plugin.getNationManager().getNationByPlayer(player.getUniqueId());
        if (my == null) return false;
        if (my.getId().equalsIgnoreCase(claimOwnerId)) return true;
        return plugin.getDiplomacyManager().isAtWar(my.getId(), claimOwnerId);
    }

    public boolean proposeTruce(Player president) {
        if (!plugin.getRoleManager().hasRoleAtLeast(president.getUniqueId(), RoleType.PRESIDENT)) {
            president.sendMessage(Theme.MM.deserialize("<red>Apenas o Presidente pode propor tregua.</red>"));
            return false;
        }
        Nation my = plugin.getNationManager().getNationByPlayer(president.getUniqueId());
        if (my == null) return false;

        List<String> wars = getWarsFor(my.getId());
        if (wars.isEmpty() && !worldWarActive) {
            president.sendMessage(Theme.MM.deserialize("<red>Sua nacao nao esta em guerra.</red>"));
            return false;
        }

        // cooldown por cada guerra envolvida
        long now = System.currentTimeMillis();
        if (!worldWarActive) {
            for (String enemy : wars) {
                String wk = warKey(my.getId(), enemy);
                Long last = truceCooldown.get(wk);
                if (last != null && now - last < TRUCE_COOLDOWN_MS) {
                    long left = (TRUCE_COOLDOWN_MS - (now - last)) / 3600_000L;
                    president.sendMessage(Theme.MM.deserialize(
                            "<red>Aguarde " + (left + 1) + "h para propor nova tregua nesta guerra.</red>"));
                    return false;
                }
            }
        }

        TruceProposal prop = new TruceProposal(my.getId());
        if (worldWarActive) {
            for (Nation n : plugin.getNationManager().getAllNations()) {
                prop.involvedNations.add(n.getId().toLowerCase());
            }
        } else {
            prop.involvedNations.add(my.getId().toLowerCase());
            for (String e : wars) prop.involvedNations.add(e.toLowerCase());
        }
        // auto-voto do proponente
        prop.presidentVotes.put(my.getId().toLowerCase(), true);
        truceProposals.put(my.getId().toLowerCase(), prop);

        for (String enemy : wars) {
            truceCooldown.put(warKey(my.getId(), enemy), now);
        }

        String msg = "<gold>==========================================</gold>\n"
                + "<gradient:#FFD700:#FFA000><bold>[◆] PROPOSTA DE TREGUA</bold></gradient>\n"
                + "<gray>[■] A nacao <white>" + my.getDisplayName()
                + "</white> propos o fim das hostilidades.</gray>\n"
                + "<gray>[■] Para votar, use os comandos abaixo:</gray>\n"
                + "<yellow>[»] /nation tregua votar sim " + my.getId() + "</yellow>\n"
                + "<yellow>[»] /nation tregua votar nao " + my.getId() + "</yellow>\n"
                + "<gold>==========================================</gold>";

        notifyInvolvedPresidents(prop, msg);
        president.sendMessage(Theme.MM.deserialize(
                "<green>[◆] Proposta de tregua enviada aos presidentes envolvidos.</green>"));
        return true;
    }

    public boolean voteTruce(Player president, String proposerNationId, boolean yes) {
        if (!plugin.getRoleManager().hasRoleAtLeast(president.getUniqueId(), RoleType.PRESIDENT)) {
            president.sendMessage(Theme.MM.deserialize("<red>Apenas o Presidente pode votar tregua.</red>"));
            return false;
        }
        Nation my = plugin.getNationManager().getNationByPlayer(president.getUniqueId());
        if (my == null) return false;

        TruceProposal prop = truceProposals.get(proposerNationId.toLowerCase());
        if (prop == null) {
            president.sendMessage(Theme.MM.deserialize("<red>Nenhuma proposta de tregua desta nacao.</red>"));
            return false;
        }
        if (!prop.involvedNations.contains(my.getId().toLowerCase())) {
            president.sendMessage(Theme.MM.deserialize("<red>Sua nacao nao esta envolvida nesta tregua.</red>"));
            return false;
        }

        prop.presidentVotes.put(my.getId().toLowerCase(), yes);
        president.sendMessage(Theme.MM.deserialize(yes
                ? "<green>[◆] Voto A FAVOR da tregua registrado.</green>"
                : "<yellow>[◆] Voto CONTRA a tregua registrado.</yellow>"));

        checkTruce(prop);
        return true;
    }

    private void checkTruce(TruceProposal prop) {
        int totalPresidents = 0;
        int yesVotes = 0;
        for (String nid : prop.involvedNations) {
            Nation n = plugin.getNationManager().getNation(nid);
            if (n == null || n.getPresident() == null) continue;
            totalPresidents++;
            Boolean v = prop.presidentVotes.get(nid.toLowerCase());
            if (Boolean.TRUE.equals(v)) yesVotes++;
        }
        if (totalPresidents == 0) return;
        double ratio = (double) yesVotes / totalPresidents;

        // precisa de todos votarem OU 70% ja atingido com maioria dos votos
        int voted = prop.presidentVotes.size();
        if (ratio >= 0.70 && (voted >= totalPresidents || yesVotes >= Math.ceil(totalPresidents * 0.70))) {
            // Aprovar
            Nation proposer = plugin.getNationManager().getNation(prop.proposerNation);
            String pname = proposer != null ? proposer.getDisplayName() : prop.proposerNation;

            if (worldWarActive && prop.involvedNations.size() >= plugin.getNationManager().getAllNations().size()) {
                endWorldWar();
                Bukkit.broadcast(Theme.MM.deserialize(
                        "<gold>==========================================</gold>\n"
                                + "<green><bold>[◆] TREGUA APROVADA!</bold></green>\n"
                                + "<gray>A Guerra Mundial foi encerrada.</gray>\n"
                                + "<gold>==========================================</gold>"));
            } else {
                // encerra guerras do proponente com cada inimigo envolvido
                for (String other : prop.involvedNations) {
                    if (other.equalsIgnoreCase(prop.proposerNation)) continue;
                    if (plugin.getDiplomacyManager().isAtWar(prop.proposerNation, other)) {
                        endWar(prop.proposerNation, other);
                    }
                }
                Bukkit.broadcast(Theme.MM.deserialize(
                        "<gold>==========================================</gold>\n"
                                + "<green><bold>[◆] TREGUA APROVADA!</bold></green>\n"
                                + "<gray>A guerra envolvendo <white>" + pname + "</white> terminou.</gray>\n"
                                + "<gold>==========================================</gold>"));
            }
            truceProposals.remove(prop.proposerNation.toLowerCase());
        } else if (voted >= totalPresidents && ratio < 0.70) {
            // rejeitada
            truceProposals.remove(prop.proposerNation.toLowerCase());
            notifyInvolvedPresidents(prop,
                    "<red>[◆] TREGUA REJEITADA</red> <gray>(<" + String.format("%.0f", ratio * 100)
                            + "%). A guerra continua. Timer de 7 dias segue.</gray>");
        }
    }

    private void notifyInvolvedPresidents(TruceProposal prop, String msg) {
        var component = Theme.MM.deserialize(msg);
        for (String nid : prop.involvedNations) {
            Nation n = plugin.getNationManager().getNation(nid);
            if (n == null || n.getPresident() == null) continue;
            Player p = Bukkit.getPlayer(n.getPresident());
            if (p != null) p.sendMessage(component);
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        List<String> expired = new ArrayList<>();
        for (var e : warEnds.entrySet()) {
            if (e.getValue() <= now) expired.add(e.getKey());
        }
        for (String key : expired) {
            String[] p = key.split(":");
            if (p.length == 2) {
                endWar(p[0], p[1]);
                Nation a = plugin.getNationManager().getNation(p[0]);
                Nation b = plugin.getNationManager().getNation(p[1]);
                String an = a != null ? a.getDisplayName() : p[0];
                String bn = b != null ? b.getDisplayName() : p[1];
                Bukkit.broadcast(Theme.MM.deserialize(
                        "<yellow>[◆] Guerra entre <white>" + an + "</white> e <white>" + bn
                                + "</white> terminou automaticamente (7 dias).</yellow>"));
            }
        }
        // se guerra mundial e todas bilaterais expiraram
        if (worldWarActive) {
            boolean anyWar = false;
            for (Nation n1 : plugin.getNationManager().getAllNations()) {
                for (Nation n2 : plugin.getNationManager().getAllNations()) {
                    if (n1.getId().equals(n2.getId())) continue;
                    if (plugin.getDiplomacyManager().isAtWar(n1.getId(), n2.getId())) {
                        anyWar = true;
                        break;
                    }
                }
                if (anyWar) break;
            }
            if (!anyWar) {
                worldWarActive = false;
                save();
            }
        }
    }

    private void broadcastWorldWarOn() {
        var title = Title.title(
                Theme.MM.deserialize("<dark_red><bold>GUERRA MUNDIAL</bold></dark_red>"),
                Theme.MM.deserialize("<yellow>Protecao de terrenos DESATIVADA</yellow>"),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(5), Duration.ofSeconds(1)));
        String chat = "<gold>==========================================</gold>\n"
                + "<dark_red><bold>[◆] ATENCAO: GUERRA MUNDIAL DECLARADA!</bold></dark_red>\n"
                + "<gray>[■] Todas as nacoes estao em guerra.</gray>\n"
                + "<gray>[■] A protecao de terrenos foi desativada.</gray>\n"
                + "<gray>[■] Preparem suas defesas!</gray>\n"
                + "<gold>==========================================</gold>";
        var msg = Theme.MM.deserialize(chat);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(title);
            p.sendMessage(msg);
        }
    }

    private void broadcastWorldWarOff() {
        Bukkit.broadcast(Theme.MM.deserialize(
                "<green>==========================================</green>\n"
                        + "<green><bold>[◆] GUERRA MUNDIAL ENCERRADA</bold></green>\n"
                        + "<gray>[■] Nacoes voltaram a Neutralidade.</gray>\n"
                        + "<gray>[■] Protecao de terrenos REATIVADA.</gray>\n"
                        + "<green>==========================================</green>"));
    }

    private void load() {
        plugin.getDatabaseManager().queryAsync(conn -> {
            try {
                try (var st = conn.createStatement()) {
                    st.executeUpdate("CREATE TABLE IF NOT EXISTS war_state (key TEXT PRIMARY KEY, value TEXT)");
                    st.executeUpdate("CREATE TABLE IF NOT EXISTS war_timers (war_key TEXT PRIMARY KEY, end_ms INTEGER)");
                }
                try (PreparedStatement ps = conn.prepareStatement("SELECT value FROM war_state WHERE key='world_war'");
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) worldWarActive = "1".equals(rs.getString(1));
                }
                try (PreparedStatement ps = conn.prepareStatement("SELECT war_key, end_ms FROM war_timers");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        warEnds.put(rs.getString(1), rs.getLong(2));
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("WarManager load: " + e.getMessage());
            }
            return null;
        }).join();
    }

    public void save() {
        plugin.getDatabaseManager().executeAsync(conn -> {
            try {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT OR REPLACE INTO war_state (key, value) VALUES ('world_war', ?)")) {
                    ps.setString(1, worldWarActive ? "1" : "0");
                    ps.executeUpdate();
                }
                try (PreparedStatement del = conn.prepareStatement("DELETE FROM war_timers")) {
                    del.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO war_timers (war_key, end_ms) VALUES (?,?)")) {
                    for (var e : warEnds.entrySet()) {
                        ps.setString(1, e.getKey());
                        ps.setLong(2, e.getValue());
                        ps.executeUpdate();
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("WarManager save: " + e.getMessage());
            }
        });
    }
}
