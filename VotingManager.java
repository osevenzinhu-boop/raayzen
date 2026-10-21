package com.nationswaw.systems;

import com.nationswaw.NationsWaW;
import com.nationswaw.model.DiplomaticRelation;
import com.nationswaw.model.Nation;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guerra: votacao indefinida, 70% a favor, declaracao MUTUA.
 * Minimo 5 membros para iniciar.
 */
public class VotingManager {
    private final NationsWaW plugin;
    private final Map<Integer, WarProposal> proposals = new ConcurrentHashMap<>();
    private int nextId = 1;
    public static final int MIN_MEMBERS_FOR_WAR = 5;

    public VotingManager(NationsWaW plugin) { this.plugin = plugin; }

    public static class WarProposal {
        public final int id;
        public final String proposerNation;
        public final String targetNation;
        public final Map<UUID, Boolean> proposerVotes = new ConcurrentHashMap<>();
        public final Map<UUID, Boolean> targetVotes = new ConcurrentHashMap<>();
        public boolean proposerApproved;
        public boolean targetApproved;
        public boolean finished;
        public boolean waitingTarget;

        public WarProposal(int id, String proposer, String target) {
            this.id = id;
            this.proposerNation = proposer;
            this.targetNation = target;
        }
    }

    public WarProposal getProposal(int id) { return proposals.get(id); }

    public Collection<WarProposal> getActiveProposals() {
        List<WarProposal> list = new ArrayList<>();
        for (WarProposal p : proposals.values()) if (!p.finished) list.add(p);
        return list;
    }

    public Collection<WarProposal> getActiveProposalsForNation(String nationId) {
        List<WarProposal> out = new ArrayList<>();
        for (WarProposal p : proposals.values()) {
            if (p.finished) continue;
            if (p.proposerNation.equalsIgnoreCase(nationId)
                    || (p.waitingTarget && p.targetNation.equalsIgnoreCase(nationId))
                    || (!p.waitingTarget && p.proposerNation.equalsIgnoreCase(nationId))) {
                if (!out.contains(p)) out.add(p);
            }
            // also show to target when waiting
            if (!p.finished && p.waitingTarget && p.targetNation.equalsIgnoreCase(nationId)) {
                if (!out.contains(p)) out.add(p);
            }
            if (!p.finished && !p.waitingTarget && p.proposerNation.equalsIgnoreCase(nationId)) {
                if (!out.contains(p)) out.add(p);
            }
        }
        // simplify
        out.clear();
        for (WarProposal p : proposals.values()) {
            if (p.finished) continue;
            boolean relevant = p.proposerNation.equalsIgnoreCase(nationId)
                    || (p.waitingTarget && p.targetNation.equalsIgnoreCase(nationId));
            if (relevant) out.add(p);
        }
        return out;
    }

    public WarProposal getActiveProposalFor(String nationId) {
        for (WarProposal p : proposals.values()) {
            if (p.finished) continue;
            if (!p.waitingTarget && p.proposerNation.equalsIgnoreCase(nationId)) return p;
            if (p.waitingTarget && p.targetNation.equalsIgnoreCase(nationId)) return p;
        }
        return null;
    }

    public Map<UUID, Boolean> getVotesForViewer(WarProposal p, String viewerNationId) {
        if (p == null) return Collections.emptyMap();
        if (!p.waitingTarget && p.proposerNation.equalsIgnoreCase(viewerNationId)) return p.proposerVotes;
        if (p.waitingTarget && p.targetNation.equalsIgnoreCase(viewerNationId)) return p.targetVotes;
        return Collections.emptyMap();
    }

    public int startWarProposal(String proposerId, String targetId) {
        Nation atkCheck = plugin.getNationManager().getNation(proposerId);
        if (atkCheck != null && atkCheck.getPopulation() < MIN_MEMBERS_FOR_WAR) return -4;
        if (plugin.getDiplomacyManager().isAtWar(proposerId, targetId)) return -2;
        for (WarProposal p : proposals.values()) {
            if (!p.finished && p.proposerNation.equalsIgnoreCase(proposerId)
                    && p.targetNation.equalsIgnoreCase(targetId)) {
                return -3;
            }
        }
        int id = nextId++;
        WarProposal prop = new WarProposal(id, proposerId, targetId);
        proposals.put(id, prop);
        Nation atk = plugin.getNationManager().getNation(proposerId);
        Nation def = plugin.getNationManager().getNation(targetId);
        String defName = def != null ? def.getDisplayName() : targetId;
        broadcast(atk, "<gold>Votacao de GUERRA vs <red>" + defName
                + "</red> aberta! Use <yellow>/votar</yellow> (70% a favor, sem prazo)</gold>");
        return id;
    }

