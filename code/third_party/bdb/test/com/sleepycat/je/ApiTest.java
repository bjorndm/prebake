/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: ApiTest.java,v 1.25 2010/01/04 15:50:58 cwl Exp $
 */

package com.sleepycat.je;

import junit.framework.TestCase;

/**
 * Test parameter handling for api methods.
 */
public class ApiTest extends TestCase {

    public void testBasic() {
        try {
            new Environment(null, null);
            fail("Should get exception");
        } catch (IllegalArgumentException e) {
            // expected exception
        } catch (Exception e) {
            fail("Shouldn't get other exception");
        }
    }
}
