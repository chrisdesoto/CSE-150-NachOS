package nachos.network;

import nachos.machine.*;

/**
 * A mail message. Includes a packet header, a mail header, and the actual
 * payload.
 *
 * @see nachos.machine.Packet
 */
public class MailMessage {
    /**
     * Allocate a new mail message to be sent, using the specified parameters.
     *
     * @param dstLink  the destination link address.
     * @param dstPort  the destination port.
     * @param srcLink  the source link address.
     * @param srcPort  the source port.
     * @param contents the contents of the packet.
     */
    public MailMessage(int dstLink, int dstPort, int srcLink, int srcPort,
                       byte[] contents) throws MalformedPacketException {
        // make sure the paramters are valid
        if (dstPort < 0 || dstPort >= portLimit ||
                srcPort < 0 || srcPort >= portLimit ||
                contents.length > maxContentsLength)
            throw new MalformedPacketException();

        this.dstLink = dstLink;
        this.dstPort = (byte) dstPort;
        this.srcPort = (byte) srcPort;
        this.contents = contents;

        byte[] packetContents = new byte[headerLength + contents.length];

        packetContents[0] = (byte) dstPort;
        packetContents[1] = (byte) srcPort;

        System.arraycopy(contents, 0, packetContents, headerLength,
                contents.length);

        packet = new Packet(dstLink, srcLink, packetContents);
    }

    /**
     * Allocate a new mail message using the specified packet from the network.
     *
     * @param packet the packet containg the mail message.
     */
    public MailMessage(Packet packet) throws MalformedPacketException {
        this.packet = packet;

        // make sure we have a valid header
        if (packet.contents.length < headerLength ||
                packet.contents[0] < 0 || packet.contents[0] >= portLimit ||
                packet.contents[1] < 0 || packet.contents[1] >= portLimit)
            throw new MalformedPacketException();

        dstLink = -1;
        dstPort = packet.contents[0];
        srcPort = packet.contents[1];

        contents = new byte[packet.contents.length - headerLength];
        System.arraycopy(packet.contents, headerLength, contents, 0,
                contents.length);
    }

    /**
     * Return a string representation of the message headers.
     */
    public String toString() {
        return "from (" + packet.srcLink + ":" + srcPort +
                ") to (" + packet.dstLink + ":" + dstPort +
                "), " + contents.length + " bytes";
    }

    /**
     * This message, as a packet that can be sent through a network link.
     */
    public Packet packet;
    /**
     * The link identifying the destination machine.
     */
    public int dstLink;
    /**
     * The port used by this message on the destination machine.
     */
    public int dstPort;
    /**
     * The port used by this message on the source machine.
     */
    public int srcPort;
    /**
     * The contents of this message, excluding the mail message header.
     */
    public byte[] contents;

    /**
     * The number of bytes in a mail header. The header is formatted as
     * follows:
     * <p>
     * <table>
     * <tr><td>offset</td><td>size</td><td>value</td></tr>
     * <tr><td>0</td><td>1</td><td>destination port</td></tr>
     * <tr><td>1</td><td>1</td><td>source port</td></tr>
     * </table>
     */
    public static final int headerLength = 2;

    /**
     * Maximum payload (real data) that can be included in a single mesage.
     */
    public static final int maxContentsLength =
            Packet.maxContentsLength - headerLength;

    /**
     * The upper limit on mail ports. All ports fall between <tt>0</tt> and
     * <tt>portLimit - 1</tt>.
     */
    public static final int portLimit = 128;
    public static final int extraTCPHeaderLen = 6;
}


class TCPMessage extends MailMessage {
    public TCPMessage(int dstLink, int dstPort, int srcLink, int srcPort, byte[] data,
                      int seqNum, boolean SYN,
                      boolean ACK, boolean FIN, boolean STP) throws MalformedPacketException {
        super(dstLink, dstPort, srcLink, srcPort, data);
        short syn, ack, fin, stp;
        syn = ack = fin = stp = 0;
        if (SYN) {
            syn = 1;
        }
        if (ACK) {
            ack = 2;
        }
        if (STP) {
            stp = 4;
        }
        if (FIN) {
            fin = 8;
        }
        this.FIN = FIN;
        this.ACK = ACK;
        this.STP = STP;
        this.SYN = SYN;
        this.TcpOptions = (short) (this.TcpOptions | syn | ack | fin | stp);
        this.seqNum = seqNum;
        // make sure the paramters are valid
        if (dstPort < 0 || dstPort >= portLimit ||
                srcPort < 0 || srcPort >= portLimit ||
                data.length > Packet.maxContentsLength - fullHeaderLen)
            throw new MalformedPacketException();

        this.dstPort = (byte) dstPort;
        this.srcPort = (byte) srcPort;

        byte[] packetContents = new byte[fullHeaderLen + data.length];

        packetContents[0] = (byte) dstPort;
        packetContents[1] = (byte) srcPort;

        Lib.bytesFromShort(packetContents, 2, this.TcpOptions);
        Lib.bytesFromInt(packetContents, 4, this.seqNum);

        System.arraycopy(data, 0, packetContents, fullHeaderLen, data.length);

        packet = new Packet(dstLink, srcLink, packetContents);
    }

