/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: LogBufferPoolTest.java,v 1.78 2010/01/04 15:51:01 cwl Exp $
 */

package com.sleepycat.je.log;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.dbi.MemoryBudget;
import com.sleepycat.je.util.TestUtils;
import com.sleepycat.je.utilint.DbLsn;

public class LogBufferPoolTest extends TestCase {

    Environment env;
    Database db;
    EnvironmentImpl envImpl;
    FileManager fileManager;
    File envHome;
    LogBufferPool bufPool;

    public LogBufferPoolTest() {
        super();
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    @Override
    protected void setUp() {
        /* Remove files to start with a clean slate. */
        TestUtils.removeFiles("Setup", envHome, FileManager.JE_SUFFIX);
    }

    @Override
    protected void tearDown()
        throws Exception {

        bufPool = null;
        if (fileManager != null) {
            fileManager.clear();
            fileManager.close();
        }
        TestUtils.removeFiles("TearDown", envHome, FileManager.JE_SUFFIX);
    }

    /**
     * Make sure that we'll add more buffers as needed.
     */
    public void testGrowBuffers()
        throws Throwable {

        try {

            setupEnv(true);

            /*
             * Each buffer can only hold 2 items.  Put enough test items in to
             * get seven buffers.
             */
            List<Long> lsns = new ArrayList<Long>();
            for (int i = 0; i < 14; i++) {
                long lsn = insertData(bufPool, (byte) (i + 1));
                lsns.add(new Long(lsn));
            }

            /*
             * Check that the bufPool knows where each LSN lives and that the
             * fetched buffer does hold this item.
             */
            LogBuffer logBuf;
            ByteBuffer b;
            for (int i = 0; i < 14; i++) {

                /*
                 * For each test LSN, ask the bufpool for the logbuffer that
                 * houses it.
                 */
                long testLsn = DbLsn.longToLsn(lsns.get(i));
                logBuf = bufPool.getReadBufferByLsn(testLsn);
                assertNotNull(logBuf);

                /* Here's the expected data. */
                byte[] expected = new byte[10];
                Arrays.fill(expected, (byte)(i+1));

                /* Here's the data in the log buffer. */
                byte[] logData = new byte[10];
                b = logBuf.getDataBuffer();
                long firstLsnInBuf = logBuf.getFirstLsn();
                b.position((int) (DbLsn.getFileOffset(testLsn) -
                                  DbLsn.getFileOffset(firstLsnInBuf)));
                logBuf.getDataBuffer().get(logData);

                /* They'd better be equal. */
                assertTrue(Arrays.equals(logData, expected));
                logBuf.release();
            }

            /*
             * This LSN shouldn't be in the buffers, it's less than any
             * buffered item.
             */
            assertNull(bufPool.getReadBufferByLsn(DbLsn.makeLsn(0,10)));

            /*
             * This LSN is illegal to ask for, it's greater than any registered
             * LSN.
             */
            assertNull("LSN too big",
                       bufPool.getReadBufferByLsn(DbLsn.makeLsn(10, 141)));
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * Helper to insert fake data.
     * @return LSN registered for this fake data
     */
    private long insertData(LogBufferPool bufPool,
                            byte value)
        throws IOException, DatabaseException {

        byte[] data = new byte[10];
        Arrays.fill(data, value);
        boolean flippedFile = fileManager.bumpLsn(data.length);
        LogBuffer logBuf = bufPool.getWriteBuffer(data.length, flippedFile);
        logBuf.getDataBuffer().put(data);
        long lsn = fileManager.getLastUsedLsn();
        bufPool.writeCompleted(fileManager.getLastUsedLsn(),
                               true /* flushRequired */,
                               false /* fsyncRequired */);
        return lsn;
    }

    /**
     * Test buffer flushes.
     */
    public void testBufferFlush()
        throws Throwable {

        try {
            setupEnv(false);
            assertFalse("There should be no files", fileManager.filesExist());

            fileManager.VERIFY_CHECKSUMS = false;

            /*
             * Each buffer can only hold 2 items. Put enough test items in to
             * get five buffers.
             */
            for (int i = 0; i < 9; i++) {
                insertData(bufPool, (byte) (i+1));
            }
            bufPool.writeBufferToFile(0, true);
            fileManager.syncLogEnd();

            /* We should see two files exist. */
            String[] fileNames =
                fileManager.listFiles(FileManager.JE_SUFFIXES);
            assertEquals("Should be 2 files", 2, fileNames.length);

            /* Read the files. */
            if (false) {
            ByteBuffer dataBuffer = ByteBuffer.allocate(100);
            FileHandle file0 = fileManager.getFileHandle(0L);
            RandomAccessFile file = file0.getFile();
            FileChannel channel = file.getChannel();
            int bytesRead = channel.read(dataBuffer,
                                         FileManager.firstLogEntryOffset());
            dataBuffer.flip();
            assertEquals("Check bytes read", 50, bytesRead);
            assertEquals("Check size of file", 50, dataBuffer.limit());
            file.close();
            FileHandle file1 = fileManager.getFileHandle(1L);
            file = file1.getFile();
            channel = file.getChannel();
            bytesRead = channel.read(dataBuffer,
                                     FileManager.firstLogEntryOffset());
            dataBuffer.flip();
            assertEquals("Check bytes read", 40, bytesRead);
            assertEquals("Check size of file", 40, dataBuffer.limit());
            file0.release();
            file1.release();
            }
        } catch (Throwable e) {
            e.printStackTrace();
            throw e;
        }
    }

    public void testTemporaryBuffers()
        throws Exception {

        final int KEY_SIZE = 10;
        final int DATA_SIZE = 1000000;

        tempBufferInitEnvInternal
            ("0", MemoryBudget.MIN_MAX_MEMORY_SIZE_STRING);
        DatabaseEntry key = new DatabaseEntry(new byte[KEY_SIZE]);
        DatabaseEntry data = new DatabaseEntry(new byte[DATA_SIZE]);
        db.put(null, key, data);
        db.close();
        env.close();
    }

    private void tempBufferInitEnvInternal(String buffSize, String cacheSize)
        throws DatabaseException {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setTransactional(true);
        envConfig.setAllowCreate(true);
        if (!buffSize.equals("0")) {
            envConfig.setConfigParam("je.log.totalBufferBytes", buffSize);
        }

        if (!cacheSize.equals("0")) {
            envConfig.setConfigParam("je.maxMemory", cacheSize);
        }
        env = new Environment(envHome, envConfig);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setSortedDuplicates(true);
        dbConfig.setTransactional(true);
        db = env.openDatabase(null, "InsertAndDelete", dbConfig);
    }

    private void setupEnv(boolean inMemory)
        throws Exception {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();

        DbInternal.disableParameterValidation(envConfig);
        envConfig.setConfigParam(EnvironmentParams.LOG_MEM_SIZE.getName(),
                                 EnvironmentParams.LOG_MEM_SIZE_MIN_STRING);
        envConfig.setConfigParam
            (EnvironmentParams.LOG_FILE_MAX.getName(), "90");
        envConfig.setConfigParam
            (EnvironmentParams.NUM_LOG_BUFFERS.getName(), "2");
        envConfig.setAllowCreate(true);
        if (inMemory) {
            /* Make the bufPool grow some buffers. Disable writing. */
            envConfig.setConfigParam
                (EnvironmentParams.LOG_MEMORY_ONLY.getName(), "true");
        }

        env = new Environment(envHome, envConfig);
        envImpl = DbInternal.getEnvironmentImpl(env);

        /* Make a standalone file manager for this test. */
        envImpl.close();
        envImpl.open(); /* Just sets state to OPEN. */
        fileManager = new FileManager(envImpl, envHome, false);
        bufPool = new LogBufferPool(fileManager, envImpl);

        /*
         * Remove any files after the environment is created again!  We want to
         * remove the files made by recovery, so we can test the file manager
         * in controlled cases.
         */
        TestUtils.removeFiles("Setup", envHome, FileManager.JE_SUFFIX);
    }
}
