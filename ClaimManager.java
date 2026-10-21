package com.nationswaw.systems;

import com.nationswaw.NationsWaW;
import com.nationswaw.gui.Theme;
import com.nationswaw.model.Nation;
import com.nationswaw.model.RoleType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClaimManager implements Listener {
    private final NationsWaW plugin;
    private final Map<String, String> claims = new ConcurrentHashMap<>();
    /** UUID jogador -> displays ativos do outline */
    private final Map<UUID, List<BlockDisplay>> activeOutlines = new ConcurrentHashMap<>();
    /** Presidente aguardando confirmacao de unclaim em massa */
    private final Map<UUID, Long> pendingMassUnclaim = new ConcurrentHashMap<>();
    /** Raio minimo entre claims de nações diferentes (Chebyshev). */
    public static final int BUFFER_CHUNKS = 8;
    private static final int MAX_CLAIMS = 1500;
    private static final double BASE_COST = 500.0;

    public ClaimManager(NationsWaW plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void loadClaims() {
        plugin.getDatabaseManager().queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM claims")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) claims.put(rs.getString("chunk_key"), rs.getString("nation_id"));
            } catch (Exception e) { plugin.getLogger().warning("Load claims: " + e.getMessage()); }
            return null;
        }).join();
    }

    public void saveClaims() {
        plugin.getDatabaseManager().executeAsync(conn -> {
            try {
                try (PreparedStatement del = conn.prepareStatement("DELETE FROM claims")) { del.executeUpdate(); }
                for (var e : claims.entrySet()) {
                    try (PreparedStatement ps = conn.prepareStatement("INSERT INTO claims (chunk_key, nation_id) VALUES (?,?)")) {
                        ps.setString(1, e.getKey()); ps.setString(2, e.getValue()); ps.executeUpdate();
                    }
                }
            } catch (Exception ex) { plugin.getLogger().warning("Save claims: " + ex.getMessage()); }
        });
    }

    private String key(Chunk c) { return c.getWorld().getName() + ":" + c.getX() + ":" + c.getZ(); }
    public String getOwner(Chunk c) { return claims.get(key(c)); }
    public String getOwner(String world, int cx, int cz) { return claims.get(world + ":" + cx + ":" + cz); }
    public Map<String, String> getAllClaims() { return claims; }

    /** Custo do proximo claim: 500 * (1.5 ^ count) */
    public double nextClaimCost(String nationId) {
        int count = countClaims(nationId);
        return BASE_COST * Math.pow(1.5, count);
    }

    public Material borderMaterial(String nationId) {
        if (nationId == null) return Material.GREEN_CONCRETE;
        return switch (nationId.toLowerCase()) {
            case "brasil" -> Material.GREEN_CONCRETE;
            case "eua" -> Material.BLUE_CONCRETE;
            case "japao" -> Material.WHITE_CONCRETE;
            case "russia" -> Material.RED_CONCRETE;
            default -> Material.GRAY_CONCRETE;
        };
    }

    public boolean claim(Player p, Chunk chunk) {
        Nation n = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
        if (n == null) { p.sendMessage(Theme.MM.deserialize("<red>Precisa ser civil.</red>")); return false; }
        if (!plugin.getRoleManager().hasRoleAtLeast(p.getUniqueId(), RoleType.PRESIDENT)) {
            p.sendMessage(Theme.MM.deserialize("<red>Apenas o Presidente pode fazer isto!</red>")); return false;
        }
        String k = key(chunk);
        if (claims.containsKey(k)) { p.sendMessage(Theme.MM.deserialize("<red>Ja claimado.</red>")); return false; }

        // Zona de amortecimento: nenhuma nação claima a <= BUFFER_CHUNKS de outra nacao
        String foreign = findForeignNationInBuffer(chunk.getWorld().getName(), chunk.getX(), chunk.getZ(), n.getId(), BUFFER_CHUNKS);
        if (foreign != null) {
            String name = foreign;
            var other = plugin.getNationManager().getNation(foreign);
            if (other != null) name = other.getDisplayName();
            p.sendMessage(Theme.MM.deserialize(
                    "<red>Nao e permitido claimar a " + BUFFER_CHUNKS + " chunks de outra nação!</red>"));
            p.sendMessage(Theme.MM.deserialize(
                    "<gray>Territorio proximo de:</gray> <yellow>" + name + "</yellow>"));
            p.sendMessage(Theme.MM.deserialize(
                    "<dark_gray>Mantenha pelo menos " + BUFFER_CHUNKS + " chunks de distancia entre nações.</dark_gray>"));
            return false;
        }

        int count = countClaims(n.getId());
        // Expansao contigua: a partir da 2a claim, precisa tocar territorio proprio (N/S/L/O)
        if (count > 0 && !isAdjacentToOwnClaim(chunk.getWorld().getName(), chunk.getX(), chunk.getZ(), n.getId())) {
            p.sendMessage(Theme.MM.deserialize(
                    "<red>So e permitido expandir a partir do territorio ja claimado!</red>"));
            p.sendMessage(Theme.MM.deserialize(
                    "<gray>Fique ao lado (norte, sul, leste ou oeste) de uma chunk da sua nação.</gray>"));
            p.sendMessage(Theme.MM.deserialize(
                    "<dark_gray>Claims isolados ou com salto sao bloqueados.</dark_gray>"));
            return false;
        }
        if (count >= MAX_CLAIMS) {
            p.sendMessage(Theme.MM.deserialize("<red>Limite de " + MAX_CLAIMS + " chunks atingido.</red>"));
            return false;
        }
        double cost = nextClaimCost(n.getId());
        if (!n.removeFromCentralBank(cost)) {
            p.sendMessage(Theme.MM.deserialize("<red>Banco Central insuficiente. Necessario: "
                    + n.getCurrencySymbol() + " " + Theme.fmt(cost) + "</red>"));
            return false;
        }
        try { plugin.getCacheService().invalidateClaims(null); } catch (Exception ignored) {}
        claims.put(k, n.getId());
        plugin.getNationManager().saveAll();
        saveClaims();
        try { plugin.getInfrastructureMissionManager().onClaimCountChanged(n.getId()); } catch (Exception ignored) {}
        showParticleBorder(p, chunk, n.getId());
        p.sendMessage(Theme.MM.deserialize(
                "<gold>[◆]</gold> <green>Chunk claimado com sucesso!</green> " +
                "<gray>Custo:</gray> <yellow>" + n.getCurrencySymbol() + " " + Theme.fmt(cost) + "</yellow>. " +
                "<gray>Total:</gray> <aqua>" + (count + 1) + "/" + MAX_CLAIMS + " chunks</aqua>."));
        return true;
    }




    /** Alias - outline estilo LandClaimPlugin 3.4.0 */




    /** Alias */

    /**
     * Procura claim de OUTRA nação no raio Chebyshev (max(|dx|,|dz|) <= radius).
     * Retorna o id da nação estrangeira mais proxima, ou null se a area esta livre.
     */
    /**
     * true se o chunk e ortogonalmente adjacente a pelo menos um claim da mesma nação.
     * Forca expansao continua (sem saltos de chunks).
     */
    public boolean isAdjacentToOwnClaim(String world, int cx, int cz, String nationId) {
        int[][] dirs = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}}; // N S W E
        for (int[] d : dirs) {
            String owner = getOwner(world, cx + d[0], cz + d[1]);
            if (owner != null && owner.equalsIgnoreCase(nationId)) return true;
        }
        return false;
    }

    public String findForeignNationInBuffer(String world, int cx, int cz, String myNationId, int radius) {
        String closest = null;
        int bestDist = Integer.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int dist = Math.max(Math.abs(dx), Math.abs(dz));
                if (dist == 0) continue;
                String owner = getOwner(world, cx + dx, cz + dz);
                if (owner == null) continue;
                if (owner.equalsIgnoreCase(myNationId)) continue;
                if (dist < bestDist) {
                    bestDist = dist;
                    closest = owner;
                }
            }
        }
        return closest;
    }

    /** true se o chunk esta livre e respeita o buffer de 5 chunks entre nações. */
    public boolean isClaimAllowed(String world, int cx, int cz, String nationId) {
        if (getOwner(world, cx, cz) != null) return false;
        return findForeignNationInBuffer(world, cx, cz, nationId, BUFFER_CHUNKS) == null;
    }

    public void showParticleBorder(Player p, Chunk chunk, String nationId) {
        showClaimOutline(p, chunk, nationId);
    }

    /**
     * Contorno estilo LandClaim: 1 painel por face, vidro esticado minY..maxY.
     * Entidade spawnada perto do jogador (view range) + translation Y para alinhar no bedrock.
     * Mostra claims da nação no raio de 5 chunks. So presidente.
     */
    public void showClaimOutline(Player p, Chunk chunk, String nationId) {
        clearOutline(p.getUniqueId());

        Material glass = switch (nationId == null ? "" : nationId.toLowerCase()) {
            case "brasil" -> Material.LIME_STAINED_GLASS;
            case "eua" -> Material.BLUE_STAINED_GLASS;
            case "japao" -> Material.WHITE_STAINED_GLASS;
            case "russia" -> Material.RED_STAINED_GLASS;
            default -> Material.GRAY_STAINED_GLASS;
        };

        org.bukkit.World w = chunk.getWorld();
        String worldName = w.getName();
        int centerCx = chunk.getX();
        int centerCz = chunk.getZ();
        int minY = w.getMinHeight();
        int maxY = w.getMaxHeight();
        float height = (float) (maxY - minY);
        float thick = 0.05f;
        // Spawn perto do jogador para o cliente renderizar; translation leva o visual ao minY
        double spawnY = p.getLocation().getY();
        float translateY = (float) (minY - spawnY);

        final int RADIUS = 5;
        java.util.Set<String> nationChunks = new java.util.HashSet<>();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                int nx = centerCx + dx;
                int nz = centerCz + dz;
                if (sameNation(nationId, getOwner(worldName, nx, nz))) {
                    nationChunks.add(nx + ":" + nz);
                }
            }
        }
        nationChunks.add(centerCx + ":" + centerCz);

        List<BlockDisplay> displays = new ArrayList<>();
        for (String key : nationChunks) {
            String[] parts = key.split(":");
            int cx = Integer.parseInt(parts[0]);
            int cz = Integer.parseInt(parts[1]);
            double minX = cx * 16.0;
            double minZ = cz * 16.0;
            double maxX = minX + 16.0;
            double maxZ = minZ + 16.0;

            boolean drawNorth = !nationChunks.contains(cx + ":" + (cz - 1));
            boolean drawSouth = !nationChunks.contains(cx + ":" + (cz + 1));
            boolean drawWest  = !nationChunks.contains((cx - 1) + ":" + cz);
            boolean drawEast  = !nationChunks.contains((cx + 1) + ":" + cz);

            // Uma parede continua por face (scaleY = altura total)
            if (drawNorth) {
                displays.add(spawnPanel(w, p, glass, minX, spawnY, minZ, 16f, height, thick, translateY));
            }
            if (drawSouth) {
                displays.add(spawnPanel(w, p, glass, minX, spawnY, maxZ - thick, 16f, height, thick, translateY));
            }
            if (drawWest) {
                displays.add(spawnPanel(w, p, glass, minX, spawnY, minZ, thick, height, 16f, translateY));
            }
            if (drawEast) {
                displays.add(spawnPanel(w, p, glass, maxX - thick, spawnY, minZ, thick, height, 16f, translateY));
            }
        }

        activeOutlines.put(p.getUniqueId(), displays);
        Bukkit.getScheduler().runTaskLater(plugin, () -> clearOutline(p.getUniqueId()), 200L);
        p.sendMessage(Theme.MM.deserialize(
                "<gray>Contorno de " + nationChunks.size() + " chunk(s) visivel por 10 segundos.</gray>"));
    }

    /** Mostra outline das claims proximas (sem claimar). So presidente. */
    public void showNearbyClaims(Player p) {
        Nation n = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
        if (n == null) {
            p.sendMessage(Theme.MM.deserialize("<red>Sem nação.</red>"));
            return;
        }
        if (!plugin.getRoleManager().hasRoleAtLeast(p.getUniqueId(), RoleType.PRESIDENT)) {
            p.sendMessage(Theme.MM.deserialize("<red>Apenas o Presidente pode fazer isto!</red>"));
            return;
        }
        showClaimOutline(p, p.getLocation().getChunk(), n.getId());
    }

    private boolean sameNation(String nationId, String owner) {
        return nationId != null && owner != null && nationId.equalsIgnoreCase(owner);
    }

    private BlockDisplay spawnPanel(org.bukkit.World w, Player viewer, Material glass,
                                    double x, double y, double z,
                                    float scaleX, float scaleY, float scaleZ,
                                    float translateY) {
        Location loc = new Location(w, x, y, z);
        BlockDisplay display = w.spawn(loc, BlockDisplay.class);
        display.setBlock(Bukkit.createBlockData(glass));
        display.setPersistent(false);
        display.setShadowRadius(0f);
        display.setShadowStrength(0f);
        try {
            display.setViewRange(16.0f);
        } catch (Throwable ignored) {}
        try {
            display.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
        } catch (Throwable ignored) {}

        // translation Y desloca o painel ate o minHeight; scale estica ate o topo
        Transformation transform = new Transformation(
                new Vector3f(0f, translateY, 0f),
                new AxisAngle4f(0f, 0f, 0f, 1f),
                new Vector3f(scaleX, scaleY, scaleZ),
                new AxisAngle4f(0f, 0f, 0f, 1f)
        );
        display.setTransformation(transform);
        display.setInterpolationDuration(0);
        display.setInterpolationDelay(0);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(viewer.getUniqueId())) {
                online.hideEntity(plugin, display);
            }
        }
        try {
            viewer.showEntity(plugin, display);
        } catch (Throwable ignored) {}

        return display;
    }

    public void clearOutline(UUID playerId) {
        List<BlockDisplay> list = activeOutlines.remove(playerId);
        if (list == null) return;
        for (BlockDisplay d : list) {
            try {
                if (d != null && d.isValid()) d.remove();
            } catch (Throwable ignored) {}
        }
    }

    /** @deprecated sem blocos fisicos */
    public void placeBorder(Chunk chunk, String nationId) {
        // no-op
    }






    public boolean unclaim(Player p, Chunk chunk) {
        Nation n = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
        if (n == null) return false;
        if (!plugin.getRoleManager().hasRoleAtLeast(p.getUniqueId(), RoleType.PRESIDENT)) {
            p.sendMessage(Theme.MM.deserialize("<red>Apenas o Presidente pode fazer unclaim!</red>"));
            return false;
        }
        int total = countClaims(n.getId());
        if (total <= 0) {
            p.sendMessage(Theme.MM.deserialize("<red>Sua nacao nao tem claims.</red>"));
            return false;
        }
        // 1 claim: remove imediatamente o chunk atual (ou o unico)
        if (total == 1) {
            String k = key(chunk);
            if (!n.getId().equals(claims.get(k))) {
                // remove o unico claim restante em qualquer lugar
                claims.entrySet().removeIf(e -> n.getId().equals(e.getValue()));
            } else {
                claims.remove(k);
            }
            saveClaims();
            p.sendMessage(Theme.MM.deserialize("<green>[◆] Unico claim removido com sucesso.</green>"));
            return true;
        }
        // multiplos: pede confirmacao
        pendingMassUnclaim.put(p.getUniqueId(), System.currentTimeMillis() + 30_000L);
        p.sendMessage(Theme.MM.deserialize(
                "<gold>==========================================</gold>"));
        p.sendMessage(Theme.MM.deserialize(
                "<gold>[◆] AVISO - UNCLAIM EM MASSA</gold>"));
        p.sendMessage(Theme.MM.deserialize(
                "<gray>[■] Voce esta prestes a remover TODAS as suas claims (" + total + " chunks).</gray>"));
        p.sendMessage(Theme.MM.deserialize(
                "<gray>[■] Tem certeza que deseja fazer isso?</gray>"));
        p.sendMessage(Theme.MM.deserialize(
                "<yellow>[■] Digite SIM (ou S) para confirmar, ou NAO (ou N) para cancelar.</yellow>"));
        p.sendMessage(Theme.MM.deserialize(
                "<dark_gray>Expira em 30 segundos.</dark_gray>"));
        p.sendMessage(Theme.MM.deserialize(
                "<gold>==========================================</gold>"));
        return true;
    }

    public boolean isPendingMassUnclaim(UUID uuid) {
        Long exp = pendingMassUnclaim.get(uuid);
        if (exp == null) return false;
        if (System.currentTimeMillis() > exp) {
            pendingMassUnclaim.remove(uuid);
            return false;
        }
        return true;
    }

    public void cancelMassUnclaim(UUID uuid) {
        pendingMassUnclaim.remove(uuid);
    }

    public boolean confirmMassUnclaim(Player p) {
        if (!isPendingMassUnclaim(p.getUniqueId())) return false;
        pendingMassUnclaim.remove(p.getUniqueId());
        Nation n = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
        if (n == null) return false;
        if (!plugin.getRoleManager().hasRoleAtLeast(p.getUniqueId(), RoleType.PRESIDENT)) return false;
        int before = countClaims(n.getId());
        claims.entrySet().removeIf(e -> n.getId().equals(e.getValue()));
        saveClaims();
        p.sendMessage(Theme.MM.deserialize(
                "<green>[◆] Todas as claims removidas (" + before + " chunks).</green>"));
        return true;
    }


    /** Retorna "world:cx:cz" da primeira claim da nacao, ou null */
    public String getFirstClaimKey(String nationId) {
        for (var e : claims.entrySet()) {
            if (nationId.equalsIgnoreCase(e.getValue())) return e.getKey();
        }
        return null;
    }

    public int countClaims(String nationId) {
        if (nationId == null) return 0;
        try {
            return plugin.getCacheService().getClaimCount(nationId, id -> {
                int c = 0;
                for (String v : claims.values()) {
                    if (id.equalsIgnoreCase(v)) c++;
                }
                return c;
            });
        } catch (Exception e) {
            int c = 0;
            for (String v : claims.values()) {
                if (nationId.equalsIgnoreCase(v)) c++;
            }
            return c;
        }
    }

    public void openClaimsGUI(Player p) {
        Nation n = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 27, Theme.title("TERRITORIO"));
        Theme.fillBorder(inv);
        int count = n != null ? countClaims(n.getId()) : 0;
        double next = n != null ? nextClaimCost(n.getId()) : BASE_COST;
        inv.setItem(11, Theme.item(Material.GOLDEN_AXE, "<green><bold>[◆] CLAIM</bold></green>",
                Theme.DIV,
                "<gray>Claima o chunk atual</gray>",
                "<gray>Proximo custo:</gray> <yellow>" + Theme.fmt(next) + "</yellow>",
                "<gray>Debita do Banco Central</gray>"));
        inv.setItem(13, Theme.item(Material.MAP, "<aqua><bold>CLAIMS</bold></aqua>",
                Theme.DIV,
                "<gray>Usados:</gray> <white>" + count + "/" + MAX_CLAIMS + "</white>",
                "<gray>Use /mapa para ver o radar</gray>"));
        inv.setItem(15, Theme.item(Material.IRON_AXE, "<red><bold>UNCLAIM</bold></red>",
                Theme.DIV, "<gray>Libera o chunk atual (Gov+)</gray>"));
        inv.setItem(22, Theme.item(Material.FILLED_MAP, "<yellow><bold>/mapa</bold></yellow>",
                Theme.DIV, "<gray>Recebe mapa de territorio</gray>"));
        Theme.fillFooter(inv, 18, 26);
        p.openInventory(inv);
    }


    @EventHandler(priority = EventPriority.LOWEST)
    public void onChatConfirmUnclaim(AsyncChatEvent e) {
        Player p = e.getPlayer();
        if (!isPendingMassUnclaim(p.getUniqueId())) return;
        String msg = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        String low = msg.toLowerCase();
        if (low.equals("sim") || low.equals("s") || low.equals("yes") || low.equals("y")) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> confirmMassUnclaim(p));
        } else if (low.equals("nao") || low.equals("não") || low.equals("n") || low.equals("no")) {
            e.setCancelled(true);
            cancelMassUnclaim(p.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () ->
                    p.sendMessage(Theme.MM.deserialize("<yellow>[◆] Unclaim em massa cancelado.</yellow>")));
        }
    }

    @EventHandler
    public void onGui(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String t = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(e.getView().title());
        if (!t.contains("TERRITORIO") && !t.contains("Territorio")) return;
        e.setCancelled(true);
        int s = e.getSlot();
        if (s == 18 || s == 26) { p.closeInventory(); return; }
        if (s == 11) claim(p, p.getLocation().getChunk());
        else if (s == 15) unclaim(p, p.getLocation().getChunk());
        else if (s == 22) plugin.getTerritoryMapManager().giveMap(p);
        p.closeInventory();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        clearOutline(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        String owner = getOwner(e.getBlock().getChunk());
        if (owner == null) return;
        if (plugin.getWarManager().canModifyClaim(e.getPlayer(), owner)) return;
        e.setCancelled(true);
        e.getPlayer().sendMessage(Theme.MM.deserialize("<red>Territorio de outra nação.</red>"));
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        String owner = getOwner(e.getBlock().getChunk());
        if (owner == null) return;
        if (plugin.getWarManager().canModifyClaim(e.getPlayer(), owner)) return;
        e.setCancelled(true);
        e.getPlayer().sendMessage(Theme.MM.deserialize("<red>Territorio de outra nação.</red>"));
    }
}
