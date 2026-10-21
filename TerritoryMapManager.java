package com.nationswaw.systems;

import com.nationswaw.NationsWaW;
import com.nationswaw.gui.Theme;
import com.nationswaw.model.Nation;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.map.MinecraftFont;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.MapInitializeEvent;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mapa escala 1:2 (1 pixel = 2 blocos).
 * Chunk do jogador = pixels [60,68) centralizado em ~64.
 * Grade alinhada nas bordas dos chunks (60 + n*8).
 */
public class TerritoryMapManager implements Listener {
    private final NationsWaW plugin;
    private final Map<UUID, Integer> playerMaps = new HashMap<>();
    private final Set<Integer> knownMapIds = new HashSet<>();
    private final NamespacedKey mapKey;
    private static final long COOLDOWN_MS = 3L * 60 * 60 * 1000;

    private static final byte COL_OWN;
    private static final byte COL_OTHER;
    private static final byte COL_WILD;
    private static final byte COL_GRID;
    private static final byte COL_TEXT_BG;

    static {
        COL_OWN = MapPalette.matchColor(new Color(34, 139, 34));
        COL_OTHER = MapPalette.matchColor(new Color(178, 34, 34));
        COL_WILD = MapPalette.matchColor(new Color(40, 40, 40));
        COL_GRID = MapPalette.matchColor(new Color(15, 15, 15));
        COL_TEXT_BG = MapPalette.matchColor(new Color(20, 20, 20));
    }

    public TerritoryMapManager(NationsWaW plugin) {
        this.plugin = plugin;
        this.mapKey = new NamespacedKey(plugin, "territory_map");
        Bukkit.getPluginManager().registerEvents(this, plugin);
        loadKnownMaps();
        // Reanexa renderers apos 1 tick (mapas ja carregados)
        Bukkit.getScheduler().runTaskLater(plugin, this::reattachAllKnown, 40L);
    }

