/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2010 Oracle.  All rights reserved.
 *
 * $Id: LocalCBVLSNTracker.java,v 1.5 2010/01/04 15:50:47 cwl Exp $
 */

package com.sleepycat.je.rep.impl.node;

import com.sleepycat.je.rep.impl.RepParams;
import com.sleepycat.je.rep.vlsn.VLSNIndex;
import com.sleepycat.je.utilint.DbLsn;
import com.sleepycat.je.utilint.VLSN;

/**
 * The LocalCBVLSNTracker tracks this node's local CBVLSN. Each node has a
 * single tracker instance.
 *
 * The local CBVLSN is maintained by each node. Replicas periodically update
 * the Master with their current CBVLSN via a response to a heartbeat message
 * from the Master, where it is managed by the LocalCBVLSNUpdater and
 * flushed out to RepGroup database, whenever the updater notices that it
 * has changed. The change is then effectively broadcast to all the Replicas
 * including the originating Replica, via the replication stream. For this
 * reason, the CBVLSN for the node as represented in the RepGroup database
 * will generally lag the value contained in the tracker.
 *
 * Note that track() api is invoked in critical code with locks being held and
 * must be lightweight.
 *
 * Local CBVLSNs are used only to contribute to the calculation of the global
 * CBVLSN. The global CBVLSN acts as the cleaner throttle. Any invariants, such
 * as the rule that the cleaner throttle cannot regress, are applied when doing
 * the global calculation.
 */
public class LocalCBVLSNTracker {

    private final VLSNIndex vlsnIndex;
    private final int padValue;
    private VLSN currentLocalCBVLSN;

    /*
     * We really only need to update the localCBVLSN once per file. currentFile
     * is used to determine if this is the first VLSN in the file.
     */
    private long currentFile;

    LocalCBVLSNTracker(RepNode repNode) {
        padValue = repNode.getRepImpl().getConfigManager().
            getInt(RepParams.CBVLSN_PAD);

        vlsnIndex = repNode.getRepImpl().getVLSNIndex();

        currentLocalCBVLSN = vlsnIndex.getRange().getLastSync();

        /* Approximation good enough to start with. */
        currentFile = DbLsn.getFileNumber(DbLsn.NULL_LSN);
    }

    /**
     * Tracks barrier VLSNs, updating the local CBVLSN if the associated log
     * file has changed. When tracking is done on a replica, the
     * currentLocalCBVLSN value is ultimately sent via heartbeat response to
     * the master, which updates the RepGroupDb. When tracking is done on a
     * master, the update is done on this node.
     *
     * Tracking can be called quite often, and should be lightweight.
     * @param syncableVLSN
     * @param lsn
     */
    public void track(VLSN syncableVLSN, long lsn) {
        if (DbLsn.getFileNumber(lsn) == currentFile) {
            return;
        }
        this.currentLocalCBVLSN = syncableVLSN;
        currentFile = DbLsn.getFileNumber(lsn);
    }

    /**
     * Initialize the local CBVLSN with the syncup matchpoint, so that the
     * heartbeat responses sent before the node has replayed any log entries
     * are still valid for saving a place in the replication stream.
     * @param matchpoint
     */
    public void registerMatchpoint(VLSN matchpoint) {
        this.currentLocalCBVLSN = matchpoint;
    }

    /**
     * @return the local CBVLSN for broadcast from replica to master on the
     * heartbeat response. Adjustments are made here, so the value
     * broadcast is not literally the current local CBVLSN.
     */
    public VLSN getBroadcastCBVLSN() {

        /*
         * It would be better if the pad was directly correlated to the
         * je.cleaner.minAge property, but that is expressed in files, rather
         * than VLSNs, and we do not have a cheap way to translate files to
         * vlsn.
         */
        if (padValue == 0) {
            return currentLocalCBVLSN;
        }

        VLSN minVLSN = vlsnIndex.getRange().getFirst();
        long paddedCBVLSNVal =
            (currentLocalCBVLSN.getSequence() - padValue);
        if (paddedCBVLSNVal < minVLSN.getSequence()) {
            return minVLSN;
        }

        return new VLSN(paddedCBVLSNVal);
    }
}
