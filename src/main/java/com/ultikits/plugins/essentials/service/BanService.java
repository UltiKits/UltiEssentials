package com.ultikits.plugins.essentials.service;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.entity.BanData;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.Service;
import com.ultikits.ultitools.interfaces.DataOperator;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import com.ultikits.ultitools.annotations.PostConstruct;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Service for managing player bans.
 * <p>
 * 管理玩家封禁的服务。
 * <p>
 * Note: Login ban checks are handled by {@link com.ultikits.plugins.essentials.listener.BanListener}
 *
 * @author wisdomme
 * @version 1.1.0
 */
@Slf4j
@Service
public class BanService {

    @Autowired
    private UltiToolsPlugin plugin;

    @Autowired
    private EssentialsConfig config;

    private DataOperator<BanData> banOperator;

    /**
     * Initializes the service with the data operator.
     * Automatically called by the IoC container after construction.
     */
    @PostConstruct
    public void init() {
        this.banOperator = plugin.getDataOperator(BanData.class);
    }
    
    /**
     * Bans a player permanently.
     *
     * @param targetUuid  the UUID of the player to ban
     * @param targetName  the name of the player
     * @param reason      the ban reason
     * @param operatorUuid UUID of the operator (null for console)
     * @param operatorName name of the operator
     * @return result of the ban operation
     */
    public BanResult banPlayer(UUID targetUuid, String targetName, String reason, 
                               @Nullable UUID operatorUuid, String operatorName) {
        return banPlayer(targetUuid, targetName, reason, operatorUuid, operatorName, -1, null);
    }
    
    /**
     * Bans a player temporarily.
     *
     * @param targetUuid  the UUID of the player to ban
     * @param targetName  the name of the player
     * @param reason      the ban reason
     * @param operatorUuid UUID of the operator (null for console)
     * @param operatorName name of the operator
     * @param duration    ban duration in milliseconds (-1 for permanent)
     * @param ipAddress   IP address to ban (optional)
     * @return result of the ban operation
     */
    public BanResult banPlayer(UUID targetUuid, String targetName, String reason,
                               @Nullable UUID operatorUuid, String operatorName,
                               long duration, @Nullable String ipAddress) {
        if (!config.isBanEnabled()) {
            return BanResult.DISABLED;
        }
        
        // Check if already banned
        BanData existingBan = getActiveBan(targetUuid);
        if (existingBan != null) {
            return BanResult.ALREADY_BANNED;
        }
        
        long now = System.currentTimeMillis();
        long expireTime = duration == -1 ? -1 : now + duration;
        
        BanData ban = BanData.builder()
            .uuid(UUID.randomUUID())
            .playerUuid(targetUuid.toString())
            .playerName(targetName)
            .reason(reason != null ? reason : plugin.i18n("essentials.ban.no_reason"))
            .bannedBy(operatorUuid != null ? operatorUuid.toString() : null)
            .bannedByName(operatorName)
            .banTime(now)
            .expireTime(expireTime)
            .active(true)
            .ipAddress(ipAddress)
            .build();
        
        banOperator.insert(ban);
        
        // Kick the player if online
        Player target = Bukkit.getPlayer(targetUuid);
        if (target != null) {
            target.kickPlayer(formatKickMessage(ban));
        }
        
        return BanResult.SUCCESS;
    }
    
    /**
     * Unbans a player, reporting success only once no active ban record remains for them.
     *
     * @param targetUuid the UUID of the player to unban
     * @return what happened: the ban was lifted, there was none, or one survived
     */
    public UnbanResult unbanPlayer(UUID targetUuid) {
        return deactivateActiveBans("player_uuid", targetUuid.toString(), "player " + targetUuid);
    }
    
    /**
     * Unbans a player by name, reporting success only once no active ban record remains for that
     * name.
     * <p>
     * The confirmation is a re-query, not the update call returning: {@code update(T)} returns
     * {@code void} and the framework discards the affected-row count, so an update that matched no
     * row is indistinguishable from one that cleared the flag. That is what let {@code /unban}
     * report an unban -- and broadcast it to everyone online -- while the record's {@code active}
     * column stayed set and the target was still rejected at login by this module's own ban message
     * (UltiKits/UltiEssentials#35).
     *
     * @param playerName the name of the player
     * @return what happened: the ban was lifted, there was none, or one survived
     */
    public UnbanResult unbanPlayerByName(String playerName) {
        return deactivateActiveBans("player_name", playerName, "player name '" + playerName + "'");
    }
    
