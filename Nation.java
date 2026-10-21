package com.nationswaw.model;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Nation {
    private final String id;
    private String displayName;
    private String currencyName;
    private String currencySymbol;
    private String centralBankName;
    private String color;
    private String flagMaterial;
    private double treasury; // Tesouro Nacional (5M inicial)
    private double centralBankBalance; // recebe IOF
    private UUID president;
    private final Set<UUID> governors = new HashSet<>();
    private final Set<UUID> mayors = new HashSet<>();
    private final Set<UUID> citizens = new HashSet<>();

    public Nation(String id) { this.id = id; }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String v) { this.displayName = v; }
    public String getCurrencyName() { return currencyName; }
    public void setCurrencyName(String v) { this.currencyName = v; }
    public String getCurrencySymbol() { return currencySymbol; }
    public void setCurrencySymbol(String v) { this.currencySymbol = v; }
    public String getCentralBankName() { return centralBankName; }
    public void setCentralBankName(String v) { this.centralBankName = v; }
    public String getColor() { return color; }
    public void setColor(String v) { this.color = v; }
    public String getFlagMaterial() { return flagMaterial; }
    public void setFlagMaterial(String v) { this.flagMaterial = v; }
    public double getTreasury() { return treasury; }
    public void setTreasury(double v) { this.treasury = Math.max(0, v); }
    public void addTreasury(double v) { this.treasury = Math.max(0, this.treasury + v); }
    public boolean removeTreasury(double v) {
        if (treasury < v) return false;
        treasury -= v;
        return true;
    }
    public double getCentralBankBalance() { return centralBankBalance; }
    public void setCentralBankBalance(double v) { this.centralBankBalance = v; }
    public void addToCentralBank(double v) { this.centralBankBalance += v; }
    public boolean removeFromCentralBank(double v) {
        if (v <= 0 || centralBankBalance < v) return false;
        centralBankBalance -= v;
        return true;
    }
    public UUID getPresident() { return president; }
    public void setPresident(UUID v) { this.president = v; }
    public Set<UUID> getGovernors() { return governors; }
    public Set<UUID> getMayors() { return mayors; }
    public Set<UUID> getCitizens() { return citizens; }

    public boolean isCitizen(UUID uuid) {
        return citizens.contains(uuid) || isGovernor(uuid) || isMayor(uuid) || isPresident(uuid);
    }
    public boolean isPresident(UUID uuid) { return president != null && president.equals(uuid); }
    public boolean isGovernor(UUID uuid) { return governors.contains(uuid); }
    public boolean isMayor(UUID uuid) { return mayors.contains(uuid); }

    public RoleType getRole(UUID uuid) {
        if (isPresident(uuid)) return RoleType.PRESIDENT;
        if (isGovernor(uuid)) return RoleType.GOVERNOR;
        if (isMayor(uuid)) return RoleType.MAYOR;
        if (citizens.contains(uuid)) return RoleType.CITIZEN;
        return RoleType.NONE;
    }

    public int getPopulation() {
        Set<UUID> all = new HashSet<>(citizens);
        all.addAll(governors);
        all.addAll(mayors);
        if (president != null) all.add(president);
        return all.size();
    }
}
