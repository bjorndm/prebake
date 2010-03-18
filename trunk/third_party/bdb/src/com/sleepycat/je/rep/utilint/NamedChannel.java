/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2010 Oracle.  All rights reserved.
 *
 * $Id: NamedChannel.java,v 1.3 2010/01/04 15:50:50 cwl Exp $
 */

package com.sleepycat.je.rep.utilint;

import java.nio.channels.SocketChannel;

import com.sleepycat.je.rep.impl.node.NameIdPair;

/**
 * Packages a SocketChannel and a NameIdPair together so that logging
 * messages can show the node name instead of the channel toString();
 */
public class NamedChannel {

    private NameIdPair nameIdPair;
    private final SocketChannel channel; 

    public NamedChannel(SocketChannel channel, NameIdPair nameIdPair) {
        this.channel = channel;
        this.nameIdPair = nameIdPair;
    }

    /* 
     * NameIdPair unknown at this time.
     */
    public NamedChannel(SocketChannel channel) {
        this.channel = channel;
        this.nameIdPair = NameIdPair.NULL;
    }

    public void setNameIdPair(NameIdPair nameIdPair) {
        this.nameIdPair = nameIdPair;
    }

    public NameIdPair getNameIdPair() {
        return nameIdPair;
    }

    public SocketChannel getChannel() {
        return channel;
    }

    @Override
    public String toString() {
        if (getNameIdPair() == null) {
            return getChannel().toString();
        } 

        return "(" + getNameIdPair() + ")" + getChannel();
    }
}

