/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 1997-2010 Oracle.  All rights reserved.
 *
 * $Id: Quote.java,v 1.6 2010/01/05 18:17:12 sam Exp $
 */

package je.rep.quote;

import java.io.Serializable;

import com.sleepycat.persist.model.Entity;
import com.sleepycat.persist.model.PrimaryKey;

@Entity
class Quote implements Serializable{
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    @PrimaryKey
    String stockSymbol;

    float lastTrade;

    Quote(String symbol, float price) {
        this.stockSymbol = symbol;
        this.lastTrade = price;
    }

    @SuppressWarnings("unused")
    private Quote() {}  // Needed for DPL deserialization
}
