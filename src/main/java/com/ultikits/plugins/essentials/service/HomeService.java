package com.ultikits.plugins.essentials.service;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.entity.HomeData;
import com.ultikits.plugins.essentials.enums.TeleportResult;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.Service;
import com.ultikits.ultitools.interfaces.DataOperator;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import com.ultikits.ultitools.annotations.PostConstruct;
import java.util.*;

/**
 * Service for managing player homes.
 * <p>
 * 管理玩家家位置的服务。
 *
 * @author wisdomme
 * @version 1.0.0
 */
@Slf4j
@Service
public class HomeService {

    @Autowired
    private UltiToolsPlugin plugin;

    @Autowired
    private EssentialsConfig config;

    @Autowired
    private TeleportService teleportService;

    private DataOperator<HomeData> homeOperator;

    /**
     * Initializes the service with the data operator.
     * Automatically called by the IoC container after construction.
     */
    @PostConstruct
    public void init() {
        this.homeOperator = plugin.getDataOperator(HomeData.class);
    }
    
    /**
     * Gets all homes for a player.
     *
     * @param playerUuid the player's UUID
     * @return list of homes
     */
    public List<HomeData> getHomes(UUID playerUuid) {
        return homeOperator.query()
            .where("player_uuid").eq(playerUuid.toString())
            .list();
    }
    
    /**
     * Gets a specific home by name.
     *
     * @param playerUuid the player's UUID
     * @param name       the home name
     * @return the home data, or null if not found
     */
    @Nullable
    public HomeData getHome(UUID playerUuid, String name) {
        return homeOperator.query()
            .where("player_uuid").eq(playerUuid.toString())
            .where("name").eq(name.toLowerCase())
            .first();
    }
    
    /**
     * Gets the number of homes a player has.
     *
     * @param playerUuid the player's UUID
     * @return the number of homes
     */
    public int getHomeCount(UUID playerUuid) {
        return getHomes(playerUuid).size();
    }
    
    /**
     * Gets the maximum number of homes allowed for a player.
     * Can be extended with permission-based limits.
     *
     * @param player the player
     * @return the maximum number of homes
     */
    public int getMaxHomes(Player player) {
        // Check for permission-based limits (ultiessentials.home.max.<number>)
        for (int i = 100; i >= 1; i--) {
            if (player.hasPermission("ultiessentials.home.max." + i)) {
                return i;
            }
        }
        // Check for unlimited permission
        if (player.hasPermission("ultiessentials.home.unlimited")) {
            return Integer.MAX_VALUE;
        }
        return config.getHomeDefaultMaxHomes();
    }
    
    /**
     * Creates or updates a home.
     *
     * @param player the player
     * @param name   the home name
     * @return result of the operation
     */
    public SetHomeResult setHome(Player player, String name) {
        if (!config.isHomeEnabled()) {
            return SetHomeResult.DISABLED;
        }
        
        String normalizedName = name.toLowerCase().trim();
        if (normalizedName.isEmpty() || normalizedName.length() > 32) {
            return SetHomeResult.INVALID_NAME;
        }
        
        UUID playerUuid = player.getUniqueId();
        HomeData existingHome = getHome(playerUuid, normalizedName);
        
        if (existingHome != null) {
            // Update existing home, then confirm the store really holds the new location. The
            // framework's update(T) returns void and addresses its row by WHERE id = ?, so an update
            // that matched nothing is indistinguishable from one that moved the home -- which is
            // UltiKits/UltiEssentials#34's symptom on the trigger this module's CHANGELOG claims
            // fixed (gate 1 MAJOR-01). The re-query compares coordinates rather than merely finding
            // the record, because the record was always going to still be there.
            Location target = player.getLocation();
            updateHomeLocation(existingHome, target);
            try {
                homeOperator.update(existingHome);
            } catch (IllegalAccessException e) {
                log.error("Failed to update home", e);
            }
            HomeData stored = getHome(playerUuid, normalizedName);
            if (stored == null || !storesTheSamePlaceAs(stored, target)) {
                log.error("Home '{}' of player {} still reads as {} after moving it to {}; "
                        + "reporting the move as failed", normalizedName, playerUuid,
                        stored == null ? "absent" : describe(stored), describe(target));
                return SetHomeResult.FAILED;
            }
            return SetHomeResult.UPDATED;
        }
        
        // Check limit
        int currentCount = getHomeCount(playerUuid);
        int maxHomes = getMaxHomes(player);
        
        if (currentCount >= maxHomes) {
            return SetHomeResult.LIMIT_REACHED;
        }
        
        // Create new home
        Location loc = player.getLocation();
        HomeData newHome = HomeData.builder()
            .uuid(UUID.randomUUID())
            .playerUuid(playerUuid.toString())
            .name(normalizedName)
            .world(loc.getWorld().getName())
            .x(loc.getX())
            .y(loc.getY())
            .z(loc.getZ())
            .yaw(loc.getYaw())
            .pitch(loc.getPitch())
            .createdAt(System.currentTimeMillis())
            .build();
        
        homeOperator.insert(newHome);
        return SetHomeResult.CREATED;
    }
    
