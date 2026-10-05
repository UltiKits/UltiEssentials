package com.ultikits.plugins.essentials.config;

import com.ultikits.ultitools.abstracts.AbstractConfigEntity;
import com.ultikits.ultitools.annotations.ConfigEntity;
import com.ultikits.ultitools.annotations.ConfigEntry;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Location;

/**
 * Configuration for lobby/hub location.
 */
@Getter
@Setter
@ConfigEntity("config/lobby.yml")
public class LobbyConfig extends AbstractConfigEntity {

    /** The six location settings, in file order. */
    private static final String[] LOCATION_PATHS = {"lobby.location.world", "lobby.location.x", "lobby.location.y",
            "lobby.location.z", "lobby.location.yaw", "lobby.location.pitch"};

    @ConfigEntry(path = "lobby.location.world", comment = "{essentials.config.lobby.lobby.location.world}")
    private String world = "world";

    @ConfigEntry(path = "lobby.location.x", comment = "{essentials.config.lobby.lobby.location.x}")
    private double x = 0.0;

    @ConfigEntry(path = "lobby.location.y", comment = "{essentials.config.lobby.lobby.location.y}")
    private double y = 64.0;

    @ConfigEntry(path = "lobby.location.z", comment = "{essentials.config.lobby.lobby.location.z}")
    private double z = 0.0;

    @ConfigEntry(path = "lobby.location.yaw", comment = "{essentials.config.lobby.lobby.location.yaw}")
    private double yaw = 0.0;

    @ConfigEntry(path = "lobby.location.pitch", comment = "{essentials.config.lobby.lobby.location.pitch}")
    private double pitch = 0.0;

    /**
     * The six settings {@code /setlobby} writes, and the only ones it writes (UltiKits/UltiEssentials#72).
     *
     * @return the {@code @ConfigEntry} paths of the six location settings, a new array each call
     */
    public static String[] locationPaths() {
        return LOCATION_PATHS.clone();
    }

    public LobbyConfig() {
        super("config/lobby.yml");
    }

    /**
     * Gets the lobby location as a Bukkit Location object.
     *
     * @return the lobby location
     */
    public Location getLobbyLocation() {
        return new Location(
                Bukkit.getWorld(world),
                x, y, z, (float) yaw, (float) pitch
        );
    }

    /**
     * Sets the lobby location from a Bukkit Location object.
     *
     * @param location the location to set as lobby
     */
    public void setLobbyLocation(Location location) {
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
