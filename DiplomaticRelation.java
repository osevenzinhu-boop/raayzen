package com.nationswaw.model;

public enum DiplomaticRelation {
    ALLY("Aliado", "<green>Aliado</green>"),
    NEUTRAL("Neutro", "<gray>Neutro</gray>"),
    WAR("Em Guerra", "<dark_red><bold>EM GUERRA</bold></dark_red>");

    private final String displayName;
    private final String coloredName;

    DiplomaticRelation(String displayName, String coloredName) {
        this.displayName = displayName;
        this.coloredName = coloredName;
    }

    public String getDisplayName() { return displayName; }
    public String getColoredName() { return coloredName; }
}
