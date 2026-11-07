package com.ultikits.plugins.essentials.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The by-UUID unban helper remains removed (#47). IP lifting is now reachable through
 * {@code UnbanIpCommand} (#63), so both currently delivered paths are declared.
 */
@DisplayName("BanService declares only delivered unban paths (#47, #63)")
class BanServiceDeclarationTest {

    @Test
    @DisplayName("By-IP and by-name have commands; there is still no unused by-UUID helper")
    void onlyTheReachableUnbanPathIsDeclared() throws Exception {
        assertThat(BanService.class.getMethod("unbanIp", String.class)).isNotNull();
        assertThat(Arrays.stream(BanService.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("unbanPlayer"))
                .filter(m -> Arrays.equals(m.getParameterTypes(), new Class<?>[]{UUID.class})))
                .isEmpty();
        assertThat(BanService.class.getMethod("unbanPlayerByName", String.class)).isNotNull();
    }
}
