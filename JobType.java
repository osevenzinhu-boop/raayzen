package com.nationswaw.model;

import org.bukkit.Material;

public enum JobType {
    ENGINEER("Engenheiro", Material.BRICKS, 150_000, 4_000,
            "Colocar 50 blocos na claim da nacao", 50, 1_200, 0.10, 0.05),
    FARMER("Agricultor", Material.WHEAT, 130_000, 2_750,
            "Colher 10 plantacoes na claim da nacao", 10, 2_000, 0.10, 0.05),
    SECURITY("Seguranca", Material.IRON_SWORD, 200_000, 3_500,
            "Matar 40 mobs hostis na claim da nacao", 40, 7_500, 0.15, 0.05),
    MINER("Minerador", Material.DIAMOND_PICKAXE, 100_000, 1_000,
            "Minerar 20 minerios (qualquer lugar)", 20, 1_000, 0.05, 0.05),
    LUMBERJACK("Lenhador", Material.IRON_AXE, 85_000, 1_250,
            "Quebrar 30 madeiras (qualquer lugar)", 30, 900, 0.05, 0.05);

    private final String displayName;
    private final Material icon;
    private final double activationCost;
    private final double baseSalary;
    private final String missionDesc;
    private final int missionTarget;
    private final double missionReward;
    private final double missionIssp; // fraction of gross
    private final double missionIsdn;

    JobType(String displayName, Material icon, double activationCost, double baseSalary,
            String missionDesc, int missionTarget, double missionReward,
            double missionIssp, double missionIsdn) {
        this.displayName = displayName;
        this.icon = icon;
        this.activationCost = activationCost;
        this.baseSalary = baseSalary;
        this.missionDesc = missionDesc;
        this.missionTarget = missionTarget;
        this.missionReward = missionReward;
        this.missionIssp = missionIssp;
        this.missionIsdn = missionIsdn;
    }

    public String getDisplayName() { return displayName; }
    public Material getIcon() { return icon; }
    public double getActivationCost() { return activationCost; }
    public double getBaseSalary() { return baseSalary; }
    public String getMissionDesc() { return missionDesc; }
    public int getMissionTarget() { return missionTarget; }
    public double getMissionReward() { return missionReward; }
    public double getMissionIssp() { return missionIssp; }
    public double getMissionIsdn() { return missionIsdn; }

    /** Custo do upgrade de vaga (nivel 1..5): 20k * 1.25^(n-1) */
    public static double slotUpgradeCost(int nextLevel) {
        return 20_000 * Math.pow(1.25, nextLevel - 1);
    }

    /** Custo upgrade salario: 100k * 2^(level-1) for level 1..5 */
    public static double salaryUpgradeCost(int nextLevel) {
        return 100_000 * Math.pow(2, nextLevel - 1);
    }

    /** @deprecated use getBaseSalary */
    public double baseSalary() { return baseSalary; }
}
