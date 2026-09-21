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
import com.ultikits.plugins.essentials.utils.SilentlyFailingStore;
import com.ultikits.plugins.essentials.utils.TestHelper;
import com.ultikits.ultitools.abstracts.data.BaseDataEntity;
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
        @DisplayName("the un-keyed record is deleted, not merely written over")
        void theUnKeyedRecordIsDeleted() throws Exception {
            // Asserted as a call on the store rather than as a state difference, because the two
            // backends hide it differently and only one of them is reachable from a test here.
            // The JSON operator hands out the instances in its own cache and its insert is a
            // putIfAbsent, so onCreate() writes the key onto the cached instance whether or not the
            // old entry was removed first -- the repair appears to work either way. A relational
            // operator materialises every row afresh and its insert is a real INSERT, so without the
            // delete the un-keyed row would survive ALONGSIDE a new keyed one: one home listed
            // twice, and the older row still undeletable. Measured: removing the delete leaves every
            // other assertion in this class green (revert-proofs/w1,
            // UltiEssentials-34-MUTATION-repair-delete), which is why this assertion exists.
            HomeData legacy = home("farm");
            writeLegacyRecord("homes", legacy);
            SilentlyFailingStore<HomeData> store = countingOperatorOver("homes", HomeData.class);

            Outcome outcome = backfill.repair(store, "HomeData");

            assertThat(outcome.repaired()).isEqualTo(1);
            assertThat(store.deleteAttempts())
                .as("one delete of the un-keyed record, before it is written back")
                .isEqualTo(1);
        }

        @Test
        @DisplayName("a record that needs no repair is not deleted")
        void anAlreadyCorrectRecordIsNotDeleted() throws Exception {
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
        private void setBackfillField(String name, Object value) throws Exception {
            java.lang.reflect.Field field = EntityIdBackfillService.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(backfill, value);
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
