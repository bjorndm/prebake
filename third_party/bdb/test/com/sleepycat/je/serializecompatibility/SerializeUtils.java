/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2005-2010 Oracle.  All rights reserved.
 *
 * $Id: SerializeUtils.java,v 1.16 2010/01/04 15:51:07 cwl Exp $
 */

package com.sleepycat.je.serializecompatibility;

import static com.sleepycat.je.txn.LockStatDefinition.LOCK_READ_LOCKS;

import java.util.HashSet;
import java.util.Set;

import com.sleepycat.je.BtreeStats;
import com.sleepycat.je.DatabaseNotFoundException;
import com.sleepycat.je.DeadlockException;
import com.sleepycat.je.EnvironmentLockedException;
import com.sleepycat.je.EnvironmentStats;
import com.sleepycat.je.LockNotAvailableException;
import com.sleepycat.je.LockNotGrantedException;
import com.sleepycat.je.LockStats;
import com.sleepycat.je.LockTimeoutException;
import com.sleepycat.je.PreloadStats;
import com.sleepycat.je.PreloadStatus;
import com.sleepycat.je.TransactionStats;
import com.sleepycat.je.TransactionTimeoutException;
import com.sleepycat.je.tree.CursorsExistException;
import com.sleepycat.je.tree.NodeNotEmptyException;
import com.sleepycat.je.utilint.IntStat;
import com.sleepycat.je.utilint.InternalException;
import com.sleepycat.je.utilint.StatGroup;
import com.sleepycat.persist.evolve.DeletedClassException;
import com.sleepycat.persist.evolve.IncompatibleClassException;
import com.sleepycat.util.IOExceptionWrapper;
import com.sleepycat.util.RuntimeExceptionWrapper;
import com.sleepycat.util.keyrange.KeyRangeException;

abstract class SerializeInfo {
    abstract String getName();
    abstract Object getInstance();
}

/* 
 * A utility class that lists all JE classes that support serialization. Any 
 * class that may be serialized by the application and implements a 
 * serialVersionUID should be included here.
 */
class SerializeUtils {

