package nachos.network;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.HashMap;

/**
 * A <tt>VMProcess</tt> that supports networking syscalls.
 */
public class NetProcess extends UserProcess {
    /**
     * Allocate a new process.
     */
    public NetProcess() {
	super();
    }

    private static final int
	syscallConnect = 11,
    syscallClose = 8,
	syscallAccept = 12;


    // Handles the connect syscall, sleeping until it succeeds
    private int handleConnect(int host, int port)
    {
        // acting as if the source port is always zero in current implementation for connecting. make it so that in the future it makes the decided
        // port random
        // check if we need to provide a way to see if there is already a connection made with that specific host/port
        TCPMessage connectingMessage = null;
        long returnTime = Machine.timer().getTime() + 20000*4;
        byte[]emptyData = new byte[0];
        TransportFile tcp;

        try {
            int portNum = NetKernel.reservePort();
            if (portNum == -1) return -1;

            String ID = portNum + "," + host + "," + port;
            tcp = new TransportFile(portNum,host,port,TransportReliability.postOffice);
            connectingMessage = new TCPMessage(host,port,Machine.networkLink().getLinkAddress(),portNum,emptyData,0,true,false,false,false);

            tcp.bufferLock.acquire();
            tcp.sendMessage(connectingMessage);
            tcp.state = TransportFile.ConnectionState.SYN_SENT;
            tcp.bufferLock.release();
        } catch (MalformedPacketException e) {
            e.printStackTrace();
            return -1;
        }


        NetKernel.connectionsLock.acquire();
        while(tcp.state != TransportFile.ConnectionState.ESTABLISHED)
        {
            NetKernel.pendingConnectionsCond.sleep();
        }
        NetKernel.connectionsLock.release();

        if (tcp.state != TransportFile.ConnectionState.ESTABLISHED)
            return -1;

        // We have successfully connected to that endpoint, set the file descriptor, and return it
        if(tcp != null) {
            for(int i = 0; i < openFiles.length; ++i) {
                if(openFiles[i] == null) {
                    openFiles[i] = tcp;
                    return i;
                }
            }
        }

        return -1;
    }

    // Allows a server to accept a connection
    private int handleAccept(int port)
    {
        byte[]emptyData = new byte[0];

        // If there is a pending connection, we handle it otherwise we return -1
        if (!NetKernel.pendingConnections[port].isEmpty())
        {
            TCPMessage ackMessage = null;
            TransportFile tcp = NetKernel.pendingConnections[port].poll();

            tcp.sendAck(true,false,false);

            tcp.bufferLock.acquire();
            tcp.state = TransportFile.ConnectionState.ESTABLISHED;
            tcp.bufferLock.release();

            // A connection was successful, so now we set up the file descriptor and return it
            if(tcp != null) {
                for (int i = 0; i < openFiles.length; ++i) {
                    if (openFiles[i] == null) {
                        openFiles[i] = tcp;
                        return i;
                    }
                }
            }
        }

        // return -1 for failure because there wasn't a connection that was being attempted to be opened on the specified port
        return -1;
    }

