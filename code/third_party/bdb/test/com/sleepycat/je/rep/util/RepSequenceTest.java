/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2010 Oracle.  All rights reserved.
 *
 * $Id: RepSequenceTest.java,v 1.3 2010/01/04 15:51:06 cwl Exp $
 */

package com.sleepycat.je.rep.util;

import static com.sleepycat.je.rep.impl.RepParams.DEFAULT_PORT;
import static com.sleepycat.persist.model.Relationship.MANY_TO_ONE;

import java.io.File;

import junit.framework.TestCase;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.rep.InsufficientLogException;
import com.sleepycat.je.rep.NetworkRestore;
import com.sleepycat.je.rep.NetworkRestoreConfig;
import com.sleepycat.je.rep.ReplicaWriteException;
import com.sleepycat.je.rep.utilint.RepTestUtils;
import com.sleepycat.je.rep.utilint.RepTestUtils.RepEnvInfo;
import com.sleepycat.je.util.TestUtils;
import com.sleepycat.je.utilint.VLSN;
import com.sleepycat.persist.EntityStore;
import com.sleepycat.persist.PrimaryIndex;
import com.sleepycat.persist.StoreConfig;
import com.sleepycat.persist.model.Entity;
import com.sleepycat.persist.model.PrimaryKey;
import com.sleepycat.persist.model.SecondaryKey;

public class RepSequenceTest extends TestCase {
    private final boolean verbose = Boolean.getBoolean("verbose");
    private final File envRoot;
    private final String DB_NAME = "test";
    private final int dbSize = 100;
    private EnvironmentConfig envConfig;
    private RepEnvInfo[] repEnvInfo;

    public RepSequenceTest() {
        envRoot = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    @Override
    public void setUp()
        throws Exception {

        RepTestUtils.removeRepEnvironments(envRoot);
        repEnvInfo = RepTestUtils.setupEnvInfos(envRoot, 2);

        envConfig = new EnvironmentConfig();
        envConfig.setAllowCreate(true);
        envConfig.setReadOnly(false);
        envConfig.setTransactional(true);
    }

    @Override
    public void tearDown() {
        RepTestUtils.shutdownRepEnvs(repEnvInfo);
        RepTestUtils.removeRepEnvironments(envRoot);
    }

    public void testDPLSequenceWithConversion()
        throws Exception {

        syncupGroup();

        doDPLOperations(true);
    }

    private void doDPLOperations(boolean converted) 
        throws Exception {

        assertTrue(repEnvInfo[0].getEnv().getState().isMaster());
        assertFalse(repEnvInfo[1].getEnv().getState().isMaster());

        EntityStore store = openStore(repEnvInfo[0].getEnv(), DB_NAME);

        /* Do some CRUD operations on master.*/
        int beginId = converted ? dbSize : 0;
        insertData(1 + beginId, beginId + dbSize, store);
        deleteData(51 + beginId, 100 + beginId, store);
        readData(1, 50 + beginId, store);
        store.close();

        /* Open a new database and insert records to the database on master. */
        store = openStore(repEnvInfo[0].getEnv(), "testDB");
        insertData(1, dbSize, store);
        store.close();

        /* Do read operations on the replica. */
        store = openStore(repEnvInfo[1].getEnv(), DB_NAME);
        readData(1, 50 + beginId, store);
        store.close();

        try {
            /* Open a non-existed database on the replica. */
            store = openStore(repEnvInfo[1].getEnv(), "myDB");
        } catch (ReplicaWriteException e) {
            /* Expect to see this exception. */
        } finally {
            if (store != null) {
                store.close();
            }
        }

        checkEquality(repEnvInfo);        
    }

    public void testDPLSequenceWithoutConversion()
        throws Exception {

        RepTestUtils.joinGroup(repEnvInfo);

        doDPLOperations(false);
    }

    /* Sync up the whole group. */
    private void syncupGroup()
        throws Exception {

        openStandaloneEnvAndInsertData();

        DbEnableReplication converter = new DbEnableReplication
            (repEnvInfo[0].getEnvHome(), RepTestUtils.TEST_REP_GROUP_NAME,
             "Node 1", RepTestUtils.TEST_HOST + ":" + 
             DEFAULT_PORT.getDefault());

        converter.convert();

        repEnvInfo[0].openEnv();

        doNetworkRestore(repEnvInfo[1]);
    }

    /*
     * Do a NetworkRestore to copy the latest log files from master to
     * replica.
     */
    private void doNetworkRestore(RepEnvInfo repNode)
        throws Exception {

        try {
            repNode.openEnv();
        } catch (InsufficientLogException e) {
            NetworkRestore restore = new NetworkRestore();
            NetworkRestoreConfig config = new NetworkRestoreConfig();
            config.setRetainLogFiles(false);
            restore.execute(e, config);
        } finally {
            if (repNode.getEnv() != null) {
                repNode.closeEnv();
            }
        }

        try {
            repNode.openEnv();
        } catch (Exception e) {
            e.printStackTrace();
            fail("Shouldn't throw out exceptions here.");
        }
    }

    /* Check the equality of replicas in the same group. */
    private void checkEquality(RepEnvInfo[] repInfoArray)
        throws Exception {

        VLSN vlsn = RepTestUtils.syncGroupToLastCommit(repInfoArray,
                                                       repInfoArray.length);
        RepTestUtils.checkNodeEquality(vlsn, verbose, repInfoArray);
    }

    private EntityStore openStore(Environment env, String dbName) 
        throws DatabaseException {

        StoreConfig config = new StoreConfig();
        config.setAllowCreate(true);
        config.setTransactional(true);

        return new EntityStore(env, dbName, config);
    }

    /* Create a standalone environment, insert some records and close it. */
    private void openStandaloneEnvAndInsertData()
        throws Exception {

        Environment env =
            new Environment(repEnvInfo[0].getEnvHome(), envConfig);
        EntityStore store = openStore(env, DB_NAME);
        insertData(1, dbSize, store);
        store.close();

        env.close();
    }

    /* Do insert operations on the specified database. */
    private void insertData(int beginId, int endId, EntityStore store)
        throws Exception {

        PrimaryIndex<Integer, RepTestData> primaryIndex =
            store.getPrimaryIndex(Integer.class, RepTestData.class);
        for (int i = beginId; i <= endId; i++) {
            RepTestData data = new RepTestData();
            data.setName("herococo" + new Integer(i).toString());
            primaryIndex.put(data);
        }
    }

    /* Do delete operations on the specified database. */
    private void deleteData(int beginId, int endId, EntityStore store)
        throws Exception {

        PrimaryIndex<Integer, RepTestData> primaryIndex =
            store.getPrimaryIndex(Integer.class, RepTestData.class);
        for (int i = beginId; i <= endId; i++) {
            primaryIndex.delete(null, i);
        }
    }

    /* Do read operations on the specified database. */
    private void readData(int beginId, int endId, EntityStore store)
        throws Exception {

        PrimaryIndex<Integer, RepTestData> primaryIndex =
            store.getPrimaryIndex(Integer.class, RepTestData.class);
        for (int i = beginId; i <= endId; i++) {
            
            /* 
             * Do reads to exercise the replica read route, even though the
             * value is not used.
             */
            @SuppressWarnings("unused")
            RepTestData data = primaryIndex.get(i);
        }
    }

    @Entity
    static class RepTestData {
        @PrimaryKey(sequence="KEY")
        private int key;

        @SecondaryKey(relate=MANY_TO_ONE)
        private String name;

        public void setKey(int key) {
            this.key = key;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getKey() {
            return key;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return "RepTestData: key = " + key + ", name = " + name;
        }
    }
}
