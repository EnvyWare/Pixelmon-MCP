package com.envyware.pixelmonmcp;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

/**
 * Client-side mod that contributes Pixelmon tools to minecraft-mcp. The tools are registered through
 * {@link PixelmonMcpTools}, which minecraft-mcp discovers as a service.
 */
@Mod(value = PixelmonMcpMod.MOD_ID, dist = Dist.CLIENT)
public class PixelmonMcpMod {
    public static final String MOD_ID = "pixelmon_mcp";
}
