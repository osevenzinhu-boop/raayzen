package com.nationswaw.systems;

import com.nationswaw.NationsWaW;
import com.nationswaw.gui.Theme;
import com.nationswaw.model.Nation;
import com.nationswaw.model.RoleType;
import com.nationswaw.utils.Ledger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Salarios politicos com tempo ONLINE (pausa ao deslogar).
 *
 * Presidente: 3h online | 50.000 | debita SO do BC (sem fallback Tesouro)
 * Governador: 1h30m online | 20.000 | dinheiro sintetizado (nao debita nacao)
 * Prefeito: 45min online | 7.500 | dinheiro sintetizado
 *
 * Impostos: 10% ISSP -> Tesouro | 5% ISDN -> BC | liquido 85% ao jogador
 */
public class SalaryManager implements Listener {
    private final NationsWaW plugin;
    private final Ledger ledger;

    public static final double PRES_SALARY = 50_000;
    public static final double GOV_SALARY = 20_000;
    public static final double MAYOR_SALARY = 7_500;

    private static final double ISSP = 0.10;
    private static final double ISDN = 0.05;

    /** Intervalos em ms de tempo ONLINE */
    public static final long PRES_INTERVAL_MS = 3L * 60 * 60 * 1000;      // 3h
    public static final long GOV_INTERVAL_MS = 90L * 60 * 1000;             // 1h30m
    public static final long MAYOR_INTERVAL_MS = 45L * 60 * 1000;           // 45min

    /** uuid -> ms online acumulados desde o ultimo pagamento (ou desde o login) */
    private final Map<UUID, Long> onlineAccumulated = new ConcurrentHashMap<>();
    /** uuid -> timestamp do inicio da sessao atual (0 se offline) */
    private final Map<UUID, Long> sessionStart = new ConcurrentHashMap<>();

