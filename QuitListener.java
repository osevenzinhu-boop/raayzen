package com.nationswaw.listeners;

import com.nationswaw.NationsWaW;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class QuitListener implements Listener {
    private final NationsWaW plugin;

    public QuitListener(NationsWaW plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        try {
            plugin.getCacheService().invalidatePlayer(e.getPlayer().getUniqueId());
        } catch (Exception ignored) {}
    }
}
