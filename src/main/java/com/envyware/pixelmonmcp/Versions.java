package com.envyware.pixelmonmcp;

import net.neoforged.fml.ModList;

import java.util.Optional;

/**
 * Versions of the mods Pixelmon MCP runs against.
 */
public final class Versions {
    private Versions() {
    }

    public static String loaded(String modId) {
        ModList mods = ModList.get();
        if (mods == null) {
            return null;
        }
        Optional<String> version = mods.getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString());
        return version.orElse(null);
    }

    public static String pixelmon() {
        return loaded("pixelmon");
    }
}
