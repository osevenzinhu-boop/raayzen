package com.nationswaw.systems;

import com.nationswaw.NationsWaW;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite de saque do Banco Central: 100.000 por janela movel de 6 horas.
 * O timer comeca no primeiro saque da janela.
 */
public class WithdrawLimitManager {
    private final NationsWaW plugin;
    private static final double LIMIT = 100_000.0;
    private static final long WINDOW_MS = 6L * 60 * 60 * 1000; // 6h

    private static class Withdrawal {
        final long time;
        final double amount;
        Withdrawal(long time, double amount) { this.time = time; this.amount = amount; }
    }

    // nationId -> lista de saques na janela
    private final Map<String, List<Withdrawal>> history = new ConcurrentHashMap<>();

    public WithdrawLimitManager(NationsWaW plugin) { this.plugin = plugin; }

    private void prune(String nationId) {
        List<Withdrawal> list = history.computeIfAbsent(nationId, k -> new ArrayList<>());
        long now = System.currentTimeMillis();
        list.removeIf(w -> now - w.time > WINDOW_MS);
    }

    public double getWithdrawnInWindow(String nationId) {
        prune(nationId);
        return history.getOrDefault(nationId, List.of()).stream().mapToDouble(w -> w.amount).sum();
    }

    public double getRemaining(String nationId) {
        return Math.max(0, LIMIT - getWithdrawnInWindow(nationId));
    }

    /** ms restantes ate o saque mais antigo sair da janela (0 se vazio) */
    public long getMsUntilReset(String nationId) {
        prune(nationId);
        List<Withdrawal> list = history.get(nationId);
        if (list == null || list.isEmpty()) return 0;
        long oldest = list.stream().mapToLong(w -> w.time).min().orElse(System.currentTimeMillis());
        long resetAt = oldest + WINDOW_MS;
        return Math.max(0, resetAt - System.currentTimeMillis());
    }

    public boolean canWithdraw(String nationId, double amount) {
        return amount > 0 && amount <= getRemaining(nationId);
    }

    public void record(String nationId, double amount) {
        prune(nationId);
        history.computeIfAbsent(nationId, k -> new ArrayList<>())
                .add(new Withdrawal(System.currentTimeMillis(), amount));
    }

    public static double getLimit() { return LIMIT; }

    public String formatRemainingTime(String nationId) {
        long ms = getMsUntilReset(nationId);
        if (ms <= 0) return "disponível agora";
        long h = ms / 3_600_000;
        long m = (ms % 3_600_000) / 60_000;
        return h + "h " + m + "min";
    }
}
