/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: PartKey.java,v 1.16 2010/01/04 15:50:33 cwl Exp $
 */

package collections.ship.entity;

import java.io.Serializable;

/**
 * A PartKey serves as the key in the key/data pair for a part entity.
 *
 * <p> In this sample, PartKey is used both as the storage entry for the key as
 * well as the object binding to the key.  Because it is used directly as
 * storage data using serial format, it must be Serializable. </p>
 *
 * @author Mark Hayes
 */
public class PartKey implements Serializable {

    private String number;

    public PartKey(String number) {

        this.number = number;
    }

    public final String getNumber() {

        return number;
    }

    public String toString() {

        return "[PartKey: number=" + number + ']';
    }
}
