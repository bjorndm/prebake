/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: Department.java,v 1.8 2010/01/04 15:50:35 cwl Exp $
 */

package persist.sqlapp;

import com.sleepycat.persist.model.Entity;
import com.sleepycat.persist.model.PrimaryKey;
import com.sleepycat.persist.model.SecondaryKey;
import static com.sleepycat.persist.model.Relationship.ONE_TO_ONE;

/**
 * The Department entity class.
 * 
 * @author chao
 */
@Entity
class Department {

    @PrimaryKey
    int departmentId;

    @SecondaryKey(relate = ONE_TO_ONE)
    String departmentName;

    String location;

    public Department(int departmentId,
                      String departmentName,
                      String location) {

        this.departmentId = departmentId;
        this.departmentName = departmentName;
        this.location = location;
    }

        @SuppressWarnings("unused")
    private Department() {} // Needed for deserialization.

    public int getDepartmentId() {
        return departmentId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public String getLocation() {
        return location;
    }

    @Override
    public String toString() {
        return this.departmentId + ", " +
               this.departmentName + ", " +
               this.location;
    }
}
