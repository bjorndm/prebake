/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: VLPair.java,v 1.3 2010/01/04 15:51:07 cwl Exp $
 */

package com.sleepycat.je.rep.vlsn;

import com.sleepycat.je.utilint.DbLsn;
import com.sleepycat.je.utilint.VLSN;

/**
 * Just a struct for testing convenience.
 */
class VLPair {
    final VLSN vlsn;
    final long lsn;

    VLPair(VLSN vlsn, long lsn) {
        this.vlsn = vlsn;
        this.lsn = lsn;
    }

    VLPair(int vlsnSequence, long fileNumber, long offset) {
        this.vlsn = new VLSN(vlsnSequence);
        this.lsn = DbLsn.makeLsn(fileNumber, offset);
    }

    @Override
        public String toString() {
        return vlsn + "/" + DbLsn.getNoFormatString(lsn);
    }
}
