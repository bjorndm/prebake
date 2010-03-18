/*
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: EnvironmentNotFoundException.java,v 1.6 2010/01/04 15:50:36 cwl Exp $
 */

package com.sleepycat.je;

import com.sleepycat.je.dbi.EnvironmentFailureReason;
import com.sleepycat.je.dbi.EnvironmentImpl;

/**
 * Thrown by the {@link Environment} constructor when {code EnvironmentConfig
 * AllowCreate} property is false (environment creation is not permitted), but
 * there are no log files in the environment directory.
 *
 * @since 4.0
 */
public class EnvironmentNotFoundException extends EnvironmentFailureException {

    private static final long serialVersionUID = 1;

    /** 
     * For internal use only.
     * @hidden 
     */
    public EnvironmentNotFoundException(EnvironmentImpl envImpl,
                                        String message) {
        super(envImpl, EnvironmentFailureReason.ENV_NOT_FOUND, message);
    }

    /** 
     * For internal use only.
     * @hidden 
     */
    private EnvironmentNotFoundException(String message,
                                         EnvironmentNotFoundException cause) {
        super(message, cause);
    }

    /** 
     * For internal use only.
     * @hidden 
     */
    @Override
    public EnvironmentFailureException wrapSelf(String msg) {
        return new EnvironmentNotFoundException(msg, this);
    }
}
