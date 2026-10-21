package com.nationswaw.listeners;

import org.bukkit.Bukkit;

import com.nationswaw.NationsWaW;
import com.nationswaw.model.Nation;
import com.nationswaw.model.PlayerData;
import com.nationswaw.model.RoleType;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class JoinListener implements Listener {
    private final NationsWaW plugin;
    public JoinListener(NationsWaW plugin) { this.plugin = plugin; }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.getDisplayManager().updatePlayerDisplay(e.getPlayer());
            try { plugin.getJoinRequestManager().notifyLeaderOnJoin(e.getPlayer()); } catch (Exception ignored) {}
        }, 10L);
        PlayerData data = plugin.getRoleManager().getPlayerData(e.getPlayer().getUniqueId());
        var mm = MiniMessage.miniMessage();
        if (data.isImmigrant()) {
            e.getPlayer().sendMessage(mm.deserialize(plugin.getConfigManager().getPrefix() +
                    "<gray>Voce e um <yellow>imigrante</yellow>. Solicite cidadania!</gray>"));
        } else {
            Nation n = plugin.getNationManager().getNation(data.getNationId());
            if (n != null)
                e.getPlayer().sendMessage(mm.deserialize(plugin.getConfigManager().getPrefix() +
                        "<green>Bem-vindo, civil de <bold>" + n.getDisplayName() + "</bold>!</green>"));
        }
    }
}
