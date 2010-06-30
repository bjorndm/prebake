/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: CursorEdgeTest.java,v 1.6 2010/01/04 15:51:04 cwl Exp $
 */

package com.sleepycat.je.rep.dual;

public class CursorEdgeTest extends com.sleepycat.je.CursorEdgeTest {

    /* Database in this test case is set non-transactional. */
    @Override
    public void testGetPrevNoDupWithEmptyTree() {
    }
}
