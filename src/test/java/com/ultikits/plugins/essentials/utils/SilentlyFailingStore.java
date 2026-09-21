package com.ultikits.plugins.essentials.utils;

import com.google.gson.Gson;
import com.ultikits.ultitools.abstracts.data.BaseDataEntity;
import com.ultikits.ultitools.entities.WhereCondition;
import com.ultikits.ultitools.interfaces.impl.data.json.SimpleJsonDataOperator;
import com.ultikits.ultitools.utils.ReflectionUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A real {@link SimpleJsonDataOperator} whose delete and/or whole-entity update can be turned into
 * a silent no-op — the exact store behaviour UltiEssentials#34, #35 and #37 observed in production,
 * where the statement ran, matched no row, and returned without telling anyone.
 * <p>
 * This exists because "report success only once the record is verified gone" cannot be proved
 * against a store that always succeeds: that test would pass whether the verification is there or
 * not. The subject under test is the service's verification, so the store is the part that has to
 * be able to fail. Everything else about it — insert, the query paths, the round trip through
 * disk — is the framework's own real implementation, unmodified.
 * <p>
 * The attempt counters are what make a vacuous pass impossible: a test asserting "the service
 * reported failure" also asserts the service actually tried to delete or update, so a service that
 * refuses for some unrelated reason (and never touched the store at all) cannot be mistaken for one
 * that verified.
 *
 * <h2>Why an ignored update also restores the entity</h2>
 * {@code SimpleJsonDataOperator.getAll} hands out the instances it holds in its own cache, not
 * copies. A caller that mutates a returned entity — {@code ban.setActive(false)}, say — has
 * therefore already changed what the store's next read reports, with or without an
 * {@code update(T)} call. Simply skipping the update would change nothing observable, and a service
 * that never verifies anything would look correct.
 * <p>
 * So an ignored update restores the entity to the state this store last handed out, which is what a
 * store that persisted nothing looks like on the next read. Reads are deliberately left aliased
 * rather than detached, because {@code update(T)} cannot carry a detached entity's {@code boolean}
 * field back into the store at all: it copies with {@code BeanCopyUtil}, whose {@code convertValue}
 * unboxes a {@code Number} into a primitive but has no branch for {@code Boolean}, so a boxed
 * {@code Boolean} bound for a primitive {@code boolean} field is silently dropped. Detaching reads
 * would make {@code BanData.active} impossible to change through the store at all and turn the
 * success half of every unban test red for a reason that has nothing to do with the fix.
 *
 * @param <T> the entity type
 * @author wisdomme
 * @version 1.0.0
 */
public class SilentlyFailingStore<T extends BaseDataEntity<String>> extends SimpleJsonDataOperator<T> {

    // These entities carry only primitives, Strings and UUIDs; Gson handles all three natively.
    private static final Gson GSON = new Gson();

    private final Class<T> entityType;
    private final Map<String, String> lastHandedOut = new ConcurrentHashMap<>();
    private boolean ignoreDeletes;
    private boolean ignoreUpdates;
    private int deleteAttempts;
    private int updateAttempts;

    public SilentlyFailingStore(String storeLocation, Class<T> type) {
        super(storeLocation, type);
        this.entityType = type;
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
     * NULL primary key was, rolling the entity back to the state this store last reported.
     */
    public void ignoreUpdates() {
        this.ignoreUpdates = true;
    }

    /**
     * @return how many times a caller asked this store to delete
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
            restoreLastHandedOut(obj);
            return;
        }
        super.update(obj);
    }

    @Override
    public List<T> getAll(WhereCondition... whereConditions) {
        List<T> found = super.getAll(whereConditions);
        for (T entity : found) {
            remember(entity);
        }
        return found;
    }

    @Override
    public T getById(Object id) {
        T found = super.getById(id);
        if (found != null) {
            remember(found);
        }
        return found;
    }

    private void remember(T entity) {
        if (entity.getId() != null) {
            lastHandedOut.put(entity.getId(), GSON.toJson(entity));
        }
    }

    /**
     * Puts {@code target} back into the state this store last handed out for its id, field by
     * field. A plain reflective copy rather than {@code BeanCopyUtil}, because that utility drops
     * {@code boolean} fields (see the class javadoc) — and {@code active} is the field the unban
     * tests turn on.
     */
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    private void restoreLastHandedOut(T target) {
        String snapshot = target.getId() == null ? null : lastHandedOut.get(target.getId());
        if (snapshot == null) {
            return;
        }
        T previous = GSON.fromJson(snapshot, entityType);
        for (Field field : ReflectionUtil.getFields(entityType)) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            try {
                field.set(target, field.get(previous));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Failed to restore field " + field.getName(), e);
            }
        }
    }
}
