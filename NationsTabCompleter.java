package com.nationswaw.commands;

import com.nationswaw.NationsWaW;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class NationsTabCompleter implements TabCompleter {
    private final NationsWaW plugin;

    private static final List<String> NATION_SUBS = List.of(
            "info", "claim", "unclaim", "cidade", "city", "tregua", "truce", "global", "loja", "entrar", "join", "aceitar", "recusar",
            "expulsar", "kick", "aliado", "emitir", "dividas", "debts",
            "depositar", "sacar", "help"
    );
    private static final List<String> NATIONS = List.of("brasil", "eua", "japao", "russia");
    private static final List<String> MOEDA_SUBS = List.of("converter", "saldo", "info");
    private static final List<String> ECO_SUBS = List.of("status");
    private static final List<String> ADMIN_SUBS = List.of("reload", "salary", "setrole", "give");
    private static final List<String> DEPOSIT_TARGETS = List.of("tesouro", "bc", "bancocentral");

    public NationsTabCompleter(NationsWaW plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();

        if (cmd.equals("nation") || cmd.equals("nacao") || cmd.equals("n")) {
            if (args.length == 1) {
                StringUtil.copyPartialMatches(args[0], NATION_SUBS, out);
            } else if (args.length == 2) {
                String sub = args[0].toLowerCase(Locale.ROOT);
                if (sub.equals("entrar") || sub.equals("join") || sub.equals("aliado")) {
                    StringUtil.copyPartialMatches(args[1], NATIONS, out);
                } else if (sub.equals("aceitar") || sub.equals("recusar") || sub.equals("expulsar") || sub.equals("kick")) {
                    onlineNames(args[1], out);
                } else if (sub.equals("emitir")) {
                    out.add("<valor>");
                } else if (sub.equals("depositar") || sub.equals("sacar")) {
                    StringUtil.copyPartialMatches(args[1], DEPOSIT_TARGETS, out);
                }
            } else if (args.length == 3) {
                String sub = args[0].toLowerCase(Locale.ROOT);
                if (sub.equals("aceitar") || sub.equals("recusar")) {
                    if (args[1].equalsIgnoreCase("entrar")) onlineNames(args[2], out);
                    else if (args[1].equalsIgnoreCase("aliado")) StringUtil.copyPartialMatches(args[2], NATIONS, out);
                } else if (sub.equals("depositar") || sub.equals("sacar")) {
                    out.add("<valor>");
                }
            }
        } else if (cmd.equals("moeda") || cmd.equals("cambio")) {
            if (args.length == 1) StringUtil.copyPartialMatches(args[0], MOEDA_SUBS, out);
            else if (args.length == 2 && args[0].equalsIgnoreCase("converter"))
                StringUtil.copyPartialMatches(args[1], List.of("BRL", "USD", "JPY", "RUB", "brasil", "eua", "japao", "russia"), out);
            else if (args.length == 4 && args[0].equalsIgnoreCase("converter"))
                StringUtil.copyPartialMatches(args[3], List.of("BRL", "USD", "JPY", "RUB", "brasil", "eua", "japao", "russia"), out);
        } else if (cmd.equals("economia") || cmd.equals("eco")) {
            if (args.length == 1) StringUtil.copyPartialMatches(args[0], ECO_SUBS, out);
        } else if (cmd.equals("shop")) {
            if (args.length == 1) StringUtil.copyPartialMatches(args[0], NATIONS, out);
        } else if (cmd.equals("claim")) {
            if (args.length == 1) StringUtil.copyPartialMatches(args[0], List.of("info", "unclaim"), out);
        } else if (cmd.equals("admin") || cmd.equals("nwadmin")) {
            if (args.length == 1) StringUtil.copyPartialMatches(args[0], ADMIN_SUBS, out);
        } else if (cmd.equals("guerra") || cmd.equals("votar")) {
            if (args.length == 1) StringUtil.copyPartialMatches(args[0], NATIONS, out);
        }

        Collections.sort(out);
        return out;
    }

    private void onlineNames(String partial, List<String> out) {
        List<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
        StringUtil.copyPartialMatches(partial, names, out);
    }
}
