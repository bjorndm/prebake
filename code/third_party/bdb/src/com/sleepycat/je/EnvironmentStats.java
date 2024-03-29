/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: EnvironmentStats.java,v 1.74 2010/01/04 15:50:36 cwl Exp $
 */

package com.sleepycat.je;

import static com.sleepycat.je.cleaner.CleanerStatDefinition.CLEANER_BACKLOG;
import static com.sleepycat.je.cleaner.CleanerStatDefinition.CLEANER_CLUSTER_LNS_PROCESSED;
import static com.sleepycat.je.cleaner.CleanerStatDefinition.CLEANER_DELETIONS;
import static com.sleepycat.je.cleaner.CleanerStatDefinition.CLEANER_ENTRIES_READ;
import static com.sleepycat.je.cleaner.CleanerStatDefinition.CLEANER_FILE_DELETION_BACKLOG;
import static com.sleepycat.je.cleaner.CleanerStatDefinition.CLEANER_INS_CLEANED;
import static com.sleepycat.je.cleaner.CleanerStatDefinition.CLEANER_INS_DEAD;
import static com.sleepycat.je.cleaner.CleanerStatDefinition.CLEANER_INS_MIGRATED;
import static com.sleepycat.je.cleaner.CleanerStatDefinition.CLEANER_INS_OBSOLETE;
import static com.sleepycat.je.cleaner.CleanerStatDefinition.CLEANER_LNQUEUE_HITS;
import static com.sleepycat.je.cleaner.CleanerStatDefinition.CLEANER_LNS_CLEANED;
import static com.sleepycat.je.cleaner.CleanerStatDefinition.CLEANER_LNS_DEAD;
import static com.sleepycat.je.cleaner.CleanerStatDefinition.CLEANER_LNS_LOCKED;
import static com.sleepycat.je.cleaner.CleanerStatDefinition.CLEANER_LNS_MARKED;
import static com.sleepycat.je.cleaner.CleanerStatDefinition.CLEANER_LNS_MIGRATED;
import static com.sleepycat.je.cleaner.CleanerStatDefinition.CLEANER_LNS_OBSOLETE;
import static com.sleepycat.je.cleaner.CleanerStatDefinition.CLEANER_MARKED_LNS_PROCESSED;
import static com.sleepycat.je.cleaner.CleanerStatDefinition.CLEANER_PENDING_LNS_LOCKED;
import static com.sleepycat.je.cleaner.CleanerStatDefinition.CLEANER_PENDING_LNS_PROCESSED;
import static com.sleepycat.je.cleaner.CleanerStatDefinition.CLEANER_REPEAT_ITERATOR_READS;
import static com.sleepycat.je.cleaner.CleanerStatDefinition.CLEANER_RUNS;
import static com.sleepycat.je.cleaner.CleanerStatDefinition.CLEANER_TOTAL_LOG_SIZE;
import static com.sleepycat.je.cleaner.CleanerStatDefinition.CLEANER_TO_BE_CLEANED_LNS_PROCESSED;
import static com.sleepycat.je.dbi.DbiStatDefinition.ENVIMPL_RELATCHES_REQUIRED;
import static com.sleepycat.je.dbi.DbiStatDefinition.MB_ADMIN_BYTES;
import static com.sleepycat.je.dbi.DbiStatDefinition.MB_DATA_BYTES;
import static com.sleepycat.je.dbi.DbiStatDefinition.MB_LOCK_BYTES;
import static com.sleepycat.je.dbi.DbiStatDefinition.MB_SHARED_CACHE_TOTAL_BYTES;
import static com.sleepycat.je.dbi.DbiStatDefinition.MB_TOTAL_BYTES;
import static com.sleepycat.je.evictor.EvictorStatDefinition.EVICTOR_BINS_STRIPPED;
import static com.sleepycat.je.evictor.EvictorStatDefinition.EVICTOR_EVICT_PASSES;
import static com.sleepycat.je.evictor.EvictorStatDefinition.EVICTOR_NODES_EVICTED;
import static com.sleepycat.je.evictor.EvictorStatDefinition.EVICTOR_NODES_SCANNED;
import static com.sleepycat.je.evictor.EvictorStatDefinition.EVICTOR_NODES_SELECTED;
import static com.sleepycat.je.evictor.EvictorStatDefinition.EVICTOR_REQUIRED_EVICT_BYTES;
import static com.sleepycat.je.evictor.EvictorStatDefinition.EVICTOR_ROOT_NODES_EVICTED;
import static com.sleepycat.je.evictor.EvictorStatDefinition.EVICTOR_SHARED_CACHE_ENVS;
import static com.sleepycat.je.incomp.INCompStatDefinition.INCOMP_CURSORS_BINS;
import static com.sleepycat.je.incomp.INCompStatDefinition.INCOMP_DBCLOSED_BINS;
import static com.sleepycat.je.incomp.INCompStatDefinition.INCOMP_NON_EMPTY_BINS;
import static com.sleepycat.je.incomp.INCompStatDefinition.INCOMP_PROCESSED_BINS;
import static com.sleepycat.je.incomp.INCompStatDefinition.INCOMP_QUEUE_SIZE;
import static com.sleepycat.je.incomp.INCompStatDefinition.INCOMP_SPLIT_BINS;
import static com.sleepycat.je.latch.LatchStatDefinition.LATCH_CONTENTION;
import static com.sleepycat.je.latch.LatchStatDefinition.LATCH_NOWAIT_SUCCESS;
import static com.sleepycat.je.latch.LatchStatDefinition.LATCH_NOWAIT_UNSUCCESS;
import static com.sleepycat.je.latch.LatchStatDefinition.LATCH_NO_WAITERS;
import static com.sleepycat.je.latch.LatchStatDefinition.LATCH_RELEASES;
import static com.sleepycat.je.latch.LatchStatDefinition.LATCH_SELF_OWNED;
import static com.sleepycat.je.log.LogStatDefinition.FILEMGR_BYTES_READ_FROM_WRITEQUEUE;
import static com.sleepycat.je.log.LogStatDefinition.FILEMGR_BYTES_WRITTEN_FROM_WRITEQUEUE;
import static com.sleepycat.je.log.LogStatDefinition.FILEMGR_FILE_OPENS;
import static com.sleepycat.je.log.LogStatDefinition.FILEMGR_OPEN_FILES;
import static com.sleepycat.je.log.LogStatDefinition.FILEMGR_RANDOM_READS;
import static com.sleepycat.je.log.LogStatDefinition.FILEMGR_RANDOM_READ_BYTES;
import static com.sleepycat.je.log.LogStatDefinition.FILEMGR_RANDOM_WRITES;
import static com.sleepycat.je.log.LogStatDefinition.FILEMGR_RANDOM_WRITE_BYTES;
import static com.sleepycat.je.log.LogStatDefinition.FILEMGR_READS_FROM_WRITEQUEUE;
import static com.sleepycat.je.log.LogStatDefinition.FILEMGR_SEQUENTIAL_READS;
import static com.sleepycat.je.log.LogStatDefinition.FILEMGR_SEQUENTIAL_READ_BYTES;
import static com.sleepycat.je.log.LogStatDefinition.FILEMGR_SEQUENTIAL_WRITES;
import static com.sleepycat.je.log.LogStatDefinition.FILEMGR_SEQUENTIAL_WRITE_BYTES;
import static com.sleepycat.je.log.LogStatDefinition.FILEMGR_WRITEQUEUE_OVERFLOW;
import static com.sleepycat.je.log.LogStatDefinition.FILEMGR_WRITEQUEUE_OVERFLOW_FAILURES;
import static com.sleepycat.je.log.LogStatDefinition.FILEMGR_WRITES_FROM_WRITEQUEUE;
import static com.sleepycat.je.log.LogStatDefinition.FSYNCMGR_FSYNCS;
import static com.sleepycat.je.log.LogStatDefinition.FSYNCMGR_FSYNC_REQUESTS;
import static com.sleepycat.je.log.LogStatDefinition.FSYNCMGR_FSYNC_TIMEOUTS;
import static com.sleepycat.je.log.LogStatDefinition.LBFP_BUFFER_BYTES;
import static com.sleepycat.je.log.LogStatDefinition.LBFP_LOG_BUFFERS;
import static com.sleepycat.je.log.LogStatDefinition.LBFP_MISS;
import static com.sleepycat.je.log.LogStatDefinition.LBFP_NOT_RESIDENT;
import static com.sleepycat.je.log.LogStatDefinition.LOGMGR_END_OF_LOG;
import static com.sleepycat.je.log.LogStatDefinition.LOGMGR_REPEAT_FAULT_READS;
import static com.sleepycat.je.log.LogStatDefinition.LOGMGR_TEMP_BUFFER_WRITES;
import static com.sleepycat.je.recovery.CheckpointStatDefinition.CKPT_CHECKPOINTS;
import static com.sleepycat.je.recovery.CheckpointStatDefinition.CKPT_DELTA_IN_FLUSH;
import static com.sleepycat.je.recovery.CheckpointStatDefinition.CKPT_FULL_BIN_FLUSH;
import static com.sleepycat.je.recovery.CheckpointStatDefinition.CKPT_FULL_IN_FLUSH;
import static com.sleepycat.je.recovery.CheckpointStatDefinition.CKPT_LAST_CKPTID;
import static com.sleepycat.je.recovery.CheckpointStatDefinition.CKPT_LAST_CKPT_END;
import static com.sleepycat.je.recovery.CheckpointStatDefinition.CKPT_LAST_CKPT_START;
import static com.sleepycat.je.txn.LockStatDefinition.LOCK_OWNERS;
import static com.sleepycat.je.txn.LockStatDefinition.LOCK_READ_LOCKS;
import static com.sleepycat.je.txn.LockStatDefinition.LOCK_REQUESTS;
import static com.sleepycat.je.txn.LockStatDefinition.LOCK_TOTAL;
import static com.sleepycat.je.txn.LockStatDefinition.LOCK_WAITERS;
import static com.sleepycat.je.txn.LockStatDefinition.LOCK_WAITS;
import static com.sleepycat.je.txn.LockStatDefinition.LOCK_WRITE_LOCKS;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import com.sleepycat.je.cleaner.CleanerStatDefinition;
import com.sleepycat.je.dbi.DbiStatDefinition;
import com.sleepycat.je.evictor.EvictorStatDefinition;
import com.sleepycat.je.incomp.INCompStatDefinition;
import com.sleepycat.je.log.LogStatDefinition;
import com.sleepycat.je.recovery.CheckpointStatDefinition;
import com.sleepycat.je.txn.LockStatDefinition;
import com.sleepycat.je.utilint.StatGroup;

