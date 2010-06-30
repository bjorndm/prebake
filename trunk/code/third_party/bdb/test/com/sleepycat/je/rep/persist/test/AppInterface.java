/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: AppInterface.java,v 1.3 2010/01/04 15:51:06 cwl Exp $
 */

package com.sleepycat.je.rep.persist.test;

import com.sleepycat.je.rep.ReplicatedEnvironment;
import com.sleepycat.persist.EntityStore;

public interface AppInterface {
    public void setVersion(final int label);
    public void open(final ReplicatedEnvironment env);
    public void close();
    public void writeData(final int key);
    public void writeDataA(final int key);
    public void writeDataB(final int key);
    public void writeDataC(final int key);
    public void writeData2(final int key);
    public void readData(final int key);
    public void readDataA(final int key);
    public void readDataB(final int key);
    public void readDataC(final int key);
    public void readData2(final int key);
    public void adopt(AppInterface other);
    public int getVersion();
    public ReplicatedEnvironment getEnv();
    public EntityStore getStore();
}
