package com.nationswaw;

import com.nationswaw.commands.*;
import com.nationswaw.gui.*;
import com.nationswaw.listeners.JoinListener;
import com.nationswaw.listeners.PvPListener;
import com.nationswaw.listeners.TerritoryListener;
import com.nationswaw.systems.*;
import com.nationswaw.utils.ConfigManager;
import com.nationswaw.utils.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class NationsWaW extends JavaPlugin {
    private static NationsWaW instance;
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private NationManager nationManager;
    private RoleManager roleManager;
    private EconomyManager economyManager;
    private ExchangeManager exchangeManager;
    private DiplomacyManager diplomacyManager;
    private VotingManager votingManager;
    private TradeManager tradeManager;
    private ClaimManager claimManager;
    private TreasuryManager treasuryManager;
    private EmbargoManager embargoManager;
    private ShopManager shopManager;
    private PendingManager pendingManager;
    private TreasuryImpactManager treasuryImpactManager;
    private SalaryManager salaryManager;
    private WithdrawLimitManager withdrawLimitManager;
    private JobManager jobManager;
    private DebtManager debtManager;
    private DisplayManager displayManager;
    private InfrastructureMissionManager infrastructureMissionManager;
    private CityTeleportManager cityTeleportManager;
    private CacheService cacheService;
    private WarManager warManager;
    private MarketManager marketManager;
    private MonopolyManager monopolyManager;
    private JoinRequestManager joinRequestManager;
    private TerritoryMapManager territoryMapManager;
    private ExchangeGUI exchangeGUI;
    private NationGUI nationGUI;
    private DiplomacyGUI diplomacyGUI;
    private TreasuryGUI treasuryGUI;
    private ShopGUI shopGUI;
    private CarteiraGUI carteiraGUI;
    private WarVoteGUI warVoteGUI;
    private JobsGUI jobsGUI;
    private DebtGUI debtGUI;
    private InfrastructureGUI infrastructureGUI;
    private JoinNationGUI joinNationGUI;
    private VotesGUI votesGUI;
    private MonopolyGUI monopolyGUI;
    private TopGUI topGUI;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        configManager = new ConfigManager(this);
        configManager.load();
        databaseManager = new DatabaseManager(this);
        if (!databaseManager.initialize()) {
            getLogger().severe("Falha no banco de dados!");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        nationManager = new NationManager(this);
        roleManager = new RoleManager(this);
        economyManager = new EconomyManager(this);
        exchangeManager = new ExchangeManager(this);
        diplomacyManager = new DiplomacyManager(this);
        votingManager = new VotingManager(this);
        tradeManager = new TradeManager(this);
        claimManager = new ClaimManager(this);
        treasuryManager = new TreasuryManager(this);
        embargoManager = new EmbargoManager(this);
        shopManager = new ShopManager(this);
        pendingManager = new PendingManager(this);

        nationManager.loadNations();
        roleManager.loadRoles();
        economyManager.loadBalances();
        exchangeManager.loadRates();
        diplomacyManager.loadRelations();
        claimManager.loadClaims();

        treasuryImpactManager = new TreasuryImpactManager(this);
        treasuryImpactManager.recalculateAllRates();
        salaryManager = new SalaryManager(this);
        withdrawLimitManager = new WithdrawLimitManager(this);
        jobManager = new JobManager(this);
        debtManager = new DebtManager(this);
        displayManager = new DisplayManager(this);
        infrastructureMissionManager = new InfrastructureMissionManager(this);
        cacheService = new CacheService();
        warManager = new WarManager(this);
        marketManager = new MarketManager(this);
        monopolyManager = new MonopolyManager(this);
        joinRequestManager = new JoinRequestManager(this);
        cityTeleportManager = new CityTeleportManager(this);
        territoryMapManager = new TerritoryMapManager(this);
        
        exchangeGUI = new ExchangeGUI(this);
        nationGUI = new NationGUI(this);
        diplomacyGUI = new DiplomacyGUI(this);
        treasuryGUI = new TreasuryGUI(this);
        shopGUI = new ShopGUI(this);
        carteiraGUI = new CarteiraGUI(this);
        warVoteGUI = new WarVoteGUI(this);
        jobsGUI = new JobsGUI(this);
        debtGUI = new DebtGUI(this);
        infrastructureGUI = new InfrastructureGUI(this);
        joinNationGUI = new JoinNationGUI(this);
        votesGUI = new VotesGUI(this);
        monopolyGUI = new MonopolyGUI(this);
        topGUI = new TopGUI(this);

        getCommand("nacao").setExecutor(new NacaoCommand(this));
        getCommand("nation").setExecutor(new NationCommand(this));
        getCommand("carteira").setExecutor(new CarteiraCommand(this));
        getCommand("cargo").setExecutor(new CargoCommand(this));
        getCommand("diplomacia").setExecutor(new DiplomaciaCommand(this));
        getCommand("guerra").setExecutor(new GuerraCommand(this));
        getCommand("votar").setExecutor(new VotarCommand(this));
        getCommand("moeda").setExecutor(new MoedaCommand(this));
        getCommand("cambio").setExecutor(new CambioCommand(this));
        getCommand("tesouro").setExecutor(new TesouroCommand(this));
        getCommand("shop").setExecutor(new ShopCommand(this));
        getCommand("vender").setExecutor(new VenderCommand(this));
        getCommand("confirmar").setExecutor(new ConfirmarCommand(this));
        getCommand("cancelar").setExecutor(new CancelarCommand(this));
        getCommand("admin").setExecutor(new AdminCommand(this));
        getCommand("economia").setExecutor(new EconomiaCommand(this));
        if (getCommand("mapa") != null) getCommand("mapa").setExecutor(new MapaCommand(this));
        if (getCommand("claim") != null) getCommand("claim").setExecutor(new ClaimCommand(this));
        if (getCommand("alto-falante") != null) getCommand("alto-falante").setExecutor(new AltoFalanteCommand(this));
        if (getCommand("mercado") != null) getCommand("mercado").setExecutor(new MercadoCommand(this));
        if (getCommand("top") != null) getCommand("top").setExecutor(new TopCommand(this));

        NationsTabCompleter tabs = new NationsTabCompleter(this);
        String[] tabCmds = {"nation", "nacao", "n", "moeda", "cambio", "economia", "eco", "shop",
                "claim", "admin", "guerra", "votar", "carteira", "cargo", "diplomacia", "tesouro", "mapa"};
        for (String c : tabCmds) {
            if (getCommand(c) != null) getCommand(c).setTabCompleter(tabs);
        }


        Bukkit.getPluginManager().registerEvents(new JoinListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.nationswaw.listeners.ChatListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PvPListener(this), this);
        Bukkit.getPluginManager().registerEvents(new TerritoryListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.nationswaw.listeners.QuitListener(this), this);

        Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::saveAll, 20L * 60 * 5, 20L * 60 * 5);

        getLogger().info("========================================");
        getLogger().info("  NationsWaW v2.2.5");
        getLogger().info("  Tesouro 2.5M + BC 2.5M | Carteira");
        getLogger().info("  Cambio so chat | Impacto tesouro");
        getLogger().info("========================================");
    }

    @Override
    public void onDisable() {
        getLogger().info("Salvando dados (sync) antes de desligar...");
        try {
            saveAll();
            if (infrastructureMissionManager != null) infrastructureMissionManager.saveSync();
            // flush WAL
            if (databaseManager != null) {
                databaseManager.executeSync(conn -> {
                    try (var st = conn.createStatement()) {
                        st.execute("PRAGMA wal_checkpoint(FULL);");
                    } catch (Exception ex) {
                        getLogger().warning("wal_checkpoint: " + ex.getMessage());
                    }
                });
                databaseManager.close();
            }
        } catch (Exception e) {
            getLogger().severe("onDisable save: " + e.getMessage());
            e.printStackTrace();
        }
        getLogger().info("NationsWaW desabilitado.");
    }

    public void saveAll() {
        try {
            if (nationManager != null) nationManager.saveAll();
            if (roleManager != null) roleManager.saveAll();
            if (economyManager != null) economyManager.saveAll();
            if (exchangeManager != null) exchangeManager.saveRates();
            if (claimManager != null) claimManager.saveClaims();
            if (infrastructureMissionManager != null) infrastructureMissionManager.save();
        } catch (Exception e) {
            getLogger().severe("saveAll falhou: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static NationsWaW getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public NationManager getNationManager() { return nationManager; }
    public RoleManager getRoleManager() { return roleManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public ExchangeManager getExchangeManager() { return exchangeManager; }
    public DiplomacyManager getDiplomacyManager() { return diplomacyManager; }
    public VotingManager getVotingManager() { return votingManager; }
    public TradeManager getTradeManager() { return tradeManager; }
    public ClaimManager getClaimManager() { return claimManager; }
    public TreasuryManager getTreasuryManager() { return treasuryManager; }
    public EmbargoManager getEmbargoManager() { return embargoManager; }
    public ShopManager getShopManager() { return shopManager; }
    public PendingManager getPendingManager() { return pendingManager; }
    public TreasuryImpactManager getTreasuryImpactManager() { return treasuryImpactManager; }
    public SalaryManager getSalaryManager() { return salaryManager; }
    public WithdrawLimitManager getWithdrawLimitManager() { return withdrawLimitManager; }
    public ExchangeGUI getExchangeGUI() { return exchangeGUI; }
    public NationGUI getNationGUI() { return nationGUI; }
    public DiplomacyGUI getDiplomacyGUI() { return diplomacyGUI; }
    public TreasuryGUI getTreasuryGUI() { return treasuryGUI; }
    public ShopGUI getShopGUI() { return shopGUI; }
    public CarteiraGUI getCarteiraGUI() { return carteiraGUI; }
    public WarVoteGUI getWarVoteGUI() { return warVoteGUI; }
    public JobManager getJobManager() { return jobManager; }
    public DebtManager getDebtManager() { return debtManager; }
    public DisplayManager getDisplayManager() { return displayManager; }
    public InfrastructureMissionManager getInfrastructureMissionManager() { return infrastructureMissionManager; }
    public InfrastructureGUI getInfrastructureGUI() { return infrastructureGUI; }
    public CityTeleportManager getCityTeleportManager() { return cityTeleportManager; }
    public JoinRequestManager getJoinRequestManager() { return joinRequestManager; }
    public JoinNationGUI getJoinNationGUI() { return joinNationGUI; }
    public VotesGUI getVotesGUI() { return votesGUI; }
    public CacheService getCacheService() { return cacheService; }
    public WarManager getWarManager() { return warManager; }
    public MarketManager getMarketManager() { return marketManager; }
    public MonopolyManager getMonopolyManager() { return monopolyManager; }
    public MonopolyGUI getMonopolyGUI() { return monopolyGUI; }
    public TopGUI getTopGUI() { return topGUI; }
    public DebtGUI getDebtGUI() { return debtGUI; }
    public JobsGUI getJobsGUI() { return jobsGUI; }
    public TerritoryMapManager getTerritoryMapManager() { return territoryMapManager; }
}
