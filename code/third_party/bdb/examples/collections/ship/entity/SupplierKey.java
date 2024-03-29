/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: SupplierKey.java,v 1.16 2010/01/04 15:50:33 cwl Exp $
 */

package collections.ship.entity;

import java.io.Serializable;

/**
 * A SupplierKey serves as the key in the key/data pair for a supplier entity.
 *
 * <p> In this sample, SupplierKey is used both as the storage entry for the
 * key as well as the object binding to the key.  Because it is used directly
 * as storage data using serial format, it must be Serializable. </p>
 *
 * @author Mark Hayes
 */
public class SupplierKey implements Serializable {

    private String number;

    public SupplierKey(String number) {

        this.number = number;
    }

    public final String getNumber() {

        return number;
    }

    public String toString() {

        return "[SupplierKey: number=" + number + ']';
    }
}
