/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: NetworkBackupTest.java,v 1.14 2010/01/04 15:51:05 cwl Exp $
 */

package com.sleepycat.je.rep.impl.networkRestore;

import static com.sleepycat.je.rep.impl.networkRestore.NetworkBackupStatDefinition.DISPOSED_COUNT;
import static com.sleepycat.je.rep.impl.networkRestore.NetworkBackupStatDefinition.FETCH_COUNT;
import static com.sleepycat.je.rep.impl.networkRestore.NetworkBackupStatDefinition.SKIP_COUNT;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;

import junit.framework.TestCase;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.bind.tuple.LongBinding;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.EnvironmentLockedException;
import com.sleepycat.je.VerifyConfig;
import com.sleepycat.je.rep.impl.node.NameIdPair;
import com.sleepycat.je.rep.utilint.ServiceDispatcher;
import com.sleepycat.je.rep.utilint.BinaryProtocol.ProtocolException;
import com.sleepycat.je.util.DbBackup;
import com.sleepycat.je.util.TestUtils;

public class NetworkBackupTest extends TestCase {

    /* The port being handled by the dispatcher. */
    private static final int TEST_PORT = 5000;

    private File envHome;
    private EnvironmentConfig envConfig;
    File backupDir;
    private Environment env;
    private Database db;

    private final InetSocketAddress serverAddress =
        new InetSocketAddress("localhost", TEST_PORT);

    private ServiceDispatcher dispatcher;
    private FeederManager fm;

    protected DatabaseConfig dbconfig;
    protected final DatabaseEntry key = new DatabaseEntry(new byte[] { 1 });
    protected final DatabaseEntry data = new DatabaseEntry(new byte[] { 100 });
    protected static final String TEST_DB_NAME = "TestDB";

    protected static final VerifyConfig vconfig = new VerifyConfig();

    private static final int DB_ENTRIES = 100;

    static {
        vconfig.setAggressive(false);
        vconfig.setPropagateExceptions(true);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
        TestUtils.removeLogFiles("start of test", envHome, false);
        TestUtils.removeFiles("remove lock files", envHome, ".lck");

        envConfig = TestUtils.initEnvConfig();
        DbInternal.disableParameterValidation(envConfig);
        envConfig.setConfigParam(EnvironmentConfig.LOG_FILE_MAX, "1000");
        envConfig.setAllowCreate(true);
        envConfig.setTransactional(true);

        env = new Environment(envHome, envConfig);

        dbconfig = new DatabaseConfig();
        dbconfig.setAllowCreate(true);
        dbconfig.setTransactional(true);
        dbconfig.setSortedDuplicates(false);
        db = env.openDatabase(null, TEST_DB_NAME, dbconfig);

        for (int i=0; i < DB_ENTRIES; i++) {
            IntegerBinding.intToEntry(i, key);
            LongBinding.longToEntry(i, data);
            db.put(null, key, data);
        }
        // Create cleaner fodder
        for (int i=0; i < (DB_ENTRIES/2); i++) {
            IntegerBinding.intToEntry(i, key);
            LongBinding.longToEntry(i, data);
            db.put(null, key, data);
        }
        env.cleanLog();
        env.verify(vconfig, System.err);

        backupDir = new File(envHome.getCanonicalPath() + ".backup");
        backupDir.mkdir();
        assertTrue(backupDir.exists());
        cleanBackupdir();

        dispatcher = new ServiceDispatcher(serverAddress);
        dispatcher.start();
        fm = new FeederManager(dispatcher,
                               DbInternal.getEnvironmentImpl(env),
                               new NameIdPair("n1", (short)1));
        fm.start();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        db.close();
        env.close();
        TestUtils.removeLogFiles("done with test", envHome, false);
        fm.shutdown();
        dispatcher.shutdown();
    }

