/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: DbTruncateLog.java,v 1.3 2010/01/04 15:50:52 cwl Exp $
 */

package com.sleepycat.je.util;

import java.io.File;
import java.io.IOException;

import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.utilint.CmdUtil;

/**
 * DbTruncateLog is a utility that allows the user to truncate the JE log at a
 * specified file and offset. Generally used in replication systems for
 * handling com.sleepycat.je.rep.RollbackProhibitedException. Must be used with
 * caution. See RollbackProhibitedException for the appropriate truncation 
 * parameters.
 */
public class DbTruncateLog {

    private long truncateFileNum = -1;
    private long truncateOffset = -1;
    private File envHome;

    /**
     * Usage:
     * <pre>
     *  -h environmentDirectory
     *  -f file number. If hex, prefix with "0x"
     *  -o file offset byte. If hex, prefix with "0x"
     * </pre>
     * For example, to truncate a log to file 0xa, offset 0x1223:
     * <pre>
     * DbTruncateLog -h &lt;environmentDir&gt; -f 0xa -o 0x1223
     * </pre>
     */
    public static void main(String[] argv) {
        try {
            DbTruncateLog truncator = new DbTruncateLog();
            truncator.parseArgs(argv);
            truncator.truncateLog();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(e.getMessage());
            usage();
            System.exit(1);
        }
    }

    public DbTruncateLog() {
    }
     
    private void parseArgs(String[] argv) {
        int whichArg = 0;
        boolean seenFile = false;
        boolean seenOffset = false;

        while (whichArg < argv.length) {
            String nextArg = argv[whichArg];

            if (nextArg.equals("-h")) {
                whichArg++;
                envHome = new File(CmdUtil.getArg(argv, whichArg));
            } else if (nextArg.equals("-f")) {
                whichArg++;
                truncateFileNum =
                    CmdUtil.readLongNumber(CmdUtil.getArg(argv, whichArg));
                seenFile = true;
            } else if (nextArg.equals("-o")) {
                whichArg++;
                truncateOffset =
                    CmdUtil.readLongNumber(CmdUtil.getArg(argv, whichArg));
                seenOffset = true;
            } else {
                throw new IllegalArgumentException
                    (nextArg + " is not a supported option.");
            }
            whichArg++;
        }

        if (envHome == null) 
            usage();
            System.exit(1);

        if ((!seenFile) || (!seenOffset)) {
            usage();
            System.exit(1);
        }
    }

    private void truncateLog() 
        throws IOException {
        
        truncateLog(envHome, truncateFileNum, truncateOffset);
    }

    /**
     * Truncate the JE log to the given file and offset.
     */
    public void truncateLog(File envHome, 
                            long truncateFileNum, 
                            long truncateOffset) 
        throws IOException {

        /* Make a read/write environment */
        EnvironmentImpl envImpl =
            CmdUtil.makeUtilityEnvironment(envHome, false);
        
        /* Go through the file manager to get the JE file. Truncate. */
        envImpl.getFileManager().truncateLog(truncateFileNum, truncateOffset);

        envImpl.close();
    }

    private static void usage() {
        System.out.println("Usage: " +
                           CmdUtil.getJavaCommand(DbTruncateLog.class));
        System.out.println("                 -h <environment home>");
        System.out.println("                 -f <file number, in hex>");
        System.out.println("                 -o <offset, in hex>");
        System.out.println("Log file is truncated at position starting at" +
                           " and inclusive of the offset.");
    }
}