    // Kicks off the connection teardown
    public int handleClose(int fileDescriptor)
    {
        if(fileDescriptor < 0 || fileDescriptor >= openFiles.length)
            return -1;

        System.out.println("HOST = " + Machine.networkLink().getLinkAddress() + " CALLING CLOSE ON DESCRIPTOR " + fileDescriptor);

        // If close is being called on a network connection, then we will cause the teardown to be sent over it
        if(openFiles[fileDescriptor] instanceof TransportFile) {
            TransportFile tcp = (TransportFile) openFiles[fileDescriptor];
            tcp.bufferLock.acquire();
            if (tcp.state == TransportFile.ConnectionState.ESTABLISHED) {
                // TODO change the below to be the numbers mentioned in the other stuff
                if (tcp.lastByteWritten == tcp.lastByteAcked) {
                    // If all our data has been received, then we can send a fin
                    System.out.println("    SENDING FIN PACKET AND TRANSITIONING TO STATE CLOSING");
                    TCPMessage finMessage = null;
                    byte[] emptyData = new byte[0];
                    try {
                        finMessage = new TCPMessage(tcp.dest, tcp.destPort,
                                Machine.networkLink().getLinkAddress(), tcp.srcPort,
                                emptyData, tcp.lastByteWritten + 1, false, false, true, false);
                    } catch (MalformedPacketException e) {
                        e.printStackTrace();
                    }

                    tcp.state = TransportFile.ConnectionState.CLOSING;
                    tcp.bufferLock.release();
                    TransportReliability.trackPacket(finMessage);
                    TransportReliability.postOffice.send(finMessage);
                    tcp.bufferLock.acquire();
                } else {
                    // If we havent received ACKs for all our data, then we send a stp packet
                    System.out.println("    SENDING STP PACKET AND TRANSITIONING TO STATE STP_SENT");
                    TCPMessage stpMessage = null;
                    byte[] emptyData = new byte[0];
                    try {
                        stpMessage = new TCPMessage(tcp.dest, tcp.destPort,
                                Machine.networkLink().getLinkAddress(), tcp.srcPort,
                                emptyData, tcp.lastByteWritten + 1, false, false, false, true);
                    } catch (MalformedPacketException e) {
                        e.printStackTrace();
                    }

                    tcp.state = TransportFile.ConnectionState.STP_SENT;
                    TransportReliability.postOffice.send(stpMessage);
                }

                tcp.bufferLock.release();
                return 0;
            } else if (tcp.state == TransportFile.ConnectionState.STP_RCVD) {
                // This is where it should fin packet and transition the connection state to CLOSED
                TCPMessage finMessage = null;
                byte[] emptyData = new byte[0];
                try {
                    finMessage = new TCPMessage(tcp.dest, tcp.destPort,
                            Machine.networkLink().getLinkAddress(), tcp.srcPort,
                            emptyData, tcp.lastByteWritten + 1, false, false, true, false);
                } catch (MalformedPacketException e) {
                    e.printStackTrace();
                }

                tcp.state = TransportFile.ConnectionState.CLOSING;

                // POSSIBLE PLACE TO DESTROY CURRENTCONNECTION ELEMENT
               
                /*
                super.handleSyscall(syscallClose,fileDescriptor,0,0,0);
                descriptorConnections.remove(fileDescriptor);

                TransportReliability.structsLock.acquire();
                TransportReliability.currentConnections.remove(closingConnection.tcp.srcPort + "," + closingConnection.tcp.dest + "," + closingConnection.tcp.destPort);
                TransportReliability.structsLock.release();
                */
                tcp.bufferLock.release();
                TransportReliability.trackPacket(finMessage);
                TransportReliability.postOffice.send(finMessage);
                return 0;
            }
            tcp.bufferLock.release();
            return -1;
        } else {
            return super.handleSyscall(syscallClose, fileDescriptor, 0,0,0);
        }
    }
    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     *
     * <table>
     * <tr><td>syscall#</td><td>syscall prototype</td></tr>
     * <tr><td>11</td><td><tt>int  connect(int host, int port);</tt></td></tr>
     * <tr><td>12</td><td><tt>int  accept(int port);</tt></td></tr>
     * </table>
     * 
     * @param	syscall	the syscall number.
     * @param	a0	the first syscall argument.
     * @param	a1	the second syscall argument.
     * @param	a2	the third syscall argument.
     * @param	a3	the fourth syscall argument.
     * @return	the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {

        // If we are calling read or write, then we need to check if the connection is still live
        if ((syscall == 6 || syscall == 7) && (a0 >= 0 && a0 < openFiles.length)) {
            // If we are calling these on a socket connection
            if (openFiles[a0] instanceof TransportFile) {
                TransportFile tcp = (TransportFile) openFiles[a0];

                int toRet = -1;
                tcp.bufferLock.acquire();

                if (syscall == 7 && (tcp.state == TransportFile.ConnectionState.STP_RCVD
                        || tcp.state == TransportFile.ConnectionState.STP_SENT
                        || tcp.state == TransportFile.ConnectionState.CLOSING
                        || tcp.state == TransportFile.ConnectionState.CLOSED)) {
                    // Tried to write to an invalid state
                    toRet = -1;
                } else if (syscall == 6 && tcp.state == TransportFile.ConnectionState.CLOSED) {
                    // Reading from a closed socket
                    tcp.bufferLock.release();
                    int returnValue = super.handleSyscall(syscall, a0, a1, a2, a3);
                    tcp.bufferLock.acquire();

                    if (returnValue == 0) {
                        //descriptorConnections.remove(a0);
                        //remove from open files
                        //System.out.println(" DELETING THIS DESCRIPTOR");
                        //super.handleSyscall(syscallClose, a0, 0, 0, 0);
                        tcp.bufferLock.release();
                        return -1;
                    }

                    toRet = returnValue;
                } else if(tcp.state == TransportFile.ConnectionState.SYN_SENT || tcp.state == TransportFile.ConnectionState.SYN_RCVD) {
                    tcp.bufferLock.release();
                    return 0;
                } else {
                    tcp.bufferLock.release();
                    toRet = super.handleSyscall(syscall, a0, a1, a2, a3);
                    return toRet;
                }

                tcp.bufferLock.release();
                return toRet;
            }
        }

            // Otherwise, handle the syscalls with our handlers
            switch (syscall) {
                case syscallAccept:
                    return handleAccept(a0);
                case syscallConnect:
                    return handleConnect(a0, a1);
                case syscallClose:
                    return handleClose(a0);
                default:
                    return super.handleSyscall(syscall, a0, a1, a2, a3);
            }
    }

}
