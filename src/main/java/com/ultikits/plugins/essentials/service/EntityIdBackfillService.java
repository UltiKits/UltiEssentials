package com.ultikits.plugins.essentials.service;

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
import java.util.List;
import java.util.Map;

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
 *   <li><b>Loses nothing quietly.</b> The delete and the insert run inside
 *       {@code DataOperator#transaction}, so a store with a transaction manager rolls the pair back
 *       together. If the insert fails anyway, the record's full contents are logged at ERROR so an
 *       operator can re-create it by hand, and the repair moves on rather than aborting the rest.</li>
 * </ul>
 *
 * @author wisdomme
 * @version 1.0.0
 */
@Slf4j
@Service
public class EntityIdBackfillService {

    /**
     * Every persisted entity type this repair covers. {@code EntityIdBackfillCoverageTest} holds
     * this list against the entity package, so a fifth entity cannot be added without either being
     * covered here or failing the build.
     */
    public static final List<Class<? extends UuidKeyedDataEntity>> REPAIRED_TYPES =
        Collections.unmodifiableList(Arrays.<Class<? extends UuidKeyedDataEntity>>asList(
            HomeData.class, WarpData.class, BanData.class, ChestLockData.class));

    @Autowired
    private UltiToolsPlugin plugin;

    /**
     * Repairs every covered entity type, returning what was done per type.
     *
     * @return the outcome, for logging and for tests
     */
    public Report run() {
        Report report = new Report();
        for (Class<? extends UuidKeyedDataEntity> type : REPAIRED_TYPES) {
            report.put(type.getSimpleName(), repair(type));
        }
        report.log();
        return report;
    }

    private <T extends UuidKeyedDataEntity> Outcome repair(Class<T> type) {
        DataOperator<T> operator = plugin.getDataOperator(type);
        if (operator == null) {
            log.warn("No data operator for {}; its records were left untouched", type.getSimpleName());
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
        for (T record : stored) {
            String uuid = record.getId();
            if (uuid != null) {
                Integer seen = occurrences.get(uuid);
                occurrences.put(uuid, seen == null ? 1 : seen + 1);
            }
        }

        int repaired = 0;
        int skipped = 0;
        for (T record : stored) {
            String uuid = record.getId();
            String persisted = record.getPersistedId();
            if (uuid != null && uuid.equals(persisted)) {
                continue;
            }
            if (uuid == null) {
                log.warn("A stored {} record has no identity of its own, so no key can be written "
                        + "for it; left untouched", label);
                skipped++;
                continue;
            }
            if (persisted != null) {
                log.warn("Stored {} record {} already carries a different primary key ({}); left "
                        + "untouched rather than guessing which is right", label, uuid, persisted);
                skipped++;
                continue;
            }
            if (occurrences.get(uuid) != 1) {
                log.warn("{} stored {} records share the identity {}; left untouched, because "
                        + "repairing one of them would delete the others",
                        occurrences.get(uuid), label, uuid);
                skipped++;
                continue;
            }
            if (rewrite(operator, record, label, uuid)) {
                repaired++;
            } else {
                skipped++;
            }
        }
        if (repaired > 0) {
            makeDurable(operator, label);
        }
        return new Outcome(repaired, skipped);
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
     */
    private void makeDurable(DataOperator<?> operator, String label) {
        if (!(operator instanceof Cached)) {
            return;
        }
        try {
            ((Cached) operator).flush();
        } catch (RuntimeException e) {
            log.error("Repaired {} records could not be written to disk; the repair will run again "
                    + "on the next start-up", label, e);
        }
    }

    private <T extends UuidKeyedDataEntity> boolean rewrite(DataOperator<T> operator, T record,
                                                           String label, String uuid) {
        try {
            operator.transaction(() -> {
                operator.del(WhereCondition.builder().column("uuid").value(uuid).build());
                operator.insert(record);
                return null;
            });
            return true;
        } catch (Exception e) {
            // The record is held in memory above, so its contents can still be reported in full --
            // this is the one place where an operator may have to re-create a record by hand.
            log.error("Failed to write a primary key for {} record " + uuid
                    + "; it may have been removed without being written back. Its contents were: "
                    + record, label, e);
            return false;
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

        boolean touchedNothing() {
            return repaired == 0 && skipped == 0;
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
         * Writes one INFO line per entity type that had anything to repair, then a summary. Silent
         * when there was nothing to do, so a healthy server's log is unchanged and a line here
         * always means something was written.
         */
        void log() {
            List<String> labels = new ArrayList<>(byType.keySet());
            Collections.sort(labels);
            for (String label : labels) {
                Outcome outcome = byType.get(label);
                if (!outcome.touchedNothing()) {
                    log.info("Repaired the stored primary key of {} {} record(s); left {} untouched",
                        outcome.repaired(), label, outcome.skipped());
                }
            }
            if (totalRepaired() > 0 || totalSkipped() > 0) {
                log.info("Records written before UltiKits/UltiEssentials#34 was fixed: {} repaired, "
                        + "{} left untouched. This runs once -- a repaired record is not visited again.",
                    totalRepaired(), totalSkipped());
            }
        }
    }
}