    /**
     * Deletes a home, reporting success only once the record is confirmed gone from the store.
     * <p>
     * The confirmation is a re-query, not the delete call returning: the framework's
     * {@code delById} returns {@code void} and discards the affected-row count, so a delete that
     * matched no row is indistinguishable from one that removed the record at the call site. That
     * is what let {@code /delhome farm} report success while {@code /homes} kept listing
     * {@code farm} (UltiKits/UltiEssentials#34). The re-query is by player and name rather than by
     * id, because that is what the player observes: a duplicate record under the same name
     * surviving is still a home that was not deleted.
     *
     * @param playerUuid the player's UUID
     * @param name       the home name
     * @return what happened: removed, no such home, or the record survived
     */
    public DeleteResult deleteHome(UUID playerUuid, String name) {
        String normalizedName = name.toLowerCase().trim();
        HomeData home = getHome(playerUuid, normalizedName);
        if (home == null) {
            return DeleteResult.NOT_FOUND;
        }
        homeOperator.delById(home.getId());
        if (getHome(playerUuid, normalizedName) != null) {
            log.error("Home '{}' of player {} is still stored after a delete of record {}; "
                    + "reporting the deletion as failed", normalizedName, playerUuid, home.getId());
            return DeleteResult.FAILED;
        }
        return DeleteResult.REMOVED;
    }

    /**
     * Whether the stored record would teleport a player to {@code target}.
     * <p>
     * Both sides are put through the entity's own {@code fromLocation}/{@code toLocation} round trip
     * and the constructed values are compared, rather than a list of fields. {@code toLocation()} is
     * the single place the stored fields are read, so a field added to the entity later cannot fall
     * out of this comparison without someone changing that method -- whereas comparing world and
     * coordinates by hand passed a move that changed only the facing direction, and would have passed
     * the next field the same way (gate 2 P2).
     * <p>
     * The round trip is applied to {@code target} as well, not just to the record, so the comparison
     * does not turn on world <em>identity</em>: both sides resolve their world by name exactly as a
     * later {@code /home} would. Comparing the raw target against the reconstructed record reported a
     * correct write as failed whenever two world objects shared a name.
     */
    private static boolean storesTheSamePlaceAs(HomeData stored, Location target) {
        HomeData asStored = new HomeData();
        asStored.fromLocation(target);
        return Objects.equals(asStored.toLocation(), stored.toLocation());
    }

    /**
     * Describes a home by the location it would actually teleport a player to.
     * <p>
     * The verification above compares {@code toLocation()} rather than a list of fields, and this
     * prints the same value, for the same reason: {@code toLocation()} is the one place the stored
     * fields are read, so a field added to the entity later cannot fall out of either the comparison
     * or the diagnostic without someone changing that method. Comparing world and coordinates by hand
     * passed a move that changed only the facing direction, and would have passed the next field too
     * (gate 2 P2).
     */
    private static String describe(HomeData home) {
        return String.valueOf(home.toLocation());
    }

    private static String describe(Location location) {
        return String.valueOf(location);
    }
    
    /**
     * Teleports a player to their home with warmup support.
     *
     * @param player the player
     * @param name   the home name
     * @return result of the operation
     */
    public TeleportResult teleportToHome(Player player, String name) {
        if (!config.isHomeEnabled()) {
            return TeleportResult.DISABLED;
        }
        
        // Check if already teleporting
        if (teleportService.isTeleporting(player.getUniqueId())) {
            return TeleportResult.ALREADY_TELEPORTING;
        }
        
        HomeData home = getHome(player.getUniqueId(), name.toLowerCase().trim());
        if (home == null) {
            return TeleportResult.NOT_FOUND;
        }
        
        Location targetLocation = home.toLocation();
        if (targetLocation == null) {
            return TeleportResult.WORLD_NOT_FOUND;
        }
        
        int warmup = config.getHomeTeleportWarmup();
        boolean skipWarmup = player.hasPermission("ultiessentials.home.nowarmup");
        
        return teleportService.teleport(
            player, 
            targetLocation, 
            skipWarmup ? 0 : warmup, 
            config.isHomeCancelOnMove()
        );
    }
    
    /**
     * Cancels a pending teleport for a player.
     *
     * @param uuid the player's UUID
     */
    public void cancelTeleport(UUID uuid) {
        teleportService.cancelTeleport(uuid);
    }
    
    /**
     * Checks if a player is currently teleporting.
     *
     * @param uuid the player's UUID
     * @return true if teleporting
     */
    public boolean isTeleporting(UUID uuid) {
        return teleportService.isTeleporting(uuid);
    }
    
    private void updateHomeLocation(HomeData home, Location loc) {
        home.fromLocation(loc);
    }
    
    public enum SetHomeResult {
        CREATED,
        UPDATED,
        LIMIT_REACHED,
        INVALID_NAME,
        DISABLED,
        /**
         * The home existed and the move did not reach the store, so the player would still be
         * teleported to the old location (gate 1 MAJOR-01).
         */
        FAILED
    }

    /**
     * What a deletion did, so the caller can tell a record that was never there from one the store
     * would not give up.
     * <p>
     * Three values rather than a boolean because the two failures are not the same thing to the
     * person reading the message: "there is no such record" ends the matter, while "the record is
     * still there" means the thing they asked for did not happen and they need to look. Collapsing
     * them told an operator a home did not exist while {@code /homes} still listed it (gate 1
     * MAJOR-03). Matches {@link ChestLockService.UnlockResult}, which already had this shape.
     */
    public enum DeleteResult {
        REMOVED,
        NOT_FOUND,
        FAILED
    }
}
