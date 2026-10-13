package com.soulaimane.sololeveling.util;

import com.soulaimane.sololeveling.SoloLeveling;
import net.minecraft.resources.ResourceLocation;

public final class SLResource {
    private SLResource() {}
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SoloLeveling.MOD_ID, path);
    }
}