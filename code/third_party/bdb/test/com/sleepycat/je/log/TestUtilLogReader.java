/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: TestUtilLogReader.java,v 1.16 2010/01/04 15:51:01 cwl Exp $
 */

package com.sleepycat.je.log;

import java.nio.ByteBuffer;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.log.entry.LogEntry;
import com.sleepycat.je.utilint.DbLsn;

/**
 * Instantiates all log entries using the shared log entry instances.
 */
public class TestUtilLogReader extends FileReader {

    private LogEntryType entryType;
    private LogEntry entry;
    private boolean readFullItem;

    public TestUtilLogReader(EnvironmentImpl env, boolean readFullItem)
        throws DatabaseException {

        super(env,
              4096,
              true,
              DbLsn.NULL_LSN,
              null,
              DbLsn.NULL_LSN,
              DbLsn.NULL_LSN);
        this.readFullItem = readFullItem;
    }

    public TestUtilLogReader(EnvironmentImpl env,
                             int readBufferSize,
                             boolean forward,
                             long startLsn,
                             Long singleFileNumber,
                             long endOfFileLsn,
                             long finishLsn)
        throws DatabaseException {

        super(env,
              readBufferSize,
              forward,
              startLsn,
              singleFileNumber,
              endOfFileLsn,
              finishLsn);
    }

    public LogEntryType getEntryType() {
        return entryType;
    }

    public int getEntryVersion() {
        return currentEntryHeader.getVersion();
    }

    public LogEntry getEntry() {
        return entry;
    }

    protected boolean isTargetEntry() {
        return true;
    }

    protected boolean processEntry(ByteBuffer entryBuffer)
        throws DatabaseException {

        entryType = LogEntryType.findType(currentEntryHeader.getType());
        entry = entryType.getSharedLogEntry();
        entry.readEntry(currentEntryHeader,
                        entryBuffer,
                        readFullItem);
        return true;
    }
}
