package com.ultikits.plugins.essentials.entity.base;

import java.lang.reflect.Field;
import java.util.UUID;

import com.ultikits.ultitools.abstracts.data.BaseDataEntity;
import com.ultikits.ultitools.annotations.Column;
import com.ultikits.ultitools.utils.ReflectionUtil;

import lombok.EqualsAndHashCode;

/**
 * Base class for this module's persisted entities, whose identity is a module-generated
 * {@link UUID} held in a {@code uuid} column.
 * <p>
 * 本模块持久化实体的基类：实体身份为模块自行生成的 UUID，存于 {@code uuid} 列。
 *
 * <h2>Why this class exists</h2>
 * Every entity here identifies itself by a {@link UUID} it generates, and exposes that value
 * through {@code getId()} so the framework's {@code DataOperator} can address it. The framework,
 * however, does not persist {@code getId()} — it persists <em>fields</em>:
 * {@code AbstractRelationalDataOperator#insert} walks {@code ReflectionUtil.getFields(...)} and
 * calls {@code field.get(obj)} for every {@code @Column}-annotated field, while
 * {@code delById}/{@code update(T)}/{@code getById}/{@code exist(T)} all address rows with
 * {@code WHERE id = ?} using {@code getId()}'s value. {@code BaseDataEntity}'s own
 * {@code @Column("id")} field is private and is the table's declared {@code PRIMARY KEY}.
 * <p>
 * Overriding {@code getId()} onto a different field therefore split the two apart: rows were
 * written with a {@code NULL} {@code id}, and every later {@code WHERE id = ?} matched nothing —
 * silently, because the framework discards the affected-row count. That single mismatch was the
 * shared root cause of {@code /delhome} and {@code /delwarp} reporting a deletion that never
 * happened (UltiKits/UltiEssentials#34), {@code /unban} reporting and broadcasting an unban while
 * the ban stayed active (#35), and an owner-broken container's lock record surviving on disk and
 * coming back on the next restart (#37).
 * <p>
 * This class closes that gap once, for every entity that extends it, rather than four times over:
 * {@link #onCreate()} and {@link #onUpdate()} copy {@code getId()} into the inherited {@code id}
 * field. Both hooks are called by the data operators <em>immediately before</em> the fields are
 * read for the SQL parameters — the framework documents that ordering explicitly on both
 * {@code insert} and {@code update(T)} — so this is the last point at which the value can still
 * reach the statement, and it covers every write path (builder, setter, Gson deserialisation)
 * without any of them having to remember anything.
 *
 * <h2>Why both columns are kept</h2>
 * The {@code uuid} column is deliberately <em>not</em> removed in favour of {@code id} alone. Rows
 * written before this fix hold the entity's identity in {@code uuid} and nothing in {@code id};
 * dropping the {@code uuid} mapping would make those rows unreadable and unrepairable, since the
 * only surviving copy of their identity would no longer map to any field. The two columns
 * consequently hold the same value for every row written from here on, and
 * {@link #getPersistedId()} is what tells a row written before this fix (persisted id {@code null},
 * {@code uuid} set) apart from a repaired or new one.
 *
 * @author wisdomme
 * @version 1.0.0
 */
// callSuper = false: BaseDataEntity's own equals covers its `id` field, which is by construction
// either null (not yet persisted) or exactly getId(). Including it would make an entity compare
// unequal to itself-before-onCreate() -- a lifecycle-dependent equality this module never had,
// because `id` was always null before this class existed. Equality stays over `uuid` (plus each
// subclass's own fields via their callSuper = true), which is what it effectively was.
@EqualsAndHashCode(callSuper = false)
public abstract class UuidKeyedDataEntity extends BaseDataEntity<String> {

    /**
     * The module-generated identity of this record. Also mirrored into the inherited
     * {@code @Column("id")} primary key when the entity is persisted — see the class javadoc.
     */
    @Column("uuid")
    private UUID uuid;

    protected UuidKeyedDataEntity() {
        // Required by Gson and by the framework's row materialisation.
    }

    protected UuidKeyedDataEntity(UUID uuid) {
        this.uuid = uuid;
    }

    /**
     * Gets this record's module-generated identity.
     *
     * @return the UUID, or null if this entity has not been given one yet
     */
    public UUID getUuid() {
        return uuid;
    }

    /**
     * Sets this record's module-generated identity.
     *
     * @param uuid the UUID to use
     */
    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    @Override
    public String getId() {
        return uuid == null ? null : uuid.toString();
    }

    @Override
    public void setId(String id) {
        this.uuid = id == null ? null : UUID.fromString(id);
    }

    /**
     * Sets this record's identity from a {@link UUID}, without going through its string form.
     *
     * @param id the UUID to use
     */
    public void setId(UUID id) {
        this.uuid = id;
    }

    /**
     * Reads the value currently held in the inherited {@code @Column("id")} primary-key field —
     * the value the data operator will actually write, and the value {@code WHERE id = ?} will
     * later match on.
     * <p>
     * {@code null} here on an entity loaded from the store means the row predates this fix and
     * cannot be addressed by id; that is exactly what the start-up repair looks for.
     *
     * @return the persisted primary-key value, or null if this record has never had one written
     */
    public String getPersistedId() {
        return super.getId();
    }

    /**
     * Describes this record field by field, for a log line an operator has to be able to act on.
     * <p>
     * Not {@code toString}: Lombok's {@code @Data} generates one per class with
     * {@code callSuper = false}, so a subclass's {@code toString} omits every inherited field -- which
     * on these entities means a home printed without its world or coordinates and without its own
     * identity, i.e. exactly the fields needed to re-create it. Switching {@code toString} to {@code
     * callSuper = true} would fix today's output and leave the promise resting on a shape a later
     * change can silently alter again.
     * <p>
     * The field list is not written out here either. It is every {@code @Column}-mapped field of the
     * concrete class, read through the same {@link ReflectionUtil#getFields(Class)} walk the data
     * operator itself uses to build its statements -- so it is by construction the set of fields that
     * make up the stored record, and a field added later is included without anyone remembering to.
     * Each is printed as {@code column=value}, so the line names what it prints.
     *
     * @return this record's stored columns and their values
     */
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    public String describeForRecovery() {
        StringBuilder description = new StringBuilder(getClass().getSimpleName()).append('[');
        boolean first = true;
        for (Field field : ReflectionUtil.getFields(getClass())) {
            Column column = field.getAnnotation(Column.class);
            if (column == null) {
                continue;
            }
            if (!first) {
                description.append(", ");
            }
            first = false;
            field.setAccessible(true);
            Object value;
            try {
                value = field.get(this);
            } catch (IllegalAccessException e) {
                value = "<unreadable>";
            }
            description.append(column.value()).append('=').append(value);
        }
        return description.append(']').toString();
    }

    /**
     * Copies {@link #getId()} into the inherited primary-key field, so the {@code id} column the
     * operator is about to read carries this record's identity.
     */
    private void syncPersistedId() {
        super.setId(getId());
    }

    @Override
    public void onCreate() {
        super.onCreate();
        syncPersistedId();
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        syncPersistedId();
    }
}
