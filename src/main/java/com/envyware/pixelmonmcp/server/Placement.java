package com.envyware.pixelmonmcp.server;

import com.envyware.pixelmonmcp.ToolArgs;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

/**
 * Positions and facings from tool arguments: {@code pos} and {@code lookAt} are {@code [x, y, z]}, relative to
 * {@code origin} ({@code "spawn"}, {@code "player"} or {@code [x, y, z]}) when one is given, as in a film scene.
 */
public final class Placement {
    private Placement() {
    }

    /**
     * The origin coordinates are relative to, or null if {@code origin} is not a known origin.
     */
    public static Vec3 origin(ServerPlayer player, Object origin) {
        if (origin == null) {
            return Vec3.ZERO;
        }
        if ("spawn".equals(origin)) {
            BlockPos spawn = player.serverLevel().getSharedSpawnPos();
            return new Vec3(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
        }
        if ("player".equals(origin)) {
            return player.position();
        }
        double[] point = ToolArgs.point(origin);
        return point == null ? null : new Vec3(point[0], point[1], point[2]);
    }

    /**
     * The point under {@code key} made absolute, or null if it is missing or not a point.
     */
    public static Vec3 point(Map<String, Object> args, String key, Vec3 origin) {
        double[] point = ToolArgs.point(args.get(key));
        return point == null ? null : origin.add(point[0], point[1], point[2]);
    }

    /**
     * A point {@code distance} blocks in front of the player, on the ground.
     */
    public static Vec3 inFront(ServerPlayer player, double distance) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1.0E-4) {
            flat = new Vec3(0, 0, 1);
        }
        return ground(player, player.position().add(flat.normalize().scale(distance)));
    }

    public static Vec3 ground(ServerPlayer player, Vec3 position) {
        int y = player.serverLevel().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(position.x),
                (int) Math.floor(position.z));
        return new Vec3(position.x, y, position.z);
    }

    /**
     * Yaw in degrees for an entity at {@code from} to face {@code to}.
     */
    public static float yawTowards(Vec3 from, Vec3 to) {
        return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
    }

    public static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }

    public static List<Double> rounded(Vec3 position) {
        return List.of(round(position.x), round(position.y), round(position.z));
    }
}
