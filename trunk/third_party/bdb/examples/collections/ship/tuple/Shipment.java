/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: Shipment.java,v 1.15 2010/01/04 15:50:34 cwl Exp $
 */

package collections.ship.tuple;

/**
 * A Shipment represents the combined key/data pair for a shipment entity.
 *
 * <p> In this sample, Shipment is created from the stored key/data entry
 * using a SerialSerialBinding.  See {@link SampleViews.ShipmentBinding} for
 * details.  Since this class is not used directly for data storage, it does
 * not need to be Serializable. </p>
 *
 * @author Mark Hayes
 */
public class Shipment {

    private String partNumber;
    private String supplierNumber;
    private int quantity;

    public Shipment(String partNumber, String supplierNumber, int quantity) {

        this.partNumber = partNumber;
        this.supplierNumber = supplierNumber;
        this.quantity = quantity;
    }

    public final String getPartNumber() {

        return partNumber;
    }

    public final String getSupplierNumber() {

        return supplierNumber;
    }

    public final int getQuantity() {

        return quantity;
    }

    public String toString() {

        return "[Shipment: part=" + partNumber +
                " supplier=" + supplierNumber +
                " quantity=" + quantity + ']';
    }
}
