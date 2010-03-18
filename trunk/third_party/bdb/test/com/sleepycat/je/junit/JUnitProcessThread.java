/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 */

package com.sleepycat.je.junit;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * [#16348] JE file handle leak when multi-process writing a same environment.
 *
 * Write this thread for creating multi-process test, you can generate 
 * a JUnitProcessThread, each thread would create a process for you, just need
 * to assign the command line parameters to the thread.
 */
public class JUnitProcessThread extends JUnitThread {
    private String cmdArrays[];

    /*
     * Variable record the return value of the process. If 0, it means the 
     * process finishes successfully, if not, the process fails.
     */
    private int exitVal = 0;

    /**
     * Pass the process name and command line to the constructor.
     */
    public JUnitProcessThread(String threadName, String parameters[]) {
        super(threadName);
        
        cmdArrays = new String[3 + parameters.length];
        cmdArrays[0] = System.getProperty("java.home") +
            System.getProperty("file.separator") + "bin" + 
            System.getProperty("file.separator") + "java" + 
            (System.getProperty("path.separator").equals(":") ? "" : "w.exe");
        cmdArrays[1] = "-cp";
        cmdArrays[2] = "." + System.getProperty("path.separator") + 
                       System.getProperty("java.class.path"); 
        
        for (int i = 0; i < parameters.length; i++) {
            cmdArrays[i + 3] = parameters[i];
        }
    }

    /** Generate a process for this thread.*/
    public void testBody() {
        Runtime runtime = Runtime.getRuntime();
        InputStream error = null;
        InputStream output = null;
        Process proc = null;

        try {
            proc = runtime.exec(cmdArrays);

            error = proc.getErrorStream();
            output = proc.getInputStream();

            Thread err = new Thread(new OutErrReader(error));
            Thread out = new Thread(new OutErrReader(output));

            err.start();
            out.start();

            exitVal = proc.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Return the return value of the created process to main thread. */
    public int getExitVal() {
        return exitVal;
    }

    /** 
     * A class prints out the output information of writing serialized files.
     */
    public static class OutErrReader implements Runnable {
        InputStream is;

        public OutErrReader(InputStream is) {
            this.is = is;
        }

        public void run() {
            try {
                BufferedReader in =
                    new BufferedReader(new InputStreamReader(is));
                String error = new String();
                String temp = new String();
                while((temp = in.readLine()) != null) {
                    error = error + temp + "\n";
                }
                is.close();
                if (error.length() != 0) {
                    throw new Exception(error);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
