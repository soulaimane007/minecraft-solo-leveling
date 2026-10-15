package com.soulaimane.sololeveling.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The player's action bar: {@link #SIZE} slots, each empty ("") or holding one entry:
 * "skill:sololeveling:power_strike" or "magic:sololeveling:flame_bolt".
 * Skills and magics share the same bar.
 */
public record PlayerActionBar(List<String> slots) {

    public static final int SIZE = 6;
    public static final String SKILL_PREFIX = "skill:";
    public static final String MAGIC_PREFIX = "magic:";

    public static final PlayerActionBar DEFAULT = new PlayerActionBar(Collections.nCopies(SIZE, ""));

    public static final Codec<PlayerActionBar> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().optionalFieldOf("slots", List.of()).forGetter(PlayerActionBar::slots)
    ).apply(i, PlayerActionBar::normalized));

    /** Always exactly SIZE entries, whatever was saved (the number of slots may change in a later version). */
    public static PlayerActionBar normalized(List<String> raw) {
        List<String> out = new ArrayList<>(SIZE);
        for (int i = 0; i < SIZE; i++) {
            out.add(i < raw.size() ? raw.get(i) : "");
        }
        return new PlayerActionBar(Collections.unmodifiableList(out));
    }

    public static String skillEntry(String skillId) { return SKILL_PREFIX + skillId; }

    public static String magicEntry(String magicId) { return MAGIC_PREFIX + magicId; }

    public String get(int slot) {
        return slot >= 0 && slot < slots.size() ? slots.get(slot) : "";
    }

    public int indexOf(String entry) {
        return slots.indexOf(entry);
    }

    /** -1 when every slot is taken. */
    public int firstFree() {
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).isEmpty()) return i;
        }
        return -1;
    }

    /** Puts {@code entry} in {@code slot}. An entry lives in one slot only: it leaves its old slot. */
    public PlayerActionBar with(int slot, String entry) {
        if (slot < 0 || slot >= SIZE) return this;
        List<String> copy = new ArrayList<>(normalized(slots).slots());
        if (!entry.isEmpty()) {
            for (int i = 0; i < copy.size(); i++) {
                if (copy.get(i).equals(entry)) copy.set(i, "");
            }
        }
        copy.set(slot, entry);
        return new PlayerActionBar(Collections.unmodifiableList(copy));
    }

    public PlayerActionBar without(String entry) {
        int at = indexOf(entry);
        return at < 0 ? this : with(at, "");
    }
}
