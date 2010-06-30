/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: LoggerUtilsTest.java,v 1.5 2010/01/04 15:51:10 cwl Exp $
 */

package com.sleepycat.je.utilint;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import junit.framework.TestCase;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.EnvironmentMutableConfig;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.junit.JUnitProcessThread;
import com.sleepycat.je.util.TestUtils;

/**
 * A unit test for testing JE logging programatically. 
 */
public class LoggerUtilsTest extends TestCase {

    private final File envHome;
    private static final String loggerPrefix = "com.sleepycat.je.";
    /* Logging configure properties file name. */
    private static final String fileName = "logging.properties";
    /* Logging settings in the properties file. */
    private static final String consoleLevel =
        "com.sleepycat.je.util.ConsoleHandler.level=INFO";
    private static final String fileLevel =
        "com.sleepycat.je.util.FileHandler.level=WARNING";

    public LoggerUtilsTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    @Override
    public void setUp() {
        TestUtils.removeLogFiles("Setup", envHome, false);
        /* Remove existing je.info files. */
        removeFiles("je.info");
    }

    @Override
    public void tearDown() {
        TestUtils.removeLogFiles("TearDown", envHome, false);
        /* Remove existing je.info files. */
        removeFiles("je.info");
    }

    /* Remove those je.info files. */
    private void removeFiles(String name) {
        File[] files = envHome.listFiles();
        for (File file : files) {
            if (file.getName().contains(name)) {
                assertTrue(file.delete());
            }
        }
    }

    /* Test whether JE logger's level can be set programatically. */
    public void testLoggerLevelSet()
        throws Exception {

        /* 
         * Set the parent's level to OFF, so all logging messages shouldn't be 
         * written to je.info files. 
         */
        Logger parent = Logger.getLogger("com.sleepycat.je");
        parent.setLevel(Level.OFF);

        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setAllowCreate(true);
        envConfig.setConfigParam(EnvironmentConfig.FILE_LOGGING_LEVEL, "ALL");
        Environment env = new Environment(envHome, envConfig);

        /* Init a messages list for use. */
        ArrayList<String> messages = new ArrayList<String>();
        messages.add("Hello, Linda!");
        messages.add("Hello, Sam!");
        messages.add("Hello, Charlie!");
        messages.add("Hello, Mark!");
        messages.add("Hello, Tao!");
        messages.add("Hello, Eric!");

        /* Check the logger level before reset. */
        checkLoggerLevel();

        /* Log these messages. */
        logMsg(DbInternal.getEnvironmentImpl(env), messages);

        /* Check there should be nothing in je.info files. */
        ArrayList<String> readMsgs = readFromInfoFile();
        assertTrue(readMsgs.size() == 0);

        /* 
         * Reset the parent level to ALL, so that all logging messages should 
         * be logged. 
         */
        parent.setLevel(Level.ALL);

        /* Log these messages. */
        logMsg(DbInternal.getEnvironmentImpl(env), messages);

        /* Check each string in messages are in the je.info.0 file. */
        readMsgs = readFromInfoFile();

        /*
         * Since setting JE logger's level to ALL, so any JE logging messages
         * may be written to je.info files, so the actual messages in je.info
         * should be equal to or larger than the size we directly log.
         */
        assertTrue(readMsgs.size() >= messages.size());
        for (int i = 0; i < messages.size(); i++) {
            boolean contained = false;
            for (int j = 0; j < readMsgs.size(); j++) {
                if (readMsgs.get(j).contains(messages.get(i))) {
                    contained = true;
                    break;
                }
            }
            assertTrue(contained);
        }

        /* Check to see that all JE loggers' level are not changed. */
        checkLoggerLevel();

        env.close();
    }

    /* Check the level for all JE loggers. */
    private void checkLoggerLevel() {
        Enumeration<String> loggerNames =
            LogManager.getLogManager().getLoggerNames();
        while (loggerNames.hasMoreElements()) {
            String loggerName = loggerNames.nextElement();
            if (loggerName.startsWith(loggerPrefix)) {
                Logger logger = Logger.getLogger(loggerName);
                assertEquals(null, logger.getLevel());
            }
        }
    }

    /* Log some messages. */
    private void logMsg(EnvironmentImpl envImpl, ArrayList<String> messages) {
        Logger envLogger = envImpl.getLogger();
        for (String message : messages) {
            LoggerUtils.info(envLogger, envImpl, message);
        }
    }

    /* Read the contents in the je.info files. */
    private ArrayList<String> readFromInfoFile() 
        throws Exception {

        /* Get the file for je.info.0. */
        File[] files = envHome.listFiles();
        File infoFile = null;
        for (File file : files) {
            if (("je.info.0").equals(file.getName())) {
                infoFile = file;
                break;
            }
        }

        /* Make sure the file exists. */
        assertTrue(infoFile != null);

        /* Read the messages from the file. */
        ArrayList<String> messages = new ArrayList<String>();
        BufferedReader in = new BufferedReader(new FileReader(infoFile));
        String message = new String();
        while ((message = in.readLine()) != null) {
            messages.add(message);
        }
        in.close();

        return messages;
    }

