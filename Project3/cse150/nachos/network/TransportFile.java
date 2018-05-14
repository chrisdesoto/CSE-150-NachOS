package nachos.network;

import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.MalformedPacketException;
import nachos.machine.OpenFile;
import nachos.threads.Lock;

import static java.lang.Math.min;

public class TransportFile extends OpenFile {

    public TransportFile(int srcPort, int dest, int destPort, PostOffice p) {
        super();
        this.postOffice = p;
        this.srcPort = srcPort;
        this.dest = dest;
        this.destPort = destPort;
        this.state = ConnectionState.CLOSED;

        TransportReliability.registerTCP(srcPort, dest, destPort, this);

        // initialize byteBufferVariables
        lastByteAcked = 0;
        lastByteWritten = 0;
        lastByteRead = 0;
        nextByteExpected = 1;

    }

    // Fills in the buffer when data arrives, bufferLock needs to be held by the caller
    public int fillReadBuffer(TCPMessage m) {
        Lib.assertTrue(bufferLock.isHeldByCurrentThread());

        // If the sequence number is less than the one we expect, then it is a retransmission, so we should ack with the
        // next byte expected to signal that we received the data
        if(m.getSeqNum() < nextByteExpected) {
             return nextByteExpected;
        }

        // If it makes it here, we have new data, so if it doesnt overrun our buffer, lets add it in
        if(m.getSeqNum()+m.getDataLen() <= lastByteRead + BUFFER_SIZE) {
            for(int i = 0; i < m.getDataLen(); ++i) {
                // Adds in the data and tracks whether or not data is present at a buffer location
                readBuffer[(i+m.getSeqNum()) % BUFFER_SIZE] = m.contents[i];
                presentPositions[(i+m.getSeqNum()) % BUFFER_SIZE] = 1;
            }

            // If the sequence number is the next byte expected, we dont have any holes in our data, so we can advance
            // the nextByteExpected to the next point where no data is present, so a user can read the data
            if(m.getSeqNum() == nextByteExpected) {
                int upperBound = lastByteRead+BUFFER_SIZE;
                for(int i = nextByteExpected; i < upperBound; ++i) {
                    if(presentPositions[i % BUFFER_SIZE] == 1) {
                        nextByteExpected++;
                    } else {
                        break;
                    }
                }
            }
        }

        return nextByteExpected;
    }

    // Advances the lastByteAcked to the contents of the ack if it can do so
    public boolean dataAcked(TCPMessage m) {
        Lib.assertTrue(bufferLock.isHeldByCurrentThread());
        boolean returnVal = false;
        if(m.hasAck()) {
            if(!m.hasFin() && !m.hasStp() && !m.hasSyn()) {
                if(m.getSeqNum() > lastByteAcked) {
                    lastByteAcked = m.getSeqNum();
                }
            }
            return true;
        }

        return returnVal;
    }

    // Helper function to begin tracking a packet for retransmission and modifies the send buffer
    public boolean sendMessage(TCPMessage m) {
        Lib.assertTrue(bufferLock.isHeldByCurrentThread());
        boolean returnVal = true;
        if(lastByteWritten+m.getDataLen() <= lastByteAcked+BUFFER_SIZE) {
            bufferLock.release();
            TransportReliability.trackPacket(m);
            postOffice.send(m);
            bufferLock.acquire();
        } else {
            returnVal = false;
        }
        return returnVal;
    }

    // Sends an ack with optional syn,stp, and fin flags
    public void sendAck(boolean syn, boolean stp, boolean fin)
    {
        TCPMessage m = null;
        try {
            m = new TCPMessage(dest,destPort, Machine.networkLink().getLinkAddress(),srcPort,new byte[0],0,syn,true,fin,stp);
        } catch (MalformedPacketException e) {
            e.printStackTrace();
        }
        postOffice.send(m);
    }

    // Overrides the parent OpenFile's write function to allow bytes to be written over the communication link
    public int write(byte[] toSend, int offset, int numToWrite) {
        int bytesWritten = 0;
        byte[] data;

        // While we havent written everything
        while(bytesWritten < numToWrite) {
            bufferLock.acquire();
            TCPMessage m;
            // Calculate how much data we can fit in this packet
            int spaceLeft = BUFFER_SIZE-(lastByteWritten-lastByteAcked);
            int packetSize = TCPMessage.TCPContentLen;
            int bytesToFill = min(packetSize, min(spaceLeft, numToWrite-bytesWritten));
            if(bytesToFill > 0) {
                // Copy bytesToFill bytes into the data field of the TCPMessage
                data = new byte[bytesToFill];
                System.arraycopy(toSend, bytesWritten + offset, data, 0, bytesToFill);

                try {
                    // Creates the packet and sends it
                    m = new TCPMessage(dest, destPort, Machine.networkLink().getLinkAddress(), srcPort, data, lastByteWritten + 1, false, false, false, false);
                    if (sendMessage(m)) {
                        lastByteWritten += bytesToFill;
                        bytesWritten += bytesToFill;
                    } else {
                        bufferLock.release();
                        return bytesWritten;
                    }
                } catch (MalformedPacketException e) {
                    Lib.assertNotReached();
                    bufferLock.release();
                    return bytesWritten;
                }
            } else {
                bufferLock.release();
                break;
            }
            bufferLock.release();
        }

        return bytesWritten;
    }

    // Overrides the parent OpenFile's read function to allow bytes to be read from the communication link
    public int read(byte[] readTo, int offset, int numToRead) {
        int bytesRead = 0;
        bufferLock.acquire();

        // Returns -1 if a read is attempted on a closed socket and all the data has been read
        if(state == ConnectionState.CLOSED && lastByteRead == nextByteExpected-2) {
            bytesRead = -1;
        } else {
            // Reads the data and clears the present bit for that byte position and advances the lastByteRead
            int dataAvail = nextByteExpected - 1 - lastByteRead;
            int numBytesToRead = min(dataAvail, numToRead);
            for (int i = 0; i < numBytesToRead; ++i) {
                readTo[i + offset] = readBuffer[(lastByteRead + 1 + i) % BUFFER_SIZE];
                presentPositions[(lastByteRead + 1 + i) % BUFFER_SIZE] = 0;
            }
            lastByteRead += numBytesToRead;
            bytesRead = numBytesToRead;
        }
        bufferLock.release();
        return bytesRead;
    }


    // Member variables
    enum ConnectionState{CLOSED,SYN_SENT,SYN_RCVD,ESTABLISHED,STP_RCVD,STP_SENT,CLOSING};
    public ConnectionState state;

    public static final int BUFFER_SIZE = 16*TCPMessage.maxContentsLength;
    public static final int RTT = 20000;

    public Lock bufferLock = new Lock();

    // Unique identifier for this connection among other connections on this same machine
    public int srcPort, dest, destPort;

    // Keeps track of the state of the buffers so we know when we can read and write
    public int lastByteAcked, lastByteWritten;
    public int lastByteRead, nextByteExpected;

    // Buffer to hold the data that the socket has received
    public byte[] readBuffer = new byte[BUFFER_SIZE];
    // Keeps track of which bytes in readBuffer are present, so we can have holes in our data when a packet is dropped,
    // significantly increasing throughput
    public byte[] presentPositions = new byte[BUFFER_SIZE];
    // Write buffer storage is handled by TransportReliability's timer task, while positioning is handled by this class

    private PostOffice postOffice;
}
