/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: ExceptionListener.java,v 1.11 2010/01/04 15:50:36 cwl Exp $
 */

package com.sleepycat.je;

/**
 * A callback to notify the application program when an exception occurs in a
 * JE Daemon thread.
 */
public interface ExceptionListener {

    /**
     * This method is called if an exception is seen in a JE Daemon thread.
     *
     * @param event the ExceptionEvent representing the exception that was
     * thrown.
     */        
    void exceptionThrown(ExceptionEvent event);
}
