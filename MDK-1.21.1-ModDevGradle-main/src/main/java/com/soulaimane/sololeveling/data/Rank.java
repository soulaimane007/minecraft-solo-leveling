package com.soulaimane.sololeveling.data;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum Rank implements StringRepresentable {
    E("E", 10), D("D", 20), C("C", 35), B("B", 50), A("A", 70), S("S", 100);

    public static final Codec<Rank> CODEC = StringRepresentable.fromEnum(Rank::values);
    private static final Rank[] VALUES = values();
    private final String name;
    public final int efficientPoints;

    Rank(String name, int efficientPoints) {
        this.name = name;
        this.efficientPoints = efficientPoints;
    }

    @Override public String getSerializedName() { return name; }

    public static Rank byOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= VALUES.length) return E;
        return VALUES[ordinal];
    }
}