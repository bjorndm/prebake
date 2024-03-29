/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2000-2010 Oracle.  All rights reserved.
 *
 * $Id: RuntimeExceptionWrapper.java,v 1.24 2010/01/04 15:50:57 cwl Exp $
 */

package com.sleepycat.util;

/**
 * A RuntimeException that can contain nested exceptions.
 *
 * @author Mark Hayes
 */
public class RuntimeExceptionWrapper extends RuntimeException
    implements ExceptionWrapper {

    /**
     * Wraps the given exception if it is not a {@code RuntimeException}.
     *
     * @param e any exception.
     *
     * @return {@code e} if it is a {@code RuntimeException}, otherwise a
     * {@code RuntimeExceptionWrapper} for {@code e}.
     */
    public static RuntimeException wrapIfNeeded(Throwable e) {
        if (e instanceof RuntimeException) {
            return (RuntimeException) e;
        }
        return new RuntimeExceptionWrapper(e);
    }

    private static final long serialVersionUID = 1106961350L;

    public RuntimeExceptionWrapper(Throwable e) {

        super(e);
    }

    /**
     * @deprecated replaced by {@link #getCause}.
     */
    public Throwable getDetail() {

        return getCause();
    }
}
