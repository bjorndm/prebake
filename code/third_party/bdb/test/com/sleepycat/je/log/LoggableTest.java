/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: LoggableTest.java,v 1.109 2010/01/04 15:51:01 cwl Exp $
 */

package com.sleepycat.je.log;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

import junit.framework.TestCase;

import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.cleaner.FileSummary;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.DatabaseId;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.DbTree;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.recovery.CheckpointEnd;
import com.sleepycat.je.recovery.CheckpointStart;
import com.sleepycat.je.tree.BIN;
import com.sleepycat.je.tree.ChildReference;
import com.sleepycat.je.tree.DBIN;
import com.sleepycat.je.tree.DIN;
import com.sleepycat.je.tree.FileSummaryLN;
import com.sleepycat.je.tree.IN;
import com.sleepycat.je.tree.INDeleteInfo;
import com.sleepycat.je.tree.LN;
import com.sleepycat.je.tree.MapLN;
import com.sleepycat.je.txn.RollbackEnd;
import com.sleepycat.je.txn.RollbackStart;
import com.sleepycat.je.txn.TxnAbort;
import com.sleepycat.je.txn.TxnCommit;
import com.sleepycat.je.txn.TxnPrepare;
import com.sleepycat.je.util.TestUtils;
import com.sleepycat.je.utilint.DbLsn;
import com.sleepycat.je.utilint.Matchpoint;
import com.sleepycat.je.utilint.VLSN;

/**
 * Check that every loggable object can be read in and out of a buffer
 */
public class LoggableTest extends TestCase {

    static final boolean verbose = Boolean.getBoolean("verbose");

    // private DocumentBuilder builder;
    private Environment env;
    private final File envHome;
    private DatabaseImpl database;

    public LoggableTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    @Override
    public void setUp()
        throws DatabaseException {

        TestUtils.removeFiles("Setup", envHome, FileManager.JE_SUFFIX);

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setConfigParam(EnvironmentParams.NODE_MAX.getName(), "6");
        envConfig.setAllowCreate(true);
        env = new Environment(envHome, envConfig);
    }

    @Override
    public void tearDown()
        throws DatabaseException {

        TestUtils.removeFiles("TearDown", envHome, FileManager.JE_SUFFIX);
        env.close();
    }

