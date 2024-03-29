package com.sleepycat.je.rep.impl.networkRestore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.zip.CheckedInputStream;
import java.util.zip.CheckedOutputStream;

import junit.framework.TestCase;

import com.sleepycat.je.log.LogUtils;
import com.sleepycat.je.rep.impl.networkRestore.Protocol.FileStart;
import com.sleepycat.je.rep.impl.node.NameIdPair;
import com.sleepycat.je.rep.util.TestChannel;
import com.sleepycat.je.rep.utilint.BinaryProtocol.Message;
import com.sleepycat.je.utilint.Adler32;
import com.sleepycat.je.utilint.VLSN;

public class ProtocolTest  extends TestCase {

    Protocol protocol;
    private Message[] messages;

    @Override
    protected void setUp() {
        protocol = new Protocol(new NameIdPair("n1", (short)1),
                                Protocol.VERSION,
                                null);

        messages = new Message[] {
                protocol.new FeederInfoReq(),
                protocol.new FeederInfoResp(1, new VLSN(100), new VLSN(200)),
                protocol.new FileListReq(),
                protocol.new FileListResp(new String[]{"f1","f2"}),
                protocol.new FileReq("f1"),
                protocol.new FileStart("f1",100, System.currentTimeMillis()),
                protocol.new FileEnd("f1", 100, System.currentTimeMillis(),
                                      new byte[100]),
                protocol.new FileInfoReq("f1", true),
                protocol.new FileInfoResp("f1", 100, System.currentTimeMillis(),
                                      new byte[100]),
                protocol.new Done(),
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

    public void testFileReqResp()
        throws IOException, Exception {

        ByteArrayOutputStream baos = new ByteArrayOutputStream(10000);
        WritableByteChannel oc = Channels.newChannel(baos);
        oc.write(protocol.new FileStart("f1", 100, System.currentTimeMillis()).
                 wireFormat().duplicate());

        Adler32 ochecksum = new Adler32();
        CheckedOutputStream cos = new CheckedOutputStream(baos, ochecksum);

        // Simulate a file payload.
        for (int i=0; i < 100; i++)  {
            cos.write(i);
        }
        ByteBuffer csum = ByteBuffer.allocate(8);
        LogUtils.writeLong(csum, ochecksum.getValue());
        baos.write(csum.array());

        byte[] o = baos.toByteArray();

        TestChannel ch =
            new TestChannel((ByteBuffer)ByteBuffer.allocate(o.length).
                            put(o).flip());

        FileStart m = (FileStart) protocol.read(ch);
        long length = m.getFileLength();
        Adler32 ichecksum = new Adler32();
        CheckedInputStream cis =
            new CheckedInputStream(Channels.newInputStream(ch), ichecksum);
        for (int i=0; i < length; i++) {
            assertEquals(i, cis.read());
        }

        csum = ByteBuffer.allocate(8);
        ch.read(csum);
        csum.flip();
        assertEquals(ochecksum.getValue(), LogUtils.readLong(csum));
        assertEquals(ochecksum.getValue(), ichecksum.getValue());
    }
}
