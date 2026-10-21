package com.nationswaw.systems;

import com.nationswaw.NationsWaW;
import com.nationswaw.model.Nation;
import com.nationswaw.model.RoleType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.util.List;

/**
 * Nametags + TAB list (tab.yml configuravel).
 */
public class DisplayManager {
    private final NationsWaW plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private FileConfiguration tabConfig;
    private List<String> headerLines = List.of("<gradient:#FFD700:#FFA000><bold>◆ NationsWaW ◆</bold></gradient>");
    private List<String> footerLines = List.of("<gray>Online: <white>%online%</white></gray>");
    private long updateInterval = 40L;

    public DisplayManager(NationsWaW plugin) {
        this.plugin = plugin;
        loadTabConfig();
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 40L, updateInterval);
    }

    public void loadTabConfig() {
        try {
            File f = new File(plugin.getDataFolder(), "tab.yml");
            if (!f.exists()) {
                plugin.saveResource("tab.yml", false);
            }
            tabConfig = YamlConfiguration.loadConfiguration(f);
            headerLines = tabConfig.getStringList("header");
            footerLines = tabConfig.getStringList("footer");
            if (headerLines == null || headerLines.isEmpty()) {
                headerLines = List.of("<gradient:#FFD700:#FFA000><bold>◆ NationsWaW ◆</bold></gradient>");
            }
            if (footerLines == null || footerLines.isEmpty()) {
                footerLines = List.of("<gray>Online: <white>%online%</white></gray>");
            }
            updateInterval = Math.max(20L, tabConfig.getLong("update-interval-ticks", 40L));
        } catch (Exception e) {
            plugin.getLogger().warning("tab.yml: " + e.getMessage());
        }
    }

    public void reload() {
        loadTabConfig();
        refreshAll();
    }

    public void refreshAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            updatePlayerDisplay(p);
        }
        updateTabHeaderFooter();
    }

    public void updatePlayerDisplay(Player player) {
        if (player == null || !player.isOnline()) return;
        Nation n = plugin.getNationManager().getNationByPlayer(player.getUniqueId());
        RoleType role = plugin.getRoleManager().getRole(player.getUniqueId());

        String nationColor = nationChatColor(n);
        String roleColor = roleChatColor(role);
        String sigla = nationSigla(n);
        String cargoShort = roleShort(role);

        String listName;
        if (n == null) {
            listName = ChatColor.DARK_GRAY + "[Sem Nacao] " + ChatColor.GRAY + "Imigrante "
                    + ChatColor.WHITE + player.getName();
        } else {
            listName = nationColor + "[" + sigla + "] " + roleColor + cargoShort + " "
                    + ChatColor.WHITE + player.getName();
        }
        if (listName.length() > 40) {
            listName = listName.substring(0, 40);
        }
        try {
            player.setPlayerListName(listName);
        } catch (Exception ignored) {}

        Scoreboard board = player.getScoreboard();
        if (board == null || board == Bukkit.getScoreboardManager().getMainScoreboard()) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            player.setScoreboard(board);
        }
        String teamName = "nw_" + (n == null ? "imig" : n.getId()).substring(0, Math.min(8, (n == null ? "imig" : n.getId()).length()));
        Team team = board.getTeam(teamName);
        if (team == null) {
            team = board.registerNewTeam(teamName);
        }
        String prefix = n == null
                ? ChatColor.DARK_GRAY + "[SN] "
                : nationColor + "[" + sigla + "] " + roleColor;
        try {
            team.setPrefix(prefix.length() > 16 ? prefix.substring(0, 16) : prefix);
            if (!team.hasEntry(player.getName())) {
                for (Team t : board.getTeams()) {
                    t.removeEntry(player.getName());
                }
                team.addEntry(player.getName());
            }
        } catch (Exception ignored) {}
    }

    public void updateTabHeaderFooter() {
        int online = Bukkit.getOnlinePlayers().size();
        String onlineStr = String.valueOf(online);
        Component header = joinLines(headerLines, onlineStr);
        Component footer = joinLines(footerLines, onlineStr);
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                p.sendPlayerListHeaderAndFooter(header, footer);
            } catch (Exception ignored) {}
        }
    }

    private Component joinLines(List<String> lines, String online) {
        if (lines == null || lines.isEmpty()) return Component.empty();
        Component out = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).replace("%online%", online);
            if (i > 0) out = out.append(Component.newline());
            out = out.append(MM.deserialize(line));
        }
        return out;
    }

    private static String nationSigla(Nation n) {
        if (n == null) return "SN";
        return switch (n.getId().toLowerCase()) {
            case "brasil" -> "BR";
            case "eua" -> "US";
            case "japao" -> "JP";
            case "russia" -> "RU";
            default -> n.getId().substring(0, Math.min(2, n.getId().length())).toUpperCase();
        };
    }

    private static String roleShort(RoleType r) {
        if (r == null) return "Imig";
        return switch (r) {
            case PRESIDENT -> "Pres";
            case GOVERNOR -> "Gov";
            case MAYOR -> "Pref";
            case CITIZEN -> "Civil";
            default -> "Imig";
        };
    }

    private static String nationChatColor(Nation n) {
        if (n == null) return ChatColor.DARK_GRAY.toString();
        return switch (n.getId().toLowerCase()) {
            case "brasil" -> ChatColor.GREEN.toString();
            case "eua" -> ChatColor.BLUE.toString();
            case "japao" -> ChatColor.WHITE.toString();
            case "russia" -> ChatColor.RED.toString();
            default -> ChatColor.GRAY.toString();
        };
    }

    private static NamedTextColor nationNamedColor(Nation n) {
        if (n == null) return NamedTextColor.DARK_GRAY;
        return switch (n.getId().toLowerCase()) {
            case "brasil" -> NamedTextColor.GREEN;
            case "eua" -> NamedTextColor.BLUE;
            case "japao" -> NamedTextColor.WHITE;
            case "russia" -> NamedTextColor.RED;
            default -> NamedTextColor.GRAY;
        };
    }

    private static String roleChatColor(RoleType r) {
        if (r == null) return ChatColor.DARK_GRAY.toString();
        return switch (r) {
            case PRESIDENT -> ChatColor.GOLD.toString();
            case GOVERNOR -> ChatColor.YELLOW.toString();
            case MAYOR -> ChatColor.LIGHT_PURPLE.toString();
            case CITIZEN -> ChatColor.GRAY.toString();
            default -> ChatColor.DARK_GRAY.toString();
        };
    }
}
