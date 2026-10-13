package com.soulaimane.sololeveling.data;

public enum EquipmentLoadState {
    LIGHT(0.30, 1.10, 0.0, 1.0),
    MEDIUM(0.70, 1.00, 0.0, 1.0),
    HEAVY(1.00, 0.75, -0.10, 1.0),
    OVERWEIGHT(Double.MAX_VALUE, 0.40, -0.40, 0.0);

    public final double maxRatio;
    public final double staminaRegenMultiplier;
    public final double moveSpeedMultiplier;
    public final double sprintAllowed;

    EquipmentLoadState(double maxRatio, double staminaRegenMult, double moveSpeedMult, double sprintAllowed) {
        this.maxRatio = maxRatio;
        this.staminaRegenMultiplier = staminaRegenMult;
        this.moveSpeedMultiplier = moveSpeedMult;
        this.sprintAllowed = sprintAllowed;
    }

    public static EquipmentLoadState fromRatio(double ratio) {
        if (ratio <= LIGHT.maxRatio) return LIGHT;
        if (ratio <= MEDIUM.maxRatio) return MEDIUM;
        if (ratio < HEAVY.maxRatio) return HEAVY;
        return OVERWEIGHT;
    }
}