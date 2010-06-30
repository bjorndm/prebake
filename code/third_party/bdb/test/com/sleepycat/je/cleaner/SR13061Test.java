/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2005-2010 Oracle.  All rights reserved.
 *
 * $Id: SR13061Test.java,v 1.16 2010/01/04 15:51:00 cwl Exp $
 */

package com.sleepycat.je.cleaner;

import java.io.File;

import junit.framework.TestCase;

import com.sleepycat.bind.tuple.TupleOutput;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.tree.FileSummaryLN;
import com.sleepycat.je.util.TestUtils;

/**
 * Tests that a FileSummaryLN with an old style string key can be read.  When
 * we relied solely on log entry version to determine whether an LN had a
 * string key, we could fail when an old style LN was migrated by the cleaner.
 * In that case the key was still a string key but the log entry version was
 * updated to something greater than zero.  See FileSummaryLN.hasStringKey for
 * details of how we now guard against this.
 */
public class SR13061Test extends TestCase {

    private final File envHome;
    private Environment env;

    public SR13061Test() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    @Override
    public void setUp() {
        TestUtils.removeLogFiles("Setup", envHome, false);
        TestUtils.removeFiles("Setup", envHome, FileManager.DEL_SUFFIX);
    }

    @Override
    public void tearDown() {
        try {
            if (env != null) {
                //env.close();
            }
        } catch (Throwable e) {
            System.out.println("tearDown: " + e);
        }

        try {
            TestUtils.removeLogFiles("tearDown", envHome, true);
        } catch (Throwable e) {
            System.out.println("tearDown: " + e);
        }

        env = null;
    }

    public void testSR13061()
        throws DatabaseException {

        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setAllowCreate(true);
        env = new Environment(envHome, envConfig);

        FileSummaryLN ln =
            new FileSummaryLN(DbInternal.getEnvironmentImpl(env),
                              new FileSummary());

        /*
         * All of these tests failed before checking that the byte array must
         * be eight bytes for integer keys.
         */
        assertTrue(ln.hasStringKey(stringKey(0)));
        assertTrue(ln.hasStringKey(stringKey(1)));
        assertTrue(ln.hasStringKey(stringKey(12)));
        assertTrue(ln.hasStringKey(stringKey(123)));
        assertTrue(ln.hasStringKey(stringKey(1234)));
        assertTrue(ln.hasStringKey(stringKey(12345)));
        assertTrue(ln.hasStringKey(stringKey(123456)));
        assertTrue(ln.hasStringKey(stringKey(1234567)));
        assertTrue(ln.hasStringKey(stringKey(123456789)));
        assertTrue(ln.hasStringKey(stringKey(1234567890)));

        /*
         * These tests failed before checking that the first byte of the
         * sequence number (in an eight byte key) must not be '0' to '9' for
         * integer keys.
         */
        assertTrue(ln.hasStringKey(stringKey(12345678)));
        assertTrue(ln.hasStringKey(stringKey(12340000)));

        /* These tests are just for good measure. */
        assertTrue(!ln.hasStringKey(intKey(0, 1)));
        assertTrue(!ln.hasStringKey(intKey(1, 1)));
        assertTrue(!ln.hasStringKey(intKey(12, 1)));
        assertTrue(!ln.hasStringKey(intKey(123, 1)));
        assertTrue(!ln.hasStringKey(intKey(1234, 1)));
        assertTrue(!ln.hasStringKey(intKey(12345, 1)));
        assertTrue(!ln.hasStringKey(intKey(123456, 1)));
        assertTrue(!ln.hasStringKey(intKey(1234567, 1)));
        assertTrue(!ln.hasStringKey(intKey(12345678, 1)));
        assertTrue(!ln.hasStringKey(intKey(123456789, 1)));
        assertTrue(!ln.hasStringKey(intKey(1234567890, 1)));
    }

    private byte[] stringKey(long fileNum) {

        try {
            return String.valueOf(fileNum).getBytes("UTF-8");
        } catch (Exception e) {
            fail(e.toString());
            return null;
        }
    }

    private byte[] intKey(long fileNum, long seqNum) {

        TupleOutput out = new TupleOutput();
        out.writeUnsignedInt(fileNum);
        out.writeUnsignedInt(seqNum);
        return out.toByteArray();
    }
}
