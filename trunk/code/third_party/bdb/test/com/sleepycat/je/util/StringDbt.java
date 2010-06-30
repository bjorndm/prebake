/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: StringDbt.java,v 1.20 2010/01/04 15:51:08 cwl Exp $
 */

package com.sleepycat.je.util;

import com.sleepycat.je.DatabaseEntry;

public class StringDbt extends DatabaseEntry {
    public StringDbt() {
    }

    public StringDbt(String value) {
        setString(value);
    }

    public StringDbt(byte[] value) {
        setData(value);
    }

    public void setString(String value) {
        byte[] data = value.getBytes();
        setData(data);
    }

    public String getString() {
        return new String(getData(), 0, getSize());
    }

    public String toString() {
        return getString();
    }
}
