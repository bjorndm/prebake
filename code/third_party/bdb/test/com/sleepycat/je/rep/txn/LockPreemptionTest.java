/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: LockPreemptionTest.java,v 1.4 2010/01/04 15:51:06 cwl Exp $
 */

package com.sleepycat.je.rep.txn;

import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.CursorConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Durability;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.rep.LockPreemptedException;
import com.sleepycat.je.rep.ReplicatedEnvironment;
import com.sleepycat.je.rep.ReplicationConfig;
import com.sleepycat.je.rep.utilint.RepTestUtils;
import com.sleepycat.je.rep.utilint.RepTestUtils.RepEnvInfo;
import com.sleepycat.je.util.TestUtils;

public class LockPreemptionTest extends TestCase {

    private static final byte[] KEY1 = new byte[] { 1 };
    private static final byte[] KEY2 = new byte[] { 2 };
    private static final byte[] DATA = new byte[1];

    private File envRoot;
    private RepEnvInfo[] repEnvInfo;
    private ReplicatedEnvironment masterEnv;
    private ReplicatedEnvironment replicaEnv;
    private Database masterDb;
    private Database replicaDb;

    public LockPreemptionTest() {
        envRoot = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    @Override
    public void setUp() {
        RepTestUtils.removeRepEnvironments(envRoot);
    }

    @Override
    public void tearDown() {
        if (repEnvInfo != null) {

            /*
             * close() was not called, test failed. Do cleanup to allow more
             * tests to run, but leave log files for debugging this test case.
             */
            try {
                close(false /*normalShutdown*/);
            } catch (Exception ignored) {
                /* This secondary exception is just noise. */
            }
        } else {

            /*
             * close() was called, test passed.  Remove log files and propagate
             * I/O errors upward. 
             */
            RepTestUtils.removeRepEnvironments(envRoot);
        }
    }

    /**
     * Create a 2 node group and insert two records into a database.
     *
     * The minimum replay txn timeout is configured to cause the replayer to
     * timeout immediately when there is a lock conflict, since that's the
     * situation we're testing for.
     *
     * ReplicaAckPolicy.ALL is used to ensure that when a master operation is
     * committed, the change is immediately available on the replica for
     * testing -- no waiting in the test is needed.
     */
    private void open()
        throws IOException {

        repEnvInfo = RepTestUtils.setupEnvInfos
            (envRoot, 2,
             RepTestUtils.createEnvConfig
                 (new Durability(Durability.SyncPolicy.WRITE_NO_SYNC,
                                 Durability.SyncPolicy.WRITE_NO_SYNC,
                                 Durability.ReplicaAckPolicy.ALL)),
             new ReplicationConfig().setConfigParam
                (ReplicationConfig.REPLAY_TXN_LOCK_TIMEOUT, "1 ms"));
        masterEnv = RepTestUtils.joinGroup(repEnvInfo);
        replicaEnv = repEnvInfo[1].getEnv();
        assertNotNull(masterEnv);
        assertNotNull(replicaEnv);
        assertNotSame(masterEnv, replicaEnv);
        masterDb = masterEnv.openDatabase
            (null, "foo",
             new DatabaseConfig().setAllowCreate(true).setTransactional(true));
        replicaDb = replicaEnv.openDatabase
            (null, "foo", new DatabaseConfig().setTransactional(true));
        /* Insert records to operate on. */
        assertSame(OperationStatus.SUCCESS,
                   masterDb.put(null, new DatabaseEntry(KEY1),
                                new DatabaseEntry(DATA)));
        assertSame(OperationStatus.SUCCESS,
                   replicaDb.get(null, new DatabaseEntry(KEY1),
                                 new DatabaseEntry(), null));
        assertSame(OperationStatus.SUCCESS,
                   masterDb.put(null, new DatabaseEntry(KEY2),
                                new DatabaseEntry(DATA)));
        assertSame(OperationStatus.SUCCESS,
                   replicaDb.get(null, new DatabaseEntry(KEY2),
                                 new DatabaseEntry(), null));
    }

    private void close() {
        close(true /*normalShutdown*/);
    }

    private void close(boolean normalShutdown) {
        try {
            if (normalShutdown) {
                masterDb.close();
                replicaDb.close();
                RepTestUtils.shutdownRepEnvs(repEnvInfo);
            } else {
                for (RepEnvInfo info : repEnvInfo) {
                    info.abnormalCloseEnv();
                }
            }
        } finally {
            repEnvInfo = null;
            masterEnv = null;
            replicaEnv = null;
            masterDb = null;
            replicaDb = null;
        }
    }

    /**
     * Stealing from a txn that reads again WILL cause LockPreemtped.
     */
    public void testPreempted()
        throws IOException {

        open();

        /* Read. */
        final Transaction replicaTxn = replicaEnv.beginTransaction(null, null);
        assertSame(OperationStatus.SUCCESS,
                   replicaDb.get(replicaTxn, new DatabaseEntry(KEY1),
                                  new DatabaseEntry(), null));

        /* Steal. */
        assertSame(OperationStatus.SUCCESS,
                   masterDb.put(null, new DatabaseEntry(KEY1),
                                new DatabaseEntry(DATA)));

        /* Read. */
        try {
            replicaDb.get(replicaTxn, new DatabaseEntry(KEY1),
                          new DatabaseEntry(), null);
            fail();
        } catch (LockPreemptedException expected) {
            assertFalse(replicaTxn.isValid());
            replicaTxn.abort();
        }

        close();
    }

    /**
     * Cursor variation of testPreempted.
     */
    public void testPreemptedWithCursor()
        throws IOException {

        open();

        /* Read. */
        final Transaction replicaTxn = replicaEnv.beginTransaction(null, null);
        final Cursor replicaCursor = replicaDb.openCursor(replicaTxn, null);
        assertSame(OperationStatus.SUCCESS,
                   replicaCursor.getSearchKey(new DatabaseEntry(KEY1),
                                              new DatabaseEntry(), null));

        /* Steal. */
        assertSame(OperationStatus.SUCCESS,
                   masterDb.put(null, new DatabaseEntry(KEY1),
                                new DatabaseEntry(DATA)));

        /* Read. */
        try {
            replicaCursor.getNext(new DatabaseEntry(),
                                  new DatabaseEntry(), null);
            fail();
        } catch (LockPreemptedException expected) {
            assertFalse(replicaTxn.isValid());
            replicaCursor.close();
            replicaTxn.abort();
        }

        close();
    }

    /**
     * With a ReadCommitted cursor, getCurrent (unlike getNext/getPrev/etc)
     * will cause LockPreempted after a lock is stolen, because cursor
     * stability is violated.
     */
    public void testPreemptedWithReadCommittedCursor()
        throws IOException {

        open();

        /* Read. */
        final Transaction replicaTxn = replicaEnv.beginTransaction(null, null);
        final Cursor replicaCursor =
            replicaDb.openCursor(replicaTxn, CursorConfig.READ_COMMITTED);
        assertSame(OperationStatus.SUCCESS,
                   replicaCursor.getSearchKey(new DatabaseEntry(KEY1),
                                              new DatabaseEntry(), null));

        /* Steal. */
        assertSame(OperationStatus.SUCCESS,
                   masterDb.put(null, new DatabaseEntry(KEY1),
                                new DatabaseEntry(DATA)));

        /* Read. */
        try {
            replicaCursor.getCurrent(new DatabaseEntry(),
                                     new DatabaseEntry(), null);
            fail();
        } catch (LockPreemptedException expected) {
            assertFalse(replicaTxn.isValid());
            replicaCursor.close();
            replicaTxn.abort();
        }

        close();
    }

    /**
     * Non-transactional version of testPreemptedWithReadCommittedCursor.
     */
    public void testPreemptedWithNonTransactionalCursor()
        throws IOException {

        open();

        /* Read. */
        final Cursor replicaCursor = replicaDb.openCursor(null, null);
        assertSame(OperationStatus.SUCCESS,
                   replicaCursor.getSearchKey(new DatabaseEntry(KEY1),
                                              new DatabaseEntry(), null));

        /* Steal. */
        assertSame(OperationStatus.SUCCESS,
                   masterDb.put(null, new DatabaseEntry(KEY1),
                                new DatabaseEntry(DATA)));

        /* Read. */
        try {
            replicaCursor.getCurrent(new DatabaseEntry(),
                                     new DatabaseEntry(), null);
            fail();
        } catch (LockPreemptedException expected) {
            replicaCursor.close();
        }

        close();
    }

    /**
     * When a lock is stoken from one ReadCommitted cursor and then a lock is
     * takn by another (for the same txn), LockPreempted is thrown.
     */
    public void testPreemptedWithTwoReadCommittedCursors()
        throws IOException {

        open();

        /* Read. */
        final Transaction replicaTxn = replicaEnv.beginTransaction(null, null);
        final Cursor replicaCursor1 =
            replicaDb.openCursor(replicaTxn, CursorConfig.READ_COMMITTED);
        assertSame(OperationStatus.SUCCESS,
                   replicaCursor1.getSearchKey(new DatabaseEntry(KEY1),
                                               new DatabaseEntry(), null));

        /* Steal. */
        assertSame(OperationStatus.SUCCESS,
                   masterDb.put(null, new DatabaseEntry(KEY1),
                                new DatabaseEntry(DATA)));

        /* Read. */
        final Cursor replicaCursor2 =
            replicaDb.openCursor(replicaTxn, CursorConfig.READ_COMMITTED);
        try {
            replicaCursor2.getSearchKey(new DatabaseEntry(KEY1),
                                        new DatabaseEntry(), null);
            fail();
        } catch (LockPreemptedException expected) {
            assertFalse(replicaTxn.isValid());
            replicaCursor1.close();
            replicaCursor2.close();
            replicaTxn.abort();
        }

        close();
    }

    /**
     * Non-transactional version of testPreemptedWithTwoReadCommittedCursors.
     */
    public void testPreemptedWithTwoNonTransactionalCursors()
        throws IOException {

        open();

        /* Read. */
        final Cursor replicaCursor1 = replicaDb.openCursor(null, null);
        assertSame(OperationStatus.SUCCESS,
                   replicaCursor1.getSearchKey(new DatabaseEntry(KEY1),
                                               new DatabaseEntry(), null));

        /* Steal. */
        assertSame(OperationStatus.SUCCESS,
                   masterDb.put(null, new DatabaseEntry(KEY1),
                                new DatabaseEntry(DATA)));

        /* Read. */
        final Cursor replicaCursor2 = replicaDb.openCursor(null, null);
        try {
            replicaCursor2.getSearchKey(new DatabaseEntry(KEY1),
                                        new DatabaseEntry(), null);
            fail();
        } catch (LockPreemptedException expected) {
            replicaCursor1.close();
            replicaCursor2.close();
        }

        close();
    }

    /**
     * Similar to testPreemptedWithTwoReadCommittedCursors but LockPreempted is
     * also thrown when one locker is the ReadCommittedLocker and the other is
     * the Txn itself.
     */
    public void testPreemptedWithReadCommittedCursorThenDbRead()
        throws IOException {

        open();

        /* Read. */
        final Transaction replicaTxn = replicaEnv.beginTransaction(null, null);
        final Cursor replicaCursor =
            replicaDb.openCursor(replicaTxn, CursorConfig.READ_COMMITTED);
        assertSame(OperationStatus.SUCCESS,
                   replicaCursor.getSearchKey(new DatabaseEntry(KEY1),
                                              new DatabaseEntry(), null));

        /* Steal. */
        assertSame(OperationStatus.SUCCESS,
                   masterDb.put(null, new DatabaseEntry(KEY1),
                                new DatabaseEntry(DATA)));

        /* Read. */
        try {
            replicaDb.get(replicaTxn, new DatabaseEntry(KEY1),
                          new DatabaseEntry(), null);
            fail();
        } catch (LockPreemptedException expected) {
            assertFalse(replicaTxn.isValid());
            replicaCursor.close();
            replicaTxn.abort();
        }

        close();
    }

    /**
     * Reverse situation from testPreemptedWithReadCommittedCursorThenDbRead.
     */
    public void testPreemptedWithDbReadThenReadCommittedCursor()
        throws IOException {

        open();

        /* Read. */
        final Transaction replicaTxn = replicaEnv.beginTransaction(null, null);
        assertSame(OperationStatus.SUCCESS,
                   replicaDb.get(replicaTxn, new DatabaseEntry(KEY1),
                                  new DatabaseEntry(), null));

        /* Steal. */
        assertSame(OperationStatus.SUCCESS,
                   masterDb.put(null, new DatabaseEntry(KEY1),
                                new DatabaseEntry(DATA)));

        /* Read. */
        final Cursor replicaCursor =
            replicaDb.openCursor(replicaTxn, CursorConfig.READ_COMMITTED);
        try {
            replicaCursor.getSearchKey(new DatabaseEntry(KEY1),
                                       new DatabaseEntry(), null);
            fail();
        } catch (LockPreemptedException expected) {
            assertFalse(replicaTxn.isValid());
            replicaCursor.close();
            replicaTxn.abort();
        }

        close();
    }

    /**
     * Make sure LockPreempted is thrown when (after lock stealing), the cursor
     * first attempts to move and fails (gets a NOTFOUND in this case), and
     * then calls getCurrent.  In other words, make sure the preempted state is
     * not mistakenly reset by the NOTFOUND operation.
     */
    public void testPreemptedAfterAttemptToMoveReadCommittedCursor()
        throws IOException {

        open();

        /* Read. */
        final Transaction replicaTxn = replicaEnv.beginTransaction(null, null);
        final Cursor replicaCursor =
            replicaDb.openCursor(replicaTxn, CursorConfig.READ_COMMITTED);
        assertSame(OperationStatus.SUCCESS,
                   replicaCursor.getSearchKey(new DatabaseEntry(KEY1),
                                              new DatabaseEntry(), null));

        /* Steal. */
        assertSame(OperationStatus.SUCCESS,
                   masterDb.put(null, new DatabaseEntry(KEY1),
                                new DatabaseEntry(DATA)));

        /* Read. */
        assertSame(OperationStatus.NOTFOUND,
                   replicaCursor.getPrev(new DatabaseEntry(),
                                         new DatabaseEntry(), null));
        try {
            replicaCursor.getCurrent(new DatabaseEntry(),
                                     new DatabaseEntry(), null);
            fail();
        } catch (LockPreemptedException expected) {
            assertFalse(replicaTxn.isValid());
            replicaCursor.close();
            replicaTxn.abort();
        }

        close();
    }

    /**
     * Non-transactional variant of
     * testPreemptedAfterAttemptToMoveReadCommittedCursor.
     */
    public void testPreemptedAfterAttemptToMoveNonTransactionalCursor()
        throws IOException {

        open();

        /* Read. */
        final Cursor replicaCursor = replicaDb.openCursor(null, null);
        assertSame(OperationStatus.SUCCESS,
                   replicaCursor.getSearchKey(new DatabaseEntry(KEY1),
                                              new DatabaseEntry(), null));

        /* Steal. */
        assertSame(OperationStatus.SUCCESS,
                   masterDb.put(null, new DatabaseEntry(KEY1),
                                new DatabaseEntry(DATA)));

        /* Read. */
        assertSame(OperationStatus.NOTFOUND,
                   replicaCursor.getPrev(new DatabaseEntry(),
                                         new DatabaseEntry(), null));
        try {
            replicaCursor.getCurrent(new DatabaseEntry(),
                                     new DatabaseEntry(), null);
            fail();
        } catch (LockPreemptedException expected) {
            replicaCursor.close();
        }

        close();
    }

    /**
     * If a ReadCommitted cursor is moved after lock stealing, LockPreempted is
     * NOT thrown.
     */
    public void testNotPreemptedMoveReadCommittedCursor()
        throws IOException {

        open();

        /* Read. */
        final Transaction replicaTxn = replicaEnv.beginTransaction(null, null);
        final Cursor replicaCursor =
            replicaDb.openCursor(replicaTxn, CursorConfig.READ_COMMITTED);
        assertSame(OperationStatus.SUCCESS,
                   replicaCursor.getSearchKey(new DatabaseEntry(KEY1),
                                              new DatabaseEntry(), null));

        /* Steal. */
        assertSame(OperationStatus.SUCCESS,
                   masterDb.put(null, new DatabaseEntry(KEY1),
                                new DatabaseEntry(DATA)));

        /* Read. */
        assertSame(OperationStatus.SUCCESS,
                   replicaCursor.getNext(new DatabaseEntry(),
                                         new DatabaseEntry(), null));
        assertSame(OperationStatus.NOTFOUND,
                   replicaCursor.getNext(new DatabaseEntry(),
                                         new DatabaseEntry(), null));
        assertSame(OperationStatus.SUCCESS,
                   replicaCursor.getPrev(new DatabaseEntry(),
                                         new DatabaseEntry(), null));
        assertSame(OperationStatus.NOTFOUND,
                   replicaCursor.getPrev(new DatabaseEntry(),
                                         new DatabaseEntry(), null));

        replicaCursor.close();
        replicaTxn.commit();
        close();
    }

    /**
     * Non-transactional variant of testNotPreemptedMoveReadCommittedCursor.
     */
    public void testNotPreemptedMoveNonTransactionalCursor()
        throws IOException {

        open();

        /* Read. */
        final Cursor replicaCursor = replicaDb.openCursor(null, null);
        assertSame(OperationStatus.SUCCESS,
                   replicaCursor.getSearchKey(new DatabaseEntry(KEY1),
                                              new DatabaseEntry(), null));

        /* Steal. */
        assertSame(OperationStatus.SUCCESS,
                   masterDb.put(null, new DatabaseEntry(KEY1),
                                new DatabaseEntry(DATA)));

        /* Read. */
        assertSame(OperationStatus.SUCCESS,
                   replicaCursor.getNext(new DatabaseEntry(),
                                         new DatabaseEntry(), null));
        assertSame(OperationStatus.NOTFOUND,
                   replicaCursor.getNext(new DatabaseEntry(),
                                         new DatabaseEntry(), null));
        assertSame(OperationStatus.SUCCESS,
                   replicaCursor.getPrev(new DatabaseEntry(),
                                         new DatabaseEntry(), null));
        assertSame(OperationStatus.NOTFOUND,
                   replicaCursor.getPrev(new DatabaseEntry(),
                                         new DatabaseEntry(), null));

        replicaCursor.close();
        close();
    }

    /**
     * Make sure LockPreempted is NOT thrown when (after lock stealing), the
     * cursor first attempts to move and fails (gets a NOTFOUND in this case),
     * and then moves the cursor.  In other words, make sure the preempted
     * state is not mistakenly reset by the NOTFOUND operation.
     */
    public void testNotPreemptedAfterAttemptToMoveReadCommittedCursor()
                
        throws IOException {

        open();

        /* Read. */
        final Transaction replicaTxn = replicaEnv.beginTransaction(null, null);
        final Cursor replicaCursor =
            replicaDb.openCursor(replicaTxn, CursorConfig.READ_COMMITTED);
        assertSame(OperationStatus.SUCCESS,
                   replicaCursor.getSearchKey(new DatabaseEntry(KEY1),
                                              new DatabaseEntry(), null));

        /* Steal. */
        assertSame(OperationStatus.SUCCESS,
                   masterDb.put(null, new DatabaseEntry(KEY1),
                                new DatabaseEntry(DATA)));

        /* Read. */
        assertSame(OperationStatus.NOTFOUND,
                   replicaCursor.getPrev(new DatabaseEntry(),
                                         new DatabaseEntry(), null));
        assertSame(OperationStatus.SUCCESS,
                   replicaCursor.getNext(new DatabaseEntry(),
                                         new DatabaseEntry(), null));
        assertSame(OperationStatus.NOTFOUND,
                   replicaCursor.getNext(new DatabaseEntry(),
                                         new DatabaseEntry(), null));
        assertSame(OperationStatus.SUCCESS,
                   replicaCursor.getPrev(new DatabaseEntry(),
                                         new DatabaseEntry(), null));

        replicaCursor.close();
        replicaTxn.commit();
        close();
    }

    /**
     * Non-transactional variant of
     * testNotPreemptedAfterAttemptToMoveReadCommittedCursor.
     */
    public void testNotPreemptedAfterAttemptToMoveNonTransactionalCursor()
        throws IOException {

        open();

        /* Read. */
        final Cursor replicaCursor = replicaDb.openCursor(null, null);
        assertSame(OperationStatus.SUCCESS,
                   replicaCursor.getSearchKey(new DatabaseEntry(KEY1),
                                              new DatabaseEntry(), null));

        /* Steal. */
        assertSame(OperationStatus.SUCCESS,
                   masterDb.put(null, new DatabaseEntry(KEY1),
                                new DatabaseEntry(DATA)));

        /* Read. */
        assertSame(OperationStatus.NOTFOUND,
                   replicaCursor.getPrev(new DatabaseEntry(),
                                         new DatabaseEntry(), null));
        assertSame(OperationStatus.SUCCESS,
                   replicaCursor.getNext(new DatabaseEntry(),
                                         new DatabaseEntry(), null));
        assertSame(OperationStatus.NOTFOUND,
                   replicaCursor.getNext(new DatabaseEntry(),
                                         new DatabaseEntry(), null));
        assertSame(OperationStatus.SUCCESS,
                   replicaCursor.getPrev(new DatabaseEntry(),
                                         new DatabaseEntry(), null));

        replicaCursor.close();
        close();
    }

    /**
     * Ensure that LockPreempted is not thrown by commit when no additional
     * lock is taken after a lock is stolen.
     */
    public void testNotPreemptedCommit()
        throws IOException {

        open();

        /* Read. */
        final Transaction replicaTxn = replicaEnv.beginTransaction(null, null);
        assertSame(OperationStatus.SUCCESS,
                   replicaDb.get(replicaTxn, new DatabaseEntry(KEY1),
                                 new DatabaseEntry(), null));

        /* Steal. */
        assertSame(OperationStatus.SUCCESS,
                   masterDb.put(null, new DatabaseEntry(KEY1),
                                new DatabaseEntry(DATA)));

        /* Commit. */
        replicaTxn.commit();

        close();
    }

    /**
     * Cursor variant of testNotPreemptedCommit.
     */
    public void testNotPreemptedCommitWithCursor()
        throws IOException {

        open();

        /* Read. */
        final Transaction replicaTxn = replicaEnv.beginTransaction(null, null);
        final Cursor replicaCursor = replicaDb.openCursor(replicaTxn, null);
        assertSame(OperationStatus.SUCCESS,
                   replicaCursor.getSearchKey(new DatabaseEntry(KEY1),
                                              new DatabaseEntry(), null));

        /* Steal. */
        assertSame(OperationStatus.SUCCESS,
                   masterDb.put(null, new DatabaseEntry(KEY1),
                                new DatabaseEntry(DATA)));

        /* Commit. */
        replicaCursor.close();
        replicaTxn.commit();

        close();
    }

    /**
     * Ensure that LockPreempted is not thrown by commit when no additional
     * lock is taken after a lock is stolen.
     */
    public void testNotPreemptedAbort()
        throws IOException {

        open();

        /* Read. */
        final Transaction replicaTxn = replicaEnv.beginTransaction(null, null);
        assertSame(OperationStatus.SUCCESS,
                   replicaDb.get(replicaTxn, new DatabaseEntry(KEY1),
                                 new DatabaseEntry(), null));

        /* Steal. */
        assertSame(OperationStatus.SUCCESS,
                   masterDb.put(null, new DatabaseEntry(KEY1),
                                new DatabaseEntry(DATA)));

        /* Abort. */
        replicaTxn.abort();

        close();
    }

    /**
     * Cursor variant of testNotPreemptedAbort.
     */
    public void testNotPreemptedAbortWithCursor()
        throws IOException {

        open();

        /* Read. */
        final Transaction replicaTxn = replicaEnv.beginTransaction(null, null);
        final Cursor replicaCursor = replicaDb.openCursor(replicaTxn, null);
        assertSame(OperationStatus.SUCCESS,
                   replicaCursor.getSearchKey(new DatabaseEntry(KEY1),
                                              new DatabaseEntry(), null));

        /* Steal. */
        assertSame(OperationStatus.SUCCESS,
                   masterDb.put(null, new DatabaseEntry(KEY1),
                                new DatabaseEntry(DATA)));

        /* Abort. */
        replicaCursor.close();
        replicaTxn.abort();

        close();
    }
}
