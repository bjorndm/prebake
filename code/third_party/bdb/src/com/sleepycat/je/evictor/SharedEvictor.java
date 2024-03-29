/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: SharedEvictor.java,v 1.18 2010/01/04 15:50:40 cwl Exp $
 */

package com.sleepycat.je.evictor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.EnvironmentFailureException;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.dbi.INList;
import com.sleepycat.je.tree.IN;
import com.sleepycat.je.utilint.StatGroup;

/**
 * The Evictor that operates on the INLists for multiple environments that
 * share a single cache.  Multiple iterators, once for each INList, are used to
 * implement getNextIN.  INs are returned from from each iterator in a
 * round-robin rotation, giving larger INLists proportionally more rotations.
 * This "mixes up" the INs from all INlists so that the LRU algorithm is
 * applied across all lists.
 */
public class SharedEvictor extends Evictor {

    /**
     * MIN_ROTATIONS is the number of rotations given to the smallest INList in
     * a single "round".  In each round we return a number of INs from each
     * INList that is proportional to the relative size of the INList.
     *
     * Imagine 2 INLists, one with 70 INs and the other with 100 INs.  If we
     * use simple integer division to create the ratio of their sizes, the
     * result is either 1-to-1 or 1-to-2.  To create a more accurate ratio we
     * multiply the size of the INLists by MIN_ROTATIONS to get the initial
     * Subject.remaining value.  But for each rotation we subtract from
     * remaining the size of the smallest INList, without multiplying.  When
     * the remaining value is less than zero, that INList is taken out of the
     * rotation.  In this example the ratio would be:
     *
     *   (70*10)/70 to (100*10)/70, or 10-to-14
     *
     * So in one complete round we would return 10 INs from the INList with 70
     * INs and 14 INs from the INList with 100 INs.
     */
    private static final int MIN_ROTATIONS = 10;

    /**
     * We re-initialize sizes after 1/INIT_SIZE_THRESHOLD of all INs have
     * changed, to account for changes to the relative sizes of the INLists.
     * We don't re-initialize sizes every time the INLists are changed because
     * it is relatively expensitve to call ConcurrentHashMap.size.  Sizes are
     * re-initialized:
     * - before the first time eviction
     * - after an environment is added or removed
     * - after 1/INIT_SIZE_THRESHOLD of all INs have been added or removed
     */
    private static final int INIT_SIZE_THRESHOLD = 10;

    /**
     * A Subject is an environment that is sharing the global cache, and
     * related info.
     */
    private static class Subject {
        EnvironmentImpl env;
        INList ins;
        Iterator<IN> iter;
        int size;
        int remaining;
    }

    private List<Subject> subjects;
    private int rotationIndex;
    private int specialEvictionIndex;
    private boolean needInitSizes;
    private int smallestSize;
    private int totalSize;
    private AtomicInteger changedINs;

    public SharedEvictor(EnvironmentImpl env, String name)
        throws DatabaseException {

        super(env, name);

        /* Set level of this logger OFF, so that it can't do any logging. */
        logger.setLevel(Level.OFF);

        subjects = new ArrayList<Subject>();
        changedINs = new AtomicInteger();
        needInitSizes = true;
    }

    @Override
    public StatGroup loadStats(StatsConfig config) {
        /* No synchronization on subjects is intentional here. */
        sharedCacheEnvs.set(subjects.size());

        return super.loadStats(config);
    }

    /**
     * Only supported by PrivateEvictor.
     */
    public void clearEnv() {
        throw EnvironmentFailureException.unexpectedState();
    }

    /**
     * After 1/INIT_SIZE_THRESHOLD of all INs have been changed, reinitialize
     * the sizes.
     */
    public void noteINListChange(int nINs) {
        if (changedINs.addAndGet(nINs) > totalSize / INIT_SIZE_THRESHOLD) {
            needInitSizes = true;
        }
    }

    /**
     * Synchronized so that the set of environments cannot be changed in the
     * middle of an eviction (which is also synchronized).
     */
    public synchronized void addEnvironment(EnvironmentImpl env) {
        int nSubjects = subjects.size();
        for (int i = 0; i < nSubjects; i += 1) {
            Subject subject = subjects.get(i);
            if (subject.env == env) {
                return;
            }
        }
        Subject subject = new Subject();
        subject.env = env;
        subject.ins = env.getInMemoryINs();
        subjects.add(subject);
        needInitSizes = true;
    }

