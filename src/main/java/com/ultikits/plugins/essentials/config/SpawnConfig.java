package com.ultikits.plugins.essentials.config;

import com.ultikits.ultitools.abstracts.AbstractConfigEntity;
import com.ultikits.ultitools.annotations.ConfigEntity;
import com.ultikits.ultitools.annotations.ConfigEntry;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Location;

/**
 * Configuration for spawn point location.
 */
@Getter
@Setter
@ConfigEntity("config/spawn.yml")
public class SpawnConfig extends AbstractConfigEntity {

    /** The six location settings, in file order. */
    private static final String[] LOCATION_PATHS = {"spawn.location.world", "spawn.location.x", "spawn.location.y",
            "spawn.location.z", "spawn.location.yaw", "spawn.location.pitch"};

    @ConfigEntry(path = "spawn.location.world", comment = "{essentials.config.spawn.spawn.location.world}")
    private String world = "world";

    @ConfigEntry(path = "spawn.location.x", comment = "{essentials.config.spawn.spawn.location.x}")
    private double x = 0.0;

    @ConfigEntry(path = "spawn.location.y", comment = "{essentials.config.spawn.spawn.location.y}")
    private double y = 64.0;

    @ConfigEntry(path = "spawn.location.z", comment = "{essentials.config.spawn.spawn.location.z}")
    private double z = 0.0;

    @ConfigEntry(path = "spawn.location.yaw", comment = "{essentials.config.spawn.spawn.location.yaw}")
    private double yaw = 0.0;

    @ConfigEntry(path = "spawn.location.pitch", comment = "{essentials.config.spawn.spawn.location.pitch}")
    private double pitch = 0.0;

    @ConfigEntry(path = "spawn.teleport-on-first-join", comment = "{essentials.config.spawn.spawn.teleport-on-first-join}")
    private boolean teleportOnFirstJoin = true;

    @ConfigEntry(path = "spawn.teleport-on-respawn", comment = "{essentials.config.spawn.spawn.teleport-on-respawn}")
    private boolean teleportOnRespawn = true;

    /**
     * The six settings {@code /setspawn} writes, and the only ones it writes (UltiKits/UltiEssentials#72).
     *
     * @return the {@code @ConfigEntry} paths of the six location settings, a new array each call
     */
    public static String[] locationPaths() {
        return LOCATION_PATHS.clone();
    }

    public SpawnConfig() {
        super("config/spawn.yml");
    }

    /**
     * Gets the spawn location as a Bukkit Location object.
     *
     * @return the spawn location
     */
    public Location getSpawnLocation() {
        return new Location(
                Bukkit.getWorld(world),
                x, y, z, (float) yaw, (float) pitch
        );
    }

    /**
     * Sets the spawn location from a Bukkit Location object.
     *
     * @param location the location to set as spawn
     */
    public void setSpawnLocation(Location location) {
        if (location.getWorld() != null) {
            this.world = location.getWorld().getName();
        }
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
        this.yaw = location.getYaw();
        this.pitch = location.getPitch();
    }
}