    /**
     * Checks whether a player is currently banned in the server's own ban list, independent of
     * this plugin's own ban records.
     * <p>
     * {@link #banPlayer} never writes to the server's own ban list -- a ban created through this
     * plugin's own commands lives only in {@link #banOperator}. That means a name for which
     * {@link #unbanPlayerByName} returns {@code false} is not necessarily unbanned everywhere: it
     * may still be banned through the server's own list (for example, a vanilla {@code /ban}).
     * Callers use this method to tell "not banned by this plugin" apart from "not banned
     * anywhere" when reporting an unban outcome, rather than reporting the two as the same thing.
     *
     * @param playerName the player name to check
     * @return true if the server's own ban list currently bans this name
     */
    // BanList.Type.NAME and isBanned(String) are deprecated, but the non-deprecated
    // isBanned(PlayerProfile) route is worse for this exact use case, not merely differently
    // spelled: disassembling CraftProfileBanList (paper-1.21.4) shows isBanned(PlayerProfile)
    // keys the lookup by profile.getId() (UserBanList.getKeyForUser), and
    // Bukkit.createProfile(name) substitutes the all-zero NIL_UUID for any name that is not the
    // currently-connected player -- so it would silently return false for every offline banned
    // player, which is what /unban checks against. isBanned(String) instead resolves via the
    // server's GameProfileCache and null-checks before ever reaching that keyed lookup. Separately
    // verified that BanList.Type.NAME and .PROFILE both construct the identical
    // CraftProfileBanList backed by vanilla's UserBanList, so this is not a disused/separate ban
    // list either way -- kept deliberately, not out of inertia.
    @SuppressWarnings("deprecation")
    public boolean isBannedInServerBanList(String playerName) {
        return Bukkit.getBanList(BanList.Type.NAME).isBanned(playerName);
    }

    /**
     * Unbans an IP address.
     *
     * @param ipAddress the IP address to unban
     * @return true if unbanned, false if not banned
     */
    public UnbanResult unbanIp(String ipAddress) {
        return deactivateActiveBans("ip_address", ipAddress, "IP address " + ipAddress);
    }

    /**
     * Deactivates every active ban record matching one column value, then confirms by re-query that
     * none is left active before reporting success.
     * <p>
     * The three unban paths held three copies of this body, so a fix applied to one of them would
     * have left the other two reporting success on an unchanged store. A failed update on a single
     * record is logged and the remaining records are still attempted: the re-query, not the
     * individual call, decides the outcome.
     *
     * @param column  the {@code @Column} name to match on
     * @param value   the value to match
     * @param subject how to name the unban's subject in a diagnostic
     * @return what happened: the ban was lifted, there was none, or one survived
     */
    private UnbanResult deactivateActiveBans(String column, String value, String subject) {
        List<BanData> activeBans = activeBansMatching(column, value);
        if (activeBans.isEmpty()) {
            return UnbanResult.NOT_BANNED;
        }

        for (BanData ban : activeBans) {
            ban.setActive(false);
            try {
                banOperator.update(ban);
            } catch (IllegalAccessException e) {
                log.error(plugin.i18n("essentials.log.ban_update_failed"), e);
            }
        }

        List<BanData> stillActive = activeBansMatching(column, value);
        if (!stillActive.isEmpty()) {
            log.error(plugin.i18n("essentials.log.unban_still_active"), subject, stillActive.size(), activeBans.size());
            return UnbanResult.FAILED;
        }
        return UnbanResult.REMOVED;
    }

    private List<BanData> activeBansMatching(String column, String value) {
        return banOperator.query()
            .where(column).eq(value)
            .list()
            .stream()
            .filter(b -> b.isActive() && !b.hasExpired())
            .collect(Collectors.toList());
    }
    
