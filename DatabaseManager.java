package com.nationswaw.utils;

import com.nationswaw.NationsWaW;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

public class DatabaseManager {
    private final NationsWaW plugin;
    private HikariDataSource dataSource;
    private File dbFile;

    public DatabaseManager(NationsWaW plugin) { this.plugin = plugin; }

    public boolean initialize() {
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            dbFile = new File(plugin.getDataFolder(), "nationswaw.db");
            openPool();
            createTables();
            enableWal();
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("DB init failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void openPool() {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        cfg.setDriverClassName("org.sqlite.JDBC");
        cfg.setMaximumPoolSize(8);
        cfg.setPoolName("NationsWaW-Pool");
        cfg.setConnectionTestQuery("SELECT 1");
        cfg.setMaxLifetime(60000);
        cfg.setConnectionTimeout(10000);
        cfg.addDataSourceProperty("journal_mode", "WAL");
        cfg.addDataSourceProperty("synchronous", "NORMAL");
        cfg.addDataSourceProperty("busy_timeout", "10000");
        dataSource = new HikariDataSource(cfg);
    }

    private void enableWal() {
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL;");
            st.execute("PRAGMA synchronous=NORMAL;");
            st.execute("PRAGMA busy_timeout=10000;");
            st.execute("PRAGMA foreign_keys=ON;");
            plugin.getLogger().info("SQLite WAL mode ativo (anti-corrupcao).");
        } catch (Exception e) {
            plugin.getLogger().warning("WAL pragma: " + e.getMessage());
        }
    }

    private void createTables() throws SQLException {
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS nations (id TEXT PRIMARY KEY, president TEXT, treasury REAL DEFAULT 5000000, central_bank_balance REAL DEFAULT 0)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS players (uuid TEXT PRIMARY KEY, nation_id TEXT, role TEXT DEFAULT 'NONE', daily_exchange_volume REAL DEFAULT 0, last_exchange_reset_day INTEGER DEFAULT 0)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS balances (uuid TEXT NOT NULL, currency_nation_id TEXT NOT NULL, balance REAL DEFAULT 0, PRIMARY KEY (uuid, currency_nation_id))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS nation_roles (nation_id TEXT NOT NULL, uuid TEXT NOT NULL, role TEXT NOT NULL, PRIMARY KEY (nation_id, uuid))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS diplomacy (nation_a TEXT NOT NULL, nation_b TEXT NOT NULL, relation TEXT DEFAULT 'NEUTRAL', PRIMARY KEY (nation_a, nation_b))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS exchange_rates (from_nation TEXT NOT NULL, to_nation TEXT NOT NULL, rate REAL NOT NULL, PRIMARY KEY (from_nation, to_nation))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS exchange_history (id INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT, from_nation TEXT, to_nation TEXT, amount_from REAL, amount_to REAL, spread REAL, iof REAL, rate REAL, timestamp INTEGER)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS claims (chunk_key TEXT PRIMARY KEY, nation_id TEXT NOT NULL)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS map_cooldown (uuid TEXT PRIMARY KEY, last_map_ms INTEGER NOT NULL)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS ledger (id INTEGER PRIMARY KEY AUTOINCREMENT, nation_id TEXT, type TEXT, amount REAL, actor TEXT, description TEXT, timestamp INTEGER)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS votes (id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT, proposer_nation TEXT, target_nation TEXT, start_time INTEGER, end_time INTEGER, status TEXT DEFAULT 'ACTIVE')");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS vote_ballots (vote_id INTEGER, uuid TEXT, choice TEXT, PRIMARY KEY (vote_id, uuid))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS infra_milestones (nation_id TEXT NOT NULL, type TEXT NOT NULL, key TEXT NOT NULL, claimed INTEGER DEFAULT 0, PRIMARY KEY (nation_id, type, key))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS city_mission (nation_id TEXT PRIMARY KEY, completed INTEGER DEFAULT 0, bonus REAL DEFAULT 0)");
        }
    }

    public synchronized Connection getConnection() throws SQLException {
        try {
            if (dataSource == null || dataSource.isClosed()) {
                plugin.getLogger().warning("Pool fechado — reconectando...");
                openPool();
                enableWal();
            }
            return dataSource.getConnection();
        } catch (SQLException e) {
            plugin.getLogger().warning("Falha de conexao: " + e.getMessage() + " — tentando reconectar...");
            try {
                if (dataSource != null && !dataSource.isClosed()) dataSource.close();
            } catch (Exception ignored) {}
            openPool();
            enableWal();
            return dataSource.getConnection();
        }
    }

    public <T> CompletableFuture<T> queryAsync(Function<Connection, T> fn) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = getConnection()) {
                return fn.apply(c);
            } catch (Exception e) {
                plugin.getLogger().warning("queryAsync: " + e.getMessage());
                return null;
            }
        });
    }

    public CompletableFuture<Void> executeAsync(Consumer<Connection> fn) {
        return CompletableFuture.runAsync(() -> {
            try (Connection c = getConnection()) {
                fn.accept(c);
            } catch (Exception e) {
                plugin.getLogger().warning("executeAsync: " + e.getMessage());
            }
        });
    }

    /** Execucao sincrona para onDisable */
    public void executeSync(Consumer<Connection> fn) {
        try (Connection c = getConnection()) {
            fn.accept(c);
        } catch (Exception e) {
            plugin.getLogger().severe("executeSync: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            if (dataSource != null && !dataSource.isClosed()) dataSource.close();
        } catch (Exception e) {
            plugin.getLogger().warning("DB close: " + e.getMessage());
        }
    }
}
