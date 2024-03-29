/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: EntityValueAdapter.java,v 1.11 2010/01/04 15:50:55 cwl Exp $
 */

package com.sleepycat.persist;

import com.sleepycat.bind.EntityBinding;
import com.sleepycat.je.DatabaseEntry;

/**
 * A ValueAdapter where the "value" is the entity.
 *
 * @author Mark Hayes
 */
class EntityValueAdapter<V> implements ValueAdapter<V> {

    private EntityBinding entityBinding;
    private boolean isSecondary;

    EntityValueAdapter(Class<V> entityClass,
                       EntityBinding entityBinding,
                       boolean isSecondary) {
        this.entityBinding = entityBinding;
        this.isSecondary = isSecondary;
    }

    public DatabaseEntry initKey() {
        return new DatabaseEntry();
    }

    public DatabaseEntry initPKey() {
        return isSecondary ? (new DatabaseEntry()) : null;
    }

    public DatabaseEntry initData() {
        return new DatabaseEntry();
    }

    public void clearEntries(DatabaseEntry key,
                             DatabaseEntry pkey,
                             DatabaseEntry data) {
        key.setData(null);
        if (isSecondary) {
            pkey.setData(null);
        }
        data.setData(null);
    }

    public V entryToValue(DatabaseEntry key,
                          DatabaseEntry pkey,
                          DatabaseEntry data) {
        return (V) entityBinding.entryToObject(isSecondary ? pkey : key, data);
    }

    public void valueToData(V value, DatabaseEntry data) {
        entityBinding.objectToData(value, data);
    }
}
