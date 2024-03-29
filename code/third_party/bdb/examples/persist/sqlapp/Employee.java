/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: Employee.java,v 1.6 2010/01/04 15:50:35 cwl Exp $
 */

package persist.sqlapp;

import com.sleepycat.persist.model.Entity;
import com.sleepycat.persist.model.PrimaryKey;
import com.sleepycat.persist.model.SecondaryKey;
import static com.sleepycat.persist.model.DeleteAction.NULLIFY;
import static com.sleepycat.persist.model.Relationship.MANY_TO_ONE;

/**
 * The Employee entity class.
 * 
 * @author chao
 */
@Entity
class Employee {

    @PrimaryKey
    int employeeId;

    /* Many Employees may have the same name. */
    @SecondaryKey(relate = MANY_TO_ONE)
    String employeeName;

    /* Many Employees may have the same salary. */
    @SecondaryKey(relate = MANY_TO_ONE)
    float salary;

    @SecondaryKey(relate = MANY_TO_ONE, relatedEntity=Employee.class,
                                        onRelatedEntityDelete=NULLIFY)
    Integer managerId; // Use "Integer" to allow null values.

    @SecondaryKey(relate = MANY_TO_ONE, relatedEntity=Department.class,
                                        onRelatedEntityDelete=NULLIFY)
    Integer departmentId;

    String address;

    public Employee(int employeeId,
                    String employeeName,
                    float salary,
                    Integer managerId,
                    int departmentId,
                    String address) {
        
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.salary = salary;
        this.managerId = managerId;
        this.departmentId = departmentId;
        this.address = address;
    }

    @SuppressWarnings("unused")
    private Employee() {} // Needed for deserialization.

    public String getAddress() {
        return address;
    }

    public int getDepartmentId() {
        return departmentId;
    }

    public int getEmployeeId() {
        return employeeId;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public Integer getManagerId() {
        return managerId;
    }

    public float getSalary() {
        return salary;
    }

    @Override
    public String toString() {
        return this.employeeId + ", " +
               this.employeeName + ", " +
               this.salary + ", " +
               this.managerId + ", " +
               this.departmentId + ", " +
               this.address;
    }
}