    public static Set<SerializeInfo> getSerializedSet() {
        final StatGroup fakeStats = new StatGroup("SerializeUtils", 
                                                  "For testing");
        new IntStat(fakeStats, LOCK_READ_LOCKS);

        Set<SerializeInfo> infoSet = new HashSet<SerializeInfo>();

        /* com.sleepycat.je package. */

        infoSet.add(new SerializeInfo() {
                @Override
                String getName() {
                    return "com.sleepycat.je.TransactionStats";
                }

                @Override
                Object getInstance() {
                    return new TransactionStats(fakeStats);
                }
            });

        infoSet.add(new SerializeInfo() {
                @Override
                String getName() {
                    return "com.sleepycat.je.TransactionStats$Active";
                }

                @Override
                Object getInstance() {
                    return new TransactionStats.Active("test", 0, 0);
                }
            });

        infoSet.add(new SerializeInfo() {
                @Override
                String getName() {
                    return "com.sleepycat.je.BtreeStats";
                }

                @Override
                Object getInstance() {
                    return new BtreeStats();
                }
            });

        infoSet.add(new SerializeInfo() {
                @Override
                String getName() {
                    return "com.sleepycat.je.DatabaseNotFoundException";
                }

                @Override
                Object getInstance() {
                    return new DatabaseNotFoundException("");
                }
            });

        infoSet.add(new SerializeInfo() {
                @Override
                String getName() {
                    return "com.sleepycat.je.DeadlockException";
                }

                @Override
                Object getInstance() {
                    return new DeadlockException(null, "");
                }
            });

        infoSet.add(new SerializeInfo() {
                @Override
                String getName() {
                    return "com.sleepycat.je.EnvironmentStats";
                }

                @Override
                Object getInstance() {
                    return new EnvironmentStats();
                }
            });

        infoSet.add(new SerializeInfo() {
                @Override
                String getName() {
                    return "com.sleepycat.je.EnvironmentLockedException";
                }

                @Override
                Object getInstance() {
                    return new EnvironmentLockedException(null, "");
                }
            });

        infoSet.add(new SerializeInfo() {
                @Override
                String getName() {
                    return "com.sleepycat.je.LockNotAvailableException";
                }

                @Override
                Object getInstance() {
                    return new LockNotAvailableException(null, "");
                }
            });

        infoSet.add(new SerializeInfo() {
                @Override
                String getName() {
                    return "com.sleepycat.je.LockNotGrantedException";
                }

                @Override
                Object getInstance() {
                    return new LockNotGrantedException(null, "");
                }
            });

        infoSet.add(new SerializeInfo() {
                @Override
                String getName() {
                    return "com.sleepycat.je.LockStats";
                }

                @Override
                Object getInstance() {
                    return new LockStats(fakeStats, fakeStats, fakeStats);
                }
            });

        infoSet.add(new SerializeInfo() {
                @Override
                String getName() {
                    return "com.sleepycat.je.LockTimeoutException";
                }

                @Override
                Object getInstance() {
                    return new LockTimeoutException(null, "");
                }
            });

        infoSet.add(new SerializeInfo() {
                @Override
                String getName() {
                    return "com.sleepycat.je.PreloadStats";
                }

                @Override
                Object getInstance() {
                    return new PreloadStats();
                }
            });

        infoSet.add(new SerializeInfo() {
                @Override
                String getName() {
                    return "com.sleepycat.je.PreloadStatus";
                }

                @Override
                Object getInstance() {
                    return new PreloadStatus("test");
                }
            });

        infoSet.add(new SerializeInfo() {
                @Override
                String getName() {
                    return "com.sleepycat.je.TransactionTimeoutException";
                }

                @Override
                Object getInstance() {
                    return new TransactionTimeoutException(null, "");
                }
            });

        /* com.sleepycat.je.jca.ra package.
         * And because these classes need j2ee.jar to compile, but we currently
         * don't have it in CVS, so ignore them now.
         */
        /******
        infoSet.add(new SerializeInfo() {
                @Override
                String getName() {
                    return "com.sleepycat.je.jca.ra.JEConnectionFactoryImpl";
                }

                @Override
                Object getInstance() {
                    return new JEConnectionFactoryImpl(null, null);
                }
            });

        infoSet.add(new SerializeInfo() {
                @Override
                String getName() {
                    return "com.sleepycat.je.jca.ra.JEException";
                }

                @Override
                Object getInstance() {
                    return new JEException("test");
                }
            });

        infoSet.add(new SerializeInfo() {
                @Override
                String getName() {
                    return
                        "com.sleepycat.je.jca.ra.JEManagedConnectionFactory";
                }

                @Override
                Object getInstance() {
                    return new JEManagedConnectionFactory();
                }
            });
        ******/

        /* com.sleepycat.je.tree package. */
        infoSet.add(new SerializeInfo() {
                @Override
                String getName() {
                    return "com.sleepycat.je.tree.CursorsExistException";
                }

                @Override
                Object getInstance() {
                    return new CursorsExistException();
                }
            });

        infoSet.add(new SerializeInfo() {
                @Override
                String getName() {
                    return "com.sleepycat.je.tree.NodeNotEmptyException";
                }

                @Override
                Object getInstance() {
                    return new NodeNotEmptyException();
                }
            });

        /* com.sleepycat.je.utilint package. */
        infoSet.add(new SerializeInfo() {
                @Override
                String getName() {
                    return "com.sleepycat.je.utilint.InternalException";
                }

                @Override
                Object getInstance() {
                    return new InternalException();
                }
            });

        /* com.sleepycat.persist.evolve package. */
        infoSet.add(new SerializeInfo() {
                @Override
                String getName() {
                    return
                        "com.sleepycat.persist.evolve.DeletedClassException";
                }

                @Override
                Object getInstance() {
                    return new DeletedClassException("test");
                }
            });

        infoSet.add(new SerializeInfo() {
                @Override
                String getName() {
                    return "com.sleepycat.persist.evolve." + 
                        "IncompatibleClassException";
                }

                @Override
                Object getInstance() {
                    return new IncompatibleClassException("test");
                }
            });

        /* com.sleepycat.util packge. */
        infoSet.add(new SerializeInfo() {
                @Override
                String getName() {
                    return "com.sleepycat.util.IOExceptionWrapper";
                }

                @Override
                Object getInstance() {
                    return new IOExceptionWrapper(new Throwable());
                }
            });

        infoSet.add(new SerializeInfo() {
                @Override
                String getName() {
                    return "com.sleepycat.util.RuntimeExceptionWrapper";
                }

                @Override
                Object getInstance() {
                    return new RuntimeExceptionWrapper(new Throwable());
                }
            });

        /* com.sleepycat.util.keyrange package. */
        infoSet.add(new SerializeInfo() {
                @Override
                String getName() {
                    return "com.sleepycat.util.keyrange.KeyRangeException";
                }

                @Override
                Object getInstance() {
                    return new KeyRangeException("test");
                }
            });
  
        return infoSet;
    }
}
