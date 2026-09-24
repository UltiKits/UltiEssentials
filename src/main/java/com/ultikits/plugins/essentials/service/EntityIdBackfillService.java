package com.ultikits.plugins.essentials.service;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.entity.BanData;
import com.ultikits.plugins.essentials.entity.ChestLockData;
import com.ultikits.plugins.essentials.entity.HomeData;
import com.ultikits.plugins.essentials.entity.WarpData;
import com.ultikits.plugins.essentials.entity.base.UuidKeyedDataEntity;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.Service;
import com.ultikits.ultitools.entities.WhereCondition;
import com.ultikits.ultitools.interfaces.Cached;
import com.ultikits.ultitools.interfaces.DataOperator;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Repairs records this module persisted before UltiKits/UltiEssentials#34 was fixed, whose
 * primary-key column was never written.
 * <p>
 * 修复 #34 修复之前写入的记录：这些记录的主键列从未被写入。
 *
 * <h2>What it repairs and how it recognises a record</h2>
 * Before the fix, an entity's identity lived only in its {@code uuid} column while the framework's
 * own {@code @Column("id")} primary key went in as {@code NULL} — so every
 * {@code delById}/{@code update(T)}/{@code getById} matched nothing. A record written that way is
 * recognisable without guessing: {@link UuidKeyedDataEntity#getPersistedId()} is {@code null} while
 * {@code getId()} is not.
 *
 * <h2>Why delete-and-reinsert</h2>
 * Nothing in the public {@code DataOperator} API can set a column on a row it cannot address:
 * {@code update(T)}, {@code update(column, value, id)}, {@code delById} and {@code getById} all key
 * on {@code WHERE id = ?}, which is the column that is {@code NULL}. {@code Query#delete()} is no
 * help either — it counts the rows it <em>matched</em> and deletes them through
 * {@code delById(entity.getId())}, so it is broken for exactly these rows. The one remaining route
 * is {@code del(WhereCondition)} on the {@code uuid} column followed by {@code insert}, whose
 * {@code onCreate()} now writes the key. Adding a framework method was not an option for this
 * phase.
 * <p>
 * A conditional update -- an {@code UPDATE} targeted by a {@code WhereCondition} rather than by the
 * id column -- would remove the need for the delete entirely. Measured: it does not exist.
 * {@code DataOperator} exposes three update entry points and all three key on {@code id}
 * ({@code update(String, Object, Object)}, {@code update(T)}, and {@code updateAll(List)} which
 * delegates to {@code update(T)}); {@code Query}'s nineteen methods have no update terminal, only
 * {@code delete()}; and of the framework's three {@code UPDATE} statement builders, none calls
 * {@code appendConditions}, whose four call sites are {@code exist}, {@code getAll}, {@code page} and
 * {@code del}. That last pair is the control for the negative: predicate targeting exists in this
 * codebase, and is absent from every update path.
 *
 * <h2>Properties this repair is required to have</h2>
 * <ul>
 *   <li><b>Idempotent.</b> A repaired record has a non-null persisted id, so the next start-up's
 *       scan finds no candidate and performs no write at all. Running it twice changes nothing the
 *       second time.</li>
 *   <li><b>Skips rather than guesses.</b> A record is left exactly as it is, counted and logged,
 *       when it has no identity to write ({@code getId()} is {@code null}), when its {@code uuid}
 *       value is shared by more than one stored record (deleting by {@code uuid} would take the
 *       others with it), or when it already carries a persisted id that disagrees with its
 *       {@code uuid}. A skipped record keeps working for reads exactly as it does today.</li>
 *   <li><b>Says what it touched.</b> One INFO line per entity type that had anything to repair,
 *       plus a summary; silence when there is nothing to do, so a healthy server's log is
 *       unchanged.</li>
 *   <li><b>Loses nothing quietly.</b> One transaction covers the whole entity type, so a failure
 *       anywhere leaves the store exactly as it was rather than part-repaired -- and on a store with
 *       no transaction manager bound, where the framework runs the action with no transaction at all,
 *       every affected record's columns and values are logged at ERROR through
 *       {@code describeForRecovery()}, which is enough to re-create them by hand.</li>
 * </ul>
 *
 * @author wisdomme
 * @version 1.0.0
 */
@Slf4j
@Service
public class EntityIdBackfillService {

    /**
     * Every persisted entity type this repair covers. {@code EntityIdBackfillServiceTest$CoverageTests} holds
     * this list against the entity package, so a fifth entity cannot be added without either being
     * covered here or failing the build.
     */
    public static final List<Class<? extends UuidKeyedDataEntity>> REPAIRED_TYPES =
        Collections.unmodifiableList(Arrays.<Class<? extends UuidKeyedDataEntity>>asList(
            HomeData.class, WarpData.class, BanData.class, ChestLockData.class));

    @Autowired
    private UltiToolsPlugin plugin;

    @Autowired
    private EssentialsConfig config;

    /**
     * Repairs every covered entity type, returning what was done per type.
     * <p>
     * Held off entirely when {@code features.data-repair.enabled} is {@code false}. It defaults to
     * true because an operator who never learns this defect exists would never turn a repair on, and
     * records that stay silently un-keyed are worse than a repair that runs; the key exists because
     * running without anyone deciding is exactly what makes this repair costly, and an operator
     * mid-migration or without a backup has a real reason to wait. There is no dry-run mode on
     * purpose: it would double the paths and the interesting one would be the one nobody runs, while
     * the WARNING per skipped record and the INFO per repaired type already say what happened.
     *
     * @return the outcome, for logging and for tests; all zeroes when the key is off
     */
    public Report run() {
        Report report = new Report();
        if (config == null || !config.isDataRepairEnabled()) {
            // An unresolved config bean refuses rather than proceeding: for a repair that changes an
            // operator's data, "we could not read the key that turns this off" must not mean "run it"
            // (gate 1 MINOR-06). One line rather than silence either way -- "why did it not run?" has
            // to be answerable from the log, and an operator who set the key will recognise it.
            log.info(plugin.i18n("essentials.log.repair_skipped"),
                    config == null ? plugin.i18n("essentials.log.repair_skipped_no_config")
                            : plugin.i18n("essentials.log.repair_skipped_disabled"));
            return report;
        }
        for (Class<? extends UuidKeyedDataEntity> type : REPAIRED_TYPES) {
            report.put(type.getSimpleName(), repair(type));
        }
        report.log(plugin);
        return report;
    }

    private <T extends UuidKeyedDataEntity> Outcome repair(Class<T> type) {
        DataOperator<T> operator = plugin.getDataOperator(type);
        if (operator == null) {
            log.warn(plugin.i18n("essentials.log.repair_no_operator"), type.getSimpleName());
            return new Outcome(0, 0);
        }
        return repair(operator, type.getSimpleName());
    }

    /**
     * Visible for testing against a real operator over a temporary store.
     *
     * @param operator the operator to repair through
     * @param label    the entity type name, for diagnostics
     * @param <T>      the entity type
     * @return how many records were repaired and how many were skipped
     */
    <T extends UuidKeyedDataEntity> Outcome repair(DataOperator<T> operator, String label) {
        List<T> stored = operator.getAll();
        Map<String, Integer> occurrences = new HashMap<>();
        Set<String> alreadyUsedKeys = new HashSet<>();
        for (T record : stored) {
            String uuid = record.getId();
            if (uuid != null) {
                Integer seen = occurrences.get(uuid);
                occurrences.put(uuid, seen == null ? 1 : seen + 1);
            }
            String persisted = record.getPersistedId();
            if (persisted != null) {
                alreadyUsedKeys.add(persisted);
            }
        }

        List<T> candidates = new ArrayList<>();
        int skipped = 0;
        for (T record : stored) {
            String uuid = record.getId();
            String persisted = record.getPersistedId();
            if (uuid != null && uuid.equals(persisted)) {
                continue;
            }
            if (uuid == null) {
                log.warn(plugin.i18n("essentials.log.repair_no_identity"), label);
                skipped++;
                continue;
            }
            if (persisted != null) {
                log.warn(plugin.i18n("essentials.log.repair_different_key"), label, uuid, persisted);
                skipped++;
                continue;
            }
            if (occurrences.get(uuid) != 1) {
                log.warn(plugin.i18n("essentials.log.repair_shared_identity"), occurrences.get(uuid), label, uuid);
                skipped++;
                continue;
            }
            if (alreadyUsedKeys.contains(uuid)) {
                // Another record already holds this identity as its primary key, so writing it here
                // would collide with it. Detected from the same snapshot rather than discovered by
                // attempting the write and catching the failure: with one transaction around the
                // whole type, an attempted collision would roll back every record repaired with it.
                log.warn(plugin.i18n("essentials.log.repair_key_taken"), label, uuid);
                skipped++;
                continue;
            }
            candidates.add(record);
        }

        int repaired = candidates.isEmpty() ? 0 : rewriteAll(operator, candidates, label);
        if (repaired > 0 && !makeDurable(operator, label)) {
            // The keys reached the cache but not the disk, so after a restart those records are
            // un-keyed again. Reporting them as repaired would make the INFO line claim a durable
            // write that did not happen; they count as untouched and the next start-up retries.
            repaired = 0;
        }
        return new Outcome(repaired, skipped + (candidates.size() - repaired));
    }

    /**
     * Writes a cache-backed store to disk straight away, instead of leaving the repair to the next
     * scheduled flush.
     * <p>
     * The JSON backend's {@code insert} and {@code del} touch only its in-memory cache;
     * {@code JsonStore} persists it on a timer and at shutdown. Without this, a repair would report
     * having written records that are still absent from disk, and a server stopped before the next
     * tick would come back up unrepaired. Re-running the repair then is harmless — it is
     * idempotent — but a log line saying a record was repaired should mean it was. A relational
     * operator is not {@code Cached} and writes as it goes, so this is a no-op there.
     * <p>
     * A failed write is reported rather than swallowed, and the caller drops the repaired count to
     * zero on it: the keys would be in the cache but not on disk, so the records come back un-keyed
     * after a restart, and an INFO line claiming they were repaired would be claiming a durable write
     * that did not happen (gate 2 round 2).
     *
     * @param operator the operator to repair through
     * @param label    the entity type name, for diagnostics
     * @return true if the records are durable; false if writing them to disk failed
     */
    private boolean makeDurable(DataOperator<?> operator, String label) {
        if (!(operator instanceof Cached)) {
            return true;
        }
        try {
            ((Cached) operator).flush();
            return true;
        } catch (RuntimeException e) {
            log.error(plugin.i18n("essentials.log.repair_flush_failed"), label, e);
            return false;
        }
    }

    /**
     * Rewrites every candidate inside <strong>one</strong> transaction, and reports how many were
     * written.
     * <p>
     * One transaction for the whole entity type rather than one per record, for two measured reasons.
     * Cost: {@code SimpleJsonDataOperator.transaction} opens by deep-copying its entire cache through
     * Gson, so a transaction per record made the repair superlinear on the main thread inside
     * {@code onEnable} -- 100/200/400/800 records took 0.66/0.73/2.32/11.64 s, which extrapolates to
     * minutes for a few thousand and looks exactly like a hung server (gate 1 MAJOR-05). One
     * transaction is one deep copy per entity type instead of N. Safety: it also closes the window
     * between the delete and the insert for the whole run rather than one record at a time, so a
     * failure anywhere leaves the store as it was rather than part-repaired.
     * <p>
     * All-or-nothing is only acceptable because the one failure this loop could previously expect --
     * colliding with a primary key another record already holds -- is now excluded before the
     * transaction opens. What remains is an infrastructure failure, and rolling the whole run back is
     * the right answer to that: the repair is idempotent, so the next start-up tries again.
     *
     * @param operator   the operator to repair through
     * @param candidates the records to give a key, each already checked as safe to key
     * @param label      the entity type name, for diagnostics
     * @param <T>        the entity type
     * @return the number of records whose key is confirmed written; 0 if the transaction rolled back
     */
    private <T extends UuidKeyedDataEntity> int rewriteAll(DataOperator<T> operator,
                                                           List<T> candidates, String label) {
        // The delete is needed only where a row can exist independently of the object that
        // represents it. A cache-backed store hands out the instances it holds, so insert's
        // onCreate() writes the key onto the stored record itself and the delete would remove and
        // re-add the same entry -- while costing a full-cache Gson pass per call, because the
        // framework's own del(WhereCondition) serialises every entry to evaluate the condition. That
        // was the second superlinear term behind gate 1 MAJOR-05, and dropping the delete where it is
        // redundant removes it rather than shrinking it. Whether the key really was written is then
        // confirmed below rather than assumed, so relying on that behaviour cannot fail silently.
        boolean cacheBacked = operator instanceof Cached;
        Set<String> expected = new HashSet<>();
        for (T record : candidates) {
            expected.add(record.getId());
        }
        try {
            operator.transaction(() -> {
                for (T record : candidates) {
                    if (!cacheBacked) {
                        operator.del(WhereCondition.builder()
                                .column("uuid").value(record.getId()).build());
                    }
                    operator.insert(record);
                }
                return null;
            });
            int written = 0;
            for (T record : operator.getAll()) {
                if (expected.contains(record.getId()) && record.getPersistedId() != null) {
                    written++;
                }
            }
            if (written != candidates.size()) {
                log.error(plugin.i18n("essentials.log.repair_partial"), candidates.size(), label, written);
            }
            return written;
        } catch (Exception e) {
            // Every candidate is still held in memory here, so their contents can be reported in
            // full. Through describeForRecovery(), which names the fields it prints -- the entities'
            // own toString does not carry inherited fields (gate 1 MAJOR-06).
            StringBuilder contents = new StringBuilder();
            for (T record : candidates) {
                contents.append("\n  ").append(record.describeForRecovery());
            }
            log.error(plugin.i18n("essentials.log.repair_rolled_back"), candidates.size(), label, contents, e);
            return 0;
        }
    }

    /**
     * How many records of one entity type were repaired and how many were deliberately left alone.
     */
    public static final class Outcome {
        private final int repaired;
        private final int skipped;

        Outcome(int repaired, int skipped) {
            this.repaired = repaired;
            this.skipped = skipped;
        }

        public int repaired() {
            return repaired;
        }

        public int skipped() {
            return skipped;
        }

    }

    /**
     * What the whole repair did, per entity type.
     */
    public static final class Report {
        private final Map<String, Outcome> byType = new HashMap<>();

        void put(String label, Outcome outcome) {
            byType.put(label, outcome);
        }

        /**
         * @param label an entity type's simple name
         * @return that type's outcome, or null if it was not covered
         */
        public Outcome of(String label) {
            return byType.get(label);
        }

        /**
         * @return how many records were repaired across every entity type
         */
        public int totalRepaired() {
            int total = 0;
            for (Outcome outcome : byType.values()) {
                total += outcome.repaired();
            }
            return total;
        }

        /**
         * @return how many records were deliberately left alone across every entity type
         */
        public int totalSkipped() {
            int total = 0;
            for (Outcome outcome : byType.values()) {
                total += outcome.skipped();
            }
            return total;
        }

        /**
         * Writes one INFO line per entity type that had records <strong>written</strong>, then a
         * summary. An INFO line here therefore always means records were written, which is what the
         * javadoc, CHANGELOG and FEATURES.md all claim; a run that only skipped records used to log
         * one too, making that claim false (gate 1 MINOR-01). A skip is already reported at WARNING,
         * one line per record, with the reason -- so a skip-only run is not silent, it is simply not
         * claiming a write.
         */
        void log(UltiToolsPlugin plugin) {
            List<String> labels = new ArrayList<>(byType.keySet());
            Collections.sort(labels);
            for (String label : labels) {
                Outcome outcome = byType.get(label);
                if (outcome.repaired() > 0) {
                    log.info(plugin.i18n("essentials.log.repair_type_done"), outcome.repaired(), label, outcome.skipped());
                }
            }
            if (totalRepaired() > 0) {
                log.info(plugin.i18n("essentials.log.repair_summary"), totalRepaired(), totalSkipped());
            }
        }
    }
}
