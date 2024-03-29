/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2000-2010 Oracle.  All rights reserved.
 *
 * $Id: EnvConfigObserver.java,v 1.13 2010/01/04 15:50:40 cwl Exp $
 */

package com.sleepycat.je.dbi;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.EnvironmentMutableConfig;

/**
 * Implemented by observers of mutable config changes.
 */
public interface EnvConfigObserver {

    /**
     * Notifies the observer that one or more mutable properties have been
     * changed.
     */
    void envConfigUpdate(DbConfigManager configMgr,
                         EnvironmentMutableConfig newConfig)
        throws DatabaseException;
}
