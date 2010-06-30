/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2000-2010 Oracle.  All rights reserved.
 *
 * $Id: PersistTestUtils.java,v 1.14 2010/01/04 15:51:10 cwl Exp $
 */
package com.sleepycat.persist.test;

import junit.framework.TestCase;

import com.sleepycat.compat.DbCompat;
import com.sleepycat.je.Environment;

class PersistTestUtils {

    /**
     * Asserts than a database expectExists or does not exist. If keyName is
     * null, checks an entity database.  If keyName is non-null, checks a
     * secondary database.
     */
    static void assertDbExists(boolean expectExists,
                               Environment env,
                               String storeName,
                               String entityClassName,
                               String keyName) {
        String fileName;
        String dbName;
        if (DbCompat.SEPARATE_DATABASE_FILES) {
            fileName = storeName + '-' + entityClassName;
            if (keyName != null) {
                fileName += "-" + keyName;
            }
            dbName = null;
        } else {
            fileName = null;
            dbName = "persist#" + storeName + '#' + entityClassName;
            if (keyName != null) {
                dbName += "#" + keyName;
            }
        }
        boolean exists = DbCompat.databaseExists(env, fileName, dbName);
        if (expectExists != exists) {
            TestCase.fail
                ((expectExists ? "Does not exist: " : "Does exist: ") +
                 dbName);
        }
    }
}
