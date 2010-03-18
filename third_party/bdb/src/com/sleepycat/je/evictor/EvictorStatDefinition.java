/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: EvictorStatDefinition.java,v 1.4 2010/01/04 15:50:40 cwl Exp $
 */

package com.sleepycat.je.evictor;

import com.sleepycat.je.utilint.StatDefinition;

/**
 * Per-stat Metadata for JE evictor statistics.
 */
public class EvictorStatDefinition {
    public static final String GROUP_NAME = "Cache";
    public static final String GROUP_DESC = 
        "Current size, allocations, and eviction activity.";

    public static final StatDefinition EVICTOR_EVICT_PASSES =
        new StatDefinition("nEvictPasses", 
                           "Number of passes made to the evictor.");

    public static final StatDefinition EVICTOR_NODES_SELECTED =
        new StatDefinition("nNodesSelected", 
                           "Accumulated number of nodes selected to evict.");

    public static final StatDefinition EVICTOR_NODES_SCANNED =
        new StatDefinition("nNodesScanned",
                           "Accumulated number of nodes scanned in order to " +
                           "select the eviction set."); 

    public static final StatDefinition EVICTOR_NODES_EVICTED =
        new StatDefinition("nNodesEvicted",
                           "Accumulated number of nodes evicted.");

    public static final StatDefinition EVICTOR_ROOT_NODES_EVICTED =
        new StatDefinition("nRootNodesEvicted", 
                           "Accumulated number of database root nodes " +
                           "evicted.");

    public static final StatDefinition EVICTOR_BINS_STRIPPED =
        new StatDefinition("nBINsStripped",
                           "Number of BINs stripped by the evictor.");

    public static final StatDefinition EVICTOR_REQUIRED_EVICT_BYTES =
        new StatDefinition("requiredEvictBytes",
                           "Number of bytes we need to evict in order to " +
                           "get under budget.");

    public static final StatDefinition EVICTOR_SHARED_CACHE_ENVS =
        new StatDefinition("nSharedCacheEnvironments",
                           "Number of Environments sharing the cache.");
}
