package com.nationswaw.commands;

import com.nationswaw.NationsWaW;
import com.nationswaw.gui.Theme;
import com.nationswaw.model.Nation;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AltoFalanteCommand implements CommandExecutor {
    private final NationsWaW plugin;
    private static final double COST = 250.0;
    private static final long COOLDOWN_MS = 120_000L;
    private final Map<UUID, Long> cooldownUntil = new ConcurrentHashMap<>();

    public AltoFalanteCommand(NationsWaW plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (args.length < 1) {
            p.sendMessage(Theme.MM.deserialize(
                    "<yellow>/alto-falante [mensagem]</yellow> <gray>(custo 250, cooldown 2 min)</gray>"));
            return true;
        }
        Nation n = plugin.getNationManager().getNationByPlayer(p.getUniqueId());
        if (n == null) {
            p.sendMessage(Theme.MM.deserialize("<red>Precisa pertencer a uma nacao para usar o alto-falante.</red>"));
            return true;
        }
        long now = System.currentTimeMillis();
        Long until = cooldownUntil.get(p.getUniqueId());
        if (until != null && until > now) {
            long sec = (until - now + 999) / 1000;
            p.sendMessage(Theme.MM.deserialize(
                    "<red>[X] Aguarde mais " + sec + "s para usar o alto-falante.</red>"));
            return true;
        }
        if (!plugin.getEconomyManager().removeBalance(p.getUniqueId(), n.getId(), COST)) {
            p.sendMessage(Theme.MM.deserialize(
                    "<red>[X] Saldo insuficiente. Custo: " + n.getCurrencySymbol() + " " + Theme.fmt(COST) + "</red>"));
            return true;
        }
        cooldownUntil.put(p.getUniqueId(), now + COOLDOWN_MS);
        String msg = String.join(" ", args);
        String line = "<aqua>[</aqua><white>" + p.getName() + "</white><aqua>] Disse:</aqua> <aqua>" + msg + "</aqua>";
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(Theme.MM.deserialize(line));
        }
        return true;
    }
}
