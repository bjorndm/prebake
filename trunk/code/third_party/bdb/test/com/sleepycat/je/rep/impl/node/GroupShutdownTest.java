package com.sleepycat.je.rep.impl.node;

import java.util.concurrent.TimeUnit;

import com.sleepycat.je.CommitToken;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.rep.GroupShutdownException;
import com.sleepycat.je.rep.NoConsistencyRequiredPolicy;
import com.sleepycat.je.rep.ReplicatedEnvironment;
import com.sleepycat.je.rep.impl.RepTestBase;
import com.sleepycat.je.rep.utilint.RepTestUtils.RepEnvInfo;

public class GroupShutdownTest extends RepTestBase {

    public void testShutdownExceptions() {
        createGroup();
        ReplicatedEnvironment mrep = repEnvInfo[0].getEnv();

        try {
            repEnvInfo[1].getEnv().shutdownGroup(10000, TimeUnit.MILLISECONDS);
            fail("expected exception");
        } catch (IllegalStateException e) {
            /* OK, shutdownGroup on Replica. */
        }

        ReplicatedEnvironment mrep2 =
            new ReplicatedEnvironment(repEnvInfo[0].getEnvHome(),
                                      repEnvInfo[0].getRepConfig(),
                                      repEnvInfo[0].getEnvConfig());

        try {
            mrep.shutdownGroup(10000, TimeUnit.MILLISECONDS);
            fail("expected exception");
        } catch (IllegalStateException e) {
            /* OK, multiple master handles. */
            mrep2.close();
        }
        mrep.shutdownGroup(10000, TimeUnit.MILLISECONDS);
        for (int i=1; i < repEnvInfo.length; i++) {
            repEnvInfo[i].closeEnv();
        }
    }


    public void testShutdownTimeout()
        throws InterruptedException {

        new ShutdownSupport() {
            @Override
            void checkException(@SuppressWarnings("unused")
                                GroupShutdownException e){}
        }.shutdownBasic(500, 1);
    }

    public void testShutdownBasic()
        throws InterruptedException {

        new ShutdownSupport() {
            @Override
            void checkException(GroupShutdownException e) {
                assertTrue(ct.getVLSN() <=
                        e.getShutdownVLSN().getSequence());
            }
        }.shutdownBasic(10000, 0);
    }

    public void testShutdownImmediate()
        throws InterruptedException {

        new ShutdownSupport() {
            @Override
            void checkException(@SuppressWarnings("unused")
                                GroupShutdownException e){}
        }.shutdownBasic(0, 0);
    }

    abstract class ShutdownSupport {
        CommitToken ct;

        abstract void checkException(GroupShutdownException e);

        public void shutdownBasic(long timeoutMs,
                                  int testDelayMs)
            throws InterruptedException {

            /* Avoid ILEs, workaround for #17522 */
            for (RepEnvInfo ri : repEnvInfo) {
                ri.getEnvConfig().
                setConfigParam(EnvironmentConfig.ENV_RUN_CLEANER, "false");
            }
            createGroup();
            ReplicatedEnvironment mrep = repEnvInfo[0].getEnv();
            leaveGroupAllButMaster();

            ct = populateDB(mrep, TEST_DB_NAME, 1000);
            repEnvInfo[0].getRepNode().feederManager().
                setTestDelayMs(testDelayMs);
            restartReplicasNoWait();

            mrep.shutdownGroup(timeoutMs, TimeUnit.MILLISECONDS);

            for (int i=1; i < repEnvInfo.length; i++) {
                RepEnvInfo repi = repEnvInfo[i];
                final int retries = 5;
                for (int j=0; j < retries; j++) {
                    try {
                        /* Provoke exception */
                        repi.getEnv().getState();
                        if ((j+1) == retries) {
                            fail("expected exception");
                        }
                        /* Give the replica time to react */
                        Thread.sleep(1000); /* a second between retries */
                    } catch (GroupShutdownException e) {
                        checkException(e);
                        break;
                    }
                }
                /* Close the handle. */
                repi.closeEnv();
            }
        }
    }

    /**
     * Start up replicas for existing master, but don't wait for any
     * consistency to be reached.
     */
    private void restartReplicasNoWait() {
        for (int i=1; i < repEnvInfo.length; i++) {
            RepEnvInfo ri = repEnvInfo[i];
            ri.openEnv(new NoConsistencyRequiredPolicy());
        }
    }
}