    /**
     * Synchronized so that the set of environments cannot be changed in the
     * middle of an eviction (which is also synchronized).
     */
    public synchronized void removeEnvironment(EnvironmentImpl env) {
        int nSubjects = subjects.size();
        for (int i = 0; i < nSubjects; i += 1) {
            Subject subject = subjects.get(i);
            if (subject.env == env) {
                subjects.remove(i);
                needInitSizes = true;
                return;
            }
        }
    }

    /**
     * Returns true if the given environment is present in the set of subject
     * environments.  Used in assertions.
     */
    public boolean checkEnv(EnvironmentImpl env) {
        int nSubjects = subjects.size();
        for (int i = 0; i < nSubjects; i += 1) {
            Subject subject = subjects.get(i);
            if (env == subject.env) {
                return true;
            }
        }
        return false;
    }

    /**
     * Initializes the sizes if needed, and performs special eviction for one
     * environment in rotatation.
     */
    long startBatch()
        throws DatabaseException {

        /* Init sizes for the first eviction or after env add/remove. */
        if (needInitSizes) {
            initSizes();
        }
        /* Evict utilization tracking info without holding any latches. */
        int nSubjects = subjects.size();
        if (nSubjects > 0) {
            if (specialEvictionIndex >= nSubjects) {
                specialEvictionIndex = 0;
            }
            Subject subject = subjects.get(specialEvictionIndex);
            specialEvictionIndex += 1;
            return subject.env.specialEviction();
        } else {
            return 0;
        }
    }

    /**
     * Returns the total of all INList sizes, as of the last time sizes were
     * initialized.
     */
    int getMaxINsPerBatch() {
        return totalSize;
    }

    /**
     * Returns the next IN, wrapping if necessary.  Returns a number of INs
     * from each INList that is proportional to the sizes of the lists.  When a
     * round is complete (we have returned the correct ratio from all INLists
     * and all Subject.remaining fields are less than zero), start a new round
     * by reinitializing the Subject.remaining fields.
     */
    IN getNextIN() {
        int nSubjects = subjects.size();
        if (nSubjects == 0) {
            /* No environments are sharing the cache. */
            return null;
        }
        int nSubjectsExamined = 0;
        while (true) {
            if (rotationIndex >= nSubjects) {
                rotationIndex = 0;
            }
            Subject subject = subjects.get(rotationIndex);
            rotationIndex += 1;
            if (subject.remaining > 0 && isEvictionAllowed(subject)) {
                subject.remaining -= smallestSize;
                if (subject.iter == null || !subject.iter.hasNext()) {
                    subject.iter = subject.ins.iterator();
                }
                if (subject.iter.hasNext()) {
                    /* Found an IN to return. */
                    return subject.iter.next();
                } else {
                    /* This INList is empty. */
                    subject.remaining = -1;
                }
            }
            nSubjectsExamined += 1;
            if (nSubjectsExamined >= nSubjects) {
                /* All Subject.remaining fields are <= 0. */
                boolean foundAny = false;
                for (int i = 0; i < nSubjects; i += 1) {
                    Subject sub = subjects.get(i);
                    if (sub.size > 0) {
                        sub.remaining = sub.size * MIN_ROTATIONS;
                        if (isEvictionAllowed(sub)) {
                            foundAny = true;
                        }
                    }
                }
                if (!foundAny) {
                    /* All INLists are empty or not evictable. */
                    return null;
                }
                /* Start a new round. */
                nSubjectsExamined = 0;
            }
        }
    }

    private boolean isEvictionAllowed(Subject subject) {
        return subject.env.getMemoryBudget().isTreeUsageAboveMinimum();
    }

    /**
     * Sets up the Subject size and remaining fields, and resets the rotation
     * to the beginning.
     */
    private void initSizes() {
        totalSize = 0;
        smallestSize = Integer.MAX_VALUE;
        int nSubjects = subjects.size();
        for (int i = 0; i < nSubjects; i += 1) {
            Subject subject = subjects.get(i);
            int size = subject.ins.getSize();
            if (smallestSize > size) {
                smallestSize = size;
            }
            totalSize += size;
            subject.size = size;
            subject.remaining = size * MIN_ROTATIONS;
        }
        needInitSizes = false;
    }

    /* For unit testing only.  Supported only by PrivateEvictor. */
    Iterator<IN> getScanIterator() {
        throw EnvironmentFailureException.unexpectedState();
    }

    /* For unit testing only.  Supported only by PrivateEvictor. */
    void setScanIterator(Iterator<IN> iter) {
        throw EnvironmentFailureException.unexpectedState();
    }
}
