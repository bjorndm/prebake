/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: TestEntityBinding.java,v 1.28 2010/01/04 15:50:58 cwl Exp $
 */

package com.sleepycat.collections.test;

import com.sleepycat.bind.EntityBinding;
import com.sleepycat.bind.RecordNumberBinding;
import com.sleepycat.je.DatabaseEntry;

/**
 * @author Mark Hayes
 */
class TestEntityBinding implements EntityBinding {

    private boolean isRecNum;

    TestEntityBinding(boolean isRecNum) {

        this.isRecNum = isRecNum;
    }

    public Object entryToObject(DatabaseEntry key, DatabaseEntry value) {

        byte keyByte;
        if (isRecNum) {
            if (key.getSize() != 4) {
                throw new IllegalStateException();
            }
            keyByte = (byte) RecordNumberBinding.entryToRecordNumber(key);
        } else {
            if (key.getSize() != 1) {
                throw new IllegalStateException();
            }
            keyByte = key.getData()[key.getOffset()];
        }
        if (value.getSize() != 1) {
            throw new IllegalStateException();
        }
        byte valByte = value.getData()[value.getOffset()];
        return new TestEntity(keyByte, valByte);
    }

    public void objectToKey(Object object, DatabaseEntry key) {

        byte val = (byte) ((TestEntity) object).key;
        if (isRecNum) {
            RecordNumberBinding.recordNumberToEntry(val, key);
        } else {
            key.setData(new byte[] { val }, 0, 1);
        }
    }

    public void objectToData(Object object, DatabaseEntry value) {

        byte val = (byte) ((TestEntity) object).value;
        value.setData(new byte[] { val }, 0, 1);
    }
}
