/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2004-2010 Oracle.  All rights reserved.
 *
 * $Id: WindowTest.java,v 1.4 2010/01/04 15:51:06 cwl Exp $
 */

package com.sleepycat.je.rep.util.ldiff;

import java.io.File;

import junit.framework.TestCase;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;

public class WindowTest extends TestCase {
    private static File envDir = new File("ENV");
    private static String dbName = "window.db";

    public void setUp() {
        if (envDir.exists()) {
            for (File f : envDir.listFiles())
                f.delete();
            envDir.delete();
        }
        envDir.mkdir();
    }

    public void tearDown() {
        if (envDir.exists()) {
            for (File f : envDir.listFiles())
                f.delete();
            envDir.delete();
        }
    }

    /**
     * Test that rolling the checksum yields the same value as calculating the
     * checksum directly.
     */
    public void testRollingChecksum() {
        Cursor c1, c2;
        Database db;
        DatabaseEntry data, key;
        Environment env;
        Window w1, w2;
        byte[] dataarr =
            { (byte) 0xdb, (byte) 0xdb, (byte) 0xdb, (byte) 0xdb };
        byte[] keyarr = { 0, 0, 0, 0 };
        final int blockSize = 5;
        final int dbSize = 2 * blockSize;

        /* Open the database environment. */
        EnvironmentConfig envConfig = new EnvironmentConfig();
        /* envConfig.setTransactional(false); */
        envConfig.setAllowCreate(true);
        try {
            env = new Environment(envDir, envConfig);
        } catch (Exception e) {
            assertTrue(false);
            return;
        }

        /* Open a database within the environment. */
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setExclusiveCreate(true);
        dbConfig.setSortedDuplicates(true);
        try {
            db = env.openDatabase(null, dbName, dbConfig);
        } catch (Exception e) {
            assertTrue(false);
            return;
        }

        for (int i = 0; i < dbSize; i++) {
            key = new DatabaseEntry(keyarr);
            data = new DatabaseEntry(dataarr);
            db.put(null, key, data);
            keyarr[3]++;
        }

        c1 = db.openCursor(null, null);
        c2 = db.openCursor(null, null);
        try {
            w1 = new Window(c1, blockSize);
            w2 = new Window(c2, blockSize);
        } catch (Exception e) {
            c1.close();
            c2.close();
            db.close();
            env.close();
            assertTrue(false);
            return;
        }
        assertEquals(w1.getChecksum(), w2.getChecksum());
        key = new DatabaseEntry();
        key.setPartial(0, 0, true);
        data = new DatabaseEntry();
        data.setPartial(0, 0, true);
        for (int i = blockSize; i < dbSize; i++) {
            try {
                /* Advance w1 by one key/data pair. */
                w1.rollWindow();

                /* 
                 * Position c2 to the next key/data pair and get a new window
                 * (Constructing the window modifiers the cursor, so we need to
                 * reposition it.
                 */
                assertTrue(c2.getFirst(key, data, LockMode.DEFAULT) ==
                           OperationStatus.SUCCESS);
                for (int j = 0; j < i - blockSize; j++)
                    assertTrue(c2.getNext(key, data, LockMode.DEFAULT) ==
                               OperationStatus.SUCCESS);
                w2 = new Window(c2, blockSize);

                /* 
                 * The windows are referring to the same range of key/data
                 * pairs.
                 */
                assertEquals(w1.getChecksum(), w2.getChecksum());
            } catch (Exception e) {
                c1.close();
                c2.close();
                db.close();
                env.close();
                assertTrue(false);
                return;
            }
        }
        c1.close();
        c2.close();
        db.close();
        env.close();
    }
}
