package com.ultikits.plugins.essentials.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ban service declares only the unban path a command reaches: {@code /unban <player>} through
 * {@link BanService#unbanPlayerByName(String)}. The by-UUID and by-IP unban methods had no caller
 * and no command, so they were removed rather than left as a declared capability nothing delivers
 * (maintainer ruling 2026-09-22, UltiKits/UltiEssentials#47).
 */
@DisplayName("BanService declares only the unban path a command reaches (#47)")
class BanServiceDeclarationTest {

    @Test
    @DisplayName("There is no by-IP unban method and no by-UUID unban method; the by-name one remains")
    void onlyTheReachableUnbanPathIsDeclared() throws Exception {
        assertThat(Arrays.stream(BanService.class.getDeclaredMethods()).map(Method::getName))
                .doesNotContain("unbanIp");
        assertThat(Arrays.stream(BanService.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("unbanPlayer"))
                .filter(m -> Arrays.equals(m.getParameterTypes(), new Class<?>[]{UUID.class})))
                .isEmpty();
        assertThat(BanService.class.getMethod("unbanPlayerByName", String.class)).isNotNull();
    }
}