    public SalaryManager(NationsWaW plugin) {
        this.plugin = plugin;
        this.ledger = new Ledger(plugin);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        // Tick a cada 30s — leve, so jogadores online
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickOnline, 20L * 30, 20L * 30);
        plugin.getLogger().info("SalaryManager: Pres 3h (BC), Gov 1h30 (sint), Pref 45m (sint) — tempo online.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        sessionStart.put(u, System.currentTimeMillis());
        onlineAccumulated.putIfAbsent(u, 0L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        flushSession(u);
        sessionStart.remove(u);
    }

    /** Soma o tempo da sessao atual no acumulado e reinicia o marcador. */
    private void flushSession(UUID u) {
        Long start = sessionStart.get(u);
        if (start == null) return;
        long now = System.currentTimeMillis();
        long delta = Math.max(0, now - start);
        onlineAccumulated.merge(u, delta, Long::sum);
        sessionStart.put(u, now);
    }

    private long currentOnlineMs(UUID u) {
        long acc = onlineAccumulated.getOrDefault(u, 0L);
        Long start = sessionStart.get(u);
        if (start != null) {
            acc += Math.max(0, System.currentTimeMillis() - start);
        }
        return acc;
    }

    private void tickOnline() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID u = p.getUniqueId();
            if (!sessionStart.containsKey(u)) {
                sessionStart.put(u, System.currentTimeMillis());
                onlineAccumulated.putIfAbsent(u, 0L);
            }
            RoleType role = plugin.getRoleManager().getRole(u);
            if (role == null) continue;
            Nation n = plugin.getNationManager().getNationByPlayer(u);
            if (n == null) continue;

            long need;
            double gross;
            boolean fromBc;
            String roleName;
            switch (role) {
                case PRESIDENT -> {
                    need = PRES_INTERVAL_MS;
                    gross = PRES_SALARY;
                    fromBc = true;
                    roleName = "Presidente";
                }
                case GOVERNOR -> {
                    need = GOV_INTERVAL_MS;
                    gross = GOV_SALARY;
                    fromBc = false;
                    roleName = "Governador";
                }
                case MAYOR -> {
                    need = MAYOR_INTERVAL_MS;
                    gross = MAYOR_SALARY;
                    fromBc = false;
                    roleName = "Prefeito";
                }
                default -> {
                    continue;
                }
            }

            long online = currentOnlineMs(u);
            if (online < need) continue;

            // Consome exatamente 1 intervalo (permite multiplos se ficar muito tempo)
            while (currentOnlineMs(u) >= need) {
                if (!payOne(p, n, roleName, gross, fromBc, need)) {
                    break; // falhou (ex: BC sem saldo) — nao consome tempo
                }
                // debita o intervalo do acumulado
                flushSession(u);
                onlineAccumulated.put(u, Math.max(0, onlineAccumulated.getOrDefault(u, 0L) - need));
            }
        }
    }

    /**
     * @return true se pagou; false se nao pagou (ex: BC insuficiente para presidente)
     */
    private boolean payOne(Player p, Nation n, String roleName, double gross, boolean fromBc, long intervalMs) {
        double issp = gross * ISSP;
        double isdn = gross * ISDN;
        double net = gross - issp - isdn;

        if (fromBc) {
            // Presidente: debita bruto inteiro do BC. Sem fallback Tesouro.
            if (n.getCentralBankBalance() + 0.001 < gross) {
                p.sendMessage(Theme.MM.deserialize(plugin.getConfigManager().getPrefix()
                        + "<red>Salário de " + roleName + " nao pago: Banco Central insuficiente "
                        + "(" + n.getCurrencySymbol() + " " + Theme.fmt(n.getCentralBankBalance())
                        + " / " + Theme.fmt(gross) + ").</red>"));
                return false;
            }
            n.removeFromCentralBank(gross);
            // ISSP vai ao Tesouro; ISDN volta ao BC
            n.addTreasury(issp);
            n.addToCentralBank(isdn);
        } else {
            // Governador/Prefeito: dinheiro sintetizado — so credita impostos nas contas
            n.addTreasury(issp);
            n.addToCentralBank(isdn);
        }

        plugin.getEconomyManager().addBalance(p.getUniqueId(), n.getId(), net);
        ledger.record(n.getId(), "POL_SALARY_" + roleName, net, p.getUniqueId(), roleName);
        try {
            plugin.getNationManager().saveAll();
        } catch (Exception ignored) {}

        long nextH = intervalMs / 3_600_000;
        long nextM = (intervalMs % 3_600_000) / 60_000;
        String nextStr = nextH > 0
                ? (nextM > 0 ? nextH + "h" + nextM + "m" : nextH + "h")
                : nextM + "min";

        p.sendMessage(Theme.MM.deserialize(plugin.getConfigManager().getPrefix()
                + "<gold>[◆] Salário de " + roleName + "</gold> <green>+"
                + n.getCurrencySymbol() + " " + Theme.fmt(net) + "</green> "
                + "<gray>(bruto " + Theme.fmt(gross)
                + " | ISSP 10% + ISDN 5%)</gray>\n"
                + "<gray>Proximo pagamento em " + nextStr + " online.</gray>"));
        return true;
    }

    public void forcePay() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            sessionStart.put(p.getUniqueId(), System.currentTimeMillis());
            // forca acumulado alto para test
            RoleType role = plugin.getRoleManager().getRole(p.getUniqueId());
            if (role == RoleType.PRESIDENT) onlineAccumulated.put(p.getUniqueId(), PRES_INTERVAL_MS);
            else if (role == RoleType.GOVERNOR) onlineAccumulated.put(p.getUniqueId(), GOV_INTERVAL_MS);
            else if (role == RoleType.MAYOR) onlineAccumulated.put(p.getUniqueId(), MAYOR_INTERVAL_MS);
        }
        tickOnline();
    }

    public static String intervalLabel(RoleType role) {
        if (role == null) return "-";
        return switch (role) {
            case PRESIDENT -> "3h online (BC)";
            case GOVERNOR -> "1h30m online (gerado)";
            case MAYOR -> "45min online (gerado)";
            default -> "-";
        };
    }
}
