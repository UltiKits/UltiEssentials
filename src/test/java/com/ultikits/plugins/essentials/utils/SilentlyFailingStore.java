package com.ultikits.plugins.essentials.utils;

import com.ultikits.ultitools.abstracts.data.BaseDataEntity;
import com.ultikits.ultitools.entities.WhereCondition;
import com.ultikits.ultitools.interfaces.impl.data.json.SimpleJsonDataOperator;

/**
 * A real {@link SimpleJsonDataOperator} whose delete and/or update can be turned into a silent
 * no-op — the exact store behaviour UltiEssentials#34, #35 and #37 observed in production, where
 * the statement ran, matched no row, and returned without telling anyone.
 * <p>
 * This exists because "report success only once the record is verified gone" cannot be proved
 * against a store that always succeeds: that test would pass whether the verification is there or
 * not. The subject under test is the service's verification, so the store is the part that has to
 * be able to fail. Everything else about it — insert, the query paths, the round trip through
 * disk — is the framework's own real implementation, unmodified.
 * <p>
 * The attempt counters are what make a vacuous pass impossible: a test asserting "the service
 * reported failure" also asserts the service actually tried to delete, so a service that refuses
 * for some unrelated reason (and never touched the store at all) cannot be mistaken for one that
 * verified.
 *
 * @param <T> the entity type
 * @author wisdomme
 * @version 1.0.0
 */
public class SilentlyFailingStore<T extends BaseDataEntity<String>> extends SimpleJsonDataOperator<T> {

    private boolean ignoreDeletes;
    private boolean ignoreUpdates;
    private int deleteAttempts;
    private int updateAttempts;

    public SilentlyFailingStore(String storeLocation, Class<T> type) {
        super(storeLocation, type);
    }

    /**
     * Makes every subsequent delete a silent no-op, as a {@code WHERE id = ?} against a NULL
     * primary key was.
     */
    public void ignoreDeletes() {
        this.ignoreDeletes = true;
    }

    /**
     * Makes every subsequent {@code update(T)} a silent no-op, as a {@code WHERE id = ?} against a
     * NULL primary key was.
     */
    public void ignoreUpdates() {
        this.ignoreUpdates = true;
    }

    /**
     * @return how many times a caller asked this store to delete by id
     */
    public int deleteAttempts() {
        return deleteAttempts;
    }

    /**
     * @return how many times a caller asked this store to update a whole entity
     */
    public int updateAttempts() {
        return updateAttempts;
    }

    @Override
    public synchronized void delById(Object id) {
        deleteAttempts++;
        if (ignoreDeletes) {
            return;
        }
        super.delById(id);
    }

    @Override
    public synchronized void del(WhereCondition... whereConditions) {
        deleteAttempts++;
        if (ignoreDeletes) {
            return;
        }
        super.del(whereConditions);
    }

    @Override
    public synchronized void update(T obj) {
        updateAttempts++;
        if (ignoreUpdates) {
            return;
        }
        super.update(obj);
    }
}
