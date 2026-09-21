package com.ultikits.plugins.essentials.entity;

import com.ultikits.ultitools.abstracts.data.BaseDataEntity;
import com.ultikits.ultitools.annotations.Column;
import com.ultikits.ultitools.annotations.Table;
import com.ultikits.ultitools.utils.ReflectionUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the invariant every persisted entity of this module has to satisfy for the framework's
 * {@code delById} / {@code update(T)} / {@code getById} / {@code exist(T)} to address the right
 * row: at the moment the data operator reads the entity's fields to build its SQL, the value in
 * the {@code @Column("id")} field must equal what {@code getId()} returns.
 * <p>
 * Why this is the invariant, measured against the framework at
 * {@code UltiTools-Reborn@6.3.0-SNAPSHOT}:
 * <ul>
 *   <li>{@code BaseDataEntity} declares {@code @Column("id") private ID id}, and
 *       {@code SQLiteDataOperator}/{@code MysqlDataOperator} both emit
 *       {@code PRIMARY KEY (`id`)}, so {@code id} is the real persisted primary key.</li>
 *   <li>{@code AbstractRelationalDataOperator#insert} builds its parameter list by walking
 *       {@code ReflectionUtil.getFields(obj.getClass())} and calling {@code field.get(obj)} — it
 *       reads the <em>field</em>, never {@code getId()}. An entity that overrides
 *       {@code getId()}/{@code setId()} to read and write some other field therefore persists a
 *       {@code NULL} {@code id}.</li>
 *   <li>{@code delById} then issues {@code DELETE ... WHERE id = ?} with {@code getId()}'s value,
 *       and {@code update(T)} issues {@code ... WHERE id = ?} the same way. Neither can ever match
 *       a {@code NULL} column, and neither looks at the affected-row count — which is what made
 *       {@code /delhome}, {@code /delwarp}, {@code /unban} and owner-break lock removal all report
 *       success while changing nothing (UltiKits/UltiEssentials#34, #35, #37).</li>
 *   <li>Both {@code insert} and {@code update(T)} call {@code onCreate()} / {@code onUpdate()}
 *       <em>before</em> reading those fields — the framework documents this explicitly — so those
 *       hooks are the one point where an entity can still put the value where the SQL will find
 *       it.</li>
 * </ul>
 * This test reads the same field, through the same framework helper, that the real INSERT reads.
 * It needs no database and no test double: the subject is the entity's own field state.
 * <p>
 * The entity list is <strong>discovered</strong> from the compiled {@code entity} package rather
 * than hard-coded, so a fifth persisted entity added later is covered without anyone remembering
 * to add it here. {@link DiscoveryTests} is the positive control for that discovery — a discovery
 * bug that silently returns nothing would otherwise make every assertion below vacuous.
 *
 * @author wisdomme
 * @version 1.0.0
 */
@DisplayName("Persisted id column invariant (UltiEssentials#34/#35/#37 root cause)")
class PersistedIdColumnTest {

    private static final String ENTITY_PACKAGE = "com.ultikits.plugins.essentials.entity";

    @Nested
    @DisplayName("Discovery (positive control)")
    class DiscoveryTests {

        @Test
        @DisplayName("the entity package scan finds every persisted entity, not an empty list")
        void discoversEveryPersistedEntity() throws Exception {
            List<Class<? extends BaseDataEntity<?>>> found = persistedEntities();

            List<String> names = new ArrayList<>();
            for (Class<? extends BaseDataEntity<?>> type : found) {
                names.add(type.getSimpleName());
            }
            Collections.sort(names);

            // If this list changes, the sweep below changes with it -- that is the point.
            assertThat(names)
                .as("concrete BaseDataEntity subclasses under " + ENTITY_PACKAGE)
                .containsExactly("BanData", "ChestLockData", "HomeData", "WarpData");
        }

        @Test
        @DisplayName("every discovered entity really carries @Table and an id column")
        void discoveredEntitiesArePersisted() throws Exception {
            for (Class<? extends BaseDataEntity<?>> type : persistedEntities()) {
                assertThat(type.isAnnotationPresent(Table.class))
                    .as(type.getSimpleName() + " carries @Table")
                    .isTrue();
                assertThat(idColumnField(type))
                    .as(type.getSimpleName() + " has a field mapped to the `id` column")
                    .isNotNull();
            }
        }
    }

    @Nested
    @DisplayName("The id column the operator will write")
    class IdColumnTests {

        @Test
        @DisplayName("onCreate() leaves the id column holding getId(), for every entity")
        void onCreateSyncsTheIdColumn() throws Exception {
            int checked = 0;
            for (Class<? extends BaseDataEntity<?>> type : persistedEntities()) {
                BaseDataEntity<?> entity = type.getDeclaredConstructor().newInstance();
                String id = UUID.randomUUID().toString();
                setIdAsString(entity, id);

                // Exactly what AbstractRelationalDataOperator#insert does before reading fields.
                entity.onCreate();

                assertThat(readIdColumn(entity))
                    .as(type.getSimpleName() + ": the `id` column value INSERT would persist")
                    .isEqualTo(id);
                assertThat(entity.getId())
                    .as(type.getSimpleName() + ": getId() is unchanged by the sync")
                    .isEqualTo(id);
                checked++;
            }
            assertThat(checked).as("entities actually exercised").isEqualTo(4);
        }

        @Test
        @DisplayName("onUpdate() leaves the id column holding getId(), for every entity")
        void onUpdateSyncsTheIdColumn() throws Exception {
            int checked = 0;
            for (Class<? extends BaseDataEntity<?>> type : persistedEntities()) {
                BaseDataEntity<?> entity = type.getDeclaredConstructor().newInstance();
                String id = UUID.randomUUID().toString();
                setIdAsString(entity, id);

                // Exactly what AbstractRelationalDataOperator#update(T) does before reading fields.
                entity.onUpdate();

                assertThat(readIdColumn(entity))
                    .as(type.getSimpleName() + ": the `id` column value UPDATE would match on")
                    .isEqualTo(id);
                checked++;
            }
            assertThat(checked).as("entities actually exercised").isEqualTo(4);
        }

        @Test
        @DisplayName("an entity with no id of its own keeps a null id column, so insert() can generate one")
        void anEntityWithoutAnIdIsLeftAlone() throws Exception {
            int checked = 0;
            for (Class<? extends BaseDataEntity<?>> type : persistedEntities()) {
                BaseDataEntity<?> entity = type.getDeclaredConstructor().newInstance();

                entity.onCreate();

                // insert() generates an id only when getId() is null; writing a placeholder here
                // would take that branch away from the framework.
                assertThat(readIdColumn(entity))
                    .as(type.getSimpleName() + ": id column with no id set")
                    .isNull();
                assertThat(entity.isNew())
                    .as(type.getSimpleName() + ": still reported as a new entity")
                    .isTrue();
                checked++;
            }
            assertThat(checked).as("entities actually exercised").isEqualTo(4);
        }

        @Test
        @DisplayName("the id the framework generates for an id-less entity reaches the id column too")
        void aFrameworkGeneratedIdReachesTheIdColumn() throws Exception {
            int checked = 0;
            for (Class<? extends BaseDataEntity<?>> type : persistedEntities()) {
                BaseDataEntity<?> entity = type.getDeclaredConstructor().newInstance();

                // insert()'s own two first statements, in order.
                String generated = UUID.randomUUID().toString();
                setIdAsString(entity, generated);
                entity.onCreate();

                assertThat(readIdColumn(entity))
                    .as(type.getSimpleName() + ": generated id reaches the `id` column")
                    .isEqualTo(generated);
                checked++;
            }
            assertThat(checked).as("entities actually exercised").isEqualTo(4);
        }
    }

    // === helpers ===

    /**
     * Reads the value of the field mapped to the {@code id} column, through the same
     * {@link ReflectionUtil#getFields(Class)} walk {@code AbstractRelationalDataOperator#insert}
     * uses to build its parameter list.
     */
    private static Object readIdColumn(BaseDataEntity<?> entity) throws Exception {
        Field field = idColumnField(entity.getClass());
        assertThat(field).as("field mapped to the `id` column").isNotNull();
        field.setAccessible(true);
        return field.get(entity);
    }

    private static Field idColumnField(Class<?> type) {
        for (Field field : ReflectionUtil.getFields(type)) {
            Column column = field.getAnnotation(Column.class);
            if (column != null && "id".equals(column.value())) {
                return field;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static void setIdAsString(BaseDataEntity<?> entity, String id) {
        ((BaseDataEntity<String>) entity).setId(id);
    }

    /**
     * Discovers every concrete {@link BaseDataEntity} subclass compiled under the entity package,
     * including its subpackages, from the classpath directories rather than a hard-coded list.
     * <p>
     * Every root carrying this package is scanned, not just the first: {@code target/test-classes}
     * precedes {@code target/classes} on Surefire's classpath and holds this package's <em>test</em>
     * classes, so {@code getResource} alone finds a real directory that contains no entity at all.
     */
    private static List<Class<? extends BaseDataEntity<?>>> persistedEntities() throws Exception {
        String path = ENTITY_PACKAGE.replace('.', '/');
        List<Class<? extends BaseDataEntity<?>>> types = new ArrayList<>();
        java.util.Enumeration<URL> roots =
            PersistedIdColumnTest.class.getClassLoader().getResources(path);
        int rootCount = 0;
        while (roots.hasMoreElements()) {
            URL root = roots.nextElement();
            if (!"file".equals(root.getProtocol())) {
                continue;
            }
            rootCount++;
            collect(new File(root.toURI()), ENTITY_PACKAGE, types);
        }
        assertThat(rootCount).as("classpath directories carrying " + ENTITY_PACKAGE).isPositive();
        return types;
    }

    @SuppressWarnings("unchecked")
    private static void collect(File dir, String packageName,
                                List<Class<? extends BaseDataEntity<?>>> into) throws Exception {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collect(child, packageName + "." + child.getName(), into);
                continue;
            }
            String name = child.getName();
            if (!name.endsWith(".class") || name.contains("$")) {
                continue;
            }
            Class<?> type = Class.forName(packageName + "." + name.substring(0, name.length() - 6));
            if (BaseDataEntity.class.isAssignableFrom(type) && !Modifier.isAbstract(type.getModifiers())) {
                into.add((Class<? extends BaseDataEntity<?>>) type);
            }
        }
    }
}
