package com.nationswaw.listeners;

import com.nationswaw.NationsWaW;
import com.nationswaw.gui.Theme;
import com.nationswaw.model.Nation;
import com.nationswaw.model.RoleType;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;
import java.util.Set;

/**
 * Chat restrito por nacao + bloqueio de mensagens privadas.
 */
public class ChatListener implements Listener {
    private final NationsWaW plugin;
    private static final Set<String> BLOCKED_PM = Set.of(
            "msg", "tell", "w", "whisper", "r", "reply", "m", "pm", "message", "dm"
    );

    public ChatListener(NationsWaW plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        // Confirmacao de unclaim em massa: deixa o ClaimManager tratar
        if (plugin.getClaimManager().isPendingMassUnclaim(e.getPlayer().getUniqueId())) return;
        if (e.isCancelled()) return;
        e.setCancelled(true);

        Player sender = e.getPlayer();
        String plain = PlainTextComponentSerializer.plainText().serialize(e.message());
        Nation n = plugin.getNationManager().getNationByPlayer(sender.getUniqueId());
        RoleType role = plugin.getRoleManager().getRole(sender.getUniqueId());

        final String formatted;
        final boolean immigrant = (n == null);

        if (immigrant) {
            // Imigrantes: visivel para TODOS (nacoes + imigrantes)
            formatted = "<gray>[Imigrante]</gray> <white>" + sender.getName()
                    + "</white><gray>:</gray> <gray>" + plain + "</gray>";
            var msg = Theme.MM.deserialize(formatted);
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.sendMessage(msg);
            }
            return;
        }

        String roleTag = role == null || role == RoleType.NONE ? "Civil" : role.getDisplayName();
        formatted = "<gray>[</gray><gold>" + n.getDisplayName() + "</gold><gray>]</gray> "
                + "<yellow>[" + roleTag + "]</yellow> "
                + "<white>" + sender.getName() + "</white><gray>:</gray> <white>" + plain + "</white>";
        var msg = Theme.MM.deserialize(formatted);
        String nationId = n.getId();
        for (Player online : Bukkit.getOnlinePlayers()) {
            Nation on = plugin.getNationManager().getNationByPlayer(online.getUniqueId());
            if (on != null && on.getId().equalsIgnoreCase(nationId)) {
                online.sendMessage(msg);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        String msg = e.getMessage();
        if (msg == null || msg.length() < 2) return;
        String without = msg.substring(1).trim();
        int sp = without.indexOf(' ');
        String cmd = (sp < 0 ? without : without.substring(0, sp)).toLowerCase(Locale.ROOT);
        // strip plugin prefix
        if (cmd.contains(":")) cmd = cmd.substring(cmd.indexOf(':') + 1);
        if (BLOCKED_PM.contains(cmd)) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(Theme.MM.deserialize(
                    "<red>[X] Mensagens privadas estao desativadas neste servidor. "
                            + "Use /alto-falante para falar com todos.</red>"));
        }
    }
}
