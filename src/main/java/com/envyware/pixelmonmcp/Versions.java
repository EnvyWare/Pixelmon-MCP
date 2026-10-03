package com.envyware.pixelmonmcp;

import net.neoforged.fml.ModList;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;

/**
 * Versions of this mod, the mods it runs against and the ones it was compiled against.
 */
public final class Versions {
    private static final Properties BUILD = load();

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

    public static String mod() {
        return loaded(PixelmonMcpMod.MOD_ID);
    }

    public static String pixelmon() {
        return loaded("pixelmon");
    }

    public static String minecraftMcp() {
        return loaded("minecraft_mcp");
    }

    public static String compiledPixelmon() {
        return BUILD.getProperty("pixelmon");
    }

    public static String compiledMinecraftMcp() {
        return BUILD.getProperty("minecraftMcp");
    }

    private static Properties load() {
        Properties properties = new Properties();
        try (InputStream stream = Versions.class.getResourceAsStream("build.properties")) {
            if (stream != null) {
                properties.load(stream);
            }
        } catch (IOException ignored) {
        }
        return properties;
    }
}
