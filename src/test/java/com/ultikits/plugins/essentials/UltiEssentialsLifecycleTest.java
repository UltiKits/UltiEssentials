package com.ultikits.plugins.essentials;

import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Structural lifecycle contract for {@link UltiEssentials} (UltiKits/UltiEssentials#23).
 * <p>
 * As of UltiTools 6.3.0, {@link UltiToolsPlugin#reloadSelf()} and
 * {@link UltiToolsPlugin#unregisterSelf()} are {@code final} template methods that always run
 * the framework's own steps (config reload, language refresh, drift report and one per-module
 * reload line; command and listener unregistration) around the {@code onReload()} /
 * {@code onUnregister()} hooks. This module's former overrides only logged a line and replaced
 * those steps, so a reload re-read nothing. They are deleted, not renamed to hooks, because
 * they carried no work of their own.
 * <p>
 * 生命周期结构约束：本模块不再声明框架的模板方法，也不声明空的钩子方法。
 */
@DisplayName("UltiEssentials lifecycle contract (UltiKits/UltiEssentials#23)")
class UltiEssentialsLifecycleTest {

    @Test
    @DisplayName("declares neither framework template method nor a lifecycle hook of its own")
    void declaresNoTemplateMethodOrLifecycleHook() {
        List<String> lifecycleMethods = new ArrayList<>();
        for (Method method : UltiEssentials.class.getDeclaredMethods()) {
            String name = method.getName();
            if ("reloadSelf".equals(name) || "unregisterSelf".equals(name)
                    || "onReload".equals(name) || "onUnregister".equals(name)) {
                lifecycleMethods.add(name);
            }
        }

        assertThat(lifecycleMethods).isEmpty();
    }
}
