/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2005-2010 Oracle.  All rights reserved.
 *
 * $Id: SerializeReadObjectsTest.java,v 1.8 2010/01/04 15:51:07 cwl Exp $
 */

package com.sleepycat.je.serializecompatibility;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;

import junit.framework.TestCase;

import com.sleepycat.je.JEVersion;

/*
 * Test whether those serializable classes of prior versions can be read by
 * the latest one.
 *
 * This test is used in conjunction with SerializeWriteObjects, a main program
 * which is used to generate the serialized outputs of those serializable
 * classesfor in a version. When a new version is to be released,
 * run SerializedWriteObjects to generate serialized outputs, and then
 * add a test_x_y_z() method to this class.
 */
public class SerializeReadObjectsTest extends TestCase {

    /* Used to identify the two versions is compatible. */
    private boolean serializedSuccess = true;

    /* The directory where serialized files saved. */
    private File outputDir;

    /* The directory where outputDir saved. */
    private static final String parentDir =
        "test/com/sleepycat/je/serializecompatibility";

    /**
     * Test whether the latest version is compatible with 4.0.0.
     * @throws ClassNotFoundException when the test is enabled
     */
    public void test_4_0_0() throws ClassNotFoundException {
        //doTest(new JEVersion("4.0.0"));
    }

    /*
     * Read these serialized files and convert it.  If it's compatible, it
     * won't throw the InvalidClassException; if not, it would throw out the
     * exception, serializedSuccess is false.
     */
    public void doTest(JEVersion version)
        throws ClassNotFoundException, IOException {

        outputDir = new File(parentDir, version.getNumericVersionString());
        if (!outputDir.exists()) {
            System.out.println("No such directory, try it again");
            System.exit(1);
        }

        try {
            ObjectInputStream in;
            for (SerializeInfo s : SerializeUtils.getSerializedSet()) {

                /*
                 * Do the check when the latest version larger than the
                 * assigned version.
                 */
                if (JEVersion.CURRENT_VERSION.compareTo(version) >= 0) {
                    in = new ObjectInputStream
                        (new FileInputStream
                            (outputDir.getPath() +
                             System.getProperty("file.separator") +
                             s.getName() + ".out"));
                    /* Check that we can read the object successfully. */
                    in.readObject();
                    in.close();
                }
            }
        } catch (InvalidClassException e) {
            /* Reading serialized output failed.*/
            serializedSuccess = false;
        } catch (FileNotFoundException fnfe) {
            /* A class which doesn't exist in the former version. */
            serializedSuccess = false;
            System.out.println("The older version doesn't include this file.");
        }

        if (serializedSuccess) {
            System.out.println("Serialization is compatible");
        } else {
            System.out.println("Serialization is not compatible");
        }

        assertTrue(serializedSuccess);
    }
}
