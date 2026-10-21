package com.nationswaw.model;

public enum RoleType {
    NONE("Imigrante", 0),
    CITIZEN("Civil", 1),
    MAYOR("Prefeito", 2),
    GOVERNOR("Governador", 3),
    PRESIDENT("Presidente", 4);

    private final String displayName;
    private final int power;

    RoleType(String displayName, int power) {
        this.displayName = displayName;
        this.power = power;
    }

    public String getDisplayName() { return displayName; }
    public int getPower() { return power; }
    public boolean hasPowerOf(RoleType other) { return this.power >= other.power; }
}
