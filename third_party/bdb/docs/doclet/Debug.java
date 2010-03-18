/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle. All rights reserved.
 *
 * $Id: Debug.java,v 1.7 2010/01/04 15:50:33 cwl Exp $
 */

import java.io.PrintStream;

final class Debug {
    /**
     * Set this value to true to enable debugging.
     */
    public static final boolean DEBUG = true;

    /**
     * Set this value to control where debug messages appear.
     */
    public static PrintStream pstrmError = System.out;

    public static void println(Object objMessage) {
        if (DEBUG) {
            pstrmError.println(objMessage);
        }
    }

    private static void _printAssert() {
        println("Assertion failed at :");
    
        (new Throwable()).printStackTrace();
    }

    public static void _assert(boolean fExp) {
        if (DEBUG) {
            if (!fExp) {
                _printAssert();
            }
        }
    }

    public static void _assert(boolean fExp, String szMessage) {
        if (DEBUG) {
            if (!fExp) {
                println(szMessage);
                _printAssert();
            }
        }
    }

    public static void printStackTrace(Throwable t) {
        t.printStackTrace(pstrmError);
    }

    public static void printException(Exception e) {
        pstrmError.println(e);
        printStackTrace(e);
    }
}
