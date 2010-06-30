/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: GroupShutdownException.java,v 1.4 2010/01/04 15:50:45 cwl Exp $
 */
package com.sleepycat.je.rep;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.sleepycat.je.EnvironmentFailureException;
import com.sleepycat.je.dbi.EnvironmentFailureReason;
import com.sleepycat.je.rep.impl.node.RepNode;
import com.sleepycat.je.utilint.LoggerUtils;
import com.sleepycat.je.utilint.VLSN;
/**
 * Thrown when an attempt is made to access an environment  that was
 * shutdown by the Master as a result of a call to
 * {@link ReplicatedEnvironment#shutdownGroup(long, TimeUnit)}.
 */
@SuppressWarnings("serial")
public class GroupShutdownException extends EnvironmentFailureException {
    /* The time that the shutdown was initiated on the master. */
    private final long shutdownTimeMs;

    /* The master node that initiated the shutdown. */
    private final String masterNodeName;

    /* The time at which the shutdown was initiated on the master. */
    private final VLSN shutdownVLSN;

    private String wrapMessage = null;

    /**
     * For internal use only.
     * @hidden
     */
    public GroupShutdownException(Logger logger,
                                  RepNode repNode,
                                  long shutdownTimeMs) {
        super(repNode.getRepImpl(),
              EnvironmentFailureReason.SHUTDOWN_REQUESTED,
              (Throwable)null);

        shutdownVLSN = repNode.getVLSNIndex().getRange().getLast();
        masterNodeName =
            repNode.getMasterStatus().getNodeMasterNameId().getName();
        this.shutdownTimeMs = shutdownTimeMs;

        LoggerUtils.warning(logger, repNode.getRepImpl(), 
                            "Explicit shutdown request from Master:" +
                            masterNodeName);
    }

    /**
     * For internal use only.
     * @hidden
     */
    private GroupShutdownException(String message,
                                   GroupShutdownException shutdownException) {
        super(message, shutdownException);
        shutdownVLSN = shutdownException.shutdownVLSN;
        shutdownTimeMs = shutdownException.shutdownTimeMs;
        masterNodeName =  shutdownException.masterNodeName;
        this.wrapMessage = message;
    }

    /**
     * @see com.sleepycat.je.DatabaseException#getMessage()
     */
    @Override
    public String getMessage() {
        return (wrapMessage != null) ?
                wrapMessage :
                String.format("Master:%s, initiated shutdown on %1tc.",
                              masterNodeName,
                              shutdownTimeMs);
    }

    /**
     * For internal use only.
     * @hidden
     */
    @Override
    public  GroupShutdownException wrapSelf(String msg) {
        return new GroupShutdownException(msg, this);
    }

    /**
     * For internal use only.
     * @hidden
     */
    public VLSN getShutdownVLSN() {
        return shutdownVLSN;
    }
}