    public void testBackupFiles() throws Exception {

        /* The client side */
        NetworkBackup backup1 =
            new NetworkBackup(serverAddress,
                              backupDir,
                              new NameIdPair("n1", (short)1),
                              false);
        String files1[] = backup1.execute();
        assertEquals(0, backup1.getStats().getInt(SKIP_COUNT));

        verify(envHome, backupDir, files1);

        /* Corrupt the currently backed up log files. */
        for (File f : backupDir.listFiles(new NetworkBackup.JDBFilter())) {
            FileOutputStream os = new FileOutputStream(f);
            os.write(1);
            os.close();
        }
        int count = backupDir.listFiles(new NetworkBackup.JDBFilter()).length;
        NetworkBackup backup2 =
            new NetworkBackup(serverAddress, backupDir,
                              new NameIdPair("n1", (short)1), false);
        String files2[] = backup2.execute();
        verify(envHome, backupDir, files2);
        assertEquals(count, backup2.getStats().getInt(DISPOSED_COUNT));
        verifyAsEnv(backupDir);
    }

    /**
     * Performs a backup while the database is growing actively
     *
     * @throws InterruptedException
     * @throws IOException
     * @throws DatabaseException
     */
    public void testConcurrentBackup()
        throws InterruptedException, IOException, DatabaseException {

        LogFileGeneratingThread lfThread = new LogFileGeneratingThread();
        BackupThread backupThread = new BackupThread();
        lfThread.start();

        backupThread.start();
        backupThread.join(60*1000);
        lfThread.quit = true;
        lfThread.join(60*1000);

        DbBackup dbBackup = new DbBackup(env);
        dbBackup.startBackup();
        int newCount = dbBackup.getLogFilesInBackupSet().length;

        assertNull(backupThread.error);
        assertNull(lfThread.error);

        // Verify that the count did increase while the backup was in progress
        assertTrue(newCount > backupThread.files.length);
        // Verify that the backup was correct
        verify(envHome, backupDir, backupThread.files);

        verifyAsEnv(backupDir);
    }

    class BackupThread extends Thread {
        Exception error = null;
        String files[] = null;

        BackupThread() {
            setDaemon(true);
        }

        @Override
        public void run() {
            try {
                NetworkBackup backup1 =
                    new NetworkBackup(serverAddress,
                                      backupDir,
                                      new NameIdPair("n1", (short)1),
                                      true);
                files = backup1.execute();
            } catch (Exception e) {
                error = e;
                error.printStackTrace();
            }
        }
    }

    class LogFileGeneratingThread extends Thread {
        Exception error = null;
        volatile boolean quit = false;

        LogFileGeneratingThread() {
            setDaemon(true);
        }

        @Override
        public void run() {
            try {
                for (int i=0; i < 100000; i++) {
                    IntegerBinding.intToEntry(i, key);
                    LongBinding.longToEntry(i, data);
                    db.put(null, key, data);
                    if (quit) {
                        return;
                    }
                }
            } catch (Exception e) {
                error = e;
                error.printStackTrace();
            }
            fail("Backup did not finish in time");
        }
    }

    public void testBasic() throws Exception {

        /* The client side */
        NetworkBackup backup1 =
            new NetworkBackup(serverAddress,
                              backupDir,
                              new NameIdPair("n1", (short)1),
                              true);
        backup1.execute();
        assertEquals(0, backup1.getStats().getInt(SKIP_COUNT));

        /*
         * repeat, should find mostly cached files. Invoking backup causes
         * a checkpoint to be written to the log.
         */
        NetworkBackup backup2 =
            new NetworkBackup(serverAddress, backupDir,
                              new NameIdPair("n1", (short)1),
                              true);
        String files2[] = backup2.execute();
        verify(envHome, backupDir, files2);

        assertTrue((backup1.getStats().getInt(FETCH_COUNT) -
                     backup2.getStats().getInt(SKIP_COUNT))  <= 1);

        verifyAsEnv(backupDir);
    }

