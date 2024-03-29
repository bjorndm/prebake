/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: TestKeyCreator.java,v 1.31 2010/01/04 15:50:58 cwl Exp $
 */

package com.sleepycat.collections.test;

import com.sleepycat.bind.RecordNumberBinding;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.SecondaryDatabase;
import com.sleepycat.je.SecondaryKeyCreator;

/**
 * Unused until secondaries are available.
 * @author Mark Hayes
 */
class TestKeyCreator implements SecondaryKeyCreator {

    private final boolean isRecNum;

    TestKeyCreator(boolean isRecNum) {

        this.isRecNum = isRecNum;
    }

    public boolean createSecondaryKey(SecondaryDatabase db,
                                      DatabaseEntry primaryKeyData,
                                      DatabaseEntry valueData,
                                      DatabaseEntry indexKeyData) {
        if (valueData.getSize() == 0) {
            return false;
        }
        if (valueData.getSize() != 1) {
            throw new IllegalStateException();
        }
        byte val = valueData.getData()[valueData.getOffset()];
        if (val == 0) {
            return false; // fixed-len pad value
        }
        val -= 100;
        if (isRecNum) {
            RecordNumberBinding.recordNumberToEntry(val, indexKeyData);
        } else {
            indexKeyData.setData(new byte[] { val }, 0, 1);
        }
        return true;
    }

    public void clearIndexKey(DatabaseEntry valueData) {

        throw new RuntimeException("not supported");
    }
}
