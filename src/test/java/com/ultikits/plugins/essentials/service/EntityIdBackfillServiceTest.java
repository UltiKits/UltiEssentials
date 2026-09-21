package com.ultikits.plugins.essentials.service;

import com.google.gson.Gson;
import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.entity.BanData;
import com.ultikits.plugins.essentials.entity.ChestLockData;
import com.ultikits.plugins.essentials.entity.HomeData;
import com.ultikits.plugins.essentials.entity.WarpData;
import com.ultikits.plugins.essentials.entity.base.UuidKeyedDataEntity;
import com.ultikits.plugins.essentials.service.EntityIdBackfillService.Outcome;
import com.ultikits.plugins.essentials.utils.MockBukkitHelper;
import java.util.Arrays;
import com.ultikits.plugins.essentials.utils.SilentlyFailingStore;
import com.ultikits.plugins.essentials.utils.TestHelper;
import com.ultikits.ultitools.abstracts.data.BaseDataEntity;
import com.ultikits.ultitools.interfaces.Cached;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.impl.data.json.SimpleJsonDataOperator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import com.ultikits.ultitools.abstracts.UltiToolsPlugin;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * The start-up repair for records written before UltiKits/UltiEssentials#34 was fixed.
 * <p>
 * Every fixture here is a record written <em>exactly the way the old code wrote it</em>: the entity
 * serialised by Gson with no {@code id} key at all, since Gson omits a null field and the primary
 * key was always null. The file is placed in the store directory and the operator is constructed
 * over it, which is what happens on a real server's next boot. Nothing about the legacy shape is
 * simulated.
 * <p>
 * The repair rewrites operators' data, so the properties asserted here are the ones that decide
 * whether it is safe to ship: it repairs what it recognises, it is idempotent, and it leaves alone
 * anything it cannot key safely rather than guessing.
 *
 * @author wisdomme
 * @version 1.0.0
 */