    public void testLeaseBasic()
        throws Exception {
        int errorFileNum = 2;
        NetworkBackup backup1 =
            new TestNetworkBackup(serverAddress,
                                  backupDir,
                                  (short) 1,
                                  true,
                                  errorFileNum);
        try {
            backup1.execute();
            fail("Exception expected");
        } catch (IOException e) {
            // Expected
        }
        // Wait for server to detect a broken connection
        Thread.sleep(500);
        // Verify that the lease was created.
        assertEquals(1, fm.getLeaseCount());
        NetworkBackup backup2 =
            new NetworkBackup(serverAddress,
                              backupDir,
                              new NameIdPair("n1", (short)1),
                              true);
        // Verify that the lease was renewed
        String[] files2 = backup2.execute();
        assertEquals(2, backup2.getStats().getInt(SKIP_COUNT));
        assertEquals(1, fm.getLeaseRenewalCount());

        // Verify that the copy resumed correctly
        verify(envHome, backupDir, files2);

        verifyAsEnv(backupDir);
    }

    public void testLeaseExpiration()
        throws Exception {

        int errorFileNum = 2;

        /*
         * Verify that leases are created and expire as expected.
         */
        NetworkBackup backup1 = new TestNetworkBackup(serverAddress,
                                                      backupDir,
                                                      (short) 1,
                                                      true,
                                                      errorFileNum);
        // Shorten the lease duration for test purposes
        long leaseDuration = 1*1000;
        try {
            fm.setLeaseDuration(leaseDuration);
            backup1.execute();
            fail("Exception expected");
        } catch (IOException e) {
            // Expected
        }
        // Wait for server to detect broken connection
        Thread.sleep(500);
        // Verify that the lease was created.
        assertEquals(1, fm.getLeaseCount());
        Thread.sleep(leaseDuration);
        // Verify that the lease has expired after its duration
        assertEquals(0, fm.getLeaseCount());

        // Resume after lease expiration
        NetworkBackup backup2 =
            new NetworkBackup(serverAddress,
                              backupDir,
                              new NameIdPair("n1", (short)1),
                              true);
        // Verify that the lease was renewed
        String[] files2 = backup2.execute();
        // Verify that the copy resumed correctly
        verify(envHome, backupDir, files2);

        verifyAsEnv(backupDir);
    }

    private void verify(File envDir, File envBackupDir, String envFiles[])
       throws IOException {

       for (String envFile : envFiles) {
           FileInputStream envStream =
               new FileInputStream(new File(envDir,envFile));
           FileInputStream envBackupStream =
               new FileInputStream(new File(envBackupDir, envFile));
           int ib1, ib2;
           do {
               ib1 = envStream.read();
               ib2 = envBackupStream.read();
           } while ((ib1 == ib2) && (ib1 != -1));
           assertEquals(ib1, ib2);
           envStream.close();
           envBackupStream.close();
       }
    }

    void verifyAsEnv(File dir)
        throws EnvironmentLockedException, DatabaseException {

        Environment benv = new Environment(dir, envConfig);
        /* Note that verify modifies log files. */
        benv.verify(vconfig, System.err);
        benv.close();
    }

    private void cleanBackupdir() {
        for (File f : backupDir.listFiles()) {
            assertTrue(f.delete());
        }
    }

    /**
     * Class to provoke a client failure when requesting a specific file.
     */
    private class TestNetworkBackup extends NetworkBackup {
        int errorFileNum = 0;

        public TestNetworkBackup(InetSocketAddress serverSocket,
                File envDir,
                short clientId,
                boolean retainLogfiles,
                int errorFileNum)
        throws DatabaseException {
            super(serverSocket, envDir,
                  new NameIdPair("node"+clientId, clientId), retainLogfiles);
            this.errorFileNum = errorFileNum;
        }

        @Override
        protected void getFile(File file)
            throws IOException, ProtocolException, DigestException {
            if (errorFileNum-- == 0) {
                throw new IOException("test exception");
            }
            super.getFile(file);
        }
    }
}
