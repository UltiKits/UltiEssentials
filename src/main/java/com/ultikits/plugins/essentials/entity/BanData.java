package com.ultikits.plugins.essentials.entity;

import java.util.UUID;

import com.ultikits.plugins.essentials.entity.base.UuidKeyedDataEntity;
import com.ultikits.ultitools.annotations.Column;
import com.ultikits.ultitools.annotations.Table;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Entity representing a player ban record.
 * <p>
 * 表示玩家封禁记录的实体类。
 *
 * @author wisdomme
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table("essentials_bans")
public class BanData extends UuidKeyedDataEntity {

    /**
     * UUID of the banned player.
     */
    @Column("player_uuid")
    private String playerUuid;
    
    /**
     * Name of the banned player (for display purposes).
     */
    @Column("player_name")
    private String playerName;
    
    /**
     * Reason for the ban.
     */
    @Column("reason")
    private String reason;
    
    /**
     * UUID of the operator who issued the ban.
     * Null if banned by console.
     */
    @Column("banned_by")
    private String bannedBy;
    
    /**
     * Name of the operator who issued the ban.
     */
    @Column("banned_by_name")
    private String bannedByName;
    
    /**
     * Timestamp when the ban was issued.
     */
    @Column("ban_time")
    private long banTime;
    
    /**
     * Timestamp when the ban expires.
     * -1 means permanent ban.
     */
    @Column("expire_time")
    private long expireTime;
    
    /**
     * Whether this ban is currently active.
     */
    @Column("active")
    private boolean active;
    
    /**
     * IP address banned (optional, for IP bans).
     */
    @Column("ip_address")
    private String ipAddress;

    /**
     * Creates a ban record.
     * <p>
     * Declared explicitly rather than through a class-level {@code @Builder} because {@code uuid}
     * now lives on {@link UuidKeyedDataEntity}: a class-level builder covers only the class's own
     * fields and would silently drop {@code .uuid(...)} from the builder's API.
     *
     * @param uuid         the record's module-generated identity
     * @param playerUuid   UUID of the banned player
     * @param playerName   name of the banned player
     * @param reason       reason for the ban
     * @param bannedBy     UUID of the operator who issued the ban, null for console
     * @param bannedByName name of the operator who issued the ban
     * @param banTime      timestamp when the ban was issued
     * @param expireTime   timestamp when the ban expires, -1 for permanent
     * @param active       whether this ban is currently active
     * @param ipAddress    IP address banned, optional
     */
    @Builder
    public BanData(UUID uuid, String playerUuid, String playerName, String reason, String bannedBy,
                   String bannedByName, long banTime, long expireTime, boolean active, String ipAddress) {
        super(uuid);
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.reason = reason;
        this.bannedBy = bannedBy;
        this.bannedByName = bannedByName;
        this.banTime = banTime;
        this.expireTime = expireTime;
        this.active = active;
        this.ipAddress = ipAddress;
    }

    /**
     * Checks if this is a permanent ban.
     */
    public boolean isPermanent() {
        return expireTime == -1;
    }
    
    /**
     * Checks if this ban has expired.
     */
    public boolean hasExpired() {
        if (isPermanent()) {
            return false;
        }
        return System.currentTimeMillis() > expireTime;
    }
    
    /**
     * Gets remaining time in milliseconds.
     * Returns -1 for permanent bans, 0 if expired.
     */
    public long getRemainingTime() {
        if (isPermanent()) {
            return -1;
        }
        long remaining = expireTime - System.currentTimeMillis();
        return Math.max(0, remaining);
    }
}
