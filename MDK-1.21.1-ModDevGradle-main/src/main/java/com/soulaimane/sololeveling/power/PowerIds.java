package com.soulaimane.sololeveling.power;

import com.soulaimane.sololeveling.SoloLeveling;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * Skills, magics and dungeons are identified by plain strings such as "sololeveling:power_strike".
 * When you write them in code you can leave out the namespace: "power_strike" means "sololeveling:power_strike".
 */
public final class PowerIds {
    private PowerIds() {}

    public static String normalize(String id) {
        String s = id.trim().toLowerCase(Locale.ROOT);
        return s.indexOf(':') >= 0 ? s : SoloLeveling.MOD_ID + ":" + s;
    }

    public static ResourceLocation location(String id) {
        return ResourceLocation.parse(normalize(id));
    }

    /** "power_strike" -> "Power Strike" (the name used when nothing better is known). */
    public static String prettyName(String id) {
        String path = normalize(id);
        int colon = path.indexOf(':');
        if (colon >= 0) path = path.substring(colon + 1);
        StringBuilder sb = new StringBuilder();
        for (String part : path.split("[_/]")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
