/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2004-2010 Oracle.  All rights reserved.
 *
 * $Id: ProtocolTest.java,v 1.5 2010/01/04 15:51:06 cwl Exp $
 */

package com.sleepycat.je.rep.util.ldiff;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import junit.framework.TestCase;

import com.sleepycat.je.rep.impl.node.NameIdPair;
import com.sleepycat.je.rep.util.TestChannel;
import com.sleepycat.je.rep.utilint.BinaryProtocol.Message;
import com.sleepycat.je.utilint.VLSN;

public class ProtocolTest  extends TestCase {

    Protocol protocol;
    private Message[] messages;
    private Block testBlock;

    @Override
    protected void setUp() {
        protocol = new Protocol(new NameIdPair("n1", (short)1),
                                null);

        testBlock = new Block(5);
        byte[] beginKey = {0, 1, 2, 3};
        testBlock.setBeginKey(beginKey);
        byte[] beginData = {(byte)0xde, (byte)0xad, (byte)0xbe, (byte)0xef};
        testBlock.setBeginData(beginData);
        byte[] md5Hash = {(byte)0xdb, (byte)0xcd, (byte)0xdb, (byte)0xcd};
        testBlock.setMd5Hash(md5Hash);
        testBlock.setNumRecords(1 << 13);
        testBlock.setRollingChksum(123456789L);

        MismatchedRegion region = new MismatchedRegion();
        region.setLocalBeginKey(beginKey);
        region.setLocalBeginData(beginData);
        region.setLocalDiffSize(10);
        region.setRemoteBeginKey(beginKey);
        region.setRemoteBeginData(beginData);
        region.setRemoteDiffSize(10);

        Record record = new Record(beginKey, beginData, new VLSN(5));

        messages = new Message[] {
                protocol.new DbBlocks("test.db", 1 << 13),
                protocol.new DbMismatch("test.db does not exist"),
                protocol.new BlockListStart(),
                protocol.new BlockListEnd(),
                protocol.new BlockInfo(testBlock),
                protocol.new EnvDiff(),
                protocol.new EnvInfo(4),
                protocol.new RemoteDiffRequest(region),
                protocol.new RemoteRecord(record),
                protocol.new DiffAreaStart(),
                protocol.new DiffAreaEnd(),
                protocol.new Done(),
                protocol.new Error("An LDiff Error")
        };
    }

    public void testBasic()
        throws IOException {

        assertEquals(protocol.messageCount() -
                     protocol.getPredefinedMessageCount(),
                     messages.length);
        for (Message m : messages) {
            ByteBuffer testWireFormat = m.wireFormat().duplicate();
            Message newMessage =
                protocol.read(new TestChannel(testWireFormat));
            assertTrue(newMessage.getOp() + " " +
                       Arrays.toString(testWireFormat.array()) + "!=" +
                       Arrays.toString(newMessage.wireFormat().array()),
                       Arrays.equals(testWireFormat.array().clone(),
                                     newMessage.wireFormat().array().clone()));
        }
    }
}