@DisplayName("Start-up primary-key repair (UltiKits/UltiEssentials#34)")
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class EntityIdBackfillServiceTest {

    private static final Gson GSON = new Gson();

    @TempDir
    Path tempDir;

    private EntityIdBackfillService backfill;

    @BeforeEach
    void setUp() {
        MockBukkitHelper.clearForeignServer();
        MockBukkit.mock();
        TestHelper.mockUltiToolsInstance();
        backfill = new EntityIdBackfillService();
    }

    @AfterEach
    void tearDown() {
        MockBukkitHelper.safeUnmock();
    }

    @Nested
    @DisplayName("Repairing")
    class RepairTests {

        @Test
        @DisplayName("a record written with no primary key gets one, and is then addressable by id")
        void aLegacyRecordIsRepaired() throws Exception {
            HomeData legacy = home("farm");
            writeLegacyRecord("homes", legacy);
            SimpleJsonDataOperator<HomeData> operator = operatorOver("homes", HomeData.class);
            assertThat(operator.getAll()).hasSize(1);
            assertThat(operator.getAll().get(0).getPersistedId())
                .as("precondition: the fixture really is a record with no primary key")
                .isNull();

            Outcome outcome = backfill.repair(operator, "HomeData");

            assertThat(outcome.repaired()).isEqualTo(1);
            assertThat(outcome.skipped()).isZero();
            List<HomeData> after = operator.getAll();
            assertThat(after).hasSize(1);
            assertThat(after.get(0).getPersistedId()).isEqualTo(legacy.getId());
            assertThat(after.get(0).getName()).as("the record's own data is unchanged").isEqualTo("farm");
            assertThat(operator.getById(legacy.getId()))
                .as("the whole point: the record can now be addressed by id")
                .isNotNull();
        }

        @Test
        @DisplayName("every covered entity type is repaired, not only homes")
        void everyCoveredTypeIsRepaired() throws Exception {
            writeLegacyRecord("homes", home("farm"));
            writeLegacyRecord("warps", warp("shop"));
            writeLegacyRecord("bans", ban());
            writeLegacyRecord("locks", lock());

            assertThat(backfill.repair(operatorOver("homes", HomeData.class), "HomeData").repaired()).isEqualTo(1);
            assertThat(backfill.repair(operatorOver("warps", WarpData.class), "WarpData").repaired()).isEqualTo(1);
            assertThat(backfill.repair(operatorOver("bans", BanData.class), "BanData").repaired()).isEqualTo(1);
            assertThat(backfill.repair(operatorOver("locks", ChestLockData.class), "ChestLockData").repaired()).isEqualTo(1);
        }

        @Test
        @DisplayName("a record that already has its key is not rewritten")
        void anAlreadyCorrectRecordIsLeftAlone() throws Exception {
            SimpleJsonDataOperator<HomeData> operator = operatorOver("homes", HomeData.class);
            operator.insert(home("farm"));

            Outcome outcome = backfill.repair(operator, "HomeData");

            assertThat(outcome.repaired()).isZero();
            assertThat(outcome.skipped()).isZero();
        }

        @Test
        @DisplayName("a cache-backed store is not asked to delete, because the key is written in place")
        void aCacheBackedStoreIsNotAskedToDelete() throws Exception {
            // A cache-backed operator hands out the instances it holds, so insert's onCreate() writes
            // the key onto the stored record itself. The delete would remove and re-add the same
            // entry while costing a full-cache Gson pass, which was the second superlinear term
            // behind gate 1 MAJOR-05 -- measured 11.64 s for 800 records before, 0.20 s after.
            HomeData legacy = home("farm");
            writeLegacyRecord("homes", legacy);
            SilentlyFailingStore<HomeData> store = countingOperatorOver("homes", HomeData.class);

            Outcome outcome = backfill.repair(store, "HomeData");

            assertThat(outcome.repaired()).as("the record is still repaired").isEqualTo(1);
            assertThat(store.getAll().get(0).getPersistedId()).isEqualTo(legacy.getId());
            assertThat(store.deleteAttempts()).as("no delete was needed").isZero();
        }

        @Test
        @DisplayName("a store that is not cache-backed IS asked to delete before the record is written back")
        void aRowBackedStoreIsAskedToDelete() throws Exception {
            // The relational backend materialises every row afresh and its insert is a real INSERT,
            // so without the delete the un-keyed row would survive ALONGSIDE a new keyed one: one
            // home listed twice, the older row still undeletable. No JDBC driver is on this module's
            // test classpath (framework issue filed), so the shape is pinned through an operator that
            // is deliberately NOT Cached -- which is the property the repair actually branches on --
            // rather than against a real database. Gate 3 is what executes the relational round trip.
            HomeData legacy = home("farm");
            writeLegacyRecord("homes", legacy);
            RowBackedStore<HomeData> store = new RowBackedStore<>(
                new SilentlyFailingStore<>(tempDir.resolve("homes").toFile().getAbsolutePath(),
                    HomeData.class));

            Outcome outcome = backfill.repair(store, "HomeData");

            assertThat(outcome.repaired()).isEqualTo(1);
            assertThat(store.deletes())
                .as("one delete of the un-keyed record, before it is written back")
                .isEqualTo(1);
        }

        @Test
        @DisplayName("a record that needs no repair is neither deleted nor written")
        void anAlreadyCorrectRecordIsNotTouched() throws Exception {
            SilentlyFailingStore<HomeData> store = countingOperatorOver("homes", HomeData.class);
            store.insert(home("farm"));

            backfill.repair(store, "HomeData");

            assertThat(store.deleteAttempts()).as("nothing was deleted").isZero();
        }

        @Test
        @DisplayName("a repaired record survives the reload a restart performs")
        void aRepairedRecordSurvivesAReload() throws Exception {
            HomeData legacy = home("farm");
            writeLegacyRecord("homes", legacy);
            backfill.repair(operatorOver("homes", HomeData.class), "HomeData");

            // A fresh operator over the same directory: what the next boot builds.
            SimpleJsonDataOperator<HomeData> rebooted = operatorOver("homes", HomeData.class);

            assertThat(rebooted.getAll()).hasSize(1);
            assertThat(rebooted.getAll().get(0).getPersistedId()).isEqualTo(legacy.getId());
        }
    }

    @Nested
    @DisplayName("Idempotence")
    class IdempotenceTests {

        @Test
        @DisplayName("a second run repairs nothing and changes nothing")
        void aSecondRunIsANoOp() throws Exception {
            writeLegacyRecord("homes", home("farm"));
            SimpleJsonDataOperator<HomeData> operator = operatorOver("homes", HomeData.class);
            assertThat(backfill.repair(operator, "HomeData").repaired()).isEqualTo(1);
            List<String> afterFirst = storeContents("homes");

            Outcome second = backfill.repair(operator, "HomeData");

            assertThat(second.repaired()).as("records repaired on the second run").isZero();
            assertThat(second.skipped()).as("records skipped on the second run").isZero();
            assertThat(storeContents("homes"))
                .as("the stored bytes after a second run")
                .isEqualTo(afterFirst);
        }

        @Test
        @DisplayName("a second run over a mixed store leaves the repaired and the skipped as they were")
        void aSecondRunOverAMixedStoreIsANoOp() throws Exception {
            writeLegacyRecord("homes", home("farm"));
            writeDuplicateIdentityRecords("homes", home("shared"));
            SimpleJsonDataOperator<HomeData> operator = operatorOver("homes", HomeData.class);
            Outcome first = backfill.repair(operator, "HomeData");
            assertThat(first.repaired()).isEqualTo(1);
            assertThat(first.skipped()).as("the two records sharing one identity").isEqualTo(2);
            List<String> afterFirst = storeContents("homes");

            Outcome second = backfill.repair(operator, "HomeData");

            assertThat(second.repaired()).isZero();
            assertThat(second.skipped()).as("still skipped, for the same reason").isEqualTo(2);
            assertThat(storeContents("homes")).isEqualTo(afterFirst);
        }
    }

    @Nested
    @DisplayName("Skipping rather than guessing")
    class SkipTests {

        @Test
        @DisplayName("records sharing one identity are all left untouched, because deleting one would delete both")
        void recordsSharingAnIdentityAreLeftAlone() throws Exception {
            writeDuplicateIdentityRecords("homes", home("shared"));
            SimpleJsonDataOperator<HomeData> operator = operatorOver("homes", HomeData.class);
            List<String> before = storeContents("homes");

            Outcome outcome = backfill.repair(operator, "HomeData");

            assertThat(outcome.repaired()).isZero();
            assertThat(outcome.skipped()).isEqualTo(2);
            assertThat(storeContents("homes")).as("nothing was written").isEqualTo(before);
            assertThat(operator.getAll()).as("both records are still readable").hasSize(2);
        }

        @Test
        @DisplayName("a record whose stored key disagrees with its identity is left untouched")
        void aDisagreeingKeyIsLeftAlone() throws Exception {
            HomeData legacy = home("farm");
            // A record carrying a primary key that is not its own identity: no repair can decide
            // which of the two is the real one, so it must not decide.
            legacy.setId(legacy.getUuid());
            writeRecordWithKey("homes", legacy, UUID.randomUUID().toString());
            SimpleJsonDataOperator<HomeData> operator = operatorOver("homes", HomeData.class);
            List<String> before = storeContents("homes");

            Outcome outcome = backfill.repair(operator, "HomeData");

            assertThat(outcome.repaired()).isZero();
            assertThat(outcome.skipped()).isEqualTo(1);
            assertThat(storeContents("homes")).isEqualTo(before);
        }

        @Test
        @DisplayName("a record with no identity at all is left untouched")
        void aRecordWithoutAnIdentityIsLeftAlone() throws Exception {
            HomeData orphan = home("farm");
            orphan.setUuid(null);
            writeRecordAt("homes", "orphan", orphan);
            SimpleJsonDataOperator<HomeData> operator = operatorOver("homes", HomeData.class);
            assertThat(operator.getAll().get(0).getId())
                .as("precondition: the fixture really has no identity")
                .isNull();
            List<String> before = storeContents("homes");

            Outcome outcome = backfill.repair(operator, "HomeData");

            assertThat(outcome.repaired()).isZero();
            assertThat(outcome.skipped()).isEqualTo(1);
            assertThat(storeContents("homes")).isEqualTo(before);
        }

        @Test
        @DisplayName("one unrepairable record does not stop the rest of the store being repaired")
        void oneSkippedRecordDoesNotStopTheOthers() throws Exception {
            writeLegacyRecord("homes", home("farm"));
            writeLegacyRecord("homes", home("mine"));
            writeDuplicateIdentityRecords("homes", home("shared"));
            SimpleJsonDataOperator<HomeData> operator = operatorOver("homes", HomeData.class);

            Outcome outcome = backfill.repair(operator, "HomeData");

            assertThat(outcome.repaired()).isEqualTo(2);
            assertThat(outcome.skipped()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("run(), and the key that can hold it off")
    class ConfigurationGateTests {

        @Test
        @DisplayName("with the key enabled, run() repairs every covered entity type")
        void enabledRepairsEverything() throws Exception {
            HomeData legacyHome = home("farm");
            WarpData legacyWarp = warp("shop");
            writeLegacyRecord("homes", legacyHome);
            writeLegacyRecord("warps", legacyWarp);
            wire(true);

            EntityIdBackfillService.Report report = backfill.run();

            assertThat(report.totalRepaired()).isEqualTo(2);
            assertThat(operatorOver("homes", HomeData.class).getAll().get(0).getPersistedId())
                .isEqualTo(legacyHome.getId());
            assertThat(operatorOver("warps", WarpData.class).getAll().get(0).getPersistedId())
                .isEqualTo(legacyWarp.getId());
        }

        @Test
        @DisplayName("with no configuration available at all, run() refuses rather than proceeding")
        void anUnresolvedConfigRefuses() throws Exception {
            // The safe default on a gate that could not be read is to refuse: "we could not read the
            // key that turns this off" must not mean "run it" for something that changes an operator's
            // data (gate 1 MINOR-06). Asserted as stored bytes, like the disabled case.
            writeLegacyRecord("homes", home("farm"));
            List<String> before = storeContents("homes");
            wire(true);
            setBackfillField("config", null);

            EntityIdBackfillService.Report report = backfill.run();

            assertThat(report.totalRepaired()).isZero();
            assertThat(storeContents("homes")).as("stored bytes").isEqualTo(before);
        }

        @Test
        @DisplayName("with the key disabled, run() writes nothing and the records keep no key")
        void disabledRepairsNothing() throws Exception {
            writeLegacyRecord("homes", home("farm"));
            writeLegacyRecord("warps", warp("shop"));
            List<String> homesBefore = storeContents("homes");
            List<String> warpsBefore = storeContents("warps");
            wire(false);

            EntityIdBackfillService.Report report = backfill.run();

            assertThat(report.totalRepaired()).isZero();
            assertThat(report.totalSkipped()).isZero();
            assertThat(storeContents("homes")).as("stored bytes").isEqualTo(homesBefore);
            assertThat(storeContents("warps")).as("stored bytes").isEqualTo(warpsBefore);
            assertThat(operatorOver("homes", HomeData.class).getAll().get(0).getPersistedId())
                .as("still un-keyed, which is what the operator asked for")
                .isNull();
        }

        /**
         * A plugin handing out one real store per covered entity type, plus a config whose repair
         * key answers {@code enabled}. Stores are created once and reused, so a second lookup
         * during the same run sees what the first one wrote.
         */
        private void wire(boolean enabled) throws Exception {
            EssentialsConfig config = mock(EssentialsConfig.class);
            lenient().when(config.isDataRepairEnabled()).thenReturn(enabled);

            UltiToolsPlugin plugin = mock(UltiToolsPlugin.class);
            lenient().when(plugin.getDataOperator(HomeData.class))
                .thenReturn(operatorOver("homes", HomeData.class));
            lenient().when(plugin.getDataOperator(WarpData.class))
                .thenReturn(operatorOver("warps", WarpData.class));
            lenient().when(plugin.getDataOperator(BanData.class))
                .thenReturn(operatorOver("bans", BanData.class));
            lenient().when(plugin.getDataOperator(ChestLockData.class))
                .thenReturn(operatorOver("locks", ChestLockData.class));

            setBackfillField("plugin", plugin);
            setBackfillField("config", config);
        }

        @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
        void setBackfillField(String name, Object value) throws Exception {
            java.lang.reflect.Field field = EntityIdBackfillService.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(backfill, value);
        }
    }

    @Nested
    @DisplayName("One transaction for the whole type (gate 1 MAJOR-05)")
    class TransactionShapeTests {

        @Test
        @DisplayName("repairing many records opens one transaction, not one per record")
        void oneTransactionForTheWholeType() throws Exception {
            // The cost this pins is the JSON operator's per-transaction full-cache Gson deep copy:
            // one per record made the repair superlinear on the main thread inside onEnable
            // (measured 0.66/0.73/2.32/11.64 s for 100/200/400/800 records). Asserted as the number
            // of transactions rather than as wall time, because a timing assertion on a shared
            // machine is a flaky test that proves nothing on the day it passes.
            for (int i = 0; i < 25; i++) {
                writeLegacyRecord("homes", home("home" + i));
            }
            CountingTransactionStore<HomeData> store = new CountingTransactionStore<>(
                tempDir.resolve("homes").toFile().getAbsolutePath(), HomeData.class);

            Outcome outcome = backfill.repair(store, "HomeData");

            assertThat(outcome.repaired()).as("records repaired").isEqualTo(25);
            assertThat(store.transactions()).as("transactions opened for 25 records").isEqualTo(1);
        }

        @Test
        @DisplayName("a store that hands out detached copies is reported as repairing nothing, not as succeeding")
        void aDetachedStoreIsNotReportedAsRepaired() throws Exception {
            // Skipping the delete on a cache-backed store depends on that store handing out the
            // instances it holds, so insert's onCreate() writes the key onto the stored record. If
            // that ever stops being true -- and the framework has an open question about exactly this
            // (UltiKits/UltiTools-Reborn#522 asks whether reads should be detached) -- the repair
            // would write nothing. It must then SAY so rather than counting the records as repaired,
            // which is what the confirmation step exists for. Without it, this reports 4 repaired and
            // the records still have no key.
            for (int i = 0; i < 4; i++) {
                writeLegacyRecord("homes", home("home" + i));
            }
            DetachedReadStore<HomeData> store = new DetachedReadStore<>(
                operatorOver("homes", HomeData.class), HomeData.class);

            Outcome outcome = backfill.repair(store, "HomeData");

            assertThat(outcome.repaired()).as("nothing reached the store").isZero();
            assertThat(outcome.skipped()).as("all four are reported as untouched").isEqualTo(4);
            assertThat(store.getAll()).as("and none of them was destroyed in the attempt").hasSize(4);
            for (HomeData record : store.getAll()) {
                assertThat(record.getPersistedId()).as("still un-keyed, and reported as such").isNull();
            }
        }

        @Test
        @DisplayName("a repair whose write to disk fails is reported as untouched, not as repaired")
        void aFailedFlushIsNotReportedAsRepaired() throws Exception {
            // The keys reach the cache but not the disk, so the records come back un-keyed after a
            // restart. An INFO line claiming them would be claiming a durable write that did not
            // happen, which is the same class as the counts this repair exists to make trustworthy
            // (gate 2 round 2).
            for (int i = 0; i < 3; i++) {
                writeLegacyRecord("homes", home("home" + i));
            }
            UnwritableStore<HomeData> store = new UnwritableStore<>(
                tempDir.resolve("homes").toFile().getAbsolutePath(), HomeData.class);

            Outcome outcome = backfill.repair(store, "HomeData");

            assertThat(store.flushAttempts()).as("it really tried to write them").isEqualTo(1);
            assertThat(outcome.repaired()).as("nothing durable, so nothing claimed").isZero();
            assertThat(outcome.skipped()).as("all three reported as untouched").isEqualTo(3);
        }

        @Test
        @DisplayName("a store with nothing to repair opens no transaction at all")
        void nothingToRepairOpensNoTransaction() throws Exception {
            CountingTransactionStore<HomeData> store = new CountingTransactionStore<>(
                tempDir.resolve("homes").toFile().getAbsolutePath(), HomeData.class);
            store.insert(home("farm"));

            backfill.repair(store, "HomeData");

            assertThat(store.transactions()).isZero();
        }
    }

    @Nested
    @DisplayName("A key another record already holds (gate 1 MAJOR-05 pre-check)")
    class CollisionTests {

        @Test
        @DisplayName("a record whose identity is another record's key is left untouched, and the rest are repaired")
        void aCollidingIdentityIsSkipped() throws Exception {
            HomeData collides = home("farm");
            HomeData holder = home("shop");
            // holder already carries `collides`'s identity as its own primary key, so writing that
            // key for `collides` would collide on it. Before this pre-check the attempt was made and
            // rolled back -- which, with one transaction for the whole type, would take every other
            // record's repair down with it.
            writeRecordWithKey("homes", holder, collides.getId());
            writeLegacyRecord("homes", collides);
            writeLegacyRecord("homes", home("mine"));
            SimpleJsonDataOperator<HomeData> operator = operatorOver("homes", HomeData.class);

            Outcome outcome = backfill.repair(operator, "HomeData");

            assertThat(outcome.repaired()).as("the record that could be keyed safely").isEqualTo(1);
            assertThat(outcome.skipped()).as("the colliding record, plus the holder itself").isEqualTo(2);
            assertThat(operator.getAll()).as("nothing was lost").hasSize(3);
        }
    }

    @Nested
    @DisplayName("Recovery description (gate 1 MAJOR-06)")
    class RecoveryDescriptionTests {

        @Test
        @DisplayName("every stored column of every entity appears, named, including inherited ones")
        void everyStoredColumnIsNamed() throws Exception {
            for (UuidKeyedDataEntity record : Arrays.asList(home("farm"), warp("shop"), ban(), lock())) {
                String description = record.describeForRecovery();
                int columns = 0;
                for (java.lang.reflect.Field field
                        : com.ultikits.ultitools.utils.ReflectionUtil.getFields(record.getClass())) {
                    com.ultikits.ultitools.annotations.Column column =
                        field.getAnnotation(com.ultikits.ultitools.annotations.Column.class);
                    if (column == null) {
                        continue;
                    }
                    columns++;
                    assertThat(description)
                        .as(record.getClass().getSimpleName() + " recovery line names " + column.value())
                        .contains(column.value() + "=");
                }
                assertThat(columns)
                    .as(record.getClass().getSimpleName() + " columns examined (positive control)")
                    .isGreaterThan(4);
            }
        }

        @Test
        @DisplayName("a home's line carries the coordinates and identity its toString omits")
        void aHomeLineCarriesWhatToStringOmits() throws Exception {
            HomeData home = home("farm");

            String description = home.describeForRecovery();

            // The exact omissions gate 1 measured: @Data's toString is callSuper = false, so the
            // inherited world, coordinates and uuid are absent from it.
            assertThat(home.toString()).doesNotContain("world");
            assertThat(description).contains("world=", "x=", "y=", "z=", "yaw=", "pitch=", "uuid=");
        }
    }

    @Nested
    @DisplayName("Coverage")
    class CoverageTests {

        @Test
        @DisplayName("every persisted entity in this module is covered by the repair")
        void everyPersistedEntityIsCovered() throws Exception {
            List<String> covered = new ArrayList<>();
            for (Class<?> type : EntityIdBackfillService.REPAIRED_TYPES) {
                covered.add(type.getSimpleName());
            }
            Collections.sort(covered);

            List<String> persisted = discoverPersistedEntities();
            Collections.sort(persisted);

            assertThat(persisted).as("positive control: the scan found entities at all").isNotEmpty();
            assertThat(covered)
                .as("a persisted entity absent from REPAIRED_TYPES would never be repaired")
                .isEqualTo(persisted);
        }
    }

    /** A real operator whose write to disk always fails, as a full or unwritable disk would. */
    private static final class UnwritableStore<T extends BaseDataEntity<String>>
            extends SimpleJsonDataOperator<T> {
        private int flushAttempts;

        UnwritableStore(String storeLocation, Class<T> type) {
            super(storeLocation, type);
        }

        int flushAttempts() {
            return flushAttempts;
        }

        @Override
        public synchronized void flush() {
            flushAttempts++;
            throw new IllegalStateException("disk unwritable");
        }
    }

    /**
     * A {@link DataOperator} that is deliberately <strong>not</strong> {@link Cached}, delegating
     * everything to a real operator and counting the deletes it is asked for.
     * <p>
     * The repair branches on {@code instanceof Cached} — "can a stored row exist independently of the
     * object representing it" — so that is the property this pins, rather than pretending to be
     * SQLite. Needed because no JDBC driver is on this module's test classpath and the pom must not
     * change in this phase.
     */
    private static final class RowBackedStore<T extends BaseDataEntity<String>> implements DataOperator<T> {
        private final DataOperator<T> delegate;
        private int deletes;

        RowBackedStore(DataOperator<T> delegate) {
            this.delegate = delegate;
        }

        int deletes() {
            return deletes;
        }

        @Override
        public void del(com.ultikits.ultitools.entities.WhereCondition... whereConditions) {
            deletes++;
            delegate.del(whereConditions);
        }

        @Override public boolean exist(T object) { return delegate.exist(object); }
        @Override public boolean exist(com.ultikits.ultitools.entities.WhereCondition... c) { return delegate.exist(c); }
        @Override public T getById(Object id) { return delegate.getById(id); }
        @Override public List<T> getAll() { return delegate.getAll(); }
        @Override public List<T> getAll(com.ultikits.ultitools.entities.WhereCondition... c) { return delegate.getAll(c); }
        @Override public List<T> getLike(String column, String value, LikeType likeType) {
            return delegate.getLike(column, value, likeType);
        }
        @Override public List<T> page(int page, int size,
                com.ultikits.ultitools.entities.WhereCondition... c) { return delegate.page(page, size, c); }
        @Override public void insert(T obj) { delegate.insert(obj); }
        @Override public void delById(Object id) { deletes++; delegate.delById(id); }
        @Override public void update(String column, Object value, Object id) { delegate.update(column, value, id); }
        @Override public void update(T obj) throws IllegalAccessException { delegate.update(obj); }
    }

    /**
     * A <strong>cache-backed</strong> operator that returns a fresh copy of every entity it is read
     * for: the shape {@code SimpleJsonDataOperator} would take if the framework ever detached its
     * reads, which UltiKits/UltiTools-Reborn#522 asks about.
     * <p>
     * It must be {@link Cached}, or the repair takes its row-backed branch, deletes each record and
     * then cannot write it back -- which destroys the records and makes every assertion here pass for
     * the wrong reason. Measured: that is exactly what happened on the first attempt at this test.
     */
    private static final class DetachedReadStore<T extends BaseDataEntity<String>>
            implements DataOperator<T>, Cached {
        private static final Gson COPY = new Gson();
        private final DataOperator<T> delegate;
        private final Class<T> type;

        DetachedReadStore(DataOperator<T> delegate, Class<T> type) {
            this.delegate = delegate;
            this.type = type;
        }

        private List<T> detach(List<T> found) {
            List<T> copies = new ArrayList<>(found.size());
            for (T each : found) {
                copies.add(COPY.fromJson(COPY.toJson(each), type));
            }
            return copies;
        }

        @Override public List<T> getAll() { return detach(delegate.getAll()); }
        @Override public List<T> getAll(com.ultikits.ultitools.entities.WhereCondition... c) { return detach(delegate.getAll(c)); }
        @Override public T getById(Object id) {
            T found = delegate.getById(id);
            return found == null ? null : COPY.fromJson(COPY.toJson(found), type);
        }
        @Override public boolean exist(T object) { return delegate.exist(object); }
        @Override public boolean exist(com.ultikits.ultitools.entities.WhereCondition... c) { return delegate.exist(c); }
        @Override public List<T> getLike(String column, String value, LikeType likeType) { return detach(delegate.getLike(column, value, likeType)); }
        @Override public List<T> page(int page, int size, com.ultikits.ultitools.entities.WhereCondition... c) { return detach(delegate.page(page, size, c)); }
        @Override public void insert(T obj) { /* a detached entity reaches nothing the store keeps */ }
        @Override public void flush() { /* nothing of its own to write */ }
        @Override public void gc() { /* nothing of its own to collect */ }
        @Override public void del(com.ultikits.ultitools.entities.WhereCondition... c) { delegate.del(c); }
        @Override public void delById(Object id) { delegate.delById(id); }
        @Override public void update(String column, Object value, Object id) { delegate.update(column, value, id); }
        @Override public void update(T obj) throws IllegalAccessException { delegate.update(obj); }
    }

    /**
     * A real operator that counts the transactions opened on it. Declared on the outer class because
     * a {@code @Nested} class is an inner class and cannot hold a static member.
     */
    private static final class CountingTransactionStore<T extends BaseDataEntity<String>>
            extends SimpleJsonDataOperator<T> {
        private int transactions;

        CountingTransactionStore(String storeLocation, Class<T> type) {
            super(storeLocation, type);
        }

        int transactions() {
            return transactions;
        }

        @Override
        public synchronized <R> R transaction(java.util.concurrent.Callable<R> action) throws Exception {
            transactions++;
            return super.transaction(action);
        }
    }

    // === fixtures ===

    /**
     * Writes a record the way the code before the fix wrote it: Gson omits the null primary key, so
     * the document has no {@code id} key at all, and the file is named after the identity the old
     * cache keyed on.
     */
    private void writeLegacyRecord(String store, UuidKeyedDataEntity record) throws IOException {
        writeRecordAt(store, record.getId(), record);
    }

    /**
     * Two documents holding the same identity — the shape a store can reach when a write happened
     * twice for one logical record. Deleting by identity would take both.
     */
    private void writeDuplicateIdentityRecords(String store, UuidKeyedDataEntity record) throws IOException {
        writeRecordAt(store, record.getId(), record);
        writeRecordAt(store, record.getId() + "-copy", record);
    }

    private void writeRecordWithKey(String store, UuidKeyedDataEntity record, String key) throws IOException {
        String json = GSON.toJson(record);
        assertThat(json).doesNotContain("\"id\"");
        json = "{\"id\":\"" + key + "\"," + json.substring(1);
        writeJson(store, record.getId(), json);
    }

    private void writeRecordAt(String store, String fileName, UuidKeyedDataEntity record) throws IOException {
        writeJson(store, fileName, GSON.toJson(record));
    }

    private void writeJson(String store, String fileName, String json) throws IOException {
        File dir = tempDir.resolve(store).toFile();
        assertThat(dir.isDirectory() || dir.mkdirs()).isTrue();
        try (Writer writer = new OutputStreamWriter(
                Files.newOutputStream(new File(dir, fileName + ".json").toPath()), StandardCharsets.UTF_8)) {
            writer.write(json);
        }
    }

    /**
     * The same real JSON operator, counting the deletes and updates it is asked for -- used where a
     * step's effect is invisible on this backend but matters on the relational one.
     */
    private <T extends BaseDataEntity<String>> SilentlyFailingStore<T> countingOperatorOver(
            String store, Class<T> type) {
        File dir = tempDir.resolve(store).toFile();
        assertThat(dir.isDirectory() || dir.mkdirs()).isTrue();
        return new SilentlyFailingStore<>(dir.getAbsolutePath(), type);
    }

    private <T extends BaseDataEntity<String>> SimpleJsonDataOperator<T> operatorOver(
            String store, Class<T> type) {
        File dir = tempDir.resolve(store).toFile();
        assertThat(dir.isDirectory() || dir.mkdirs()).isTrue();
        return new SimpleJsonDataOperator<>(dir.getAbsolutePath(), type);
    }

    /**
     * Every document in a store, sorted — a byte-level "nothing changed" check that does not depend
     * on any operator's own reporting.
     */
    private List<String> storeContents(String store) throws IOException {
        List<String> contents = new ArrayList<>();
        File[] files = tempDir.resolve(store).toFile().listFiles();
        if (files != null) {
            for (File file : files) {
                contents.add(file.getName() + '=' + new String(
                    Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            }
        }
        Collections.sort(contents);
        return contents;
    }

    /**
     * Every concrete persisted entity compiled under this module's entity package, discovered rather
     * than listed, so a fifth one cannot be added without this test noticing.
     */
    private static List<String> discoverPersistedEntities() throws Exception {
        String path = "com/ultikits/plugins/essentials/entity";
        List<String> names = new ArrayList<>();
        java.util.Enumeration<URL> roots =
            EntityIdBackfillServiceTest.class.getClassLoader().getResources(path);
        while (roots.hasMoreElements()) {
            URL root = roots.nextElement();
            if ("file".equals(root.getProtocol())) {
                collect(new File(root.toURI()), path.replace('/', '.'), names);
            }
        }
        return names;
    }

    private static void collect(File dir, String packageName, List<String> into) throws Exception {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collect(child, packageName + '.' + child.getName(), into);
                continue;
            }
            String name = child.getName();
            if (!name.endsWith(".class") || name.contains("$")) {
                continue;
            }
            Class<?> type = Class.forName(packageName + '.' + name.substring(0, name.length() - 6));
            if (BaseDataEntity.class.isAssignableFrom(type)
                    && !Modifier.isAbstract(type.getModifiers())) {
                into.add(type.getSimpleName());
            }
        }
    }

    private static HomeData home(String name) {
        return HomeData.builder()
            .uuid(UUID.randomUUID())
            .playerUuid(UUID.randomUUID().toString())
            .name(name)
            .world("world")
            .x(1).y(2).z(3)
            .createdAt(1_700_000_000_000L)
            .build();
    }

    private static WarpData warp(String name) {
        return WarpData.builder()
            .uuid(UUID.randomUUID())
            .name(name)
            .world("world")
            .x(1).y(2).z(3)
            .createdBy(UUID.randomUUID().toString())
            .createdAt(1_700_000_000_000L)
            .build();
    }

    private static BanData ban() {
        return BanData.builder()
            .uuid(UUID.randomUUID())
            .playerUuid(UUID.randomUUID().toString())
            .playerName("BannedPlayer")
            .reason("test ban")
            .bannedByName("console")
            .banTime(1_700_000_000_000L)
            .expireTime(-1)
            .active(true)
            .build();
    }

    private static ChestLockData lock() {
        return ChestLockData.builder()
            .uuid(UUID.randomUUID())
            .world("world")
            .x(10).y(64).z(10)
            .ownerUuid(UUID.randomUUID().toString())
            .ownerName("LockOwner")
            .createdAt(1_700_000_000_000L)
            .build();
    }
}
