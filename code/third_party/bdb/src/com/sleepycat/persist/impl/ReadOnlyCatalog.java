/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: ReadOnlyCatalog.java,v 1.19 2010/01/18 15:27:04 cwl Exp $
 */

package com.sleepycat.persist.impl;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import com.sleepycat.compat.DbCompat;
import com.sleepycat.persist.raw.RawObject;
import com.sleepycat.je.utilint.IdentityHashMap;

/**
 * Read-only catalog operations used when initializing new formats.  This
 * catalog is used temprarily when the main catalog has not been updated yet,
 * but the new formats need to do catalog lookups.
 *
 * @see PersistCatalog#addNewFormat
 *
 * @author Mark Hayes
 */
class ReadOnlyCatalog implements Catalog {

    private List<Format> formatList;
    private Map<String, Format> formatMap;

    ReadOnlyCatalog(List<Format> formatList, Map<String, Format> formatMap) {
        this.formatList = formatList;
        this.formatMap = formatMap;
    }

    public int getInitVersion(Format format, boolean forReader) {
        return Catalog.CURRENT_VERSION;
    }

    public Format getFormat(int formatId) {
        try {
            Format format = formatList.get(formatId);
            if (format == null) {
                throw DbCompat.unexpectedState
                    ("Format does not exist: " + formatId);
            }
            return format;
        } catch (NoSuchElementException e) {
            throw DbCompat.unexpectedState
                ("Format does not exist: " + formatId);
        }
    }

    public Format getFormat(Class cls, boolean checkEntitySubclassIndexes) {
        Format format = formatMap.get(cls.getName());
        if (format == null) {
            throw new IllegalArgumentException
                ("Class is not persistent: " + cls.getName());
        }
        return format;
    }

    public Format getFormat(String className) {
        return formatMap.get(className);
    }

    public Format createFormat(String clsName,
                               Map<String, Format> newFormats) {
        throw DbCompat.unexpectedState();
    }

    public Format createFormat(Class type, Map<String, Format> newFormats) {
        throw DbCompat.unexpectedState();
    }

    public boolean isRawAccess() {
        return false;
    }

    public Object convertRawObject(RawObject o, IdentityHashMap converted) {
        throw DbCompat.unexpectedState();
    }
}
