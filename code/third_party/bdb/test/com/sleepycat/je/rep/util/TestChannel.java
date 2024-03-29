/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2004-2010 Oracle.  All rights reserved.
 *
 * $Id: TestChannel.java,v 1.3 2010/01/04 15:51:06 cwl Exp $
 */
package com.sleepycat.je.rep.util;

import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

/**
 * This mimics the two part SocketChannel read done in real life when
 * assembling an incoming message.
 */
public class TestChannel implements ReadableByteChannel {

    ByteBuffer content;

    public TestChannel(ByteBuffer content) {
        this.content = content;
    }

    public int read(ByteBuffer fill) {
        int remaining = fill.remaining();
        for (int i = 0; i < remaining; i++) {
            fill.put(content.get());
        }

        return fill.limit();
    }

    public void close() {
        throw new UnsupportedOperationException();
    }

    public boolean isOpen() {
        throw new UnsupportedOperationException();
    }
}
