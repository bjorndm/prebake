/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: CleanerTest.java,v 1.119 2010/01/29 17:34:03 mark Exp $
 */

package com.sleepycat.je.cleaner;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.bind.tuple.LongBinding;
import com.sleepycat.je.CacheMode;
import com.sleepycat.je.CheckpointConfig;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.EnvironmentMutableConfig;
import com.sleepycat.je.EnvironmentStats;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.CursorImpl;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.dbi.MemoryBudget;
import com.sleepycat.je.junit.JUnitThread;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.recovery.Checkpointer;
import com.sleepycat.je.tree.BIN;
import com.sleepycat.je.tree.FileSummaryLN;
import com.sleepycat.je.tree.IN;
import com.sleepycat.je.tree.Node;
import com.sleepycat.je.txn.BasicLocker;
import com.sleepycat.je.txn.LockType;
import com.sleepycat.je.util.StringDbt;
import com.sleepycat.je.util.TestUtils;
import com.sleepycat.je.utilint.TestHook;

public class CleanerTest extends TestCase {

    private static final int N_KEYS = 300;
    private static final int N_KEY_BYTES = 10;

    /*
     * Make the log file size small enough to allow cleaning, but large enough
     * not to generate a lot of fsyncing at the log file boundaries.
     */
    private static final int FILE_SIZE = 10000;
    protected File envHome = null;
    protected Database db = null;
    private Environment exampleEnv;
    private Database exampleDb;
    private final CheckpointConfig forceConfig;
    private JUnitThread junitThread;
    private volatile int synchronizer;

