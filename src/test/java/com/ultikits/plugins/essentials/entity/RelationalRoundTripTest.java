package com.ultikits.plugins.essentials.entity;

import com.ultikits.plugins.essentials.utils.RelationalStores;
import com.ultikits.ultitools.interfaces.DataOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One insert, update and delete of a module entity through the framework's relational operator and
 * real SQL, so a store change is proven by the rows it leaves rather than by a mock's recorded
 * calls (UltiKits/UltiEssentials#49). The update and the delete find the row by its primary key,
 * the column earlier versions persisted as {@code NULL} (UltiKits/UltiEssentials#34).
 */
@DisplayName("A module entity survives a real relational insert, update and delete (#49)")
class RelationalRoundTripTest {

    @Test
    @DisplayName("insert, update by primary key and delete by primary key each change the stored rows")
    void insertUpdateDeleteRoundTrip() throws Exception {
        DataOperator<WarpData> warps = RelationalStores.inMemory(WarpData.class, "warps-" + UUID.randomUUID());
        UUID id = UUID.randomUUID();
        WarpData warp = WarpData.builder()
                .uuid(id).name("market").world("world")
                .x(1.5).y(64).z(-2.5).yaw(90f).pitch(0f)
                .createdBy(UUID.randomUUID().toString()).createdAt(1_000L)
                .build();

        warps.insert(warp);
        List<WarpData> stored = warps.getAll();
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getName()).isEqualTo("market");
        assertThat(stored.get(0).getId()).isEqualTo(warp.getId()).isNotNull();

        warp.setName("bazaar");
        warps.update(warp);
        assertThat(warps.getById(warp.getId()).getName()).isEqualTo("bazaar");

        warps.delById(warp.getId());
        assertThat(warps.getAll()).isEmpty();
    }
}