/**
 * Statistics for a single environment. 
 * <p>
 * The statistics are logically grouped into several categories. Viewing the
 * statistics through {@link EnvironmentStats#toString()} displays the values
 * in these categories, as does viewing the stats through the {@link <a
 * href="{@docRoot}/../jconsole/JConsole-plugin.html">JEMonitor mbean</a>}.
 * Viewing the stats with {@link EnvironmentStats#toStringVerbose()} will
 * provide more detailed descriptions of the stats and stat categories.
 * <p>
 * The current categories are:
 * <ul>
 * <li><b>IO</b>: Log file opens, reads, writes, cache misses.</li>
 * <li><b>Cache</b>: Data, keys, internal btree nodes, locks and JE metadata
 * reside in the cache. If the cache is too full, nodes, data and keys are
 * periodically evicted to stay within the defined cache size.</li>
 * <li><b>Log Cleaning</b>: Maintenance of JE's append only storage system.
 * See this <a href="{@docRoot}/../GettingStartedGuide/logfilesrevealed.html">
 * overview</a> of the logging system. Log files are removed as their 
 * contents become obsolete.</li>
 * <li><b>Node Compression</b>: Internal btree nodes are compressed and removed
 * when their contents become sparse. </li>
 * <li><b>Checkpoints</b>: The frequency and extent of checkpointing activity.
 * </li>
 * <li><b>Environment</b>: Miscellaneous environment wide statistics.
 * </li>
 * <li><b>Locks</b>: Number of locking operations, contention on lock table.
 * </li>
 * </ul>
 * @see <a href="{@docRoot}/../jconsole/JConsole-plugin.html">Viewing
 * Statistics with JConsole</a>
 */
public class EnvironmentStats implements Serializable {

    private static final long serialVersionUID = 1734048134L;

    private StatGroup incompStats;
    private StatGroup cacheStats;
    private StatGroup ckptStats;
    private StatGroup cleanerStats;
    private StatGroup logStats;
    private StatGroup lockStats;
    private StatGroup envImplStats;
    private final Map<String, String> tipsMap = new HashMap<String, String>();

    /**
     * @hidden
     * Internal use only.
     */
    public EnvironmentStats() {
        incompStats = new StatGroup(INCompStatDefinition.GROUP_NAME,
                                    INCompStatDefinition.GROUP_DESC);
        /* 
         * Memory budget stats are displayed with evictor stats, as a single
         * cache category.
         */
        cacheStats = new StatGroup(EvictorStatDefinition.GROUP_NAME,
                                   EvictorStatDefinition.GROUP_DESC);
        ckptStats = new StatGroup(CheckpointStatDefinition.GROUP_NAME,
                                  CheckpointStatDefinition.GROUP_DESC);
        cleanerStats = new StatGroup(CleanerStatDefinition.GROUP_NAME,
                                     CleanerStatDefinition.GROUP_DESC);
        logStats = new StatGroup(LogStatDefinition.GROUP_NAME, 
                                 LogStatDefinition.GROUP_DESC);
        lockStats = new StatGroup(LockStatDefinition.GROUP_NAME,
                                  LockStatDefinition.GROUP_DESC);
        envImplStats = new StatGroup(DbiStatDefinition.ENV_GROUP_NAME,
                                     DbiStatDefinition.ENV_GROUP_DESC);
    }

