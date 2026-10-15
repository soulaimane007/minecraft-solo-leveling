package com.soulaimane.sololeveling.magic;

import com.soulaimane.sololeveling.SoloLeveling;
import com.soulaimane.sololeveling.power.PowerIds;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every magic of the mod, in the order they were registered. Filled once, while the mod starts
 * (see {@link ModMagics#init()}); only read afterwards.
 */
public final class MagicRegistry {
    private MagicRegistry() {}

    private static final Map<String, Magic> MAGICS = new LinkedHashMap<>();

    /** Adds a magic. Registering the same id twice is a mistake and stops the game with a clear message. */
    public static Magic register(Magic magic) {
        if (MAGICS.containsKey(magic.id())) {
            throw new IllegalStateException("Magic registered twice: " + magic.id());
        }
        MAGICS.put(magic.id(), magic);
        return magic;
    }

    /** The magic with this id ("flame_bolt" or "sololeveling:flame_bolt"), or null. */
    public static Magic get(String id) {
        return MAGICS.get(PowerIds.normalize(id));
    }

    /** Exact lookup with a full id ("sololeveling:flame_bolt"). */
    public static Magic byId(String fullId) {
        return MAGICS.get(fullId);
    }

    public static Collection<Magic> all() {
        return Collections.unmodifiableCollection(MAGICS.values());
    }

    public static void validate() {
        for (Magic m : MAGICS.values()) {
            for (String needed : m.requirements().magicIds()) {
                if (!MAGICS.containsKey(needed)) {
                    SoloLeveling.LOGGER.warn("Magic {} needs the unknown magic {}", m.id(), needed);
                }
            }
        }
        SoloLeveling.LOGGER.info("Magic: {} magic(s) registered", MAGICS.size());
    }
}
