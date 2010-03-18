/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: DbTestProxy.java,v 1.17 2010/01/04 15:50:58 cwl Exp $
 */

package com.sleepycat.je;

import com.sleepycat.je.dbi.CursorImpl;

/**
 * DbTestProxy is for internal use only. It serves to shelter methods that must
 * be public to be used by JE unit tests that but are not part of the
 * public api available to applications.
 */
public class DbTestProxy {
    /**
     * Proxy to Cursor.getCursorImpl
     */
    public static CursorImpl dbcGetCursorImpl(Cursor dbc) {
        return dbc.getCursorImpl();
    }
}