    /**
     * @hidden
     * Internal use only
     * For JConsole plugin support.
     */
    public static String[] getStatGroupTitles() {
        return new String[] {
            LogStatDefinition.GROUP_NAME,
            EvictorStatDefinition.GROUP_NAME,
            CleanerStatDefinition.GROUP_NAME,
            INCompStatDefinition.GROUP_NAME,
            CheckpointStatDefinition.GROUP_NAME,
            DbiStatDefinition.ENV_GROUP_NAME,
            LockStatDefinition.GROUP_NAME};
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setINCompStats(StatGroup stats) {
        incompStats = stats;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setCkptStats(StatGroup stats) {
        ckptStats = stats;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setCleanerStats(StatGroup stats) {
        cleanerStats = stats;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setLogStats(StatGroup stats) {
        logStats = stats;
    }

    /**
     * @hidden
     * Inernal use only.
     */
    public void setMBAndEvictorStats(StatGroup mbStats,
                                     StatGroup evictorStats){
        cacheStats = evictorStats.cloneGroup(false);
        cacheStats.addAll(mbStats);
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setLockStats(StatGroup stats) {
        lockStats = stats;
    }

    /**
     * @hidden
     * Internal use only.
     */
    public void setEnvImplStats(StatGroup stats) {
        envImplStats = stats;
    }

    /* INCompressor stats. */

    /**
     * The number of BINs encountered by the INCompressor that had cursors
     * referring to them when the compressor ran.
     */
    public long getCursorsBins() {
        return incompStats.getLong(INCOMP_CURSORS_BINS);
    }

    /**
     * The number of BINs encountered by the INCompressor that had their
     * database closed between the time they were put on the compressor queue
     * and when the compressor ran.
     */
    public long getDbClosedBins() {
        return incompStats.getLong(INCOMP_DBCLOSED_BINS);
    }

    /**
     * The number of entries in the INCompressor queue when the getStats()
     * call was made.
     */
    public long getInCompQueueSize() {
        return incompStats.getLong(INCOMP_QUEUE_SIZE);
    }

    /**
     * The number of BINs encountered by the INCompressor that were not
     * actually empty when the compressor ran.
     */
    public long getNonEmptyBins() {
        return incompStats.getLong(INCOMP_NON_EMPTY_BINS);
    }

    /**
     * The number of BINs that were successfully processed by the IN
     * Compressor.
     */
    public long getProcessedBins() {
        return incompStats.getLong(INCOMP_PROCESSED_BINS);
    }

    /**
     * The number of BINs encountered by the INCompressor that were split
     * between the time they were put on the compressor queue and when the
     * compressor ran.
     */
    public long getSplitBins() {
        return incompStats.getLong(INCOMP_SPLIT_BINS);
    }

    /* Checkpointer stats. */

    /**
     * The Id of the last checkpoint.
     */
    public long getLastCheckpointId() {
        return ckptStats.getLong(CKPT_LAST_CKPTID);
    }

    /**
     * The total number of checkpoints run so far.
     */
    public long getNCheckpoints() {
        return ckptStats.getLong(CKPT_CHECKPOINTS);
    }

    /**
     * The accumulated number of full INs flushed to the log.
     */
    public long getNFullINFlush() {
        return ckptStats.getLong(CKPT_FULL_IN_FLUSH);
    }

    /**
     * The accumulated number of full BINs flushed to the log.
     */
    public long getNFullBINFlush() {
        return ckptStats.getLong(CKPT_FULL_BIN_FLUSH);
    }

    /**
     * The accumulated number of Delta INs flushed to the log.
     */
    public long getNDeltaINFlush() {
        return ckptStats.getLong(CKPT_DELTA_IN_FLUSH);
    }

    /**
     * The location in the log of the last checkpoint start.
     */
    public long getLastCheckpointStart() {
        return ckptStats.getLong(CKPT_LAST_CKPT_START);
    }

    /**
     * The location in the log of the last checkpoint end.
     */
    public long getLastCheckpointEnd() {
        return ckptStats.getLong(CKPT_LAST_CKPT_END);
    }

    /* Cleaner stats. */

    /**
     * The number of files to be cleaned to reach the target utilization.
     */
    public int getCleanerBacklog() {
        return cleanerStats.getInt(CLEANER_BACKLOG);
    }

    /**
     * The number of log files that are ready to be deleted.  A file that is
     * ready to be deleted may not yet have been deleted for any of the
     * following reasons:
     * <ul>
     * <li>A checkpoint has not yet completed.  Files are deleted only at the
     * end of each checkpoint.</li>
     * <li>A read-only process is running, which prevents file deletion.</li>
     * <li>A file is protected from deletion by an in-progress {@link
     * com.sleepycat.je.util.DbBackup}.</li>
     * <li>A file is protected from deletion because it is needed for
     * replication (High Availability applications only).</li>
     * </ul>
     */
    public int getFileDeletionBacklog() {
        return cleanerStats.getInt(CLEANER_FILE_DELETION_BACKLOG);
    }

    /**
     * The number of cleaner runs this session.
     */
    public long getNCleanerRuns() {
        return cleanerStats.getLong(CLEANER_RUNS);
    }

    /**
     * The number of cleaner file deletions this session.
     */
    public long getNCleanerDeletions() {
        return cleanerStats.getLong(CLEANER_DELETIONS);
    }

    /**
     * The accumulated number of log entries read by the cleaner.
     */
    public long getNCleanerEntriesRead() {
        return cleanerStats.getLong(CLEANER_ENTRIES_READ);
    }

    /**
     * The accumulated number of INs obsolete.
     */
    public long getNINsObsolete() {
        return cleanerStats.getLong(CLEANER_INS_OBSOLETE);
    }

    /**
     * The accumulated number of INs cleaned.
     */
    public long getNINsCleaned() {
        return cleanerStats.getLong(CLEANER_INS_CLEANED);
    }

    /**
     * The accumulated number of INs that were not found in the tree anymore
     * (deleted).
     */
    public long getNINsDead() {
        return cleanerStats.getLong(CLEANER_INS_DEAD);
    }

    /**
     * The accumulated number of INs migrated.
     */
    public long getNINsMigrated() {
        return cleanerStats.getLong(CLEANER_INS_MIGRATED);
    }

    /**
     * The accumulated number of LNs obsolete.
     */
    public long getNLNsObsolete() {
        return cleanerStats.getLong(CLEANER_LNS_OBSOLETE);
    }

    /**
     * The accumulated number of LNs cleaned.
     */
    public long getNLNsCleaned() {
        return cleanerStats.getLong(CLEANER_LNS_CLEANED);
    }

    /**
     * The accumulated number of LNs that were not found in the tree anymore
     * (deleted).
     */
    public long getNLNsDead() {
        return cleanerStats.getLong(CLEANER_LNS_DEAD);
    }

    /**
     * The accumulated number of LNs encountered that were locked.
     */
    public long getNLNsLocked() {
        return cleanerStats.getLong(CLEANER_LNS_LOCKED);
    }

    /**
     * The accumulated number of LNs encountered that were migrated forward in
     * the log.
     */
    public long getNLNsMigrated() {
        return cleanerStats.getLong(CLEANER_LNS_MIGRATED);
    }

    /**
     * The accumulated number of LNs that were marked for migration during
     * cleaning.
     */
    public long getNLNsMarked() {
        return cleanerStats.getLong(CLEANER_LNS_MARKED);
    }

    /**
     * The accumulated number of LNs processed without a tree lookup.
     */
    public long getNLNQueueHits() {
        return cleanerStats.getLong(CLEANER_LNQUEUE_HITS);
    }

    /**
     * The accumulated number of LNs processed because they were previously
     * locked.
     */
    public long getNPendingLNsProcessed() {
        return cleanerStats.getLong(CLEANER_PENDING_LNS_PROCESSED);
    }

    /**
     * The accumulated number of LNs processed because they were previously
     * marked for migration.
     */
    public long getNMarkedLNsProcessed() {
        return cleanerStats.getLong(CLEANER_MARKED_LNS_PROCESSED);
    }

    /**
     * The accumulated number of LNs processed because they are soon to be
     * cleaned.
     */
    public long getNToBeCleanedLNsProcessed() {
        return cleanerStats.getLong(CLEANER_TO_BE_CLEANED_LNS_PROCESSED);
    }

    /**
     * The accumulated number of LNs processed because they qualify for
     * clustering.
     */
    public long getNClusterLNsProcessed() {
        return cleanerStats.getLong(CLEANER_CLUSTER_LNS_PROCESSED);
    }

    /**
     * The accumulated number of pending LNs that could not be locked for
     * migration because of a long duration application lock.
     */
    public long getNPendingLNsLocked() {
        return cleanerStats.getLong(CLEANER_PENDING_LNS_LOCKED);
    }

    /**
     * The number of times we tried to read a log entry larger than the read
     * buffer size and couldn't grow the log buffer to accommodate the large
     * object. This happens during scans of the log during activities like
     * environment open or log cleaning. Implies that the read chunk size
     * controlled by je.log.iteratorReadSize is too small.
     */
    public long getNRepeatIteratorReads() {
        return cleanerStats.getLong(CLEANER_REPEAT_ITERATOR_READS);
    }

    /**
     * An approximation of the current total log size in bytes.
     */
    public long getTotalLogSize() {
        return cleanerStats.getLong(CLEANER_TOTAL_LOG_SIZE);
    }

    /* LogManager stats. */

    /**
     * The total number of requests for database objects which were not in
     * memory.
     */
    public long getNCacheMiss() {
        return logStats.getLong(LBFP_MISS);
    }

    /**
     * The location of the next entry to be written to the log.
     *
     * <p>Note that the log entries prior to this position may not yet have
     * been flushed to disk.  Flushing can be forced using a Sync or
     * WriteNoSync commit, or a checkpoint.</p>
     */
    public long getEndOfLog() {
        return logStats.getLong(LOGMGR_END_OF_LOG);
    }

    /**
     * The number of fsyncs issued through the group commit manager.
     */
    public long getNFSyncs() {
        return logStats.getLong(FSYNCMGR_FSYNCS);
    }

    /**
     * The number of fsyncs requested through the group commit manager.
     */
    public long getNFSyncRequests() {
        return logStats.getLong(FSYNCMGR_FSYNC_REQUESTS);
    }

    /**
     * The number of fsync requests submitted to the group commit manager which
     * timed out.
     */
    public long getNFSyncTimeouts() {
        return logStats.getLong(FSYNCMGR_FSYNC_TIMEOUTS);
    }

    /**
     * The number of log buffers currently instantiated.
     */
    public int getNLogBuffers() {
        return logStats.getInt(LBFP_LOG_BUFFERS);
    }

    /**
     * The number of disk reads which required repositioning the disk head
     * more than 1MB from the previous file position.  Reads in a different
     * *.jdb log file then the last IO constitute a random read.
     * <p>
     * This number is approximate and may differ from the actual number of
     * random disk reads depending on the type of disks and file system, disk
     * geometry, and file system cache size.
     */
    public long getNRandomReads() {
        return logStats.getLong(FILEMGR_RANDOM_READS);
    }

    /**
     * The number of bytes read which required repositioning the disk head
     * more than 1MB from the previous file position.  Reads in a different
     * *.jdb log file then the last IO constitute a random read.
     * <p>
     * This number is approximate vary depending on the type of disks and file
     * system, disk geometry, and file system cache size.
     */
    public long getNRandomReadBytes() {
        return logStats.getLong(FILEMGR_RANDOM_READ_BYTES);
    }

    /**
     * The number of disk writes which required repositioning the disk head by
     * more than 1MB from the previous file position.  Writes to a different
     * *.jdb log file (i.e. a file "flip") then the last IO constitute a random
     * write.
     * <p>
     * This number is approximate and may differ from the actual number of
     * random disk writes depending on the type of disks and file system, disk
     * geometry, and file system cache size.
     */
    public long getNRandomWrites() {
        return logStats.getLong(FILEMGR_RANDOM_WRITES);
    }

    /**
     * The number of bytes written which required repositioning the disk head
     * more than 1MB from the previous file position.  Writes in a different
     * *.jdb log file then the last IO constitute a random write.
     * <p>
     * This number is approximate vary depending on the type of disks and file
     * system, disk geometry, and file system cache size.
     */
    public long getNRandomWriteBytes() {
        return logStats.getLong(FILEMGR_RANDOM_WRITE_BYTES);
    }

    /**
     * The number of disk reads which did not require repositioning the disk
     * head more than 1MB from the previous file position.  Reads in a
     * different *.jdb log file then the last IO constitute a random read.
     * <p>
     * This number is approximate and may differ from the actual number of
     * sequential disk reads depending on the type of disks and file system,
     * disk geometry, and file system cache size.
     */
    public long getNSequentialReads() {
        return logStats.getLong(FILEMGR_SEQUENTIAL_READS);
    }

    /**
     * The number of bytes read which did not require repositioning the disk
     * head more than 1MB from the previous file position.  Reads in a
     * different *.jdb log file then the last IO constitute a random read.
     * <p>
     * This number is approximate vary depending on the type of disks and file
     * system, disk geometry, and file system cache size.
     */
    public long getNSequentialReadBytes() {
        return logStats.getLong(FILEMGR_SEQUENTIAL_READ_BYTES);
    }

    /**
     * The number of disk writes which did not require repositioning the disk
     * head by more than 1MB from the previous file position.  Writes to a
     * different *.jdb log file (i.e. a file "flip") then the last IO
     * constitute a random write.
     * <p>
     * This number is approximate and may differ from the actual number of
     * sequential disk writes depending on the type of disks and file system,
     * disk geometry, and file system cache size.
     */
    public long getNSequentialWrites() {
        return logStats.getLong(FILEMGR_SEQUENTIAL_WRITES);
    }

    /**
     * The number of bytes written which did not require repositioning the
     * disk head more than 1MB from the previous file position.  Writes in a
     * different *.jdb log file then the last IO constitute a random write.
     * <p>
     * This number is approximate vary depending on the type of disks and file
     * system, disk geometry, and file system cache size.
     */
    public long getNSequentialWriteBytes() {
        return logStats.getLong(FILEMGR_SEQUENTIAL_WRITE_BYTES);
    }

    /**
     * The number of bytes read to fulfill file read operations by reading out
     * of the pending write queue.
     */
    public long getNBytesReadFromWriteQueue() {
        return logStats.getLong(FILEMGR_BYTES_READ_FROM_WRITEQUEUE);
    }

    /**
     * The number of bytes written from the pending write queue.
     */
    public long getNBytesWrittenFromWriteQueue() {
        return logStats.getLong(FILEMGR_BYTES_WRITTEN_FROM_WRITEQUEUE);
    }

    /**
     * The number of file read operations which were fulfilled by reading out
     * of the pending write queue.
     */
    public long getNReadsFromWriteQueue() {
        return logStats.getLong(FILEMGR_READS_FROM_WRITEQUEUE);
    }

    /**
     * The number of file writes operations executed from the pending write
     * queue.
     */
    public long getNWritesFromWriteQueue() {
        return logStats.getLong(FILEMGR_WRITES_FROM_WRITEQUEUE);
    }

    /**
     * The number of writes operations which would overflow the Write Queue.
     */
    public long getNWriteQueueOverflow() {
        return logStats.getLong(FILEMGR_WRITEQUEUE_OVERFLOW);
    }

    /**
     * The number of writes operations which would overflow the Write Queue
     * and could not be queued.
     */
    public long getNWriteQueueOverflowFailures() {
        return logStats.getLong(FILEMGR_WRITEQUEUE_OVERFLOW_FAILURES);
    }

    /**
     * The total memory currently consumed by log buffers, in bytes.  If this
     * environment uses the shared cache, this method returns only the amount
     * used by this environment.
     */
    public long getBufferBytes() {
        return logStats.getLong(LBFP_BUFFER_BYTES);
    }

    /**
     * The number of requests for database objects not contained within the
     * in memory data structures.
     */
    public long getNNotResident() {
        return logStats.getLong(LBFP_NOT_RESIDENT);
    }

    /**
     * The number of reads which had to be repeated when faulting in an object
     * from disk because the read chunk size controlled by je.log.faultReadSize
     * is too small.
     */
    public long getNRepeatFaultReads() {
        return logStats.getLong(LOGMGR_REPEAT_FAULT_READS);
    }

    /**
     * The number of writes which had to be completed using the temporary
     * marshalling buffer because the fixed size log buffers specified by
     * je.log.totalBufferBytes and je.log.numBuffers were not large enough.
     */
    public long getNTempBufferWrites() {
        return logStats.getLong(LOGMGR_TEMP_BUFFER_WRITES);
    }

    /**
     * The number of times a log file has been opened.
     */
    public int getNFileOpens() {
        return logStats.getInt(FILEMGR_FILE_OPENS);
    }

    /**
     * The number of files currently open in the file cache.
     */
    public int getNOpenFiles() {
        return logStats.getInt(FILEMGR_OPEN_FILES);
    }

    /* Return Evictor stats. */

    /**
     * The number of passes made to the evictor.
     */
    public long getNEvictPasses() {
        return cacheStats.getLong(EVICTOR_EVICT_PASSES);
    }

    /**
     * The accumulated number of nodes evicted.
     */
    public long getNNodesExplicitlyEvicted() {
        return cacheStats.getLong(EVICTOR_NODES_EVICTED);
    }

    /**
     * The accumulated number of database root nodes evicted.
     */
    public long getNRootNodesEvicted() {
        return cacheStats.getLong(EVICTOR_ROOT_NODES_EVICTED);
    }

    /**
     * The number of BINs stripped by the evictor.
     */
    public long getNBINsStripped() {
        return cacheStats.getLong(EVICTOR_BINS_STRIPPED);
    }

    /**
     * The number of bytes that must be evicted in order to get within the
     * memory budget.
     */
    public long getRequiredEvictBytes() {
        return cacheStats.getLong(EVICTOR_REQUIRED_EVICT_BYTES);
    }

    /**
     * The accumulated number of nodes scanned in order to select the
     * eviction set.
     */
    public long getNNodesScanned() {
        return cacheStats.getLong(EVICTOR_NODES_SCANNED);
    }

    /**
     * The accumulated number of nodes selected to evict.
     */
    public long getNNodesSelected() {
        return cacheStats.getLong(EVICTOR_NODES_SELECTED);
    }

    /**
     * The number of environments using the shared cache.  This method says
     * nothing about whether this environment is using the shared cache or not.
     */
    public int getNSharedCacheEnvironments() {
        return cacheStats.getInt(EVICTOR_SHARED_CACHE_ENVS);
    }

    /* MemoryBudget stats. */

    /**
     * The total amount of the shared JE cache in use, in bytes.  If this
     * environment uses the shared cache, this method returns the total amount
     * used by all environments that are sharing the cache.  If this
     * environment does not use the shared cache, this method returns zero.
     *
     * <p>To get the configured maximum cache size, see {@link
     * EnvironmentMutableConfig#getCacheSize}.</p>
     */
    public long getSharedCacheTotalBytes() {
        return cacheStats.getLong(MB_SHARED_CACHE_TOTAL_BYTES);
    }

    /**
     * The total amount of JE cache in use, in bytes.  If this environment uses
     * the shared cache, this method returns only the amount used by this
     * environment.
     *
     * <p>This method returns the sum of {@link #getDataBytes}, {@link
     * #getAdminBytes}, {@link #getLockBytes} and {@link #getBufferBytes}.</p>
     *
     * <p>To get the configured maximum cache size, see {@link
     * EnvironmentMutableConfig#getCacheSize}.</p>
     */
    public long getCacheTotalBytes() {
        return cacheStats.getLong(MB_TOTAL_BYTES);
    }

    /**
     * The amount of JE cache used for holding data, keys and internal Btree
     * nodes, in bytes.  If this environment uses the shared cache, this method
     * returns only the amount used by this environment.
     */
    public long getDataBytes() {
        return cacheStats.getLong(MB_DATA_BYTES);
    }

    /**
     * The number of bytes of JE cache used for log cleaning metadata and other
     * administrative structures.  If this environment uses the shared cache,
     * this method returns only the amount used by this environment.
     */
    public long getAdminBytes() {
        return cacheStats.getLong(MB_ADMIN_BYTES);
    }

    /**
     * The number of bytes of JE cache used for holding locks and transactions.
     * If this environment uses the shared cache, this method returns only the
     * amount used by this environment.
     */
    public long getLockBytes() {
        return cacheStats.getLong(MB_LOCK_BYTES);
    }

    /**
     * The amount of JE cache used for all items except for the log buffers, in
     * bytes.  If this environment uses the shared cache, this method returns
     * only the amount used by this environment.
     *
     * @deprecated Please use {@link #getDataBytes} to get the amount of cache
     * used for data and use {@link #getAdminBytes}, {@link #getLockBytes} and
     * {@link #getBufferBytes} to get other components of the total cache usage
     * ({@link #getCacheTotalBytes}).
     */
    public long getCacheDataBytes() {
        return getCacheTotalBytes() - getBufferBytes();
    }

    /* EnvironmentImpl stats. */

    /**
     * Returns the number of latch upgrades (relatches) required while
     * operating on this Environment.  Latch upgrades are required when an
     * operation assumes that a shared (read) latch will be sufficient but
     * later determines that an exclusive (write) latch will actually be
     * required.
     *
     * @return number of latch upgrades (relatches) required.
     */
    public long getRelatchesRequired() {
        return envImplStats.getLong(ENVIMPL_RELATCHES_REQUIRED);
    }

    /* TxnManager stats. */

    /**
     * Total lock owners in lock table.  Only provided when {@link
     * com.sleepycat.je.Environment#getStats Environment.getStats} is
     * called in "slow" mode.
     */
    public int getNOwners() {
        return lockStats.getInt(LOCK_OWNERS);
    }

    /**
     * Total read locks currently held.  Only provided when {@link
     * com.sleepycat.je.Environment#getStats Environment.getStats} is
     * called in "slow" mode.
     */
    public int getNReadLocks() {
        return lockStats.getInt(LOCK_READ_LOCKS);
    }

    /**
     * Total locks currently in lock table.  Only provided when {@link
     * com.sleepycat.je.Environment#getStats Environment.getStats} is
     * called in "slow" mode.
     */
    public int getNTotalLocks() {
        return lockStats.getInt(LOCK_TOTAL);
    }

    /**
     * Total transactions waiting for locks.  Only provided when {@link
     * com.sleepycat.je.Environment#getStats Environment.getStats} is
     * called in "slow" mode.
     */
    public int getNWaiters() {
        return lockStats.getInt(LOCK_WAITERS);
    }

    /**
     * Total write locks currently held.  Only provided when {@link
     * com.sleepycat.je.Environment#getStats Environment.getStats} is
     * called in "slow" mode.
     */
    public int getNWriteLocks() {
        return lockStats.getInt(LOCK_WRITE_LOCKS);
    }

    /**
     * Total number of lock requests to date.
     */
    public long getNRequests() {
        return lockStats.getLong(LOCK_REQUESTS);
    }

    /**
     * Total number of lock waits to date.
     */
    public long getNWaits() {
        return lockStats.getLong(LOCK_WAITS);
    }

    /**
     * Number of acquires of lock table latch with no contention.
     */
    public int getNAcquiresNoWaiters() {
        return lockStats.getInt(LATCH_NO_WAITERS);
    }

    /**
     * Number of acquires of lock table latch when it was already owned
     * by the caller.
     */
    public int getNAcquiresSelfOwned() {
        return lockStats.getInt(LATCH_SELF_OWNED);
    }

    /**
     * Number of acquires of lock table latch when it was already owned by
     * another thread.
     */
    public int getNAcquiresWithContention() {
        return lockStats.getInt(LATCH_CONTENTION);
    }

    /**
     * Number of times acquireNoWait() was successfully called for the lock
     * table latch.
     */
    public int getNAcquiresNoWaitSuccessful() {
        return lockStats.getInt(LATCH_NOWAIT_SUCCESS);
    }

    /**
     * Number of times acquireNoWait() was unsuccessfully called for the lock
     * table latch.
     */
    public int getNAcquiresNoWaitUnSuccessful() {
        return lockStats.getInt(LATCH_NOWAIT_UNSUCCESS);
    }

    /**
     * Number of releases of the lock table latch.
     */
    public int getNReleases() {
        return lockStats.getInt(LATCH_RELEASES);
    }

    /**
     * Returns a String representation of the stats in the form of
     * &lt;stat&gt;=&lt;value&gt;
     */
    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        
        /* IO */
        sb.append(logStats.toString());

        /* Cache */
        sb.append(cacheStats.toString());

        /* Cleaner */
        sb.append(cleanerStats.toString());

        /* INCompressor */
        sb.append(incompStats.toString());

        /* Checkpointer */
        sb.append(ckptStats.toString());
        
        /* EnvironmentImpl */
        sb.append(envImplStats.toString());

        /* Lock Stats */
        sb.append(lockStats.toString());

        return sb.toString();
    }

    /**
     * Returns a String representation of the stats which includes stats
     * descriptions in addition to &lt;stat&gt;=&lt;value&gt;
     */
    public String toStringVerbose() {
        StringBuffer sb = new StringBuffer();

        /* IO */
        sb.append(logStats.toStringVerbose());

        /* Cache */
        sb.append(cacheStats.toStringVerbose());

        /* Cleaner */
        sb.append(cleanerStats.toStringVerbose());

        /* INCompressor */
        sb.append(incompStats.toStringVerbose());

        /* Checkpointer */
        sb.append(ckptStats.toStringVerbose());

        /* EnvironmentImpl */
        sb.append(envImplStats.toStringVerbose());

        /* Lock Stats */
        sb.append(lockStats.toStringVerbose());

        return sb.toString();
    }

    /** 
     * @hidden
     * Internal use only.
     * JConsole plugin support: Get tips for stats.
     */
    public Map<String, String> getTips() {
        /* Put Cleaner stats definition. */
        tipsMap.put(cleanerStats.getName(), cleanerStats.getDescription());
        tipsMap.put(CLEANER_BACKLOG.getName(), 
                    CLEANER_BACKLOG.getDescription());
        tipsMap.put(CLEANER_CLUSTER_LNS_PROCESSED.getName(), 
                    CLEANER_CLUSTER_LNS_PROCESSED.getDescription());
        tipsMap.put(CLEANER_DELETIONS.getName(), 
                    CLEANER_DELETIONS.getDescription());
        tipsMap.put(CLEANER_ENTRIES_READ.getName(), 
                    CLEANER_ENTRIES_READ.getDescription());
        tipsMap.put(CLEANER_FILE_DELETION_BACKLOG.getName(), 
                    CLEANER_FILE_DELETION_BACKLOG.getDescription());
        tipsMap.put(CLEANER_INS_CLEANED.getName(), 
                    CLEANER_INS_CLEANED.getDescription());
        tipsMap.put(CLEANER_INS_DEAD.getName(), 
                    CLEANER_INS_DEAD.getDescription());
        tipsMap.put(CLEANER_INS_MIGRATED.getName(), 
                    CLEANER_INS_MIGRATED.getDescription());
        tipsMap.put(CLEANER_INS_OBSOLETE.getName(), 
                    CLEANER_INS_OBSOLETE.getDescription());
        tipsMap.put(CLEANER_LNQUEUE_HITS.getName(), 
                    CLEANER_LNQUEUE_HITS.getDescription());
        tipsMap.put(CLEANER_LNS_CLEANED.getName(), 
                    CLEANER_LNS_CLEANED.getDescription());
        tipsMap.put(CLEANER_LNS_DEAD.getName(), 
                    CLEANER_LNS_DEAD.getDescription());
        tipsMap.put(CLEANER_LNS_LOCKED.getName(), 
                    CLEANER_LNS_LOCKED.getDescription());
        tipsMap.put(CLEANER_LNS_MARKED.getName(), 
                    CLEANER_LNS_MARKED.getDescription());
        tipsMap.put(CLEANER_LNS_MIGRATED.getName(), 
                    CLEANER_LNS_MIGRATED.getDescription());
        tipsMap.put(CLEANER_LNS_OBSOLETE.getName(), 
                    CLEANER_LNS_OBSOLETE.getDescription());
        tipsMap.put(CLEANER_MARKED_LNS_PROCESSED.getName(), 
                    CLEANER_MARKED_LNS_PROCESSED.getDescription());
        tipsMap.put(CLEANER_PENDING_LNS_LOCKED.getName(), 
                    CLEANER_PENDING_LNS_LOCKED.getDescription());
        tipsMap.put(CLEANER_PENDING_LNS_PROCESSED.getName(), 
                    CLEANER_PENDING_LNS_PROCESSED.getDescription());
        tipsMap.put(CLEANER_REPEAT_ITERATOR_READS.getName(), 
                    CLEANER_REPEAT_ITERATOR_READS.getDescription());
        tipsMap.put(CLEANER_RUNS.getName(), 
                    CLEANER_RUNS.getDescription());
        tipsMap.put(CLEANER_TOTAL_LOG_SIZE.getName(), 
                    CLEANER_TOTAL_LOG_SIZE.getDescription());
        tipsMap.put(CLEANER_TO_BE_CLEANED_LNS_PROCESSED.getName(), 
                    CLEANER_TO_BE_CLEANED_LNS_PROCESSED.getDescription());

        /* Put EnvironmentImpl's stats. */
        tipsMap.put(envImplStats.getName(), envImplStats.getDescription());
        tipsMap.put(ENVIMPL_RELATCHES_REQUIRED.getName(), 
                    ENVIMPL_RELATCHES_REQUIRED.getDescription());

        /* Put Cache (Evictor and Memory budget) stats. */
        tipsMap.put(cacheStats.getName(), cacheStats.getDescription());
        tipsMap.put(MB_ADMIN_BYTES.getName(), 
                    MB_ADMIN_BYTES.getDescription());
        tipsMap.put(MB_DATA_BYTES.getName(), 
                    MB_DATA_BYTES.getDescription());
        tipsMap.put(MB_LOCK_BYTES.getName(), 
                    MB_LOCK_BYTES.getDescription());
        tipsMap.put(MB_SHARED_CACHE_TOTAL_BYTES.getName(), 
                    MB_SHARED_CACHE_TOTAL_BYTES.getDescription());
        tipsMap.put(MB_TOTAL_BYTES.getName(), 
                    MB_TOTAL_BYTES.getDescription());
        tipsMap.put(EVICTOR_BINS_STRIPPED.getName(), 
                    EVICTOR_BINS_STRIPPED.getDescription());
        tipsMap.put(EVICTOR_EVICT_PASSES.getName(), 
                    EVICTOR_EVICT_PASSES.getDescription());
        tipsMap.put(EVICTOR_NODES_EVICTED.getName(), 
                    EVICTOR_NODES_EVICTED.getDescription());
        tipsMap.put(EVICTOR_NODES_SCANNED.getName(), 
                    EVICTOR_NODES_SCANNED.getDescription());
        tipsMap.put(EVICTOR_NODES_SELECTED.getName(), 
                    EVICTOR_NODES_SELECTED.getDescription());
        tipsMap.put(EVICTOR_REQUIRED_EVICT_BYTES.getName(), 
                    EVICTOR_REQUIRED_EVICT_BYTES.getDescription());
        tipsMap.put(EVICTOR_ROOT_NODES_EVICTED.getName(), 
                    EVICTOR_ROOT_NODES_EVICTED.getDescription());
        tipsMap.put(EVICTOR_SHARED_CACHE_ENVS.getName(), 
                    EVICTOR_SHARED_CACHE_ENVS.getDescription());
        
        /* Put INCompressor stats. */
        tipsMap.put(incompStats.getName(), incompStats.getDescription());
        tipsMap.put(INCOMP_CURSORS_BINS.getName(), 
                    INCOMP_CURSORS_BINS.getDescription());
        tipsMap.put(INCOMP_DBCLOSED_BINS.getName(), 
                    INCOMP_DBCLOSED_BINS.getDescription());
        tipsMap.put(INCOMP_NON_EMPTY_BINS.getName(), 
                    INCOMP_NON_EMPTY_BINS.getDescription());
        tipsMap.put(INCOMP_PROCESSED_BINS.getName(), 
                    INCOMP_PROCESSED_BINS.getDescription());
        tipsMap.put(INCOMP_QUEUE_SIZE.getName(), 
                    INCOMP_QUEUE_SIZE.getDescription());
        tipsMap.put(INCOMP_SPLIT_BINS.getName(), 
                    INCOMP_SPLIT_BINS.getDescription());
        
        /* Put latch stats. */
        tipsMap.put(lockStats.getName(), lockStats.getDescription());
        tipsMap.put(LATCH_CONTENTION.getName(), 
                    LATCH_CONTENTION.getDescription());
        tipsMap.put(LATCH_NOWAIT_SUCCESS.getName(), 
                    LATCH_NOWAIT_SUCCESS.getDescription());
        tipsMap.put(LATCH_NOWAIT_UNSUCCESS.getName(), 
                    LATCH_NOWAIT_UNSUCCESS.getDescription());
        tipsMap.put(LATCH_NO_WAITERS.getName(), 
                    LATCH_NO_WAITERS.getDescription());
        tipsMap.put(LATCH_RELEASES.getName(), 
                    LATCH_RELEASES.getDescription());
        tipsMap.put(LATCH_SELF_OWNED.getName(), 
                    LATCH_SELF_OWNED.getDescription());
        
        /* Put LogManager stats. */
        tipsMap.put(logStats.getName(), logStats.getDescription());
        tipsMap.put(FILEMGR_BYTES_READ_FROM_WRITEQUEUE.getName(), 
                    FILEMGR_BYTES_READ_FROM_WRITEQUEUE.getDescription());
        tipsMap.put(FILEMGR_BYTES_WRITTEN_FROM_WRITEQUEUE.getName(), 
                    FILEMGR_BYTES_WRITTEN_FROM_WRITEQUEUE.getDescription());
        tipsMap.put(FILEMGR_FILE_OPENS.getName(), 
                    FILEMGR_FILE_OPENS.getDescription());
        tipsMap.put(FILEMGR_OPEN_FILES.getName(), 
                    FILEMGR_OPEN_FILES.getDescription());
        tipsMap.put(FILEMGR_RANDOM_READS.getName(), 
                    FILEMGR_RANDOM_READS.getDescription());
        tipsMap.put(FILEMGR_RANDOM_READ_BYTES.getName(), 
                    FILEMGR_RANDOM_READ_BYTES.getDescription());
        tipsMap.put(FILEMGR_RANDOM_WRITES.getName(), 
                    FILEMGR_RANDOM_WRITES.getDescription());
        tipsMap.put(FILEMGR_RANDOM_WRITE_BYTES.getName(), 
                    FILEMGR_RANDOM_WRITE_BYTES.getDescription());
        tipsMap.put(FILEMGR_READS_FROM_WRITEQUEUE.getName(), 
                    FILEMGR_READS_FROM_WRITEQUEUE.getDescription());
        tipsMap.put(FILEMGR_SEQUENTIAL_READS.getName(), 
                    FILEMGR_SEQUENTIAL_READS.getDescription());
        tipsMap.put(FILEMGR_SEQUENTIAL_READ_BYTES.getName(), 
                    FILEMGR_SEQUENTIAL_READ_BYTES.getDescription());
        tipsMap.put(FILEMGR_SEQUENTIAL_WRITES.getName(), 
                    FILEMGR_SEQUENTIAL_WRITES.getDescription());
        tipsMap.put(FILEMGR_SEQUENTIAL_WRITE_BYTES.getName(), 
                    FILEMGR_SEQUENTIAL_WRITE_BYTES.getDescription());
        tipsMap.put(FILEMGR_WRITEQUEUE_OVERFLOW.getName(), 
                    FILEMGR_WRITEQUEUE_OVERFLOW.getDescription());
        tipsMap.put(FILEMGR_WRITEQUEUE_OVERFLOW_FAILURES.getName(), 
                    FILEMGR_WRITEQUEUE_OVERFLOW_FAILURES.getDescription());
        tipsMap.put(FILEMGR_WRITES_FROM_WRITEQUEUE.getName(), 
                    FILEMGR_WRITES_FROM_WRITEQUEUE.getDescription());
        tipsMap.put(FSYNCMGR_FSYNCS.getName(), 
                    FSYNCMGR_FSYNCS.getDescription());
        tipsMap.put(FSYNCMGR_FSYNC_REQUESTS.getName(), 
                    FSYNCMGR_FSYNC_REQUESTS.getDescription());
        tipsMap.put(FSYNCMGR_FSYNC_TIMEOUTS.getName(), 
                    FSYNCMGR_FSYNC_TIMEOUTS.getDescription());
        tipsMap.put(LBFP_BUFFER_BYTES.getName(), 
                    LBFP_BUFFER_BYTES.getDescription());
        tipsMap.put(LBFP_LOG_BUFFERS.getName(), 
                    LBFP_LOG_BUFFERS.getDescription());
        tipsMap.put(LBFP_MISS.getName(), 
                    LBFP_MISS.getDescription());
        tipsMap.put(LBFP_NOT_RESIDENT.getName(), 
                    LBFP_NOT_RESIDENT.getDescription());
        tipsMap.put(LOGMGR_END_OF_LOG.getName(), 
                    LOGMGR_END_OF_LOG.getDescription());
        tipsMap.put(LOGMGR_REPEAT_FAULT_READS.getName(), 
                    LOGMGR_REPEAT_FAULT_READS.getDescription());
        tipsMap.put(LOGMGR_TEMP_BUFFER_WRITES.getName(), 
                    LOGMGR_TEMP_BUFFER_WRITES.getDescription());
        
        /* Put Checkpointer stats. */
        tipsMap.put(ckptStats.getName(), ckptStats.getDescription());
        tipsMap.put(CKPT_CHECKPOINTS.getName(), 
                    CKPT_CHECKPOINTS.getDescription());
        tipsMap.put(CKPT_DELTA_IN_FLUSH.getName(), 
                    CKPT_DELTA_IN_FLUSH.getDescription());
        tipsMap.put(CKPT_FULL_BIN_FLUSH.getName(), 
                    CKPT_FULL_BIN_FLUSH.getDescription());
        tipsMap.put(CKPT_FULL_IN_FLUSH.getName(), 
                    CKPT_FULL_IN_FLUSH.getDescription());
        tipsMap.put(CKPT_LAST_CKPTID.getName(), 
                    CKPT_LAST_CKPTID.getDescription());
        tipsMap.put(CKPT_LAST_CKPT_END.getName(), 
                    CKPT_LAST_CKPT_END.getDescription());
        tipsMap.put(CKPT_LAST_CKPT_START.getName(), 
                    CKPT_LAST_CKPT_START.getDescription());

        /* Put Lock stats. */
        tipsMap.put(LOCK_OWNERS.getName(), 
                    LOCK_OWNERS.getDescription());
        tipsMap.put(LOCK_READ_LOCKS.getName(), 
                    LOCK_READ_LOCKS.getDescription());
        tipsMap.put(LOCK_REQUESTS.getName(), 
                    LOCK_REQUESTS.getDescription());
        tipsMap.put(LOCK_TOTAL.getName(), 
                    LOCK_TOTAL.getDescription());
        tipsMap.put(LOCK_WAITERS.getName(), 
                    LOCK_WAITERS.getDescription());
        tipsMap.put(LOCK_WAITS.getName(), 
                    LOCK_WAITS.getDescription());
        tipsMap.put(LOCK_WRITE_LOCKS.getName(), 
                    LOCK_WRITE_LOCKS.getDescription());
        
        return tipsMap;
    }
}
