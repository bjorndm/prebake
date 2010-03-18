/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2000-2010 Oracle.  All rights reserved.
 *
 * $Id: BaseIterator.java,v 1.10 2010/01/04 15:50:35 cwl Exp $
 */

package com.sleepycat.collections;

import java.util.ListIterator;

/**
 * Common interface for BlockIterator and StoredIterator.  This is an abstract
 * class rather than in interface to prevent exposing these methods in javadoc.
 */
abstract class BaseIterator<E> implements ListIterator<E> {

    abstract ListIterator<E> dup();

    abstract boolean isCurrentData(Object currentData);

    abstract boolean moveToIndex(int index);
}