    /* Test the FileHandler and ConsoleHandler level setting. */
    public void testParamsSetting()
        throws Exception {

        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setAllowCreate(true);
        Environment env = new Environment(envHome, envConfig);

        /* Check the init handlers' level setting. */
        EnvironmentImpl envImpl = DbInternal.getEnvironmentImpl(env);
        Level consoleHandlerLevel = envImpl.getConsoleHandler().getLevel();
        Level fileHandlerLevel = envImpl.getFileHandler().getLevel();
        assertEquals(consoleHandlerLevel, Level.OFF);
        assertEquals(fileHandlerLevel, Level.INFO);

        env.close();
        
        /* Reopen the Environment with params setting. */
        envConfig.setConfigParam(EnvironmentConfig.CONSOLE_LOGGING_LEVEL, 
                                 "WARNING");
        envConfig.setConfigParam(EnvironmentConfig.FILE_LOGGING_LEVEL, 
                                 "SEVERE");
        env = new Environment(envHome, envConfig);
        envImpl = DbInternal.getEnvironmentImpl(env);
        Level newConsoleHandlerLevel = envImpl.getConsoleHandler().getLevel();
        Level newFileHandlerLevel = envImpl.getFileHandler().getLevel();
        /* Check that the new level are the same as param setting. */
        assertEquals(newConsoleHandlerLevel, Level.WARNING);
        assertEquals(newFileHandlerLevel, Level.SEVERE);

        /* Make sure the lavel are different before and after param setting. */
        assertFalse(consoleHandlerLevel.toString() == 
                    newConsoleHandlerLevel.toString());
        assertFalse(fileHandlerLevel.toString() == 
                    newFileHandlerLevel.toString());

        env.close();
    }

    /* 
     * Test whether the configurations inside the properties file are set 
     * correctly in JE Environment. 
     */
    public void testPropertiesSetting() 
        throws Exception {

        invokeProcess(false);
    }

    /**
     *  Start the a process and check the exited value. 
     *
     *  @param bothSetting presents whether the logging configuration and JE
     *                     params are set on a same Environment.
     */
    private void invokeProcess(boolean bothSetting) 
        throws Exception {

        /* Create a property file and write configurations into the file. */
        String propertiesFile = createPropertiesFile();

        /* 
         * If bothSetting is true, which means we need to set JE params, so 
         * obviously we need to add another two arguments.
         */
        String[] envCommand = bothSetting ? new String[8] : new String[6];
        envCommand[0] = "-Djava.util.logging.config.file=" + propertiesFile;
        envCommand[1] = "com.sleepycat.je.utilint.LoggerUtilsTest$" +
                        "PropertiesSettingProcess";
        envCommand[2] = envHome.getAbsolutePath();
        envCommand[3] = "INFO";
        envCommand[4] = "WARNING";
        envCommand[5] = bothSetting ? "true" : "false";
        /* JE Param setting. */
        if (bothSetting) {
            envCommand[6] = "WARNING";
            envCommand[7] = "SEVERE";
        }

        /* Start a process. */
        JUnitProcessThread thread =
            new JUnitProcessThread("PropertiesSettingProcess", envCommand);
        thread.start();

        try {
            thread.finishTest();
        } catch (Throwable t) {
            System.err.println(t.toString());
        }

        /* We expect the process exited normally. */
        assertEquals(thread.getExitVal(), 0);

        /* Remove the created property file. */
        removeFiles(fileName);
    }

    /* Create a properties file for use. */
    private String createPropertiesFile() 
        throws Exception {

        String name = envHome.getAbsolutePath() + 
                      System.getProperty("file.separator") + fileName;
        File file = new File(name);
        PrintWriter out = 
            new PrintWriter(new BufferedWriter(new FileWriter(file)));
        out.println(consoleLevel);
        out.println(fileLevel);
        out.close();

        return name;
    }

    /*
     * Test that JE ConsoleHandler and FileHandler get the correct level when
     * their levels are set both by the properties file and JE params.
     *
     * We want JE params would override the levels set by properties file.
     */
    public void testPropertiesAndParamSetting()
        throws Exception {

        invokeProcess(true);
    }

