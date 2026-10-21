package com.nationswaw.systems;

import com.nationswaw.NationsWaW;
import com.nationswaw.model.ExchangeResult;
import com.nationswaw.model.Nation;
import com.nationswaw.model.PlayerData;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cambio com paridade inicial 1:1.
 * Spread some; IOF vai para Banco Central da moeda de ORIGEM.
 * Taxas dinamicas baseadas em volume de conversao.
 */
public class ExchangeManager {
    private final NationsWaW plugin;
    // rate[from][to] = quantas unidades de 'to' por 1 de 'from'. Inicial = 1.0
    private final Map<String, Map<String, Double>> rates = new ConcurrentHashMap<>();
    private final Map<String, Double> buyVolume = new ConcurrentHashMap<>(); // volume comprado da moeda

    public ExchangeManager(NationsWaW plugin) { this.plugin = plugin; }

    public void loadRates() {
        double initial = plugin.getConfigManager().getInitialRate();
        for (String from : plugin.getConfigManager().getNationIds()) {
            Map<String, Double> row = new ConcurrentHashMap<>();
            for (String to : plugin.getConfigManager().getNationIds()) {
                row.put(to, from.equals(to) ? 1.0 : initial);
            }
            rates.put(from, row);
            buyVolume.put(from, 0.0);
        }
        // Override from DB
        plugin.getDatabaseManager().queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM exchange_rates")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    String from = rs.getString("from_nation");
                    String to = rs.getString("to_nation");
                    double rate = rs.getDouble("rate");
                    rates.computeIfAbsent(from, k -> new ConcurrentHashMap<>()).put(to, rate);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Load rates: " + e.getMessage());
            }
            return null;
        }).join();
    }

    public void saveRates() {
        plugin.getDatabaseManager().executeAsync(conn -> {
            try {
                for (var fe : rates.entrySet()) {
                    for (var te : fe.getValue().entrySet()) {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "INSERT INTO exchange_rates (from_nation, to_nation, rate) VALUES (?,?,?) " +
                                "ON CONFLICT(from_nation, to_nation) DO UPDATE SET rate = excluded.rate")) {
                            ps.setString(1, fe.getKey());
                            ps.setString(2, te.getKey());
                            ps.setDouble(3, te.getValue());
                            ps.executeUpdate();
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Save rates: " + e.getMessage());
            }
        });
    }

        public double getRate(String from, String to) {
        if (from == null || to == null) return 1.0;
        if (from.equalsIgnoreCase(to)) return 1.0;
        String key = from.toLowerCase() + ":" + to.toLowerCase();
        try {
            Double cached = plugin.getCacheService().getExchangeRate(key, k -> {
                String[] parts = k.split(":", 2);
                return rates.getOrDefault(parts[0], java.util.Collections.emptyMap())
                        .getOrDefault(parts[1], 1.0);
            });
            return cached != null ? cached : 1.0;
        } catch (Exception e) {
            return rates.getOrDefault(from.toLowerCase(), java.util.Collections.emptyMap())
                    .getOrDefault(to.toLowerCase(), 1.0);
        }
    }

    
    public ExchangeResult convert(UUID playerUuid, String fromId, String toId, double amount) {
        if (amount < plugin.getConfigManager().getMinAmount())
            return ExchangeResult.fail("Valor abaixo do minimo.");
        if (amount > plugin.getConfigManager().getMaxAmount())
            return ExchangeResult.fail("Valor acima do maximo.");
        if (fromId.equalsIgnoreCase(toId))
            return ExchangeResult.fail("Moedas iguais.");

        Nation fromNation = plugin.getNationManager().getNation(fromId);
        Nation toNation = plugin.getNationManager().getNation(toId);
        if (fromNation == null || toNation == null)
            return ExchangeResult.fail("Nacao/moeda invalida.");

        PlayerData data = plugin.getRoleManager().getPlayerData(playerUuid);
        long today = System.currentTimeMillis() / 86_400_000L;
        if (data.getLastExchangeResetDay() != today) {
            data.setDailyExchangeVolume(0);
            data.setLastExchangeResetDay(today);
        }
        double dailyCap = plugin.getConfigManager().getDailyLimit();
        if (dailyCap > 0 && data.getDailyExchangeVolume() + amount > dailyCap)
            return ExchangeResult.fail("Limite diario de cambio excedido.");

        if (data.getBalance(fromId) < amount)
            return ExchangeResult.fail("Saldo insuficiente em " + fromNation.getCurrencyName() + ".");

        // ISSP 3% -> Tesouro da nação DESTINO | ISDN 2% -> BC da nação DESTINO
        double isspPct = plugin.getConfigManager().getIssp();
        double isdnPct = plugin.getConfigManager().getIsdn();
        double isspAmount = amount * isspPct; // na moeda de origem
        double isdnAmount = amount * isdnPct;
        double effective = amount - isspAmount - isdnAmount;
        if (effective <= 0) return ExchangeResult.fail("Valor efetivo inválido.");

        double rate = getRate(fromId, toId);
        double amountTo = effective * rate;

        if (!data.removeBalance(fromId, amount))
            return ExchangeResult.fail("Falha ao debitar.");
        data.addBalance(toId, amountTo);
        data.setDailyExchangeVolume(data.getDailyExchangeVolume() + amount);

        // Impostos creditados na nação de DESTINO (convertidos pela taxa)
        double isspDest = isspAmount * rate;
        double isdnDest = isdnAmount * rate;
        toNation.addTreasury(isspDest);
        toNation.addToCentralBank(isdnDest);

        buyVolume.merge(toId, amountTo, Double::sum);
        buyVolume.merge(fromId, -amount * 0.3, Double::sum);
        applyDynamicRates();

        plugin.getEconomyManager().savePlayerBalances(playerUuid);
        plugin.getNationManager().saveAll();
        logHistory(playerUuid, fromId, toId, amount, amountTo, isspAmount, isdnAmount, rate);

        return ExchangeResult.success(amount, amountTo, isspAmount, isdnAmount,
                fromNation.getCurrencyName(), toNation.getCurrencyName(), rate);
    }

    /** Ajusta taxas com base no volume de compra (oferta/demanda). */
    public void applyDynamicRates() {
        double min = plugin.getConfigManager().getMinRate();
        double max = plugin.getConfigManager().getMaxRate();
        for (String from : rates.keySet()) {
            for (String to : rates.get(from).keySet()) {
                if (from.equals(to)) continue;
                double volTo = buyVolume.getOrDefault(to, 0.0);
                double volFrom = buyVolume.getOrDefault(from, 0.0);
                // Mais demanda por 'to' -> rate from->to sobe (voce recebe mais 'to')
                double factor = 1.0 + (volTo - volFrom) / 5_000_000.0;
                factor = Math.max(0.5, Math.min(1.5, factor));
                double current = rates.get(from).get(to);
                double next = current * 0.95 + (plugin.getConfigManager().getInitialRate() * factor) * 0.05;
                next = Math.max(min, Math.min(max, next));
                rates.get(from).put(to, next);
                try { plugin.getCacheService().invalidateExchangeRates(); } catch (Exception ignored) {}
            }
        }
    }

    private void logHistory(UUID uuid, String from, String to, double af, double at, double sp, double iof, double rate) {
        plugin.getDatabaseManager().executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO exchange_history (uuid, from_nation, to_nation, amount_from, amount_to, spread, iof, rate, timestamp) VALUES (?,?,?,?,?,?,?,?,?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, from); ps.setString(3, to);
                ps.setDouble(4, af); ps.setDouble(5, at);
                ps.setDouble(6, sp); ps.setDouble(7, iof);
                ps.setDouble(8, rate); ps.setLong(9, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (Exception ignored) {}
        });
    }

    public Map<String, Map<String, Double>> getAllRates() { return rates; }

    public void setPairRate(String from, String to, double rate) {
        rates.computeIfAbsent(from.toLowerCase(), k -> new java.util.concurrent.ConcurrentHashMap<>())
                .put(to.toLowerCase(), rate);
    }

}
