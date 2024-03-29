/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2005-2010 Oracle.  All rights reserved.
 *
 * $Id: SerializeWriteObjects.java,v 1.6 2010/01/04 15:51:07 cwl Exp $
 */

package com.sleepycat.je.serializecompatibility;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

import com.sleepycat.je.JEVersion;

public class SerializeWriteObjects {
    private File outputDir;

    public SerializeWriteObjects(String dirName) {
        outputDir = new File(dirName);
    }

    /* Delete the existed directory for serialized outputs. */
    private void deleteExistDir(File fileDir) {
        if (!fileDir.exists())
            return;
        if (fileDir.isFile()) {
            fileDir.delete();
            return;
        }

        File[] files = fileDir.listFiles();
        for (int i = 0; i < files.length; i++)
            deleteExistDir(files[i]);
        
        fileDir.delete();
    }

    /* 
     * If the directory doesn't exist, create a new one;
     * Or delete it and make a fresh one.
     */
    private void createHome() {
        if (outputDir.exists()) {
            deleteExistDir(outputDir);
        }

        outputDir.mkdirs();
    }

    /*
     * Generate a directory of .out files representing the serialized versions
     * of all serializable classes for this JE version. The directory will be 
     * named with JE version number, and each file will be named 
     * <classname>.out. These files will be used by SerializedReadObjectsTest.
     */
    public void writeObjects()
        throws IOException {

        createHome();
        ObjectOutputStream out;
        for (SerializeInfo s : SerializeUtils.getSerializedSet()) {
            out = new ObjectOutputStream
                (new FileOutputStream
                 (outputDir.getPath() + System.getProperty("file.separator") +
                  s.getInstance().getClass().getName() + ".out"));
            out.writeObject(s.getInstance());
            out.close();
        }
    }

    /* 
     * When do the test, it will create a sub process to run this main program
     * to call the writeObjects() method to generate serialized outputs.
     */
    public static void main(String args[]) 
        throws IOException {

        String dirName = args[0] + System.getProperty("file.separator") + 
                         JEVersion.CURRENT_VERSION.toString();
        SerializeWriteObjects writeTest = new SerializeWriteObjects(dirName);
        writeTest.writeObjects();
    }
}