    /*
     * Test the handler's level param are mutable configuartions.
     */
    public void testMutableConfig()
        throws Exception {

        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setAllowCreate(true);
        envConfig.setConfigParam(EnvironmentConfig.CONSOLE_LOGGING_LEVEL,
                                 "WARNING");
        envConfig.setConfigParam(EnvironmentConfig.FILE_LOGGING_LEVEL,
                                 "SEVERE");
        Environment env = new Environment(envHome, envConfig);

        /* Check the init handlers' level setting. */
        EnvironmentImpl envImpl = DbInternal.getEnvironmentImpl(env);
        Level consoleHandlerLevel = envImpl.getConsoleHandler().getLevel();
        Level fileHandlerLevel = envImpl.getFileHandler().getLevel();
        assertEquals(consoleHandlerLevel, Level.WARNING);
        assertEquals(fileHandlerLevel, Level.SEVERE);

        /* Change the handler param setting for an open Environment. */
        EnvironmentMutableConfig mutableConfig = env.getMutableConfig();
        mutableConfig.setConfigParam(EnvironmentConfig.CONSOLE_LOGGING_LEVEL,
                                     "SEVERE");
        mutableConfig.setConfigParam(EnvironmentConfig.FILE_LOGGING_LEVEL,
                                     "WARNING");
        env.setMutableConfig(mutableConfig);

        /* Check the handler's level has changed. */
        Level newConsoleHandlerLevel = envImpl.getConsoleHandler().getLevel();
        Level newFileHandlerLevel = envImpl.getFileHandler().getLevel();
        assertEquals(newConsoleHandlerLevel, Level.SEVERE);
        assertEquals(newFileHandlerLevel, Level.WARNING);
        assertTrue(newConsoleHandlerLevel != consoleHandlerLevel);
        assertTrue(newFileHandlerLevel != fileHandlerLevel);

        /* Check levels again. */
        mutableConfig = env.getMutableConfig();
        env.setMutableConfig(mutableConfig);
        consoleHandlerLevel = envImpl.getConsoleHandler().getLevel();
        fileHandlerLevel = envImpl.getFileHandler().getLevel();
        assertEquals(consoleHandlerLevel, Level.SEVERE);
        assertEquals(fileHandlerLevel, Level.WARNING);
        assertTrue(newConsoleHandlerLevel == consoleHandlerLevel);
        assertTrue(newFileHandlerLevel == fileHandlerLevel);

        env.close();
    }

    /* 
     * A process for staring a JE Environment with propreties file or 
     * configured JE params. 
     */
    static class PropertiesSettingProcess {
        private final File envHome;
        /* Handler levels set through properties configuration file. */
        private final Level propertyConsole;
        private final Level propertyFile;
        /* Handler levels set through JE params. */
        private final Level paramConsole;
        private final Level paramFile;
        /* Present whehter property file and params set both. */
        private final boolean bothSetting;
        private Environment env;

        public PropertiesSettingProcess(File envHome, 
                                        Level propertyConsole, 
                                        Level propertyFile,
                                        boolean bothSetting,
                                        Level paramConsole,
                                        Level paramFile) {
            this.envHome = envHome;
            this.propertyConsole = propertyConsole;
            this.propertyFile = propertyFile;
            this.bothSetting = bothSetting;
            this.paramConsole = paramConsole;
            this.paramFile = paramFile;
        }

        /* Open a JE Environment. */
        public void openEnv() {
            try {
                EnvironmentConfig envConfig = new EnvironmentConfig();
                envConfig.setAllowCreate(true);

                /* If bothSetting is true, set the JE params. */
                if (bothSetting) {
                    envConfig.setConfigParam
                        (EnvironmentConfig.CONSOLE_LOGGING_LEVEL, 
                         paramConsole.toString());
                    envConfig.setConfigParam
                        (EnvironmentConfig.FILE_LOGGING_LEVEL,
                         paramFile.toString());
                }

                env = new Environment(envHome, envConfig);
            } catch (DatabaseException e) {
                e.printStackTrace();
                System.exit(1);
            }
        }

        /* Check the configured levels. */
        public void check() {
            if (bothSetting) {
                doCheck(paramConsole, paramFile);
            } else {
                doCheck(propertyConsole, propertyFile);
            }
        }

        private void doCheck(Level consoleLevel, Level fileLevel) {
            try {
                EnvironmentImpl envImpl = DbInternal.getEnvironmentImpl(env);
                assertTrue
                    (envImpl.getConsoleHandler().getLevel() == consoleLevel);
                assertTrue(envImpl.getFileHandler().getLevel() == fileLevel);
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(2);
            } finally {
                env.close();
            }
        }

        public static void main(String args[]) {
            PropertiesSettingProcess process = null;
            try {
                Level paramConsole = null;
                Level paramFile = null;
                if (args.length == 6) {
                    paramConsole = Level.parse(args[4]);
                    paramFile = Level.parse(args[5]);
                }

                process = new PropertiesSettingProcess(new File(args[0]), 
                                                       Level.parse(args[1]), 
                                                       Level.parse(args[2]),
                                                       new Boolean(args[3]),
                                                       paramConsole,
                                                       paramFile);
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(3);
            }
            process.openEnv();
            process.check();
        }
    }
}
