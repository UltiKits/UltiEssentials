package com.ultikits.plugins.essentials.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers which elements of a repeating loop (a player in an update task) are currently failing,
 * so a failure is logged once when it starts rather than on every run, and again only after the
 * element has succeeded in between.
 * <p>
 * Callers must {@link #forget(Object)} an element that leaves the loop (a player who quits or turns
 * the feature off), so the state stays bounded by the loop's own size.
 * <p>
 * 记录重复执行的循环中哪些元素正在失败：失败开始时只记录一次日志，恢复后再次失败才会再记录。
 *
 * @param <K> the element key, for example a player's UUID
 */
final class RepeatedFailureFilter<K> {

    private final Set<K> failing = ConcurrentHashMap.newKeySet();

    /**
     * Records a failure of {@code key}.
     *
     * @param key the failing element
     * @return {@code true} if this is the first failure since the element last succeeded, so it
     *     should be logged
     */
    boolean firstFailure(K key) {
        return failing.add(key);
    }

    /**
     * Records a success of {@code key}.
     *
     * @param key the element that succeeded
     * @return {@code true} if the element had been failing, so its recovery may be logged
     */
    boolean recovered(K key) {
        return failing.remove(key);
    }

    /**
     * Drops any state for an element that left the loop.
     *
     * @param key the element
     */
    void forget(K key) {
        failing.remove(key);
    }

    /** Drops all state, when the loop itself stops. */
    void clear() {
        failing.clear();
    }
}
