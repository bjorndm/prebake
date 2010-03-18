/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: RepTestData.java,v 1.4 2010/01/04 15:51:12 cwl Exp $
 */

import static com.sleepycat.persist.model.Relationship.MANY_TO_ONE;
import static com.sleepycat.persist.model.Relationship.ONE_TO_ONE;

import com.sleepycat.persist.EntityStore;
import com.sleepycat.persist.PrimaryIndex;
import com.sleepycat.persist.model.Entity;
import com.sleepycat.persist.model.PrimaryKey;
import com.sleepycat.persist.model.SecondaryKey;

@Entity
class RepTestData {
    @SuppressWarnings("unused")
    @PrimaryKey(sequence="KEY")
    private int key;

    @SuppressWarnings("unused")
    @SecondaryKey(relate=ONE_TO_ONE)
    private int data;

    @SuppressWarnings("unused")
    @SecondaryKey(relate=MANY_TO_ONE)
    private String name;

    public void setKey(int key) {
        this.key = key;
    }

    public void setData(int data) {
        this.data = data;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getKey() {
        return key;
    }

    public int getData() {
        return data;
    }

    public String getName() {
        return name;
    }

    public boolean logicEquals(RepTestData object, int distance) {
        if (object == null) {
            return false;
        }

        if (key == (object.getKey() + distance) &&
            data == (object.getData() + distance) &&
            name.equals(object.getName())) {
            return true;
        }
        
        return false;
    }

    public String toString() {
        return "Instance: key = " + key + ", data = " + data +
               ", name = " + name;
    }

    /* Insert dbSize records to the specified EntityStore. */
    public static void insertData(EntityStore dbStore, int dbSize) 
        throws Exception {

        PrimaryIndex<Integer, RepTestData> primaryIndex =
            dbStore.getPrimaryIndex(Integer.class, RepTestData.class);

        for (int i = 1; i <= dbSize; i++) {
            RepTestData data = new RepTestData();
            data.setData(i);
            data.setName("test");
            primaryIndex.put(data);
        }
        dbStore.close();
    }
}
