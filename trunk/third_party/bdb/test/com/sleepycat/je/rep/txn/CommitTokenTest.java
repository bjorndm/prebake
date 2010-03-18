package com.sleepycat.je.rep.txn;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.UUID;

import junit.framework.TestCase;

import com.sleepycat.je.CommitToken;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.rep.ReplicatedEnvironment;
import com.sleepycat.je.rep.utilint.RepTestUtils;
import com.sleepycat.je.rep.utilint.RepTestUtils.RepEnvInfo;
import com.sleepycat.je.util.TestUtils;

public class CommitTokenTest extends TestCase {

    private final File envRoot;

    public CommitTokenTest() {
        envRoot = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    @Override
    protected void setUp() 
        throws Exception {

        super.setUp();
        RepTestUtils.removeRepEnvironments(envRoot);
    }

    @Override
    protected void tearDown() 
        throws Exception {

        super.tearDown();
        try {
            RepTestUtils.removeRepEnvironments(envRoot);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void testBasic() 
        throws IOException, ClassNotFoundException {

        UUID repenvUUID = UUID.randomUUID();

        CommitToken t1 = new CommitToken(repenvUUID, 1);
        CommitToken t2 = new CommitToken(repenvUUID, 2);
        CommitToken t3 = new CommitToken(repenvUUID, 3);

        assertTrue((t1.compareTo(t2) < 0) && (t2.compareTo(t1) > 0));
        assertTrue((t2.compareTo(t3) < 0) && (t3.compareTo(t2) > 0));
        assertTrue((t1.compareTo(t3) < 0) && (t3.compareTo(t1) > 0));

        assertEquals(t1, new CommitToken(repenvUUID, 1));
        assertEquals(0, t1.compareTo(new CommitToken(repenvUUID, 1)));

        try {
            t1.compareTo(new CommitToken(UUID.randomUUID(), 1));
            fail("Expected exception");
        } catch (IllegalArgumentException ie) {
            // expected
        }

        /* test serialization/de-serialization. */
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(t1);
        ByteArrayInputStream bais =
            new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        CommitToken t11 = (CommitToken)ois.readObject();

        assertEquals(t1, t11);
    }

    /**
     * Make sure that we only return a commit token when we've done real work.
     */
    public void testCommitTokenFailures() 
        throws IOException {

        RepEnvInfo[] repEnvInfo = RepTestUtils.setupEnvInfos(envRoot, 1);
        ReplicatedEnvironment master = RepTestUtils.joinGroup(repEnvInfo);

        /* It's illegal to get a commit token before it has closed. */
        Transaction txn = master.beginTransaction(null, null);
        try {
            txn.getCommitToken();
            fail("Should have gotten IllegalStateException");
        } catch (IllegalStateException expected) {
            /* expected outcome. */
        }

        /* 
         * Now abort and try again. Simce this transaction has done no writing
         * the commit token should be null.
         */
        txn.abort();
        CommitToken token = txn.getCommitToken();
        assertTrue(token == null);

        /* 
         * A committed txn that has done no writing should also return a null
         * commit token.
         */
        txn = master.beginTransaction(null, null);
        txn.commit();
        token = txn.getCommitToken();
        assertTrue(token == null);

        /* 
         * A committed txn that has done a write should return a non-null
         * token.
         */
        txn = master.beginTransaction(null, null);
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);
        Database db = master.openDatabase(txn, "foo", dbConfig);
        db.close();
        txn.commit();
        token = txn.getCommitToken();
        assertTrue(token != null);

        RepTestUtils.shutdownRepEnvs(repEnvInfo);
    }
}