    public void testEntryData()
        throws Throwable {

        try {
            ByteBuffer buffer = ByteBuffer.allocate(1000);
            EnvironmentImpl envImpl = DbInternal.getEnvironmentImpl(env);
            database = new DatabaseImpl("foo", new DatabaseId(1),
                                         envImpl, new DatabaseConfig());

            /*
             * For each loggable object, can we write the entry data out?
             */

            /*
             * Trace records.
             */
            Trace dMsg = new Trace("Hello there");
            writeAndRead(buffer, LogEntryType.LOG_TRACE,  dMsg, new Trace());

            /*
             * LNs
             */
            String data = "abcdef";
            LN ln = new LN(data.getBytes(), envImpl, false /* replicated */);
            LN lnFromLog = new LN();
            writeAndRead(buffer, LogEntryType.LOG_LN, ln, lnFromLog);
            lnFromLog.verify(null);
            assertTrue(LogEntryType.LOG_LN.marshallOutsideLatch());

            FileSummaryLN fsLN = new FileSummaryLN(envImpl, new FileSummary());
            FileSummaryLN fsLNFromLog = new FileSummaryLN();
            writeAndRead(buffer, LogEntryType.LOG_FILESUMMARYLN,
                         fsLN, fsLNFromLog);
            assertFalse(
                   LogEntryType.LOG_FILESUMMARYLN.marshallOutsideLatch());

            /*
             * INs
             */
            IN in = new IN(database,
                           new byte[] {1,0,1,0},
                           7, 5);
            in.latch();
            in.insertEntry(new ChildReference(null,
                                              new byte[] {1,0,1,0},
                                              DbLsn.makeLsn(12, 200)));
            in.insertEntry(new ChildReference(null,
                                              new byte[] {1,1,1,0},
                                              DbLsn.makeLsn(29, 300)));
            in.insertEntry(new ChildReference(null,
                                              new byte[] {0,0,1,0},
                                              DbLsn.makeLsn(35, 400)));

            /* Write it. */
            IN inFromLog = new IN();
            inFromLog.latch();
            writeAndRead(buffer, LogEntryType.LOG_IN, in, inFromLog);
            inFromLog.releaseLatch();
            in.releaseLatch();

            /*
             * IN - long form
             */
            in = new IN(database,
                        new byte[] {1,0,1,0},
                        7, 5);
            in.latch();
            in.insertEntry(new ChildReference(null,
                                              new byte[] {1,0,1,0},
                                              DbLsn.makeLsn(12, 200)));
            in.insertEntry(new ChildReference(null,
                                              new byte[] {1,1,1,0},
                                              DbLsn.makeLsn(29, 300)));
            in.insertEntry(new ChildReference(null,
                                              new byte[] {0,0,1,0},
                                              DbLsn.makeLsn(1235, 400)));
            in.insertEntry(new ChildReference(null,
                                              new byte[] {0,0,1,0},
                                              DbLsn.makeLsn(0xFFFFFFF0L, 400)));

            /* Write it. */
            inFromLog = new IN();
            inFromLog.latch();
            writeAndRead(buffer, LogEntryType.LOG_IN, in, inFromLog);
            inFromLog.releaseLatch();
            in.releaseLatch();

            /*
             * BINs
             */
            BIN bin = new BIN(database,
                              new byte[] {3,2,1},
                              8, 5);
            bin.latch();
            bin.insertEntry(new ChildReference(null,
                                               new byte[] {1,0,1,0},
                                               DbLsn.makeLsn(212, 200)));
            bin.insertEntry(new ChildReference(null,
                                               new byte[] {1,1,1,0},
                                               DbLsn.makeLsn(229, 300)));
            bin.insertEntry(new ChildReference(null,
                                               new byte[] {0,0,1,0},
                                               DbLsn.makeLsn(235, 400)));
            BIN binFromLog = new BIN();
            binFromLog.latch();
            writeAndRead(buffer, LogEntryType.LOG_BIN, bin, binFromLog);
            binFromLog.verify(null);
            binFromLog.releaseLatch();
            bin.releaseLatch();

            /*
             * DINs
             */
            DIN din = new DIN(database,
                              new byte[] {1,0,0,1},
                              7,
                              new byte[] {0,1,1,0},
                              new ChildReference(null,
                                                 new byte[] {1,0,0,1},
                                                 DbLsn.makeLsn(10, 100)),
                              5);
            din.latch();
            din.insertEntry(new ChildReference(null,
                                               new byte[] {1,0,1,0},
                                               DbLsn.makeLsn(12, 200)));
            din.insertEntry(new ChildReference(null,
                                               new byte[] {1,1,1,0},
                                               DbLsn.makeLsn(29, 300)));
            din.insertEntry(new ChildReference(null,
                                               new byte[] {0,0,1,0},
                                               DbLsn.makeLsn(35, 400)));

            /* Write it. */
            DIN dinFromLog = new DIN();
            dinFromLog.latch();
            writeAndRead(buffer, LogEntryType.LOG_DIN, din, dinFromLog);
            din.releaseLatch();
            dinFromLog.releaseLatch();

            /*
             * DBINs
             */
            DBIN dbin = new DBIN(database,
                                 new byte[] {3,2,1},
                                 8,
                                 new byte[] {1,2,3},
                                 5);
            dbin.latch();
            dbin.insertEntry(new ChildReference(null,
                                                new byte[] {1,0,1,0},
                                                DbLsn.makeLsn(212, 200)));
            dbin.insertEntry(new ChildReference(null,
                                                new byte[] {1,1,1,0},
                                                DbLsn.makeLsn(229, 300)));
            dbin.insertEntry(new ChildReference(null,
                                                new byte[] {0,0,1,0},
                                                DbLsn.makeLsn(235, 400)));
            DBIN dbinFromLog = new DBIN();
            dbinFromLog.latch();
            writeAndRead(buffer, LogEntryType.LOG_DBIN, dbin, dbinFromLog);
            dbinFromLog.verify(null);
            dbin.releaseLatch();
            dbinFromLog.releaseLatch();

            /*
             * Root
             */
            DbTree dbTree = new DbTree(envImpl,
                                       false /* replicationIntended */);
            DbTree dbTreeFromLog = new DbTree();
            writeAndRead(buffer, LogEntryType.LOG_ROOT, dbTree, dbTreeFromLog);
            dbTree.close();

            /*
             * MapLN
             */
            MapLN mapLn = new MapLN(database);
            MapLN mapLnFromLog = new MapLN();
            writeAndRead(buffer, LogEntryType.LOG_MAPLN, mapLn, mapLnFromLog);

            /*
             * UserTxn
             */

            /*
             * Disabled for now because these txns don't compare equal,
             * because one has a name of "main" and the other has a name of
             * null because it was read from the log.

             Txn txn = new Txn(envImpl, new TransactionConfig());
             Txn txnFromLog = new Txn();
             writeAndRead(buffer, LogEntryType.TXN_COMMIT, txn, txnFromLog);
             txn.commit();
            */

            /*
             * TxnCommit
             */
            TxnCommit commit = new TxnCommit(111, DbLsn.makeLsn(10, 10),
                                                 179 /* masterNodeId */);
            TxnCommit commitFromLog = new TxnCommit();
            writeAndRead(buffer, LogEntryType.LOG_TXN_COMMIT, commit,
                         commitFromLog);

            /*
             * TxnAbort
             */
            TxnAbort abort = new TxnAbort(111, DbLsn.makeLsn(11, 11),
                                              7654321 /* masterNodeId*/);
            TxnAbort abortFromLog = new TxnAbort();
            writeAndRead(buffer, LogEntryType.LOG_TXN_ABORT,
                         abort, abortFromLog);

            /*
             * TxnPrepare
             */
            byte[] gid = new byte[64];
            byte[] bqual = new byte[64];
            TxnPrepare prepare =
                new TxnPrepare(111, new LogUtils.XidImpl(1, gid, bqual));
            TxnPrepare prepareFromLog = new TxnPrepare();
            writeAndRead(buffer, LogEntryType.LOG_TXN_PREPARE, prepare,
                         prepareFromLog);

            prepare =
                new TxnPrepare(111, new LogUtils.XidImpl(1, null, bqual));
            prepareFromLog = new TxnPrepare();
            writeAndRead(buffer, LogEntryType.LOG_TXN_PREPARE,
                         prepare, prepareFromLog);

            prepare =
                new TxnPrepare(111, new LogUtils.XidImpl(1, gid, null));
            prepareFromLog = new TxnPrepare();
            writeAndRead(buffer, LogEntryType.LOG_TXN_PREPARE,
                         prepare, prepareFromLog);

            /*
             * IN delete info
             */
            INDeleteInfo info = new INDeleteInfo(77, new byte[1],
                                                 new DatabaseId(100));
            INDeleteInfo infoFromLog = new INDeleteInfo();
            writeAndRead(buffer, LogEntryType.LOG_IN_DELETE_INFO,
                         info, infoFromLog);

            /*
             * Checkpoint start
             */
            CheckpointStart start = new CheckpointStart(177, "test");
            CheckpointStart startFromLog = new CheckpointStart();
            writeAndRead(buffer, LogEntryType.LOG_CKPT_START,
                         start, startFromLog);

            /*
             * Checkpoint end
             */
            CheckpointEnd end = new CheckpointEnd
                ("test",
                 DbLsn.makeLsn(20, 55),
                 envImpl.getRootLsn(),
                 envImpl.getTxnManager().getFirstActiveLsn(),
                 envImpl.getNodeSequence().getLastLocalNodeId(),
                 envImpl.getNodeSequence()
                                  .getLastReplicatedNodeId(),
                 envImpl.getDbTree().getLastLocalDbId(),
                 envImpl.getDbTree().getLastReplicatedDbId(),
                 envImpl.getTxnManager().getLastLocalTxnId(),
                 envImpl.getTxnManager().getLastReplicatedTxnId(),
                 177,
                 true /*cleanerFilesToDelete*/);
            CheckpointEnd endFromLog = new CheckpointEnd();
            writeAndRead(buffer, LogEntryType.LOG_CKPT_END, end, endFromLog);

            /**
             * RollbackStart
             */
            Set<Long> activeTxnIds = new HashSet<Long>();
            activeTxnIds.add(1999L);
            activeTxnIds.add(2999L);
            RollbackStart rs = new RollbackStart(new VLSN(1001), 
                                                 99, 
                                                 activeTxnIds);
            RollbackStart rsFromLog = new RollbackStart();
            writeAndRead(buffer, LogEntryType.LOG_ROLLBACK_START, rs, 
                         rsFromLog);
                                                          
            /**
             * RollbackEnd
             */
            RollbackEnd re = new RollbackEnd(39L, 79L);
            RollbackEnd reFromLog = new RollbackEnd();
            writeAndRead(buffer, LogEntryType.LOG_ROLLBACK_END, re, 
                         reFromLog);

            /**
             * Matchpoint
             */
            Matchpoint matchpoint = new Matchpoint(5);
            Matchpoint matchpointFromLog = new Matchpoint();
            writeAndRead(buffer, LogEntryType.LOG_ROLLBACK_END, matchpoint,
                         matchpointFromLog);

            /*
             * Mimic what happens when the environment is closed.
             */
            database.releaseTreeAdminMemory();

        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * Helper which takes a dbLoggable, writes it, reads it back and
     * checks for equality and size
     */
    private void writeAndRead(ByteBuffer buffer,
                              LogEntryType entryType,
                              Loggable orig,
                              Loggable fromLog)
        throws Exception {

        /* Write it. */
        buffer.clear();
        orig.writeToLog(buffer);

        /* Check the log size. */
        buffer.flip();
        assertEquals(buffer.limit(), orig.getLogSize());

        /*
         * Read it and compare sizes. Note that we assume we're testing
         * objects that are readable and writable to the log.
         */
        fromLog.readFromLog(buffer, LogEntryType.LOG_VERSION);
        assertEquals(orig.getLogSize(), fromLog.getLogSize());

        assertEquals("We should have read the whole buffer for " +
                     fromLog.getClass().getName(),
                     buffer.limit(), buffer.position());

        /* Compare contents. */
        StringBuilder sb1 = new StringBuilder();
        StringBuilder sb2 = new StringBuilder();
        orig.dumpLog(sb1, true);
        fromLog.dumpLog(sb2, true);

        if (verbose) {
            System.out.println("sb1 = " + sb1.toString());
            System.out.println("sb2 = " + sb2.toString());
        }
        assertEquals("Not equals for " +
                     fromLog.getClass().getName(),
                     sb1.toString(), sb2.toString());

        /* Validate that the dump string is valid XML. */
        //        builder = factory.newDocumentBuilder();
        //        builder.parse("<?xml version=\"1.0\" ?>");
        //                      sb1.toString()+
    }
}