    public boolean castVote(UUID uuid, int voteId, boolean yes) {
        WarProposal p = proposals.get(voteId);
        if (p == null || p.finished) return false;
        Nation pn = plugin.getNationManager().getNationByPlayer(uuid);
        if (pn == null) return false;

        if (!p.waitingTarget && pn.getId().equalsIgnoreCase(p.proposerNation)) {
            p.proposerVotes.put(uuid, yes);
            checkProposerComplete(p);
            return true;
        }
        if (p.waitingTarget && pn.getId().equalsIgnoreCase(p.targetNation)) {
            p.targetVotes.put(uuid, yes);
            checkTargetComplete(p);
            return true;
        }
        return false;
    }

    private void checkProposerComplete(WarProposal p) {
        Nation n = plugin.getNationManager().getNation(p.proposerNation);
        if (n == null || n.getPopulation() == 0) return;
        double favor = favorRatio(p.proposerVotes, n.getPopulation());
        int yes = (int) p.proposerVotes.values().stream().filter(b -> b).count();
        boolean allVoted = p.proposerVotes.size() >= n.getPopulation();

        if (favor >= 0.70 && (allVoted || yes >= Math.ceil(n.getPopulation() * 0.70))) {
            p.proposerApproved = true;
            p.waitingTarget = true;
            Nation def = plugin.getNationManager().getNation(p.targetNation);
            try {
                plugin.getEmbargoManager().applyEmbargo(p.proposerNation, p.targetNation);
            } catch (Exception ignored) {}
            broadcast(n, "<green>Proposta de guerra APROVADA (>=70%)! Aguardando votacao de "
                    + (def != null ? def.getDisplayName() : p.targetNation) + ".</green>");
            broadcast(def, "<red>" + n.getDisplayName()
                    + " aprovou guerra contra voces! Votem: /votar</red>");
            try {
                plugin.getEmbargoManager().applyEmbargo(p.targetNation, p.proposerNation);
            } catch (Exception ignored) {}
        } else if (allVoted) {
            p.finished = true;
            broadcast(n, "<yellow>Proposta de guerra REJEITADA. Presidente pode reiniciar.</yellow>");
        }
    }

    private void checkTargetComplete(WarProposal p) {
        Nation n = plugin.getNationManager().getNation(p.targetNation);
        if (n == null || n.getPopulation() == 0) return;
        double favor = favorRatio(p.targetVotes, n.getPopulation());
        int yes = (int) p.targetVotes.values().stream().filter(b -> b).count();
        boolean allVoted = p.targetVotes.size() >= n.getPopulation();

        if (favor >= 0.70 && (allVoted || yes >= Math.ceil(n.getPopulation() * 0.70))) {
            p.targetApproved = true;
            p.finished = true;
            Nation a = plugin.getNationManager().getNation(p.proposerNation);
            Nation b = n;
            plugin.getDiplomacyManager().setRelation(p.proposerNation, p.targetNation, DiplomaticRelation.WAR);
            try { plugin.getWarManager().startWar(p.proposerNation, p.targetNation); } catch (Exception ignored) {}
            try {
                plugin.getEmbargoManager().removeEmbargo(p.targetNation);
            } catch (Exception ignored) {}
            Bukkit.broadcast(MiniMessage.miniMessage().deserialize(
                    plugin.getConfigManager().getPrefix()
                            + "<dark_red><bold>GUERRA DECLARADA!</bold></dark_red> <yellow>"
                            + (a != null ? a.getDisplayName() : p.proposerNation)
                            + "</yellow> <gray>vs</gray> <yellow>"
                            + b.getDisplayName() + "</yellow>"));
        } else if (allVoted) {
            p.finished = true;
            try {
                plugin.getEmbargoManager().removeEmbargo(p.targetNation);
            } catch (Exception ignored) {}
            broadcast(plugin.getNationManager().getNation(p.proposerNation),
                    "<yellow>" + n.getDisplayName() + " rejeitou a guerra. Embargo removido.</yellow>");
            broadcast(n, "<green>Votacao rejeitou a guerra. Embargo levantado.</green>");
        }
    }

    private double favorRatio(Map<UUID, Boolean> votes, int population) {
        if (population == 0) return 0;
        long yes = votes.values().stream().filter(b -> b).count();
        return (double) yes / population;
    }

    private void broadcast(Nation nation, String msg) {
        if (nation == null) return;
        var c = MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getPrefix() + msg);
        Set<UUID> all = new HashSet<>(nation.getCitizens());
        all.addAll(nation.getGovernors());
        all.addAll(nation.getMayors());
        if (nation.getPresident() != null) all.add(nation.getPresident());
        for (UUID u : all) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) p.sendMessage(c);
        }
    }
}
