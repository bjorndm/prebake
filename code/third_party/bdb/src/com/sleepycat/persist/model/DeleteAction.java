/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: DeleteAction.java,v 1.13 2010/01/04 15:50:57 cwl Exp $
 */

package com.sleepycat.persist.model;


/**
 * Specifies the action to take when a related entity is deleted having a
 * primary key value that exists as a secondary key value for this entity.
 * This can be specified using a {@link SecondaryKey#onRelatedEntityDelete}
 * annotation.
 *
 * @author Mark Hayes
 */
public enum DeleteAction {

    /**
     * The default action, {@code ABORT}, means that a {@link
     * com.sleepycat.je.DeleteConstraintException} is thrown in order to abort
     * the current transaction.
     */
    ABORT,

    /**
     * If {@code CASCADE} is specified, then this entity will be deleted also,
     * which could in turn trigger further deletions, causing a cascading
     * effect.
     */
    CASCADE,

    /**
     * If {@code NULLIFY} is specified, then the secondary key in this entity
     * is set to null and this entity is updated.  For a secondary key field
     * that has an array or collection type, the array or collection element
     * will be removed by this action.  The secondary key field must have a
     * reference (not a primitive) type in order to specify this action.
     */
    NULLIFY;
}
