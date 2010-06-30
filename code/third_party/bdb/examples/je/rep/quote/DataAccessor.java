/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 1997-2010 Oracle.  All rights reserved.
 *
 * $Id: DataAccessor.java,v 1.4 2010/01/04 15:50:34 cwl Exp $
 */

package je.rep.quote;

import com.sleepycat.persist.EntityStore;
import com.sleepycat.persist.PrimaryIndex;

class DataAccessor {
    /* Quote Accessor */
    final PrimaryIndex<String, Quote> quoteById;

    DataAccessor(EntityStore store) {
        /* Primary index of the Employee database. */
        quoteById = store.getPrimaryIndex(String.class, Quote.class);
    }
}