    public TCPMessage(Packet packet) throws MalformedPacketException {
        super(packet);
        this.packet = packet;

        // make sure we have a valid header
        if (packet.contents.length < headerLength ||
                packet.contents[0] < 0 || packet.contents[0] >= portLimit ||
                packet.contents[1] < 0 || packet.contents[1] >= portLimit)
            throw new MalformedPacketException();

        dstPort = packet.contents[0];
        srcPort = packet.contents[1];

        contents = new byte[packet.contents.length - headerLength];
        System.arraycopy(packet.contents, headerLength, contents, 0,
                contents.length);
        this.TcpOptions = Lib.bytesToShort(contents, 2);
        this.seqNum = Lib.bytesToInt(contents, 4);
        this.FIN = false;
        if (((this.TcpOptions >> 3) & 1) == 1) {
            this.FIN = true;
        }
        this.STP = false;
        if (((this.TcpOptions >> 2) & 1) == 1) {
            this.STP = true;
        }
        this.ACK = false;
        if (((this.TcpOptions >> 1) & 1) == 1) {
            this.ACK = true;
        }
        this.SYN = false;
        if (((this.TcpOptions) & 1) == 1) {
            this.SYN = true;
        }
        encode();
    }

    public TCPMessage(MailMessage m) throws MalformedPacketException {
        super(m.packet);
        this.packet = m.packet;

        // make sure we have a valid header
        if (packet.contents.length < headerLength ||
                packet.contents[0] < 0 || packet.contents[0] >= portLimit ||
                packet.contents[1] < 0 || packet.contents[1] >= portLimit)
            throw new MalformedPacketException();

        this.dstLink = m.dstLink;
        this.dstPort = m.dstPort;
        this.srcPort = m.srcPort;

        contents = new byte[m.contents.length - extraTCPHeaderLen];
        System.arraycopy(m.contents, extraTCPHeaderLen, contents, 0,
                contents.length);
        this.TcpOptions = Lib.bytesToShort(m.contents, 0);
        this.seqNum = Lib.bytesToInt(m.contents, 2);
        this.FIN = false;
        if (((this.TcpOptions >> 3) & 1) == 1) {
            this.FIN = true;
        }
        this.STP = false;
        if (((this.TcpOptions >> 2) & 1) == 1) {
            this.STP = true;
        }
        this.ACK = false;
        if (((this.TcpOptions >> 1) & 1) == 1) {
            this.ACK = true;
        }
        this.SYN = false;
        if (((this.TcpOptions) & 1) == 1) {
            this.SYN = true;
        }
        encode();
    }

    private void encode() {
        short fin,stp,ack,syn;
        fin = stp = ack = syn = 0;
        this.TcpOptions = 0;
        if(this.FIN) {
            fin = 0x8;
            this.TcpOptions |= 1 >> 3;
        }
        if(this.STP) {
            stp = 0x4;
            this.TcpOptions |= 1 >> 2;
        }
        if(this.ACK) {
            ack = 0x2;
            this.TcpOptions = (short)(this.TcpOptions | (0x1 >> 1));
        }
        if(this.SYN) {
            syn = 0x1;
            this.TcpOptions |= 1;
        }
        this.TcpOptions = (short)(fin | stp | ack | syn);
        this.packet.dstLink = this.dstLink;
        this.packet.contents[0] = (byte)this.dstPort;
        this.packet.contents[1] = (byte)this.srcPort;
        Lib.bytesFromShort(this.packet.contents,2,this.TcpOptions);
        Lib.bytesFromInt(this.packet.contents,4,this.seqNum);

        System.arraycopy(contents,0,this.packet.contents,fullHeaderLen,contents.length);
    }

    public boolean hasFin() {
        return FIN;
    }
    public boolean hasAck() {
        return ACK;
    }
    public boolean hasStp() {
        return STP;
    }
    public boolean hasSyn() {
        return SYN;
    }
    public int getSeqNum() {
        return seqNum;
    }
    // There is no length function, but packet.contents.length is the size available for tcp and - full header = size of content
    public int getDataLen() {
        return this.packet.contents.length - fullHeaderLen;
    }

    public void setFin(boolean b) {
        FIN = b;
        encode();
    }
    public void setAck(boolean b) {
        ACK = b;
        encode();
    }
    public void setStp(boolean b) {
        STP = b;
        encode();
    }
    public void setSyn(boolean b) {
        SYN = b;
        encode();
    }
    public void setSeqNum(int s) {
        seqNum = s;
        encode();
    }

    public static int fullHeaderLen = extraTCPHeaderLen + headerLength;
    public static int TCPContentLen = Packet.maxContentsLength - fullHeaderLen;
    public int seqNum;
    public short TcpOptions;
    boolean FIN;
    boolean ACK;
    boolean STP;
    boolean SYN;

}
