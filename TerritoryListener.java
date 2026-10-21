package com.nationswaw.listeners;

import com.nationswaw.NationsWaW;
import com.nationswaw.gui.Theme;
import com.nationswaw.model.Nation;
import net.kyori.adventure.text.Component;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Action bar APENAS em transicao de tipo de territorio:
 * WILDERNESS / OWN_NATION / OTHER_NATION (e mudanca de nação especifica).
 * Andar de chunk livre -> chunk livre NAO notifica.
 */
public class TerritoryListener implements Listener {
    private final NationsWaW plugin;

    enum TerritoryType { WILDERNESS, OWN_NATION, OTHER_NATION }

    /** estado: "WILDERNESS" | "OWN:brasil" | "OTHER:eua" etc */
    private final Map<UUID, String> lastState = new ConcurrentHashMap<>();

    public TerritoryListener(NationsWaW plugin) {
        this.plugin = plugin;
    }

    private String stateKey(Player p, String ownerId) {
        if (ownerId == null || ownerId.isEmpty()) return "WILDERNESS";
        Nation my = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
        if (my != null && ownerId.equalsIgnoreCase(my.getId())) {
            return "OWN:" + ownerId.toLowerCase();
        }
        return "OTHER:" + ownerId.toLowerCase();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        Chunk from = e.getFrom().getChunk();
        Chunk to = e.getTo().getChunk();
        if (from.getX() == to.getX() && from.getZ() == to.getZ()
                && from.getWorld().equals(to.getWorld())) {
            return; // mesmo chunk
        }

        Player p = e.getPlayer();
        String owner = plugin.getClaimManager().getOwner(to);
        String newState = stateKey(p, owner);
        String prev = lastState.put(p.getUniqueId(), newState);

        // primeira vez ou mesmo estado -> nao notifica
        if (prev == null || prev.equals(newState)) {
            // na primeira entrada ainda notifica se prev==null? Spec: so transicao.
            // Na primeira vez (prev==null) podemos notificar uma vez.
            if (prev != null) return;
        }

        Component msg;
        if (newState.equals("WILDERNESS")) {
            msg = Theme.MM.deserialize("<gray>Local sem nacionalidade</gray>");
        } else if (newState.startsWith("OWN:")) {
            msg = Theme.MM.deserialize("<green>Voce esta em suas terras!</green>");
        } else {
            String id = newState.substring("OTHER:".length());
            Nation other = plugin.getNationManager().getNation(id);
            String name = other != null ? other.getDisplayName() : id;
            msg = Theme.MM.deserialize(
                    "<red>Estas terras pertence a outra nação! (" + name + ")</red>");
        }
        p.sendActionBar(msg);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastState.remove(e.getPlayer().getUniqueId());
    }
}
