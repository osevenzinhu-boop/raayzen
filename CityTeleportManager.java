package com.nationswaw.systems;

import com.nationswaw.NationsWaW;
import com.nationswaw.gui.Theme;
import com.nationswaw.model.Nation;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /nation cidade — teleporte seguro a primeira claim, countdown 3s, cancela ao mover/dano.
 * Cooldown 30s.
 */
public class CityTeleportManager implements Listener {
    private final NationsWaW plugin;
    private static final long COOLDOWN_MS = 5L * 60_000L; // 5 minutos
    private static final int COUNTDOWN_SEC = 30; // 30 segundos

    private final Map<UUID, Long> cooldownUntil = new ConcurrentHashMap<>();
    private final Map<UUID, PendingTp> pending = new ConcurrentHashMap<>();

    private static class PendingTp {
        final Location start;
        final Location dest;
        int remaining;
        BukkitTask task;
        PendingTp(Location start, Location dest, int remaining) {
            this.start = start;
            this.dest = dest;
            this.remaining = remaining;
        }
    }

    public CityTeleportManager(NationsWaW plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void requestTeleport(Player p) {
        Nation n = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
        if (n == null) {
            p.sendMessage(Theme.MM.deserialize("<red>Voce precisa pertencer a uma nacao.</red>"));
            return;
        }
        long now = System.currentTimeMillis();
        Long until = cooldownUntil.get(p.getUniqueId());
        if (until != null && until > now) {
            long sec = (until - now + 999) / 1000;
            p.sendMessage(Theme.MM.deserialize("<red>Aguarde " + sec + "s para usar /nation cidade novamente.</red>"));
            return;
        }
        if (pending.containsKey(p.getUniqueId())) {
            p.sendMessage(Theme.MM.deserialize("<yellow>Teleporte ja em andamento...</yellow>"));
            return;
        }
        String key = plugin.getClaimManager().getFirstClaimKey(n.getId());
        if (key == null) {
            p.sendMessage(Theme.MM.deserialize("<red>Sua nacao ainda nao tem claims. O Presidente deve claimar territorio.</red>"));
            return;
        }
        String[] parts = key.split(":");
        if (parts.length < 3) {
            p.sendMessage(Theme.MM.deserialize("<red>Claim invalido.</red>"));
            return;
        }
        World w = Bukkit.getWorld(parts[0]);
        if (w == null) {
            p.sendMessage(Theme.MM.deserialize("<red>Mundo da claim nao encontrado.</red>"));
            return;
        }
        int cx, cz;
        try {
            cx = Integer.parseInt(parts[1]);
            cz = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            p.sendMessage(Theme.MM.deserialize("<red>Coordenadas invalidas.</red>"));
            return;
        }
        int blockX = (cx << 4) + 8;
        int blockZ = (cz << 4) + 8;
        int y = w.getHighestBlockYAt(blockX, blockZ);
        Location dest = new Location(w, blockX + 0.5, y + 1.0, blockZ + 0.5);
        // safety: if block above is solid, climb
        while (y < w.getMaxHeight() - 2 && !dest.getBlock().getType().isAir()) {
            y++;
            dest.setY(y + 1.0);
        }

        Location start = p.getLocation().clone();
        PendingTp pt = new PendingTp(start, dest, COUNTDOWN_SEC);
        pending.put(p.getUniqueId(), pt);
        p.sendMessage(Theme.MM.deserialize(
                "<gold>[◆] Teleporte para a cidade em " + COUNTDOWN_SEC + "s...</gold> "
                        + "<gray>Nao se mova nem tome dano!</gray>"));

        pt.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            PendingTp cur = pending.get(p.getUniqueId());
            if (cur == null) return;
            if (!p.isOnline()) {
                cancel(p.getUniqueId(), false);
                return;
            }
            if (cur.remaining <= 0) {
                pending.remove(p.getUniqueId());
                if (cur.task != null) cur.task.cancel();
                p.teleport(cur.dest);
                cooldownUntil.put(p.getUniqueId(), System.currentTimeMillis() + COOLDOWN_MS);
                p.sendMessage(Theme.MM.deserialize("<green>[◆] Voce chegou a cidade de " + n.getDisplayName() + "!</green>"));
                p.sendActionBar(Component.empty());
                return;
            }
            p.sendActionBar(Theme.MM.deserialize(
                    "<gold>Teleportando em <bold>" + cur.remaining + "</bold>s...</gold> <gray>Nao se mova!</gray>"));
            cur.remaining--;
        }, 0L, 20L);
    }

    private void cancel(UUID uuid, boolean notify) {
        PendingTp pt = pending.remove(uuid);
        if (pt != null && pt.task != null) pt.task.cancel();
        Player p = Bukkit.getPlayer(uuid);
        if (p != null && notify) {
            p.sendMessage(Theme.MM.deserialize("<red>[X] Teleporte cancelado (movimento ou dano).</red>"));
            p.sendActionBar(Component.empty());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!pending.containsKey(e.getPlayer().getUniqueId())) return;
        Location f = e.getFrom();
        Location t = e.getTo();
        if (t == null) return;
        if (f.getBlockX() != t.getBlockX() || f.getBlockY() != t.getBlockY() || f.getBlockZ() != t.getBlockZ()) {
            cancel(e.getPlayer().getUniqueId(), true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (pending.containsKey(p.getUniqueId())) {
            cancel(p.getUniqueId(), true);
        }
    }
}
