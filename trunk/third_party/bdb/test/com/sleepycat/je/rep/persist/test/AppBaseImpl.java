/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: AppBaseImpl.java,v 1.2 2010/01/04 15:51:06 cwl Exp $
 */

package com.sleepycat.je.rep.persist.test;

import com.sleepycat.je.rep.ReplicatedEnvironment;
import com.sleepycat.persist.EntityStore;
import com.sleepycat.persist.StoreConfig;
import com.sleepycat.persist.model.AnnotationModel;
import com.sleepycat.persist.model.EntityModel;
import com.sleepycat.persist.evolve.Mutations;

public abstract class AppBaseImpl implements AppInterface {

    protected int version;
    protected ReplicatedEnvironment env;
    protected EntityStore store;

    public void setVersion(final int version) {
        this.version = version;
    }

    public void open(final ReplicatedEnvironment env) {
        this.env = env;
        StoreConfig config =
            new StoreConfig().setAllowCreate(true).setTransactional(true);
        Mutations mutations = new Mutations();
        EntityModel model = new AnnotationModel();
        setupConfig(mutations, model);
        config.setMutations(mutations);
        config.setModel(model);
        store = new EntityStore(env, "foo", config);
        init();
    }

    protected abstract void setupConfig(final Mutations mutations,
                                        final EntityModel model);

    protected abstract void init();

    public void close() {
        store.close();
    }

    public void adopt(AppInterface other) {
        version = other.getVersion();
        env = other.getEnv();
        store = other.getStore();
        init();
    }

    public ReplicatedEnvironment getEnv() {
        return env;
    }

    public EntityStore getStore() {
        return store;
    }

    public int getVersion() {
        return version;
    }
}