    private void loadKnownMaps() {
        plugin.getDatabaseManager().queryAsync(conn -> {
            try {
                try (var st = conn.createStatement()) {
                    st.executeUpdate("CREATE TABLE IF NOT EXISTS territory_maps (map_id INTEGER PRIMARY KEY)");
                }
                try (PreparedStatement ps = conn.prepareStatement("SELECT map_id FROM territory_maps");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) knownMapIds.add(rs.getInt(1));
                }
            } catch (Exception e) {
                plugin.getLogger().warning("load territory maps: " + e.getMessage());
            }
            return null;
        }).join();
        plugin.getLogger().info("Mapas de territorio conhecidos: " + knownMapIds.size());
    }

    private void saveMapId(int id) {
        knownMapIds.add(id);
        plugin.getDatabaseManager().executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR IGNORE INTO territory_maps (map_id) VALUES (?)")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            } catch (Exception e) {
                plugin.getLogger().warning("save map id: " + e.getMessage());
            }
        });
    }

    private void attachRenderer(MapView view, UUID viewerHint) {
        if (view == null) return;
        // remove vanilla renderers, keep only ours
        for (MapRenderer r : new java.util.ArrayList<>(view.getRenderers())) {
            view.removeRenderer(r);
        }
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(true);
        view.setScale(MapView.Scale.CLOSE);
        UUID vid = viewerHint != null ? viewerHint : (view.getWorld() != null ?
                null : null);
        // renderer uses online player from viewerId when rendering
        view.addRenderer(new LandClaimRenderer(plugin, viewerHint != null ? viewerHint :
                new UUID(0, 0)));
    }

    public void reattachAllKnown() {
        for (int id : new HashSet<>(knownMapIds)) {
            try {
                @SuppressWarnings("deprecation")
                MapView view = Bukkit.getMap(id);
                if (view != null) {
                    attachRenderer(view, null);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("reattach map " + id + ": " + e.getMessage());
            }
        }
    }

    @EventHandler
    public void onMapInit(MapInitializeEvent e) {
        MapView view = e.getMap();
        if (view != null && knownMapIds.contains(view.getId())) {
            attachRenderer(view, null);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> restorePlayerMaps(e.getPlayer()), 20L);
    }

    private void restorePlayerMaps(Player p) {
        for (ItemStack item : p.getInventory().getContents()) {
            if (item == null || item.getType() != Material.FILLED_MAP) continue;
            MapMeta meta = (MapMeta) item.getItemMeta();
            if (meta == null) continue;
            boolean ours = false;
            if (meta.getPersistentDataContainer().has(mapKey, PersistentDataType.BYTE)) {
                ours = true;
            }
            MapView view = meta.getMapView();
            if (view != null && knownMapIds.contains(view.getId())) ours = true;
            if (!ours) continue;
            if (view != null) {
                attachRenderer(view, p.getUniqueId());
                playerMaps.put(p.getUniqueId(), view.getId());
            }
        }
    }


    public void giveMap(Player p) {
        long remaining = getCooldownRemaining(p.getUniqueId());
        if (remaining > 0) {
            long h = remaining / 3_600_000;
            long m = (remaining % 3_600_000) / 60_000;
            p.sendMessage(Theme.MM.deserialize(
                    "<red>[X] Voce precisa esperar mais " + h + " hora(s) e " + m +
                    " minuto(s) para pegar outro mapa.</red>"));
            return;
        }
        setCooldown(p.getUniqueId());

        MapView view = Bukkit.createMap(p.getWorld());
        // CLOSE = vanilla 1:2 (tooltip do item). Nosso renderer desenha 1:2 de qualquer forma.
        view.setScale(MapView.Scale.CLOSE);
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(true);
        int cx = p.getLocation().getChunk().getX();
        int cz = p.getLocation().getChunk().getZ();
        view.setCenterX((cx << 4) + 8);
        view.setCenterZ((cz << 4) + 8);
        for (MapRenderer r : view.getRenderers()) {
            view.removeRenderer(r);
        }
        attachRenderer(view, p.getUniqueId());
        playerMaps.put(p.getUniqueId(), view.getId());
        saveMapId(view.getId());

        ItemStack map = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) map.getItemMeta();
        if (meta != null) {
            meta.setMapView(view);
            meta.getPersistentDataContainer().set(mapKey, PersistentDataType.BYTE, (byte) 1);
            meta.displayName(Theme.MM.deserialize(
                    "<gradient:#00C853:#69F0AE><bold>[◆] MAPA DE TERRITORIO</bold></gradient>"));
            meta.lore(java.util.List.of(
                    Theme.MM.deserialize("<green>■ Verde:</green> <white>suas terras</white>"),
                    Theme.MM.deserialize("<red>■ Vermelho:</red> <white>outras nações</white>"),
                    Theme.MM.deserialize("<gray>■ Cinza:</gray> <white>sem dono</white>"),
                    Theme.MM.deserialize("<aqua>Escala 1:2</aqua> <dark_gray>(1 pixel = 2 blocos)</dark_gray>"),
                    Theme.MM.deserialize("<dark_gray>Cooldown 3 horas entre mapas</dark_gray>")
            ));
            map.setItemMeta(meta);
        }
        p.getInventory().addItem(map);
        p.sendMessage(Theme.MM.deserialize("<green>Mapa de territorio recebido! (escala 1:2)</green>"));
    }

    private long getCooldownRemaining(UUID uuid) {
        long[] last = {0L};
        try {
            plugin.getDatabaseManager().queryAsync(conn -> {
                try (var ps = conn.prepareStatement("SELECT last_map_ms FROM map_cooldown WHERE uuid=?")) {
                    ps.setString(1, uuid.toString());
                    var rs = ps.executeQuery();
                    if (rs.next()) last[0] = rs.getLong(1);
                } catch (Exception e) {
                    plugin.getLogger().warning("map cooldown read: " + e.getMessage());
                }
                return null;
            }).join();
        } catch (Exception e) {
            plugin.getLogger().warning("map cooldown: " + e.getMessage());
        }
        long elapsed = System.currentTimeMillis() - last[0];
        if (last[0] <= 0 || elapsed >= COOLDOWN_MS) return 0;
        return COOLDOWN_MS - elapsed;
    }

    private void setCooldown(UUID uuid) {
        long now = System.currentTimeMillis();
        plugin.getDatabaseManager().executeAsync(conn -> {
            try (var ps = conn.prepareStatement(
                    "INSERT INTO map_cooldown (uuid, last_map_ms) VALUES (?,?) ON CONFLICT(uuid) DO UPDATE SET last_map_ms=excluded.last_map_ms")) {
                ps.setString(1, uuid.toString());
                ps.setLong(2, now);
                ps.executeUpdate();
            } catch (Exception e) {
                plugin.getLogger().warning("map cooldown write: " + e.getMessage());
            }
        });
    }

    static class LandClaimRenderer extends MapRenderer {
        private final NationsWaW plugin;
        private final UUID viewerId;
        private int lastCx = Integer.MIN_VALUE;
        private int lastCz = Integer.MIN_VALUE;
        private boolean fullRedraw = true;

        /** Pixels por chunk na escala 1:2 (16 blocos / 2) */
        private static final int PPC = 8;
        /** Offset para centralizar o chunk do jogador: pixels [60, 68) */
        private static final int ORIGIN = 60;

        LandClaimRenderer(NationsWaW plugin, UUID viewerId) {
            super(true);
            this.plugin = plugin;
            this.viewerId = viewerId;
        }

        @Override
        public void render(MapView map, MapCanvas canvas, Player player) {
            // Sempre usa o jogador que esta visualizando (persistente apos restart)
            UUID vid = player.getUniqueId();

            int cx = player.getLocation().getChunk().getX();
            int cz = player.getLocation().getChunk().getZ();

            if (cx != lastCx || cz != lastCz) {
                lastCx = cx;
                lastCz = cz;
                fullRedraw = true;
                map.setCenterX((cx << 4) + 8);
                map.setCenterZ((cz << 4) + 8);
            }

            Nation my = plugin.getNationManager().getNationByPlayer(vid);
            String myId = my != null ? my.getId() : null;
            String world = player.getWorld().getName();

            if (fullRedraw) {
                fullRedraw = false;
                // fundo
                for (int x = 0; x < 128; x++) {
                    for (int y = 0; y < 128; y++) {
                        canvas.setPixel(x, y, COL_WILD);
                    }
                }
                drawChunks(canvas, world, cx, cz, myId);
                drawGrid(canvas);
                drawTerritoryLabel(canvas, world, cx, cz);
            }
            updateCursor(canvas, player.getLocation().getYaw());
        }

        /**
         * Desenha cada chunk como bloco solido de 8x8 pixels.
         * Chunk do jogador (dx=0,dz=0) ocupa [60,68)x[60,68).
         */
        private void drawChunks(MapCanvas canvas, String world, int centerCx, int centerCz, String myId) {
            // dx de -7 a 8 cobre o mapa com origem 60
            for (int dx = -8; dx <= 8; dx++) {
                for (int dz = -8; dz <= 8; dz++) {
                    int baseX = ORIGIN + dx * PPC;
                    int baseZ = ORIGIN + dz * PPC;
                    if (baseX >= 128 || baseZ >= 128 || baseX + PPC <= 0 || baseZ + PPC <= 0) continue;

                    String owner = plugin.getClaimManager().getOwner(world, centerCx + dx, centerCz + dz);
                    byte color;
                    if (owner == null) color = COL_WILD;
                    else if (myId != null && owner.equalsIgnoreCase(myId)) color = COL_OWN;
                    else color = COL_OTHER;

                    for (int px = 0; px < PPC; px++) {
                        for (int pz = 0; pz < PPC; pz++) {
                            int x = baseX + px;
                            int z = baseZ + pz;
                            if (x >= 0 && x < 128 && z >= 0 && z < 128) {
                                canvas.setPixel(x, z, color);
                            }
                        }
                    }
                }
            }
        }

        /** Grade exatamente nas bordas dos chunks (60, 68, 76, ...) */
        private void drawGrid(MapCanvas canvas) {
            for (int n = -8; n <= 9; n++) {
                int pos = ORIGIN + n * PPC;
                if (pos < 0 || pos >= 128) continue;
                for (int j = 0; j < 128; j++) {
                    canvas.setPixel(pos, j, COL_GRID);
                    canvas.setPixel(j, pos, COL_GRID);
                }
            }
        }

        private void drawTerritoryLabel(MapCanvas canvas, String world, int cx, int cz) {
            String owner = plugin.getClaimManager().getOwner(world, cx, cz);
            String label;
            if (owner == null) {
                label = "Sem terras";
            } else {
                Nation n = plugin.getNationManager().getNation(owner);
                label = n != null ? n.getDisplayName() : owner;
            }
            int textW = Math.min(127, label.length() * 6 + 6);
            for (int x = 0; x < textW; x++) {
                for (int y = 0; y < 10; y++) {
                    canvas.setPixel(x, y, COL_TEXT_BG);
                }
            }
            try {
                canvas.drawText(2, 2, MinecraftFont.Font, label);
            } catch (Exception ignored) {
            }
        }

        private void updateCursor(MapCanvas canvas, float yaw) {
            MapCursorCollection cursors = canvas.getCursors();
            while (cursors.size() > 0) {
                cursors.removeCursor(cursors.getCursor(0));
            }
            int direction = ((int) Math.round(yaw / 22.5f)) & 15;
            try {
                @SuppressWarnings("deprecation")
                MapCursor cursor = new MapCursor((byte) 0, (byte) 0, (byte) direction, (byte) 0, true);
                cursors.addCursor(cursor);
            } catch (Throwable t) {
                try {
                    @SuppressWarnings("deprecation")
                    MapCursor cursor = new MapCursor((byte) 0, (byte) 0, (byte) direction, (byte) 1, true);
                    cursors.addCursor(cursor);
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
