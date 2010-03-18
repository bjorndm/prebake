 /*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: LogManagerTest.java,v 1.100 2010/01/04 15:51:01 cwl Exp $
 */

package com.sleepycat.je.log;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.EnvironmentStats;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.log.entry.LogEntry;
import com.sleepycat.je.log.entry.SingleItemEntry;
import com.sleepycat.je.util.TestUtils;
import com.sleepycat.je.utilint.DbLsn;

/**
 * Test basic log management.
 */
public class LogManagerTest extends TestCase {

    static private final boolean DEBUG = false;

    private FileManager fileManager;
    private LogManager logManager;
    private final File envHome;
    private Environment env;

    public LogManagerTest() {
        super();
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    @Override
    public void setUp()  {
        TestUtils.removeFiles("Setup", envHome, FileManager.JE_SUFFIX);
        TestUtils.removeFiles("Setup", envHome, FileManager.DEL_SUFFIX);
    }

    @Override
    public void tearDown() {
        TestUtils.removeFiles("TearDown", envHome, FileManager.JE_SUFFIX);
    }

    /**
     * Log and retrieve objects, with log in memory
     */
    public void testBasicInMemory()
        throws IOException, ChecksumException, DatabaseException {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        DbInternal.disableParameterValidation(envConfig);
        envConfig.setConfigParam(EnvironmentParams.NODE_MAX.getName(), "6");
        envConfig.setConfigParam
            (EnvironmentParams.LOG_FILE_MAX.getName(), "1000");
        turnOffDaemons(envConfig);
        envConfig.setAllowCreate(true);
        env = new Environment(envHome, envConfig);
        EnvironmentImpl envImpl = DbInternal.getEnvironmentImpl(env);
        fileManager = envImpl.getFileManager();
        logManager = envImpl.getLogManager();
        logAndRetrieve(envImpl);
        env.close();
    }

    /**
     * Log and retrieve objects, with log completely flushed to disk
     */
    public void testBasicOnDisk()
        throws Throwable {

        try {

            /*
             * Force the buffers and files to be small. The log buffer is
             * actually too small, will have to grow dynamically. Each file
             * only holds one test item (each test item is 50 bytes).
             */
            EnvironmentConfig envConfig = TestUtils.initEnvConfig();
            DbInternal.disableParameterValidation(envConfig);
            envConfig.setConfigParam(
                            EnvironmentParams.LOG_MEM_SIZE.getName(),
                            EnvironmentParams.LOG_MEM_SIZE_MIN_STRING);
            envConfig.setConfigParam(
                            EnvironmentParams.NUM_LOG_BUFFERS.getName(), "2");
            envConfig.setConfigParam(
                            EnvironmentParams.LOG_FILE_MAX.getName(), "79");
            envConfig.setConfigParam(
                            EnvironmentParams.NODE_MAX.getName(), "6");

            /* Disable noisy UtilizationProfile database creation. */
            DbInternal.setCreateUP(envConfig, false);
            /* Don't checkpoint utilization info for this test. */
            DbInternal.setCheckpointUP(envConfig, false);
            /* Don't run the cleaner without a UtilizationProfile. */
            envConfig.setConfigParam
                (EnvironmentParams.ENV_RUN_CLEANER.getName(), "false");

            /*
             * Don't run any daemons, those emit trace messages and other log
             * entries and mess up our accounting.
             */
            turnOffDaemons(envConfig);
            envConfig.setAllowCreate(true);

            /*
             * Recreate the file manager and log manager w/different configs.
             */
            env = new Environment(envHome, envConfig);
            EnvironmentImpl envImpl = DbInternal.getEnvironmentImpl(env);
            fileManager = envImpl.getFileManager();
            logManager = envImpl.getLogManager();

            logAndRetrieve(envImpl);

            /*
             * Expect 10 je files, 7 to hold logged records, 1 to hold root, no
             * recovery messages, 2 for checkpoint records
             */
            String[] names = fileManager.listFiles(FileManager.JE_SUFFIXES);
            assertEquals("Should be 10 files on disk", 10, names.length);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        } finally {
            env.close();
        }
    }

    /**
     * Log and retrieve objects, with some of log flushed to disk, some of log
     * in memory.
     */
    public void testComboDiskMemory()
        throws Throwable {

        try {

            /*
             * Force the buffers and files to be small. The log buffer is
             * actually too small, will have to grow dynamically. Each file
             * only holds one test item (each test item is 50 bytes)
             */
            EnvironmentConfig envConfig = TestUtils.initEnvConfig();
            DbInternal.disableParameterValidation(envConfig);
            envConfig.setConfigParam
                (EnvironmentParams.LOG_MEM_SIZE.getName(),
                 EnvironmentParams.LOG_MEM_SIZE_MIN_STRING);
            envConfig.setConfigParam
                (EnvironmentParams.NUM_LOG_BUFFERS.getName(), "2");
            envConfig.setConfigParam(EnvironmentParams.LOG_FILE_MAX.getName(),
                                     "64");
            envConfig.setConfigParam(EnvironmentParams.NODE_MAX.getName(),
                                     "6");

            /* Disable noisy UtilizationProfile database creation. */
            DbInternal.setCreateUP(envConfig, false);
            /* Don't checkpoint utilization info for this test. */
            DbInternal.setCheckpointUP(envConfig, false);
            /* Don't run the cleaner without a UtilizationProfile. */
            envConfig.setConfigParam
                (EnvironmentParams.ENV_RUN_CLEANER.getName(), "false");

            /*
             * Don't run the cleaner or the checkpointer daemons, those create
             * more log entries and mess up our accounting
             */
            turnOffDaemons(envConfig);
            envConfig.setAllowCreate(true);

            env = new Environment(envHome, envConfig);
            EnvironmentImpl envImpl = DbInternal.getEnvironmentImpl(env);
            fileManager = envImpl.getFileManager();
            logManager = envImpl.getLogManager();

            logAndRetrieve(envImpl);

            /*
             * Expect 10 JE files:
             * root
             * ckptstart
             * ckptend
             * trace trace
             * trace trace
             * trace trace
             * trace trace
             * trace trace 
             * trace trace
             * trace trace
             *
             * This is based on a manual perusal of the log files and their
             * contents. Changes in the sizes of log entries can throw this
             * test off, and require that a check and a change to the assertion
             * value.
             */
            String[] names = fileManager.listFiles(FileManager.JE_SUFFIXES);
            assertEquals("Should be 10 files on disk", 10, names.length);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        } finally {
            env.close();
        }
    }

    /**
     * Log and retrieve objects, with some of log flushed to disk, some
     * of log in memory. Force the read buffer to be very small
     */
    public void testFaultingIn()
        throws Throwable {

        try {

            /*
             * Force the buffers and files to be small. The log buffer is
             * actually too small, will have to grow dynamically. We read in 32
             * byte chunks, will have to re-read only holds one test item (each
             * test item is 50 bytes)
             */
            EnvironmentConfig envConfig = TestUtils.initEnvConfig();
            DbInternal.disableParameterValidation(envConfig);
            envConfig.setConfigParam
                (EnvironmentParams.LOG_MEM_SIZE.getName(),
                 EnvironmentParams.LOG_MEM_SIZE_MIN_STRING);
            envConfig.setConfigParam
                (EnvironmentParams.NUM_LOG_BUFFERS.getName(), "2");
            envConfig.setConfigParam
                (EnvironmentParams.LOG_FILE_MAX.getName(), "200");
            envConfig.setConfigParam
                (EnvironmentParams.LOG_FAULT_READ_SIZE.getName(), "32");
            envConfig.setConfigParam
                (EnvironmentParams.NODE_MAX.getName(), "6");
            envConfig.setAllowCreate(true);
            env = new Environment(envHome, envConfig);
            EnvironmentImpl envImpl = DbInternal.getEnvironmentImpl(env);
            fileManager = envImpl.getFileManager();
            logManager = envImpl.getLogManager();
            logAndRetrieve(envImpl);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        } finally {
            env.close();
        }
    }

    /**
     * Log several objects, retrieve them.
     */
    private void logAndRetrieve(EnvironmentImpl envImpl)
        throws IOException, ChecksumException, DatabaseException {

        /* Make test loggable objects. */
        List<Trace> testRecs = new ArrayList<Trace>();
        for (int i = 0; i < 10; i++) {
            testRecs.add(new Trace("Hello there, rec " + (i+1)));
        }

        /* Log three of them, remember their LSNs. */
        List<Long> testLsns = new ArrayList<Long>();

        for (int i = 0; i < 3; i++) {
            long lsn = testRecs.get(i).trace(envImpl, testRecs.get(i));
            if (DEBUG) {
                System.out.println("i = " + i + " test LSN: file = " +
                                   DbLsn.getFileNumber(lsn) +
                                   " offset = " +
                                   DbLsn.getFileOffset(lsn));
            }
            testLsns.add(new Long(lsn));
        }

        /* Ask for them back, out of order. */
        assertEquals(testRecs.get(2),
                     logManager.getEntry
                     (DbLsn.longToLsn(testLsns.get(2))));
        assertEquals(testRecs.get(0),
                     logManager.getEntry
                     (DbLsn.longToLsn(testLsns.get(0))));
        assertEquals(testRecs.get(1),
                     logManager.getEntry
                     (DbLsn.longToLsn(testLsns.get(1))));

        /* Intersperse logging and getting. */
        testLsns.add
            (new Long(testRecs.get(3).trace(envImpl, testRecs.get(3))));
        testLsns.add
            (new Long(testRecs.get(4).trace(envImpl, testRecs.get(4))));

        assertEquals(testRecs.get(2),
                     logManager.getEntry
                     (DbLsn.longToLsn(testLsns.get(2))));
        assertEquals(testRecs.get(4),
                     logManager.getEntry
                     (DbLsn.longToLsn(testLsns.get(4))));

        /* Intersperse logging and getting. */
        testLsns.add
            (new Long(testRecs.get(5).trace(envImpl, testRecs.get(5))));
        testLsns.add
            (new Long(testRecs.get(6).trace(envImpl, testRecs.get(6))));
        testLsns.add
            (new Long(testRecs.get(7).trace(envImpl, testRecs.get(7))));

        assertEquals(testRecs.get(7),
                     logManager.getEntry
                     (DbLsn.longToLsn(testLsns.get(7))));
        assertEquals(testRecs.get(0),
                     logManager.getEntry
                     (DbLsn.longToLsn(testLsns.get(0))));
        assertEquals(testRecs.get(6),
                     logManager.getEntry
                     (DbLsn.longToLsn(testLsns.get(6))));

        /*
         * Check that we can retrieve log entries as byte buffers, and get the
         * correct object back. Used by replication.
         */
        long lsn = testLsns.get(7).longValue();
        ByteBuffer buffer = logManager.getByteBufferFromLog(lsn);

        HeaderAndEntry contents =
            readHeaderAndEntry(buffer,
                               null,  // envImpl
                               true); // readFullItem

        assertEquals(testRecs.get(7),
                     contents.entry.getMainItem());
        assertEquals(LogEntryType.LOG_TRACE.getTypeNum(),
                     contents.header.getType());
        assertEquals(LogEntryType.LOG_VERSION,
                     contents.header.getVersion());
    }

    private void turnOffDaemons(EnvironmentConfig envConfig) {
        envConfig.setConfigParam(
                       EnvironmentParams.ENV_RUN_CLEANER.getName(),
                      "false");
        envConfig.setConfigParam(
                       EnvironmentParams.ENV_RUN_CHECKPOINTER.getName(),
                       "false");
        envConfig.setConfigParam(
                       EnvironmentParams.ENV_RUN_EVICTOR.getName(),
                       "false");
        envConfig.setConfigParam(
                       EnvironmentParams.ENV_RUN_INCOMPRESSOR.getName(),
                       "false");
    }

    /**
     * Log a few items, then hit exceptions. Make sure LSN state is correctly
     * maintained and that items logged after the exceptions are at the correct
     * locations on disk.
     */
    public void testExceptions()
        throws Throwable {

        int logBufferSize = ((int) EnvironmentParams.LOG_MEM_SIZE_MIN) / 3;
        int numLogBuffers = 5;
        int logBufferMemSize = logBufferSize * numLogBuffers;
        int logFileMax = 1000;
        int okCounter = 0;

        try {
            EnvironmentConfig envConfig = TestUtils.initEnvConfig();
            DbInternal.disableParameterValidation(envConfig);
            envConfig.setConfigParam(EnvironmentParams.LOG_MEM_SIZE.getName(),
                                     new Integer(logBufferMemSize).toString());
            envConfig.setConfigParam
                (EnvironmentParams.NUM_LOG_BUFFERS.getName(),
                 new Integer(numLogBuffers).toString());
            envConfig.setConfigParam
                (EnvironmentParams.LOG_FILE_MAX.getName(),
                 new Integer(logFileMax).toString());
            envConfig.setConfigParam(
                            EnvironmentParams.NODE_MAX.getName(), "6");

            /* Disable noisy UtilizationProfile database creation. */
            DbInternal.setCreateUP(envConfig, false);
            /* Don't checkpoint utilization info for this test. */
            DbInternal.setCheckpointUP(envConfig, false);
            /* Don't run the cleaner without a UtilizationProfile. */
            envConfig.setConfigParam
                (EnvironmentParams.ENV_RUN_CLEANER.getName(), "false");

            /*
             * Don't run any daemons, those emit trace messages and other log
             * entries and mess up our accounting.
             */
            turnOffDaemons(envConfig);
            envConfig.setAllowCreate(true);
            env = new Environment(envHome, envConfig);
            EnvironmentImpl envImpl = DbInternal.getEnvironmentImpl(env);
            fileManager = envImpl.getFileManager();
            logManager = envImpl.getLogManager();

            /* Keep track of items logged and their LSNs. */
            ArrayList<Trace> testRecs = new ArrayList<Trace>();
            ArrayList<Long> testLsns = new ArrayList<Long>();

            /*
             * Intersperse:
             * - log successfully
             * - log w/failure because the item doesn't fit in the log buffer
             * - have I/O failures writing out the log
             * Verify that all expected items can be read. Some will come
             * from the log buffer pool.
             * Then close and re-open the environment, to verify that
             * all log items are faulted from disk
             */

            /* Successful log. */
            addOkayItem(envImpl, okCounter++,
                        testRecs, testLsns, logBufferSize);

            /* Item that's too big for the log buffers. */
            attemptTooBigItem(envImpl, logBufferSize, testRecs, testLsns);

            /* Successful log. */
            addOkayItem(envImpl, okCounter++,
                        testRecs, testLsns, logBufferSize);

            /*
             * This verify read the items from the log buffers. Note before SR
             * #12638 existed (LSN state not restored properly after exception
             * because of too-small log buffer), this verify hung.
             */
            verifyOkayItems(logManager, testRecs, testLsns, true);

            /* More successful logs, along with a few too-big items. */
            for (;okCounter < 23; okCounter++) {
                addOkayItem(envImpl, okCounter, testRecs,
                            testLsns, logBufferSize);

                if ((okCounter % 4) == 0) {
                    attemptTooBigItem(envImpl, logBufferSize,
                                      testRecs, testLsns);
                }
                /*
                 * If we verify in the loop, sometimes we'll read from disk and
                 * sometimes from the log buffer pool.
                 */
                verifyOkayItems(logManager, testRecs, testLsns, true);
            }

            /*
             * Test the case where we flip files and write the old write buffer
             * out before we try getting a log buffer for the new item. We need
             * to
             *
             * - hit a log-too-small exceptin
             * - right after, we need to log an item that is small enough
             *   to fit in the log buffer but big enough to require that
             *   we flip to a new file.
             */
            long nextLsn = fileManager.getNextLsn();
            long fileOffset = DbLsn.getFileOffset(nextLsn);

            assertTrue((logFileMax - fileOffset ) < logBufferSize);
            attemptTooBigItem(envImpl, logBufferSize, testRecs, testLsns);
            addOkayItem(envImpl, okCounter++,
                        testRecs, testLsns, logBufferSize,
                        ((int)(logFileMax - fileOffset)));
            verifyOkayItems(logManager, testRecs, testLsns, true);

            /* Invoke some i/o exceptions. */
            for (;okCounter < 50; okCounter++) {
                attemptIOException(logManager, fileManager, testRecs,
                                   testLsns, false);
                addOkayItem(envImpl, okCounter,
                            testRecs, testLsns, logBufferSize);
                verifyOkayItems(logManager, testRecs, testLsns, false);
            }

            /*
             * Finally, close this environment and re-open, and read all
             * expected items from disk.
             */
            env.close();
            envConfig.setAllowCreate(false);
            env = new Environment(envHome, envConfig);
            envImpl  = DbInternal.getEnvironmentImpl(env);
            fileManager = envImpl.getFileManager();
            logManager = envImpl.getLogManager();
            verifyOkayItems(logManager, testRecs, testLsns, false);

            /* Check that we read these items off disk. */
            EnvironmentStats stats = new EnvironmentStats();
            StatsConfig config = new StatsConfig();
            stats.setLogStats(logManager.loadStats(config));
            assertTrue(stats.getEndOfLog() > 0);
            assertTrue(stats.getNNotResident() >= testRecs.size());

        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        } finally {
            env.close();
        }
    }

    private void addOkayItem(EnvironmentImpl envImpl,
                             int tag,
                             List<Trace> testRecs,
                             List<Long> testLsns,
                             int logBufferSize,
                             int fillerLen)
        throws DatabaseException {

        String filler = new String(new byte[fillerLen]);
        Trace t = new Trace("okay" + filler + tag );
        assertTrue(logBufferSize > t.getLogSize());
        testRecs.add(t);
        long lsn = t.trace(envImpl, t);
        testLsns.add(new Long(lsn));
    }

    private void addOkayItem(EnvironmentImpl envImpl,
                             int tag,
                             List<Trace> testRecs,
                             List<Long> testLsns,
                             int logBufferSize)
        throws DatabaseException {

        addOkayItem(envImpl, tag, testRecs, testLsns, logBufferSize, 0);
    }

    private void attemptTooBigItem(EnvironmentImpl envImpl,
                                   int logBufferSize,
                                   Trace big,
                                   List<Trace> testRecs,
                                   List<Long> testLsns) {
        assertTrue(big.getLogSize() > logBufferSize);

        try {
            long lsn = big.trace(envImpl, big);
            testLsns.add(new Long(lsn));
            testRecs.add(big);
        } catch (DatabaseException expected) {
            fail("Should not have hit exception.");
        }
    }

    private void attemptTooBigItem(EnvironmentImpl envImpl,
                                   int logBufferSize,
                                   List<Trace> testRecs,
                                   List<Long> testLsns) {
        String stuff = "12345679890123456798901234567989012345679890";
        while (stuff.length() < EnvironmentParams.LOG_MEM_SIZE_MIN) {
            stuff += stuff;
        }
        Trace t = new Trace(stuff);
        attemptTooBigItem(envImpl, logBufferSize, t, testRecs, testLsns);
    }

    private void attemptIOException(LogManager logManager,
                                    FileManager fileManager,
                                    List<Trace> testRecs,
                                    List<Long> testLsns,
                                    boolean forceFlush) {
        Trace t = new Trace("ioException");
        FileManager.IO_EXCEPTION_TESTING_ON_WRITE = true;
        try {

            /*
             * This object might get flushed to disk -- depend on whether
             * the ioexception happened before or after the copy into the
             * log buffer. Both are valid, but the test doesn't yet
             * know how to differentiate the cases.

               testLsns.add(new Long(fileManager.getNextLsn()));
               testRecs.add(t);
            */
            logManager.logForceFlush
                (new SingleItemEntry(LogEntryType.LOG_TRACE, t),
                 true,  // fsyncRequired
                 ReplicationContext.NO_REPLICATE);
            fail("expect io exception");
        } catch (DatabaseException expected) {
        } finally {
            FileManager.IO_EXCEPTION_TESTING_ON_WRITE = false;
        }
    }

    private void verifyOkayItems(LogManager logManager,
                                 ArrayList<Trace> testRecs,
                                 ArrayList<Long> testLsns,
                                 boolean checkOrder)
        throws IOException, DatabaseException {

        /* read forwards. */
        for (int i = 0; i < testRecs.size(); i++) {
            assertEquals(testRecs.get(i),
                         logManager.getEntry
                         (DbLsn.longToLsn(testLsns.get(i))));

        }

        /* Make sure LSNs are adjacent */
        assertEquals(testLsns.size(), testRecs.size());

        if (checkOrder) {

            /*
             * TODO: sometimes an ioexception entry will make it into the write
             * buffer, and sometimes it won't. It depends on whether the IO
             * exception was thrown when before or after the logabble item was
             * written into the buffer.  I haven't figure out yet how to tell
             * the difference, so for now, we don't check order in the portion
             * of the test that issues IO exceptions.
             */
            for (int i = 1; i < testLsns.size(); i++) {

                long lsn = testLsns.get(i).longValue();
                long lsnFile = DbLsn.getFileNumber(lsn);
                long lsnOffset = DbLsn.getFileOffset(lsn);
                long prevLsn = testLsns.get(i-1).longValue();
                long prevFile = DbLsn.getFileNumber(prevLsn);
                long prevOffset = DbLsn.getFileOffset(prevLsn);
                if (prevFile == lsnFile) {
                    assertEquals("item " + i + "prev = " +
                                 DbLsn.toString(prevLsn) +
                                 " current=" + DbLsn.toString(lsn),
                                 (testRecs.get(i-1).getLogSize() +
                                  LogEntryHeader.MIN_HEADER_SIZE),
                                 lsnOffset - prevOffset);
                } else {
                    assertEquals(prevFile+1, lsnFile);
                    assertEquals(FileManager.firstLogEntryOffset(),
                                 lsnOffset);
                }
            }
        }

        /* read backwards. */
        for (int i = testRecs.size() - 1; i > -1; i--) {
            assertEquals(testRecs.get(i),
                         logManager.getEntry
                         (DbLsn.longToLsn(testLsns.get(i))));

        }
    }

    /**
     * Convenience method for marshalling a header and log entry
     * out of a byte buffer read directly out of the log.
     * @throws DatabaseException
     */
    private static HeaderAndEntry readHeaderAndEntry(ByteBuffer bytesFromLog,
                                                     EnvironmentImpl envImpl,
                                                     boolean readFullItem)
        throws ChecksumException {

        HeaderAndEntry ret = new HeaderAndEntry();
        ret.header = new LogEntryHeader(bytesFromLog,
                                        LogEntryType.LOG_VERSION);
        ret.header.readVariablePortion(bytesFromLog);

        ret.entry =
            LogEntryType.findType(ret.header.getType()).getNewLogEntry();

        ret.entry.readEntry(ret.header,
                            bytesFromLog,
                            readFullItem);
        return ret;
    }

    private static class HeaderAndEntry {
        public LogEntryHeader header;
        public LogEntry entry;

        /* Get an HeaderAndEntry from readHeaderAndEntry */
        private HeaderAndEntry() {
        }

        public boolean logicalEquals(HeaderAndEntry other) {
            return (header.logicalEquals(other.header) &&
                    entry.logicalEquals(other.entry));
        }

        @Override
        public String toString() {
            return header.toString() + ' ' + entry;
        }
    }
}
