package com.nationswaw.listeners;

import com.nationswaw.NationsWaW;
import com.nationswaw.model.Nation;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class PvPListener implements Listener {
    private final NationsWaW plugin;
    public PvPListener(NationsWaW plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim) || !(e.getDamager() instanceof Player attacker)) return;
        if (!plugin.getConfigManager().isPvpWarOnly()) return;
        Nation atk = plugin.getNationManager().getNationByPlayer(attacker.getUniqueId());
        Nation vic = plugin.getNationManager().getNationByPlayer(victim.getUniqueId());
        if (plugin.getWarManager().isWorldWarActive()) return; // PvP livre
        if (atk == null || vic == null || !plugin.getDiplomacyManager().isAtWar(atk.getId(), vic.getId())) {
            e.setCancelled(true);
        }
    }
}
