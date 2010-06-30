/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: WriteQueueTest.java,v 1.7 2010/01/04 15:51:01 cwl Exp $
 */

package com.sleepycat.je.log;

import java.io.File;
import java.nio.ByteBuffer;

import junit.framework.TestCase;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.log.FileManager.LogEndFileDescriptor;
import com.sleepycat.je.util.TestUtils;

/**
 * Test File Manager write queue
 */
public class WriteQueueTest extends TestCase {

    static private int FILE_SIZE = 120;

    private Environment env;
    private FileManager fileManager;
    private final File envHome;

    public WriteQueueTest() {
        super();
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    @Override
    protected void setUp()
        throws DatabaseException {

        /* Remove files to start with a clean slate. */
        TestUtils.removeFiles("Setup", envHome, FileManager.JE_SUFFIX);

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        DbInternal.disableParameterValidation(envConfig);
        envConfig.setConfigParam(EnvironmentParams.LOG_FILE_MAX.getName(),
                                 new Integer(FILE_SIZE).toString());
        /* Yank the cache size way down. */
        envConfig.setConfigParam
            (EnvironmentParams.LOG_FILE_CACHE_SIZE.getName(), "3");
        envConfig.setAllowCreate(true);

        env = new Environment(envHome, envConfig);
        fileManager = DbInternal.getEnvironmentImpl(env).getFileManager();
    }

    @Override
    protected void tearDown()
        throws DatabaseException {

        if (env != null) {
            env.close();
            env = null;
        }
        TestUtils.removeFiles("TearDown", envHome, FileManager.JE_SUFFIX);
    }

    public void testReadFromEmptyWriteQueue() {
        if (fileManager.getUseWriteQueue()) {
            LogEndFileDescriptor lefd =
                fileManager.new LogEndFileDescriptor();
            ByteBuffer bb = ByteBuffer.allocate(100);
            assertFalse(lefd.checkWriteCache(bb, 0, 0));
        }
    }

    public void testReadFromWriteQueueWithDifferentFileNum() {
        if (fileManager.getUseWriteQueue()) {
            LogEndFileDescriptor lefd =
                fileManager.new LogEndFileDescriptor();
            lefd.setQueueFileNum(1);
            ByteBuffer bb = ByteBuffer.allocate(100);
            assertFalse(lefd.checkWriteCache(bb, 0, 0));
        }
    }

    public void testReadFromWriteQueueExactMatch()
        throws Exception {

        if (fileManager.getUseWriteQueue()) {
            LogEndFileDescriptor lefd =
                fileManager.new LogEndFileDescriptor();
            lefd.setQueueFileNum(0);
            lefd.enqueueWrite(0, new byte[] { 0, 1, 2, 3, 4 }, 0, 0, 5);
            ByteBuffer bb = ByteBuffer.allocate(100);
            bb.limit(5);
            assertTrue(lefd.checkWriteCache(bb, 0, 0));
            bb.position(0);
            for (int i = 0; i < 5; i++) {
                byte b = bb.get();
                assertTrue(b == i);
            }
        }
    }

    public void testReadFromWriteQueueSubset()
        throws Exception {

        if (fileManager.getUseWriteQueue()) {
            LogEndFileDescriptor lefd =
                fileManager.new LogEndFileDescriptor();
            lefd.setQueueFileNum(0);
            lefd.enqueueWrite(0, new byte[] { 0, 1, 2, 3, 4 }, 0, 0, 5);
            ByteBuffer bb = ByteBuffer.allocate(100);
            bb.limit(3);
            assertTrue(lefd.checkWriteCache(bb, 0, 0));
            bb.position(0);
            for (int i = 0; i < bb.limit(); i++) {
                byte b = bb.get();
                assertTrue(b == i);
            }
        }
    }

    public void testReadFromWriteQueueSubsetOffset()
        throws Exception {

        if (fileManager.getUseWriteQueue()) {
            LogEndFileDescriptor lefd =
                fileManager.new LogEndFileDescriptor();
            lefd.setQueueFileNum(0);
            lefd.enqueueWrite(0, new byte[] { 0, 1, 2, 3, 4 }, 0, 0, 5);
            ByteBuffer bb = ByteBuffer.allocate(100);
            bb.limit(3);
            assertTrue(lefd.checkWriteCache(bb, 2, 0));
            bb.position(0);
            for (int i = 2; i < bb.limit() + 2; i++) {
                byte b = bb.get();
                assertTrue(b == i);
            }
        }
    }

    public void testReadFromWriteQueueSubsetUnderflow()
        throws Exception {

        if (fileManager.getUseWriteQueue()) {
            LogEndFileDescriptor lefd =
                fileManager.new LogEndFileDescriptor();
            lefd.setQueueFileNum(0);
            lefd.enqueueWrite(0, new byte[] { 0, 1, 2, 3, 4 }, 0, 0, 5);
            ByteBuffer bb = ByteBuffer.allocate(100);
            bb.limit(4);
            assertTrue(lefd.checkWriteCache(bb, 2, 0));
            /* Ensure that buffer was reset to where it belongs. */
            assertEquals(bb.position(), 3);
            bb.flip();
            for (int i = 2; i < 2 + bb.limit(); i++) {
                byte b = bb.get();
                assertTrue(b == i);
            }
        }
    }

    public void testReadFromWriteQueueSubsetOverflow()
        throws Exception {

        if (fileManager.getUseWriteQueue()) {
            LogEndFileDescriptor lefd =
                fileManager.new LogEndFileDescriptor();
            lefd.setQueueFileNum(0);
            lefd.enqueueWrite(0, new byte[] { 0, 1, 2, 3, 4 }, 0, 0, 5);
            lefd.enqueueWrite(0, new byte[] { 5, 6, 7, 8, 9 }, 5, 0, 5);

            ByteBuffer bb = ByteBuffer.allocate(100);
            bb.limit(4);
            assertTrue(lefd.checkWriteCache(bb, 2, 0));
            bb.position(0);
            for (int i = 2; i < bb.limit() + 2; i++) {
                byte b = bb.get();
                assertTrue(b == i);
            }
        }
    }

    public void testReadFromWriteQueueSubsetOverflow2()
        throws Exception {

        if (fileManager.getUseWriteQueue()) {
            LogEndFileDescriptor lefd =
                fileManager.new LogEndFileDescriptor();
            lefd.setQueueFileNum(0);
            lefd.enqueueWrite(0, new byte[] { 0, 1, 2, 3, 4 }, 0, 0, 5);
            lefd.enqueueWrite(0, new byte[] { 5, 6, 7, 8, 9 }, 5, 0, 5);

            ByteBuffer bb = ByteBuffer.allocate(100);
            bb.limit(8);
            assertTrue(lefd.checkWriteCache(bb, 2, 0));
            bb.position(0);
            for (int i = 2; i < bb.limit() + 2; i++) {
                byte b = bb.get();
                assertTrue(b == i);
            }
        }
    }

    public void testReadFromWriteQueueMultipleEntries()
        throws Exception {

        if (fileManager.getUseWriteQueue()) {
            LogEndFileDescriptor lefd =
                fileManager.new LogEndFileDescriptor();
            lefd.setQueueFileNum(0);
            lefd.enqueueWrite(0, new byte[] { 0, 1, 2, 3, 4 }, 0, 0, 5);
            lefd.enqueueWrite(0, new byte[] { 5, 6, 7, 8, 9 }, 5, 0, 5);
            lefd.enqueueWrite(0, new byte[] { 10, 11, 12, 13, 14 }, 10, 0, 5);

            ByteBuffer bb = ByteBuffer.allocate(100);
            bb.limit(9);
            assertTrue(lefd.checkWriteCache(bb, 2, 0));
            bb.position(0);
            for (int i = 2; i < bb.limit() + 2; i++) {
                byte b = bb.get();
                assertTrue(b == i);
            }
        }
    }

    public void testReadFromWriteQueueLast2EntriesOnly()
        throws Exception {

        if (fileManager.getUseWriteQueue()) {
            LogEndFileDescriptor lefd =
                fileManager.new LogEndFileDescriptor();
            lefd.setQueueFileNum(0);
            lefd.enqueueWrite(0, new byte[] { 0, 1, 2, 3, 4 }, 0, 0, 5);
            lefd.enqueueWrite(0, new byte[] { 5, 6, 7, 8, 9 }, 5, 0, 5);
            lefd.enqueueWrite(0, new byte[] { 10, 11, 12, 13, 14 }, 10, 0, 5);

            ByteBuffer bb = ByteBuffer.allocate(100);
            bb.limit(9);
            assertTrue(lefd.checkWriteCache(bb, 6, 0));
            bb.position(0);
            for (int i = 6; i < bb.limit() + 6; i++) {
                byte b = bb.get();
                assertTrue(b == i);
            }
        }
    }

    public void testReadFromWriteQueueLastEntryOnly()
        throws Exception {

        if (fileManager.getUseWriteQueue()) {
            LogEndFileDescriptor lefd =
                fileManager.new LogEndFileDescriptor();
            lefd.setQueueFileNum(0);
            lefd.enqueueWrite(0, new byte[] { 0, 1, 2, 3, 4 }, 0, 0, 5);
            lefd.enqueueWrite(0, new byte[] { 5, 6, 7, 8, 9 }, 5, 0, 5);
            lefd.enqueueWrite(0, new byte[] { 10, 11, 12, 13, 14 }, 10, 0, 5);

            ByteBuffer bb = ByteBuffer.allocate(100);
            bb.limit(5);
            assertTrue(lefd.checkWriteCache(bb, 10, 0));
            bb.position(0);
            for (int i = 10; i < bb.limit() + 10; i++) {
                byte b = bb.get();
                assertTrue(b == i);
            }
        }
    }
}
