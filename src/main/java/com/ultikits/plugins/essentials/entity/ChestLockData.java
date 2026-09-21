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
 * Entity representing a chest lock.
 * <p>
 * 表示箱子锁的实体类。
 *
 * @author wisdomme
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table("essentials_chest_locks")
public class ChestLockData extends UuidKeyedDataEntity {

    /**
     * World name where the chest is located.
     */
    @Column("world")
    private String world;
    
    /**
     * X coordinate.
     */
    @Column("x")
    private int x;
    
    /**
     * Y coordinate.
     */
    @Column("y")
    private int y;
    
    /**
     * Z coordinate.
     */
    @Column("z")
    private int z;
    
    /**
     * UUID of the player who owns this lock.
     */
    @Column("owner_uuid")
    private String ownerUuid;
    
    /**
     * Name of the owner (for display).
     */
    @Column("owner_name")
    private String ownerName;
    
    /**
     * Timestamp when the lock was created.
     */
    @Column("created_at")
    private long createdAt;

    /**
     * Creates a chest-lock record.
     * <p>
     * Declared explicitly rather than through a class-level {@code @Builder} because {@code uuid}
     * now lives on {@link UuidKeyedDataEntity}: a class-level builder covers only the class's own
     * fields and would silently drop {@code .uuid(...)} from the builder's API.
     *
     * @param uuid      the record's module-generated identity
     * @param world     world name where the container is located
     * @param x         x coordinate
     * @param y         y coordinate
     * @param z         z coordinate
     * @param ownerUuid UUID of the player who owns this lock
     * @param ownerName name of the owner
     * @param createdAt timestamp when the lock was created
     */
    @Builder
    public ChestLockData(UUID uuid, String world, int x, int y, int z, String ownerUuid,
                         String ownerName, long createdAt) {
        super(uuid);
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.createdAt = createdAt;
    }

    /**
     * Creates a location key for quick lookup.
     */
    public String getLocationKey() {
        return world + ":" + x + ":" + y + ":" + z;
    }
    
    /**
     * Static method to create location key.
     */
    public static String createLocationKey(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }
}
