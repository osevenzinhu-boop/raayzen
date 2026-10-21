package com.nationswaw.model;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashSet;

/** Estado de um emprego em uma nacao */
public class NationJob {
    public final JobType type;
    public boolean active;
    /** 0..5 upgrades de vaga → vagas = 3 + slotLevel (max 8) */
    public int slotLevel;
    /** 0..5 upgrades de salario → 15% composto por nivel */
    public int salaryLevel;
    /** 0..5 upgrades de tempo de salario (individual) → -5 min por nivel (60→35) */
    public int timeLevel;
    public final Set<UUID> workers = new HashSet<>();
    public final Map<UUID, Long> quitCooldown = new ConcurrentHashMap<>();

    public static final int BASE_SLOTS = 3;
    public static final int MAX_SLOT_UPGRADES = 5;
    public static final int MAX_SALARY_UPGRADES = 5;
    public static final int MAX_TIME_UPGRADES = 5;

    public NationJob(JobType type) {
        this.type = type;
        this.active = false;
        this.slotLevel = 0;
        this.salaryLevel = 0;
        this.timeLevel = 0;
    }

    public int maxSlots() {
        return BASE_SLOTS + slotLevel;
    }

    public int usedSlots() {
        return workers.size();
    }

    public boolean hasVacancy() {
        return workers.size() < maxSlots();
    }

    /**
     * Salario bruto com juros compostos de 15% por nivel.
     * base * (1.15 ^ salaryLevel)
     * Ex: 1000 → 1150 → 1322.50 → 1520.875 ...
     */
    public double grossSalary() {
        double sal = type.getBaseSalary();
        for (int i = 0; i < salaryLevel; i++) {
            sal = sal + (sal * 0.15);
        }
        return sal;
    }

    /** Intervalo em minutos: 60 - 5*timeLevel (min 35) */
    public long salaryIntervalMinutes() {
        long m = 60 - 5L * timeLevel;
        return Math.max(35, m);
    }

    public long salaryIntervalMs() {
        return salaryIntervalMinutes() * 60_000L;
    }

    public double sellMultiplier() {
        return 1.0;
    }
}
