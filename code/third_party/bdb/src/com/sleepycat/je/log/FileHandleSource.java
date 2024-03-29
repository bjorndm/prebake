/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: FileHandleSource.java,v 1.22 2010/01/04 15:50:43 cwl Exp $
 */

package com.sleepycat.je.log;

import com.sleepycat.je.DatabaseException;

/**
 * FileHandleSource is a file source built on top of a cached file handle.
 */
class FileHandleSource extends FileSource {

    private FileHandle fileHandle;

    FileHandleSource(FileHandle fileHandle,
                     int readBufferSize,
                     FileManager fileManager) {
        super(fileHandle.getFile(), readBufferSize, fileManager,
              fileHandle.getFileNum(), fileHandle.getLogVersion());
        this.fileHandle = fileHandle;
    }

    /**
     * @see LogSource#release
     */
    @Override
    public void release()
        throws DatabaseException {

        fileHandle.release();
    }

    public int getLogVersion() {
        return fileHandle.getLogVersion();
    }
}