    public CleanerTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
        forceConfig = new CheckpointConfig();
        forceConfig.setForce(true);
    }

    @Override
    public void setUp() {
        TestUtils.removeLogFiles("Setup", envHome, false);
        TestUtils.removeFiles("Setup", envHome, FileManager.DEL_SUFFIX);
    }

    private void initEnv(boolean createDb, boolean allowDups)
        throws DatabaseException {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        DbInternal.disableParameterValidation(envConfig);
        envConfig.setTransactional(true);
        envConfig.setAllowCreate(true);
        envConfig.setTxnNoSync(Boolean.getBoolean(TestUtils.NO_SYNC));
        envConfig.setConfigParam(EnvironmentParams.LOG_FILE_MAX.getName(),
                                 Integer.toString(FILE_SIZE));
        envConfig.setConfigParam(EnvironmentParams.ENV_RUN_CLEANER.getName(),
                                 "false");
        envConfig.setConfigParam(EnvironmentParams.CLEANER_REMOVE.getName(),
                                 "false");
        envConfig.setConfigParam
            (EnvironmentParams.CLEANER_MIN_UTILIZATION.getName(), "80");
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_CHECKPOINTER.getName(), "false");
        envConfig.setConfigParam(EnvironmentParams.NODE_MAX.getName(), "6");
        envConfig.setConfigParam(EnvironmentParams.BIN_DELTA_PERCENT.getName(),
                                 "75");

        exampleEnv = new Environment(envHome, envConfig);

        String databaseName = "cleanerDb";
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(createDb);
        dbConfig.setSortedDuplicates(allowDups);
        exampleDb = exampleEnv.openDatabase(null, databaseName, dbConfig);
    }

    @Override
    public void tearDown() {
        if (junitThread != null) {
            while (junitThread.isAlive()) {
                junitThread.interrupt();
                Thread.yield();
            }
            junitThread = null;
        }

        if (exampleEnv != null) {
            try {
                exampleEnv.close();
            } catch (Throwable e) {
                System.out.println("tearDown: " + e);
            }
        }
        exampleDb = null;
        exampleEnv = null;

        //*
        try {
            TestUtils.removeLogFiles("TearDown", envHome, true);
            TestUtils.removeFiles("TearDown", envHome, FileManager.DEL_SUFFIX);
        } catch (Throwable e) {
            System.out.println("tearDown: " + e);
        }
        //*/
    }

    private void closeEnv()
        throws DatabaseException {

        if (exampleDb != null) {
            exampleDb.close();
            exampleDb = null;
        }

        if (exampleEnv != null) {
            exampleEnv.close();
            exampleEnv = null;
        }
    }

    public void testCleanerNoDupes()
        throws Throwable {

        initEnv(true, false);
        try {
            doCleanerTest(N_KEYS, 1);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public void testCleanerWithDupes()
        throws Throwable {

        initEnv(true, true);
        try {
            doCleanerTest(2, 500);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    private void doCleanerTest(int nKeys, int nDupsPerKey)
        throws DatabaseException {

        EnvironmentImpl environment =
            DbInternal.getEnvironmentImpl(exampleEnv);
        FileManager fileManager = environment.getFileManager();
        Map<String, Set<String>> expectedMap =
            new HashMap<String, Set<String>>();
        doLargePut(expectedMap, nKeys, nDupsPerKey, true);
        Long lastNum = fileManager.getLastFileNum();

        /* Read the data back. */
        StringDbt foundKey = new StringDbt();
        StringDbt foundData = new StringDbt();

        Cursor cursor = exampleDb.openCursor(null, null);

        while (cursor.getNext(foundKey, foundData, LockMode.DEFAULT) ==
               OperationStatus.SUCCESS) {
        }

        exampleEnv.checkpoint(forceConfig);

        for (int i = 0; i < (int) lastNum.longValue(); i++) {

            /*
             * Force clean one file.  Utilization-based cleaning won't
             * work here, since utilization is over 90%.
             */
            DbInternal.getEnvironmentImpl(exampleEnv).
                getCleaner().
                doClean(false, // cleanMultipleFiles
                        true); // forceCleaning
        }

        EnvironmentStats stats = exampleEnv.getStats(TestUtils.FAST_STATS);
        assertTrue(stats.getNINsCleaned() > 0);

        cursor.close();
        closeEnv();

        initEnv(false, (nDupsPerKey > 1));

        checkData(expectedMap);
        assertTrue(fileManager.getLastFileNum().longValue() >
                   lastNum.longValue());

        closeEnv();
    }

    /**
     * Ensure that INs are cleaned.
     */
    public void testCleanInternalNodes()
        throws DatabaseException {

        initEnv(true, true);
        int nKeys = 200;

        EnvironmentImpl environment =
            DbInternal.getEnvironmentImpl(exampleEnv);
        FileManager fileManager = environment.getFileManager();
        /* Insert a lot of keys. ExpectedMap holds the expected data */
        Map<String, Set<String>> expectedMap =
            new HashMap<String, Set<String>>();
        doLargePut(expectedMap, nKeys, 1, true);

        /* Modify every other piece of data. */
        modifyData(expectedMap, 10, true);
        checkData(expectedMap);

        /* Checkpoint */
        exampleEnv.checkpoint(forceConfig);
        checkData(expectedMap);

        /* Modify every other piece of data. */
        modifyData(expectedMap, 10, true);
        checkData(expectedMap);

        /* Checkpoint -- this should obsolete INs. */
        exampleEnv.checkpoint(forceConfig);
        checkData(expectedMap);

        /* Clean */
        Long lastNum = fileManager.getLastFileNum();
        exampleEnv.cleanLog();

        /* Validate after cleaning. */
        checkData(expectedMap);
        EnvironmentStats stats = exampleEnv.getStats(TestUtils.FAST_STATS);

        /* Make sure we really cleaned something.*/
        assertTrue(stats.getNINsCleaned() > 0);
        assertTrue(stats.getNLNsCleaned() > 0);

        closeEnv();
        initEnv(false, true);
        checkData(expectedMap);
        assertTrue(fileManager.getLastFileNum().longValue() >
                   lastNum.longValue());

        closeEnv();
    }

    /**
     * See if we can clean in the middle of the file set.
     */
    public void testCleanFileHole()
        throws Throwable {

        initEnv(true, true);

        int nKeys = 20; // test ends up inserting 2*nKeys
        int nDupsPerKey = 30;

        EnvironmentImpl environment =
            DbInternal.getEnvironmentImpl(exampleEnv);
        FileManager fileManager = environment.getFileManager();

        /* Insert some non dup data, modify, insert dup data. */
        Map<String, Set<String>> expectedMap =
            new HashMap<String, Set<String>>();
        doLargePut(expectedMap, nKeys, 1, true);
        modifyData(expectedMap, 10, true);
        doLargePut(expectedMap, nKeys, nDupsPerKey, true);
        checkData(expectedMap);

        /*
         * Delete all the data, but abort. (Try to fill up the log
         * with entries we don't need.
         */
        deleteData(expectedMap, false, false);
        checkData(expectedMap);

        /* Do some more insertions, but abort them. */
        doLargePut(expectedMap, nKeys, nDupsPerKey, false);
        checkData(expectedMap);

        /* Do some more insertions and commit them. */
        doLargePut(expectedMap, nKeys, nDupsPerKey, true);
        checkData(expectedMap);

        /* Checkpoint */
        exampleEnv.checkpoint(forceConfig);
        checkData(expectedMap);

        /* Clean */
        Long lastNum = fileManager.getLastFileNum();
        exampleEnv.cleanLog();

        /* Validate after cleaning. */
        checkData(expectedMap);
        EnvironmentStats stats = exampleEnv.getStats(TestUtils.FAST_STATS);

        /* Make sure we really cleaned something.*/
        assertTrue(stats.getNINsCleaned() > 0);
        assertTrue(stats.getNLNsCleaned() > 0);

        closeEnv();
        initEnv(false, true);
        checkData(expectedMap);
        assertTrue(fileManager.getLastFileNum().longValue() >
                   lastNum.longValue());

        closeEnv();
    }

    /**
     * Test for SR13191.  This SR shows a problem where a MapLN is initialized
     * with a DatabaseImpl that has a null EnvironmentImpl.  When the Database
     * gets used, a NullPointerException occurs in the Cursor code which
     * expects there to be an EnvironmentImpl present.  The MapLN gets init'd
     * by the Cleaner reading through a log file and encountering a MapLN which
     * is not presently in the DbTree.  As an efficiency, the Cleaner calls
     * updateEntry on the BIN to try to insert the MapLN into the BIN so that
     * it won't have to fetch it when it migrates the BIN.  But this is bad
     * since the MapLN has not been init'd properly.  The fix was to ensure
     * that the MapLN is init'd correctly by calling postFetchInit on it just
     * prior to inserting it into the BIN.
     *
     * This test first creates an environment and two databases.  The first
     * database it just adds to the tree with no data.  This will be the MapLN
     * that eventually gets instantiated by the cleaner.  The second database
     * is used just to create a bunch of data that will get deleted so as to
     * create a low utilization for one of the log files.  Once the data for
     * db2 is created, the log is flipped (so file 0 is the one with the MapLN
     * for db1 in it), and the environment is closed and reopened.  We insert
     * more data into db2 until we have enough .jdb files that file 0 is
     * attractive to the cleaner.  Call the cleaner to have it instantiate the
     * MapLN and then use the MapLN in a Database.get() call.
     */
    public void testSR13191()
        throws Throwable {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setAllowCreate(true);
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_CLEANER.getName(), "false");
        Environment env = new Environment(envHome, envConfig);
        EnvironmentImpl envImpl = DbInternal.getEnvironmentImpl(env);
        FileManager fileManager =
            DbInternal.getEnvironmentImpl(env).getFileManager();

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        Database db1 =
            env.openDatabase(null, "db1", dbConfig);

        Database db2 =
            env.openDatabase(null, "db2", dbConfig);

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        IntegerBinding.intToEntry(1, key);
        data.setData(new byte[100000]);
        for (int i = 0; i < 50; i++) {
            assertEquals(OperationStatus.SUCCESS, db2.put(null, key, data));
        }
        db1.close();
        db2.close();
        assertEquals("Should have 0 as current file", 0L,
                     fileManager.getCurrentFileNum());
        envImpl.forceLogFileFlip();
        env.close();

        env = new Environment(envHome, envConfig);
        fileManager = DbInternal.getEnvironmentImpl(env).getFileManager();
        assertEquals("Should have 1 as current file", 1L,
                     fileManager.getCurrentFileNum());

        db2 = env.openDatabase(null, "db2", dbConfig);

        for (int i = 0; i < 250; i++) {
            assertEquals(OperationStatus.SUCCESS, db2.put(null, key, data));
        }

        db2.close();
        env.cleanLog();
        db1 = env.openDatabase(null, "db1", dbConfig);
        db1.get(null, key, data, null);
        db1.close();
        env.close();
    }

    /**
     * Tests that setting je.env.runCleaner=false stops the cleaner from
     * processing more files even if the target minUtilization is not met
     * [#15158].
     */
    public void testCleanerStop()
        throws Throwable {

        final int fileSize = 1000000;
        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setAllowCreate(true);
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_CLEANER.getName(), "false");
        envConfig.setConfigParam
            (EnvironmentParams.LOG_FILE_MAX.getName(),
             Integer.toString(fileSize));
        envConfig.setConfigParam
            (EnvironmentParams.CLEANER_MIN_UTILIZATION.getName(), "80");
        Environment env = new Environment(envHome, envConfig);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        Database db = env.openDatabase(null, "CleanerStop", dbConfig);

        DatabaseEntry key = new DatabaseEntry(new byte[1]);
        DatabaseEntry data = new DatabaseEntry(new byte[fileSize]);
        for (int i = 0; i <= 10; i += 1) {
            db.put(null, key, data);
        }
        env.checkpoint(forceConfig);

        EnvironmentStats stats = env.getStats(null);
        assertEquals(0, stats.getNCleanerRuns());

        envConfig = env.getConfig();
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_CLEANER.getName(), "true");
        env.setMutableConfig(envConfig);

        int iter = 0;
        while (stats.getNCleanerRuns() == 0) {
            iter += 1;
            if (iter == 20) {

                /*
                 * At one time the DaemonThread did not wakeup immediately in
                 * this test.  A workaround was to add an item to the job queue
                 * in FileProcessor.wakeup.  Later the job queue was removed
                 * and the DaemonThread.run() was fixed to wakeup immediately.
                 * This test verifies that the cleanup of the run() method
                 * works properly [#15267].
                 */
                fail("Cleaner did not run after " + iter + " tries");
            }
            Thread.yield();
            Thread.sleep(1);
            stats = env.getStats(null);
        }

        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_CLEANER.getName(), "false");
        env.setMutableConfig(envConfig);

        long prevNFiles = stats.getNCleanerRuns();
        stats = env.getStats(null);
        long currNFiles = stats.getNCleanerRuns();
        if (currNFiles - prevNFiles > 5) {
            fail("Expected less than 5 files cleaned," +
                 " prevNFiles=" + prevNFiles +
                 " currNFiles=" + currNFiles);
        }

        //System.out.println("Num runs: " + stats.getNCleanerRuns());

        db.close();
        env.close();
    }

    /**
     * Tests that the FileSelector memory budget is subtracted when the
     * environment is closed.  Before the fix in SR [#16368], it was not.
     */
    public void testFileSelectorMemBudget()
        throws Throwable {

        final int fileSize = 1000000;
        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setAllowCreate(true);
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_CLEANER.getName(), "false");
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_CHECKPOINTER.getName(), "false");
        envConfig.setConfigParam
            (EnvironmentParams.LOG_FILE_MAX.getName(),
             Integer.toString(fileSize));
        envConfig.setConfigParam
            (EnvironmentParams.CLEANER_MIN_UTILIZATION.getName(), "80");
        Environment env = new Environment(envHome, envConfig);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        Database db = env.openDatabase(null, "foo", dbConfig);

        DatabaseEntry key = new DatabaseEntry(new byte[1]);
        DatabaseEntry data = new DatabaseEntry(new byte[fileSize]);
        for (int i = 0; i <= 10; i += 1) {
            db.put(null, key, data);
        }
        env.checkpoint(forceConfig);

        int nFiles = env.cleanLog();
        assertTrue(nFiles > 0);

        db.close();

        /*
         * To force the memory leak to be detected we have to close without a
         * checkpoint.  The checkpoint will finish processing all cleaned files
         * and subtract them from the budget.  But this should happen during
         * close, even without a checkpoint.
         */
        EnvironmentImpl envImpl = DbInternal.getEnvironmentImpl(env);
        envImpl.close(false /*doCheckpoint*/);
    }

    /**
     * Tests that the cleanLog cannot be called in a read-only environment.
     * [#16368]
     */
    public void testCleanLogReadOnly()
        throws Throwable {

        /* Open read-write. */
        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setAllowCreate(true);
        exampleEnv = new Environment(envHome, envConfig);
        exampleEnv.close();
        exampleEnv = null;

        /* Open read-only. */
        envConfig.setAllowCreate(false);
        envConfig.setReadOnly(true);
        exampleEnv = new Environment(envHome, envConfig);

        /* Try cleanLog in a read-only env. */
        try {
            exampleEnv.cleanLog();
            fail();
        } catch (UnsupportedOperationException e) {
            assertEquals
                ("Log cleaning not allowed in a read-only or memory-only " +
                 "environment", e.getMessage());

        }
    }

    /**
     * Tests that when a file being cleaned is deleted, we ignore the error and
     * don't repeatedly try to clean it.  This is happening when we mistakedly
     * clean a file after it has been queued for deletion.  The workaround is
     * to catch LogFileNotFoundException in the cleaner and ignore the error.
     * We're testing the workaround here by forcing cleaning of deleted files.
     * [#15528]
     */
    public void testUnexpectedFileDeletion()
        throws DatabaseException {

        initEnv(true, false);
        EnvironmentMutableConfig config = exampleEnv.getMutableConfig();
        config.setConfigParam
            (EnvironmentParams.ENV_RUN_CLEANER.getName(), "false");
        config.setConfigParam
            (EnvironmentParams.CLEANER_MIN_UTILIZATION.getName(), "80");
        exampleEnv.setMutableConfig(config);

        final EnvironmentImpl envImpl =
            DbInternal.getEnvironmentImpl(exampleEnv);
        final Cleaner cleaner = envImpl.getCleaner();
        final FileSelector fileSelector = cleaner.getFileSelector();

        Map<String, Set<String>> expectedMap =
            new HashMap<String, Set<String>>();
        doLargePut(expectedMap, 1000, 1, true);
        checkData(expectedMap);

        final long file1 = 0;
        final long file2 = 1;

        for (int i = 0; i < 100; i += 1) {
            modifyData(expectedMap, 1, true);
            checkData(expectedMap);
            fileSelector.injectFileForCleaning(new Long(file1));
            fileSelector.injectFileForCleaning(new Long(file2));
            assertTrue(fileSelector.getToBeCleanedFiles().contains(file1));
            assertTrue(fileSelector.getToBeCleanedFiles().contains(file2));
            while (exampleEnv.cleanLog() > 0) {}
            assertTrue(!fileSelector.getToBeCleanedFiles().contains(file1));
            assertTrue(!fileSelector.getToBeCleanedFiles().contains(file2));
            exampleEnv.checkpoint(forceConfig);
            Map<Long,FileSummary> allFiles = envImpl.getUtilizationProfile().
                getFileSummaryMap(true /*includeTrackedFiles*/);
            assertTrue(!allFiles.containsKey(file1));
            assertTrue(!allFiles.containsKey(file2));
        }
        checkData(expectedMap);

        closeEnv();
    }

    /**
     * Helper routine. Generates keys with random alpha values while data
     * is numbered numerically.
     */
    private void doLargePut(Map<String, Set<String>> expectedMap,
                            int nKeys,
                            int nDupsPerKey,
                            boolean commit)
        throws DatabaseException {

        Transaction txn = exampleEnv.beginTransaction(null, null);
        for (int i = 0; i < nKeys; i++) {
            byte[] key = new byte[N_KEY_BYTES];
            TestUtils.generateRandomAlphaBytes(key);
            String keyString = new String(key);

            /*
             * The data map is keyed by key value, and holds a hash
             * map of all data values.
             */
            Set<String> dataVals = new HashSet<String>();
            if (commit) {
                expectedMap.put(keyString, dataVals);
            }
            for (int j = 0; j < nDupsPerKey; j++) {
                String dataString = Integer.toString(j);
                exampleDb.put(txn,
                              new StringDbt(keyString),
                              new StringDbt(dataString));
                dataVals.add(dataString);
            }
        }
        if (commit) {
            txn.commit();
        } else {
            txn.abort();
        }
    }

    /**
     * Increment each data value.
     */
    private void modifyData(Map<String, Set<String>> expectedMap,
                            int increment,
                            boolean commit)
        throws DatabaseException {

        Transaction txn = exampleEnv.beginTransaction(null, null);

        StringDbt foundKey = new StringDbt();
        StringDbt foundData = new StringDbt();

        Cursor cursor = exampleDb.openCursor(txn, null);
        OperationStatus status = cursor.getFirst(foundKey, foundData,
                                                 LockMode.DEFAULT);

        boolean toggle = true;
        while (status == OperationStatus.SUCCESS) {
            if (toggle) {

                String foundKeyString = foundKey.getString();
                String foundDataString = foundData.getString();
                int newValue = Integer.parseInt(foundDataString) + increment;
                String newDataString = Integer.toString(newValue);

                /* If committing, adjust the expected map. */
                if (commit) {

                    Set<String> dataVals = expectedMap.get(foundKeyString);
                    if (dataVals == null) {
                        fail("Couldn't find " +
                             foundKeyString + "/" + foundDataString);
                    } else if (dataVals.contains(foundDataString)) {
                        dataVals.remove(foundDataString);
                        dataVals.add(newDataString);
                    } else {
                        fail("Couldn't find " +
                             foundKeyString + "/" + foundDataString);
                    }
                }

                assertEquals(OperationStatus.SUCCESS,
                             cursor.delete());
                assertEquals(OperationStatus.SUCCESS,
                             cursor.put(foundKey,
                                        new StringDbt(newDataString)));
                toggle = false;
            } else {
                toggle = true;
            }

            status = cursor.getNext(foundKey, foundData, LockMode.DEFAULT);
        }

        cursor.close();
        if (commit) {
            txn.commit();
        } else {
            txn.abort();
        }
    }

    /**
     * Delete data.
     */
    private void deleteData(Map<String, Set<String>> expectedMap,
                            boolean everyOther,
                            boolean commit)
        throws DatabaseException {

        Transaction txn = exampleEnv.beginTransaction(null, null);

        StringDbt foundKey = new StringDbt();
        StringDbt foundData = new StringDbt();

        Cursor cursor = exampleDb.openCursor(txn, null);
        OperationStatus status = cursor.getFirst(foundKey, foundData,
                                                 LockMode.DEFAULT);

        boolean toggle = true;
        while (status == OperationStatus.SUCCESS) {
            if (toggle) {

                String foundKeyString = foundKey.getString();
                String foundDataString = foundData.getString();

                /* If committing, adjust the expected map */
                if (commit) {

                    Set dataVals = expectedMap.get(foundKeyString);
                    if (dataVals == null) {
                        fail("Couldn't find " +
                             foundKeyString + "/" + foundDataString);
                    } else if (dataVals.contains(foundDataString)) {
                        dataVals.remove(foundDataString);
                        if (dataVals.size() == 0) {
                            expectedMap.remove(foundKeyString);
                        }
                    } else {
                        fail("Couldn't find " +
                             foundKeyString + "/" + foundDataString);
                    }
                }

                assertEquals(OperationStatus.SUCCESS, cursor.delete());
            }

            if (everyOther) {
                toggle = toggle? false: true;
            }

            status = cursor.getNext(foundKey, foundData, LockMode.DEFAULT);
        }

        cursor.close();
        if (commit) {
            txn.commit();
        } else {
            txn.abort();
        }
    }

    /**
     * Check what's in the database against what's in the expected map.
     */
    private void checkData(Map<String, Set<String>> expectedMap)
        throws DatabaseException {

        StringDbt foundKey = new StringDbt();
        StringDbt foundData = new StringDbt();
        Cursor cursor = exampleDb.openCursor(null, null);
        OperationStatus status = cursor.getFirst(foundKey, foundData,
                                                 LockMode.DEFAULT);

        /*
         * Make a copy of expectedMap so that we're free to delete out
         * of the set of expected results when we verify.
         * Also make a set of counts for each key value, to test count.
         */

        Map<String, Set<String>> checkMap = new HashMap<String, Set<String>>();
        Map<String, Integer>countMap = new HashMap<String, Integer>();
        Iterator<Map.Entry<String, Set<String>>> iter =
                        expectedMap.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, Set<String>> entry = iter.next();
            Set<String> copySet = new HashSet<String>();
            copySet.addAll(entry.getValue());
            checkMap.put(entry.getKey(), copySet);
            countMap.put(entry.getKey(), new Integer(copySet.size()));
        }

        while (status == OperationStatus.SUCCESS) {
            String foundKeyString = foundKey.getString();
            String foundDataString = foundData.getString();

            /* Check that the current value is in the check values map */
            Set dataVals = checkMap.get(foundKeyString);
            if (dataVals == null) {
                fail("Couldn't find " +
                     foundKeyString + "/" + foundDataString);
            } else if (dataVals.contains(foundDataString)) {
                dataVals.remove(foundDataString);
                if (dataVals.size() == 0) {
                    checkMap.remove(foundKeyString);
                }
            } else {
                fail("Couldn't find " +
                     foundKeyString + "/" +
                     foundDataString +
                     " in data vals");
            }

            /* Check that the count is right. */
            int count = cursor.count();
            assertEquals(countMap.get(foundKeyString).intValue(),
                         count);

            status = cursor.getNext(foundKey, foundData, LockMode.DEFAULT);
        }

        cursor.close();

        if (checkMap.size() != 0) {
            dumpExpected(checkMap);
            fail("checkMapSize = " + checkMap.size());

        }
        assertEquals(0, checkMap.size());
    }

    private void dumpExpected(Map expectedMap) {
        Iterator iter = expectedMap.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry entry = (Map.Entry) iter.next();
            String key = (String) entry.getKey();
            Iterator dataIter = ((Set) entry.getValue()).iterator();
            while (dataIter.hasNext()) {
                System.out.println("key=" + key +
                                   " data=" + (String) dataIter.next());
            }
        }
    }

    /**
     * Tests that cleaner mutable configuration parameters can be changed and
     * that the changes actually take effect.
     */
    public void testMutableConfig()
        throws DatabaseException {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setAllowCreate(true);
        exampleEnv = new Environment(envHome, envConfig);
        envConfig = exampleEnv.getConfig();
        EnvironmentImpl envImpl =
            DbInternal.getEnvironmentImpl(exampleEnv);
        Cleaner cleaner = envImpl.getCleaner();
        UtilizationProfile profile = envImpl.getUtilizationProfile();
        MemoryBudget budget = envImpl.getMemoryBudget();
        String name;
        String val;

        /* je.cleaner.minUtilization */
        name = EnvironmentParams.CLEANER_MIN_UTILIZATION.getName();
        setParam(name, "33");
        assertEquals(33, profile.minUtilization);

        /* je.cleaner.minFileUtilization */
        name = EnvironmentParams.CLEANER_MIN_FILE_UTILIZATION.getName();
        setParam(name, "7");
        assertEquals(7, profile.minFileUtilization);

        /* je.cleaner.bytesInterval */
        name = EnvironmentParams.CLEANER_BYTES_INTERVAL.getName();
        setParam(name, "1000");
        assertEquals(1000, cleaner.cleanerBytesInterval);

        /* je.cleaner.deadlockRetry */
        name = EnvironmentParams.CLEANER_DEADLOCK_RETRY.getName();
        setParam(name, "7");
        assertEquals(7, cleaner.nDeadlockRetries);

        /* je.cleaner.lockTimeout */
        name = EnvironmentParams.CLEANER_LOCK_TIMEOUT.getName();
        setParam(name, "7000");
        assertEquals(7, cleaner.lockTimeout);

        /* je.cleaner.expunge */
        name = EnvironmentParams.CLEANER_REMOVE.getName();
        val = "false".equals(envConfig.getConfigParam(name)) ?
            "true" : "false";
        setParam(name, val);
        assertEquals(val.equals("true"), cleaner.expunge);

        /* je.cleaner.minAge */
        name = EnvironmentParams.CLEANER_MIN_AGE.getName();
        setParam(name, "7");
        assertEquals(7, profile.minAge);

        /* je.cleaner.cluster */
        name = EnvironmentParams.CLEANER_CLUSTER.getName();
        val = "false".equals(envConfig.getConfigParam(name)) ?
            "true" : "false";
        setParam(name, val);
        assertEquals(val.equals("true"), cleaner.clusterResident);
        /* Cannot set both cluster and clusterAll to true. */
        setParam(name, "false");

        /* je.cleaner.clusterAll */
        name = EnvironmentParams.CLEANER_CLUSTER_ALL.getName();
        val = "false".equals(envConfig.getConfigParam(name)) ?
            "true" : "false";
        setParam(name, val);
        assertEquals(val.equals("true"), cleaner.clusterAll);

        /* je.cleaner.maxBatchFiles */
        name = EnvironmentParams.CLEANER_MAX_BATCH_FILES.getName();
        setParam(name, "7");
        assertEquals(7, cleaner.maxBatchFiles);

        /* je.cleaner.readSize */
        name = EnvironmentParams.CLEANER_READ_SIZE.getName();
        setParam(name, "7777");
        assertEquals(7777, cleaner.readBufferSize);

        /* je.cleaner.detailMaxMemoryPercentage */
        name = EnvironmentParams.CLEANER_DETAIL_MAX_MEMORY_PERCENTAGE.
            getName();
        setParam(name, "7");
        assertEquals((budget.getMaxMemory() * 7) / 100,
                     budget.getTrackerBudget());

        /* je.cleaner.threads */
        name = EnvironmentParams.CLEANER_THREADS.getName();
        setParam(name, "7");
        assertEquals((envImpl.isNoLocking() ? 0 : 7),
                     countCleanerThreads());

        exampleEnv.close();
        exampleEnv = null;
    }

    /**
     * Sets a mutable config param, checking that the given value is not
     * already set and that it actually changes.
     */
    private void setParam(String name, String val)
        throws DatabaseException {

        EnvironmentMutableConfig config = exampleEnv.getMutableConfig();
        String myVal = config.getConfigParam(name);
        assertTrue(!val.equals(myVal));

        config.setConfigParam(name, val);
        exampleEnv.setMutableConfig(config);

        config = exampleEnv.getMutableConfig();
        myVal = config.getConfigParam(name);
        assertTrue(val.equals(myVal));
    }

    /**
     * Count the number of threads with the name "Cleaner#".
     */
    private int countCleanerThreads() {

        Thread[] threads = new Thread[Thread.activeCount()];
        Thread.enumerate(threads);

        int count = 0;
        for (int i = 0; i < threads.length; i += 1) {
            if (threads[i] != null &&
                threads[i].getName().startsWith("Cleaner")) {
                count += 1;
            }
        }

        return count;
    }

    /**
     * Checks that the memory budget is updated properly by the
     * UtilizationTracker.  Prior to a bug fix [#15505] amounts were added to
     * the budget but not subtracted when two TrackedFileSummary objects were
     * merged.  Merging occurs when a local tracker is added to the global
     * tracker.  Local trackers are used during recovery, checkpoints, lazy
     * compression, and reverse splits.
     */
    public void testTrackerMemoryBudget()
        throws DatabaseException {

        /* Open environment. */
        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setAllowCreate(true);
        envConfig.setTransactional(true);
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_CLEANER.getName(), "false");
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_INCOMPRESSOR.getName(), "false");
        exampleEnv = new Environment(envHome, envConfig);

        /* Open database. */
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);
        exampleDb = exampleEnv.openDatabase(null, "foo", dbConfig);

        /* Insert data. */
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        for (int i = 1; i <= 200; i += 1) {
            IntegerBinding.intToEntry(i, key);
            IntegerBinding.intToEntry(i, data);
            exampleDb.put(null, key, data);
        }

        /* Sav the admin budget baseline. */
        flushTrackedFiles();
        long admin = exampleEnv.getStats(null).getAdminBytes();

        /*
         * Nothing becomes obsolete when inserting and no INs are logged, so
         * the budget does not increase.
         */
        IntegerBinding.intToEntry(201, key);
        exampleDb.put(null, key, data);
        assertEquals(admin, exampleEnv.getStats(null).getAdminBytes());
        flushTrackedFiles();
        assertEquals(admin, exampleEnv.getStats(null).getAdminBytes());

        /*
         * Update a record and expect the budget to increase because the old
         * LN becomes obsolete.
         */
        exampleDb.put(null, key, data);
        assertTrue(admin < exampleEnv.getStats(null).getAdminBytes());
        flushTrackedFiles();
        assertEquals(admin, exampleEnv.getStats(null).getAdminBytes());

        /*
         * Delete all records and expect the budget to increase because LNs
         * become obsolete.
         */
        for (int i = 1; i <= 201; i += 1) {
            IntegerBinding.intToEntry(i, key);
            exampleDb.delete(null, key);
        }
        assertTrue(admin < exampleEnv.getStats(null).getAdminBytes());
        flushTrackedFiles();
        assertEquals(admin, exampleEnv.getStats(null).getAdminBytes());

        /*
         * Compress and expect no change to the budget.  Prior to the fix for
         * [#15505] the assertion below failed because the baseline admin
         * budget was not restored.
         */
        exampleEnv.compress();
        flushTrackedFiles();
        assertEquals(admin, exampleEnv.getStats(null).getAdminBytes());

        closeEnv();
    }

    /**
     * Flushes all tracked files to subtract tracked info from the admin memory
     * budget.
     */
    private void flushTrackedFiles()
        throws DatabaseException {

        EnvironmentImpl envImpl = DbInternal.getEnvironmentImpl(exampleEnv);
        UtilizationTracker tracker = envImpl.getUtilizationTracker();
        UtilizationProfile profile = envImpl.getUtilizationProfile();

        for (TrackedFileSummary summary : tracker.getTrackedFiles()) {
            profile.flushFileSummary(summary);
        }
    }

    /**
     * Tests that memory is budgeted correctly for FileSummaryLNs that are
     * inserted and deleted after calling setTrackedSummary.  The size of the
     * FileSummaryLN changes during logging when setTrackedSummary is called,
     * and this is accounted for specially in Tree.logLNAfterInsert. [#15831]
     */
    public void testFileSummaryLNMemoryUsage()
        throws DatabaseException {

        /* Open environment, prevent concurrent access by daemons. */
        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setAllowCreate(true);
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_CLEANER.getName(), "false");
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_CHECKPOINTER.getName(), "false");
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_INCOMPRESSOR.getName(), "false");
        exampleEnv = new Environment(envHome, envConfig);

        EnvironmentImpl envImpl = DbInternal.getEnvironmentImpl(exampleEnv);
        UtilizationProfile up = envImpl.getUtilizationProfile();
        DatabaseImpl fileSummaryDb = up.getFileSummaryDb();
        MemoryBudget memBudget = envImpl.getMemoryBudget();

        BasicLocker locker = null;
        CursorImpl cursor = null;
        try {
            locker = BasicLocker.createBasicLocker(envImpl);
            cursor = new CursorImpl(fileSummaryDb, locker);

            /* Get parent BIN.  There should be only one BIN in the tree. */
            IN root =
                fileSummaryDb.getTree().getRootIN(CacheMode.DEFAULT);
            root.releaseLatch();
            assertEquals(1, root.getNEntries());
            BIN parent = (BIN) root.getTarget(0);

            /* Use an artificial FileSummaryLN with a tracked summary. */
            FileSummaryLN ln = new FileSummaryLN(envImpl, new FileSummary());
            TrackedFileSummary tfs = new TrackedFileSummary
                (envImpl.getUtilizationTracker(), 0 /*fileNum*/,
                 true /*trackDetail*/);
            tfs.trackObsolete(0, true/*checkDupOffsets*/);
            byte[] keyBytes =
                FileSummaryLN.makeFullKey(0 /*fileNum*/, 123 /*sequence*/);
            int keySize = MemoryBudget.byteArraySize(keyBytes.length);

            /* Perform insert after calling setTrackedSummary. */
            long oldSize = ln.getMemorySizeIncludedByParent();
            long oldParentSize = getMemSize(parent, memBudget);
            ln.setTrackedSummary(tfs);
            OperationStatus status = cursor.putLN
                (keyBytes, ln, null /*returnNewData*/,
                 false /*allowDuplicates*/, fileSummaryDb.getRepContext());
            assertSame(status, OperationStatus.SUCCESS);

            assertSame(parent, cursor.latchBIN());
            ln.addExtraMarshaledMemorySize(parent);
            cursor.releaseBIN();

            long newSize = ln.getMemorySizeIncludedByParent();
            long newParentSize = getMemSize(parent, memBudget);

            /* The size of the LN increases during logging. */
            assertEquals(newSize,
                         oldSize +
                         ln.getObsoleteOffsets().getExtraMemorySize());

            /* The correct size is accounted for by the parent BIN. */
            assertEquals(newSize + keySize, newParentSize - oldParentSize);

            /* Correct size is subtracted during eviction. */
            oldParentSize = newParentSize;
            cursor.evict();
            newParentSize = getMemSize(parent, memBudget);
            assertEquals(oldParentSize - newSize, newParentSize);
            long evictedParentSize = newParentSize;

            /* Fetch a fresh FileSummaryLN before deleting it. */
            oldParentSize = newParentSize;
            ln = (FileSummaryLN) cursor.getCurrentLN(LockType.READ);
            newSize = ln.getMemorySizeIncludedByParent();
            newParentSize = getMemSize(parent, memBudget);
            assertEquals(newSize, newParentSize - oldParentSize);

            /* Perform delete after calling setTrackedSummary. */
            oldSize = newSize;
            oldParentSize = newParentSize;
            ln.setTrackedSummary(tfs);
            status = cursor.delete(fileSummaryDb.getRepContext());
            assertSame(status, OperationStatus.SUCCESS);
            newSize = ln.getMemorySizeIncludedByParent();
            newParentSize = getMemSize(parent, memBudget);

            /* Size changes during delete also. */
            assertTrue(newSize < oldSize);
            assertTrue(oldSize - newSize >
                       ln.getObsoleteOffsets().getExtraMemorySize());
            assertEquals(newSize - oldSize, newParentSize - oldParentSize);

            /* Correct size is subtracted during eviction. */
            oldParentSize = newParentSize;
            cursor.evict();
            newParentSize = getMemSize(parent, memBudget);
            assertEquals(oldParentSize - newSize, newParentSize);
            assertEquals(evictedParentSize, newParentSize);
        } finally {
            if (cursor != null) {
                cursor.releaseBINs();
                cursor.close();
            }
            if (locker != null) {
                locker.operationEnd();
            }
        }

        TestUtils.validateNodeMemUsage(envImpl, true /*assertOnError*/);

        /* Insert again, this time using the UtilizationProfile method. */
        FileSummaryLN ln = new FileSummaryLN(envImpl, new FileSummary());
        TrackedFileSummary tfs = new TrackedFileSummary
            (envImpl.getUtilizationTracker(), 0 /*fileNum*/,
             true /*trackDetail*/);
        tfs.trackObsolete(0, true/*checkDupOffsets*/);
        ln.setTrackedSummary(tfs);
        assertTrue(up.insertFileSummary(ln, 0 /*fileNum*/, 123 /*sequence*/));
        TestUtils.validateNodeMemUsage(envImpl, true /*assertOnError*/);

        closeEnv();
    }

    /**
     * Checks that log utilization is updated incrementally during the
     * checkpoint rather than only when the highest dirty level in the Btree is
     * flushed.  This feature (incremental update) was added so that log
     * cleaning is not delayed until the end of the checkpoint. [#16037]
     */
    public void testUtilizationDuringCheckpoint()
        throws DatabaseException {

        /*
         * Use Database.sync of a deferred-write database to perform this test
         * rather than a checkpoint, because the hook is called at a
         * predictable place when only a single database is flushed.  The
         * implementation of Checkpointer.flushDirtyNodes is shared for
         * Database.sync and checkpoint, so this tests both cases.
         */
        final int FANOUT = 25;
        final int N_KEYS = FANOUT * FANOUT * FANOUT;

        /* Open environment. */
        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setAllowCreate(true);
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_CHECKPOINTER.getName(), "false");
        exampleEnv = new Environment(envHome, envConfig);

        /* Open ordinary non-transactional database. */
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setNodeMaxEntries(FANOUT);
        exampleDb = exampleEnv.openDatabase(null, "foo", dbConfig);

        /* Clear stats. */
        StatsConfig statsConfig = new StatsConfig();
        statsConfig.setClear(true);
        exampleEnv.getStats(statsConfig);

        /* Write to database to create a 3 level Btree. */
        DatabaseEntry keyEntry = new DatabaseEntry();
        DatabaseEntry dataEntry = new DatabaseEntry(new byte[0]);
        for (int i = 0; i < N_KEYS; i += 1) {
            LongBinding.longToEntry(i, keyEntry);
            assertSame(OperationStatus.SUCCESS,
                       exampleDb.put(null, keyEntry, dataEntry));
            EnvironmentStats stats = exampleEnv.getStats(statsConfig);
            if (stats.getNEvictPasses() > 0) {
                break;
            }
        }

        /*
         * Sync and write an LN in each BIN to create a bunch of dirty INs
         * that, when flushed again, will cause the prior versions to be
         * obsolete.
         */
        exampleEnv.sync();
        for (int i = 0; i < N_KEYS; i += FANOUT) {
            LongBinding.longToEntry(i, keyEntry);
            assertSame(OperationStatus.SUCCESS,
                       exampleDb.put(null, keyEntry, dataEntry));
        }

        /*
         * Close and re-open as a deferred-write DB so that we can call sync.
         * The INs will remain dirty.
         */
        exampleDb.close();
        dbConfig = new DatabaseConfig();
        dbConfig.setDeferredWrite(true);
        exampleDb = exampleEnv.openDatabase(null, "foo", dbConfig);

        /*
         * The test hook is called just before writing the highest dirty level
         * in the Btree.  At that point, utilization should be reduced if the
         * incremental utilization update feature is working properly.  Before
         * adding this feature, utilization was not changed at this point.
         */
        final int oldUtilization = getUtilization();
        final StringBuilder hookCalledFlag = new StringBuilder();

        Checkpointer.setMaxFlushLevelHook(new TestHook() {
            public void doHook() {
                hookCalledFlag.append(1);
                final int newUtilization;
                newUtilization = getUtilization();
                String msg = "oldUtilization=" + oldUtilization +
                             " newUtilization=" + newUtilization;
                assertTrue(msg, oldUtilization - newUtilization >= 10);
                /* Don't call the test hook repeatedly. */
                Checkpointer.setMaxFlushLevelHook(null);
            }
            public Object getHookValue() {
                throw new UnsupportedOperationException();
            }
            public void doIOHook() {
                throw new UnsupportedOperationException();
            }
            public void hookSetup() {
                throw new UnsupportedOperationException();
            }
        });
        exampleDb.sync();
        assertTrue(hookCalledFlag.length() > 0);

        closeEnv();
    }

    private int getUtilization() {
        EnvironmentImpl envImpl = DbInternal.getEnvironmentImpl(exampleEnv);
        Map<Long,FileSummary> map =
            envImpl.getUtilizationProfile().getFileSummaryMap(true);
        FileSummary totals = new FileSummary();
        for (FileSummary summary : map.values()) {
            totals.add(summary);
        }
        return UtilizationProfile.utilization(totals.getObsoleteSize(),
                                              totals.totalSize);
    }

    /**
     * Returns the memory size taken by the given IN and the tree memory usage.
     */
    private long getMemSize(IN in, MemoryBudget memBudget) {
        return memBudget.getTreeMemoryUsage() +
               in.getInMemorySize() -
               in.getBudgetedMemorySize();
    }

    /**
     * Tests that dirtiness is logged upwards during a checkpoint, even if a
     * node is evicted and refetched after being added to the checkpointer's
     * dirty map, and before that entry in the dirty map is processed by the
     * checkpointer.  [#16523]
     *
     *  Root INa
     *      /  \
     *     INb  ...
     *    /
     *   INc
     *  /
     * BINd
     *
     * The scenario that causes the bug is:
     *
     * 1) Prior to the final checkpoint, the cleaner processes a log file
     * containing BINd.  The cleaner marks BINd dirty so that it will be
     * flushed prior to the end of the next checkpoint, at which point the file
     * containing BINd will be deleted.  The cleaner also calls
     * setProhibitNextDelta on BINd to ensure that a full version will be
     * logged.
     *
     * 2) At checkpoint start, BINd is added to the checkpoiner's dirty map.
     * It so happens that INa is also dirty, perhaps as the result of a split,
     * and added to the dirty map.  The checkpointer's max flush level is 4.
     *
     * 3) The evictor flushes BINd and then its parent INc.  Both are logged
     * provisionally, since their level is less than 4, the checkpointer's max
     * flush level.  INb, the parent of INc, is dirty.
     *
     * 4) INc, along with BINd, is loaded back into the Btree as the result of
     * reading an LN in BINd.  INc and BINd are both non-dirty.  INb, the
     * parent of INc, is still dirty.
     *
     * 5) The checkpointer processes its reference to BINd in the dirty map.
     * It finds that BINd is not dirty, so does not need to be logged.  It
     * attempts to add the parent, INc, to the dirty map in order to propogate
     * changes upward.  However, becaue INc is not dirty, it is not added to
     * the dirty map -- this was the bug, it should be added even if not dirty.
     * So as the result of this step, the checkpointer does no logging and does
     * not add anything to the dirty map.
     *
     * 6) The checkpointer logs INa (it was dirty at the start of the
     * checkpoint) and the checkpoint finishes.  It deletes the cleaned log
     * file that contains the original version of BINd.
     *
     * The key thing is that INb is now dirty and was not logged.  It should
     * have been logged as the result of being an ancestor of BINd, which was
     * in the dirty map.  Its parent INa was logged, but does not refer to the
     * latest version of INb/INc/BINd.
     *
     * 7) Now we recover.  INc and BINd, which were evicted during step (3),
     * are not replayed because they are provisional -- they are lost.  When a
     * search for an LN in BINd is performed, we traverse down to the old
     * version of BINd, which causes LogFileNotFound.
     *
     * The fix is to add INc to the dirty map at step (5), even though it is
     * not dirty.  When the reference to INc in the dirty map is processed we
     * will not log INc, but we will add its parent INb to the dirty map.  Then
     * when the reference to INb is processed, it will be logged because it is
     * dirty.  Then INa is logged and refers to the latest version of
     * INb/INc/BINd.
     *
     * This problem could only occur with a Btree of depth 4 or greater.
     */
    public void testEvictionDuringCheckpoint()
        throws DatabaseException {

        /* Use small fanout to create a deep tree. */
        final int FANOUT = 6;
        final int N_KEYS = FANOUT * FANOUT * FANOUT;

        /* Open environment without interference of daemon threads. */
        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setAllowCreate(true);
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_CHECKPOINTER.getName(), "false");
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_CLEANER.getName(), "false");
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_INCOMPRESSOR.getName(), "false");
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_EVICTOR.getName(), "false");
        exampleEnv = new Environment(envHome, envConfig);
        final EnvironmentImpl envImpl =
            DbInternal.getEnvironmentImpl(exampleEnv);

        /* Open database. */
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setNodeMaxEntries(FANOUT);
        exampleDb = exampleEnv.openDatabase(null, "foo", dbConfig);
        DatabaseImpl dbImpl = DbInternal.getDatabaseImpl(exampleDb);

        /* Write to database to create a 4 level Btree. */
        final DatabaseEntry keyEntry = new DatabaseEntry();
        final DatabaseEntry dataEntry = new DatabaseEntry(new byte[0]);
        int nRecords;
        for (nRecords = 1;; nRecords += 1) {
            LongBinding.longToEntry(nRecords, keyEntry);
            assertSame(OperationStatus.SUCCESS,
                       exampleDb.put(null, keyEntry, dataEntry));
            if (nRecords % 10 == 0) {
                int level = envImpl.getDbTree().getHighestLevel(dbImpl);
                if ((level & IN.LEVEL_MASK) >= 4) {
                    break;
                }
            }
        }

        /* Flush all dirty nodes. */
        exampleEnv.sync();

        /* Get BINd and its ancestors.  Mark BINd and INa dirty. */
        final IN nodeINa = dbImpl.getTree().getRootIN(CacheMode.DEFAULT);
        nodeINa.releaseLatch();
        final IN nodeINb = (IN) nodeINa.getTarget(0);
        final IN nodeINc = (IN) nodeINb.getTarget(0);
        final BIN nodeBINd = (BIN) nodeINc.getTarget(0);
        assertNotNull(nodeBINd);
        nodeINa.setDirty(true);
        nodeBINd.setDirty(true);

        /*
         * The test hook is called after creating the checkpoint dirty map and
         * just before flushing dirty nodes.
         */
        final StringBuilder hookCalledFlag = new StringBuilder();

        Checkpointer.setBeforeFlushHook(new TestHook() {
            public void doHook() {
                hookCalledFlag.append(1);
                /* Don't call the test hook repeatedly. */
                Checkpointer.setBeforeFlushHook(null);
                try {
                    /* Evict BINd and INc. */
                    simulateEviction(exampleEnv, envImpl, nodeBINd, nodeINc);
                    simulateEviction(exampleEnv, envImpl, nodeINc, nodeINb);

                    /*
                     * Force BINd and INc to be loaded into cache by fetching
                     * the left-most record.
                     *
                     * Note that nodeINc and nodeBINd are different instances
                     * and are no longer in the Btree but we don't change these
                     * variables because they are final.  They should not be
                     * used past this point.
                     */
                    LongBinding.longToEntry(1, keyEntry);
                    assertSame(OperationStatus.SUCCESS,
                               exampleDb.get(null, keyEntry, dataEntry, null));
                } catch (DatabaseException e) {
                    throw new RuntimeException(e);
                }
            }
            public Object getHookValue() {
                throw new UnsupportedOperationException();
            }
            public void doIOHook() {
                throw new UnsupportedOperationException();
            }
            public void hookSetup() {
                throw new UnsupportedOperationException();
            }
        });
        exampleEnv.checkpoint(forceConfig);
        assertTrue(hookCalledFlag.length() > 0);
        assertTrue(!nodeINa.getDirty());
        assertTrue(!nodeINb.getDirty()); /* This failed before the bug fix. */

        closeEnv();
    }

    /**
     * Simulate eviction by logging this node, updating the LSN in its
     * parent slot, setting the Node to null in the parent slot, and
     * removing the IN from the INList.  Logging is provisional.  The
     * parent is dirtied.  May not be called unless this node is dirty and
     * none of its children are dirty.  Children may be resident.
     */
    private void simulateEviction(Environment env,
                                  EnvironmentImpl envImpl,
                                  IN nodeToEvict,
                                  IN parentNode)
        throws DatabaseException {

        assertTrue("not dirty " + nodeToEvict.getNodeId(),
                   nodeToEvict.getDirty());
        assertTrue(!hasDirtyChildren(nodeToEvict));
        parentNode.latch();
        long lsn = TestUtils.logIN
            (env, nodeToEvict, true /*provisional*/, parentNode);
        int index;
        for (index = 0;; index += 1) {
            if (index >= parentNode.getNEntries()) {
                fail();
            }
            if (parentNode.getTarget(index) == nodeToEvict) {
                break;
            }
        }
        parentNode.updateNode(index, null /*node*/, lsn, null /*lnSlotKey*/);
        parentNode.releaseLatch();
        envImpl.getInMemoryINs().remove(nodeToEvict);
    }

    private boolean hasDirtyChildren(IN parent) {
        for (int i = 0; i < parent.getNEntries(); i += 1) {
            Node child = parent.getTarget(i);
            if (child instanceof IN) {
                IN in = (IN) child;
                if (in.getDirty()) {
                    return true;
                }
            }
        }
        return false;
    }

    public void testMultiCleaningBug()
        throws DatabaseException {

        initEnv(true, false);

        final EnvironmentImpl envImpl =
            DbInternal.getEnvironmentImpl(exampleEnv);
        final FileSelector fileSelector =
            envImpl.getCleaner().getFileSelector();

        Map<String, Set<String>> expectedMap =
            new HashMap<String, Set<String>>();
        doLargePut(expectedMap, 1000, 1, true);
        modifyData(expectedMap, 1, true);
        checkData(expectedMap);

        final TestHook hook = new TestHook() {
            public void doHook() {
                /* Signal that hook was called. */
                if (synchronizer != 99) {
                    synchronizer = 1;
                }
                /* Wait for signal to proceed with cleaning. */
                while (synchronizer != 2 &&
                       synchronizer != 99 &&
                       !Thread.interrupted()) {
                    Thread.yield();
                }
            }
            public Object getHookValue() {
                throw new UnsupportedOperationException();
            }
            public void doIOHook() throws IOException {
                throw new UnsupportedOperationException();
            }
            public void hookSetup() {
                throw new UnsupportedOperationException();
            }
        };

        junitThread = new JUnitThread("TestMultiCleaningBug") {
            public void testBody()
                throws DatabaseException {

                try {
                    while (synchronizer != 99) {
                        /* Wait for initial state. */
                        while (synchronizer != 0 &&
                               synchronizer != 99 &&
                               !Thread.interrupted()) {
                            Thread.yield();
                        }
                        /* Clean with hook set, hook is called next. */
                        fileSelector.setFileChosenHook(hook);
                        exampleEnv.cleanLog();
                        /* Signal that cleaning is done. */
                        if (synchronizer != 99) {
                            synchronizer = 3;
                        }
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        };

        /* Kick off thread above. */
        synchronizer = 0;
        junitThread.start();

        for (int i = 0; i < 100 && junitThread.isAlive(); i += 1) {
            /* Wait for hook to be called when a file is chosen. */
            while (synchronizer != 1 && junitThread.isAlive()) {
                Thread.yield();
            }
            /* Allow the thread to clean the chosen file. */
            synchronizer = 2;
            /* But immediately clean here, which could select the same file. */
            fileSelector.setFileChosenHook(null);
            exampleEnv.cleanLog();
            /* Wait for both cleaner runs to finish. */
            while (synchronizer != 3 && junitThread.isAlive()) {
                Thread.yield();
            }
            /* Make more waste to be cleaned. */
            modifyData(expectedMap, 1, true);
            synchronizer = 0;
        }

        synchronizer = 99;

        try {
            junitThread.finishTest();
            junitThread = null;
        } catch (Throwable e) {
            e.printStackTrace();
            fail(e.toString());
        }

        closeEnv();
    }
}
