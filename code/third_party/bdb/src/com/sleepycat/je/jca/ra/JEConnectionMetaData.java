/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: JEConnectionMetaData.java,v 1.13 2010/01/04 15:50:42 cwl Exp $
 */

package com.sleepycat.je.jca.ra;

import javax.resource.spi.ManagedConnectionMetaData;

public class JEConnectionMetaData
    implements ManagedConnectionMetaData {

    public JEConnectionMetaData() {
    }

    public String getEISProductName() {
        return "Berkeley DB Java Edition JCA";
    }

    public String getEISProductVersion() {
        return "2.0";
    }

    public int getMaxConnections() {
        /* Make a je.* parameter? */
        return 100;
    }

    public String getUserName() {
            return null;
    }
}