    /**
     * Gets the active ban for a player.
     */
    @Nullable
    public BanData getActiveBan(UUID playerUuid) {
        List<BanData> bans = banOperator.query()
            .where("player_uuid").eq(playerUuid.toString())
            .list();

        return bans.stream()
            .filter(b -> b.isActive() && !b.hasExpired())
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Gets active IP ban.
     */
    @Nullable
    public BanData getActiveIpBan(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return null;
        }

        List<BanData> bans = banOperator.query()
            .where("ip_address").eq(ipAddress)
            .list();

        return bans.stream()
            .filter(b -> b.isActive() && !b.hasExpired())
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Gets all active bans.
     */
    public List<BanData> getActiveBans() {
        return banOperator.getAll().stream()
            .filter(b -> b.isActive() && !b.hasExpired())
            .collect(Collectors.toList());
    }
    
    /**
     * Gets ban history for a player.
     */
    public List<BanData> getBanHistory(UUID playerUuid) {
        return banOperator.query()
            .where("player_uuid").eq(playerUuid.toString())
            .list();
    }
    
    /**
     * Formats the kick message for a banned player.
     * 
     * @param ban the ban data
     * @return the formatted kick message
     */
    public String formatKickMessage(BanData ban) {
        StringBuilder message = new StringBuilder();
        message.append(plugin.i18n("essentials.ban.kick.title")).append("\n\n");
        message.append(String.format(plugin.i18n("essentials.ban.kick.reason"), ban.getReason())).append("\n");
        message.append(String.format(plugin.i18n("essentials.ban.kick.operator"), ban.getBannedByName())).append("\n");
        
        if (ban.isPermanent()) {
            message.append(plugin.i18n("essentials.ban.kick.permanent")).append("\n");
        } else {
            message.append(String.format(plugin.i18n("essentials.ban.kick.remaining"),
                    formatDuration(ban.getRemainingTime()))).append("\n");
        }
        
        message.append("\n").append(plugin.i18n("essentials.ban.kick.appeal"));
        return message.toString();
    }
    
    /**
     * Formats duration in human-readable format, in the server's language.
     */
    public String formatDuration(long millis) {
        if (millis <= 0) {
            return plugin.i18n("essentials.duration.expired");
        }
        
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        long hours = TimeUnit.MILLISECONDS.toHours(millis) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(String.format(plugin.i18n("essentials.duration.days"), days)).append(' ');
        if (hours > 0) sb.append(String.format(plugin.i18n("essentials.duration.hours"), hours)).append(' ');
        if (minutes > 0) sb.append(String.format(plugin.i18n("essentials.duration.minutes"), minutes)).append(' ');
        if (seconds > 0 || sb.length() == 0) sb.append(String.format(plugin.i18n("essentials.duration.seconds"), seconds));
        
        return sb.toString().trim();
    }
    
    /**
     * Parses duration string like "1d", "2h", "30m", "1d12h30m".
     * 
     * @param durationStr duration string
     * @return duration in milliseconds, or -1 if invalid
     */
    public static long parseDuration(String durationStr) {
        if (durationStr == null || durationStr.isEmpty()) {
            return -1;
        }
        
        long total = 0;
        StringBuilder number = new StringBuilder();
        
        for (char c : durationStr.toLowerCase().toCharArray()) {
            if (Character.isDigit(c)) {
                number.append(c);
            } else {
                if (number.length() == 0) {
                    return -1;
                }
                long value = Long.parseLong(number.toString());
                number.setLength(0);
                
                switch (c) {
                    case 'd':
                        total += TimeUnit.DAYS.toMillis(value);
                        break;
                    case 'h':
                        total += TimeUnit.HOURS.toMillis(value);
                        break;
                    case 'm':
                        total += TimeUnit.MINUTES.toMillis(value);
                        break;
                    case 's':
                        total += TimeUnit.SECONDS.toMillis(value);
                        break;
                    case 'w':
                        total += TimeUnit.DAYS.toMillis(value * 7);
                        break;
                    default:
                        return -1;
                }
            }
        }
        
        return total > 0 ? total : -1;
    }
    
    public enum BanResult {
        SUCCESS,
        ALREADY_BANNED,
        DISABLED
    }

    /**
     * What an unban did, so the caller can tell a name that was never banned from one whose ban
     * record is still in force.
     * <p>
     * Three values rather than a boolean because reporting "not banned" for a ban that survived
     * tells the operator to stop looking while {@code /banlist} still lists the player and the
     * player is still rejected at login -- the same harm the false success had.
     */
    public enum UnbanResult {
        REMOVED,
        NOT_BANNED,
        FAILED
    }
}
