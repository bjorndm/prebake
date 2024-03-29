/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: PartData.java,v 1.19 2010/01/04 15:50:34 cwl Exp $
 */

package collections.ship.index;

import java.io.Serializable;

/**
 * A PartData serves as the data in the key/data pair for a part entity.
 *
 * <p> In this sample, PartData is used both as the storage data for the data
 * as well as the object binding to the data.  Because it is used directly as
 * storage data using serial format, it must be Serializable. </p>
 *
 * @author Mark Hayes
 */
public class PartData implements Serializable {

    private String name;
    private String color;
    private Weight weight;
    private String city;

    public PartData(String name, String color, Weight weight, String city) {

        this.name = name;
        this.color = color;
        this.weight = weight;
        this.city = city;
    }

    public final String getName() {

        return name;
    }

    public final String getColor() {

        return color;
    }

    public final Weight getWeight() {

        return weight;
    }

    public final String getCity() {

        return city;
    }

    public String toString() {

        return "[PartData: name=" + name +
            " color=" + color +
            " weight=" + weight +
            " city=" + city + ']';
    }
}
