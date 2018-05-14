package nachos.network;

//import com.sun.xml.internal.bind.v2.model.core.ID;
import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.MalformedPacketException;
import nachos.machine.Timer;
import nachos.threads.*;
import nachos.userprog.UserKernel;

import java.util.*;

/**
 * A kernel with network support.
 */
public class TransportReliability {
    /**
     * Allocate a new networking kernel.
     */
    public TransportReliability() {

    }

    public void selfTest() {
		//System.out.println("Starting reliability test");

		/*
        TransportFile a = new TransportFile(0,Machine.networkLink().getLinkAddress(),1,postOffice);
        TransportFile b = new TransportFile(1,Machine.networkLink().getLinkAddress(),0,postOffice);

        byte[] toSend = new byte[32];
        Lib.bytesFromInt(toSend,0,0x00010203);
		Lib.bytesFromInt(toSend,4,0x04050607);
		Lib.bytesFromInt(toSend,8,0x08090a0b);
		Lib.bytesFromInt(toSend,12,0x0c0d0e0f);
		Lib.bytesFromInt(toSend,16,0x10111213);
		Lib.bytesFromInt(toSend,20,0x14151617);
		Lib.bytesFromInt(toSend,24,0x18191a1b);
		Lib.bytesFromInt(toSend,28,0x1c1d1e1f);
        byte[] readData = new byte[toSend.length];
        int writeAmount = 0;
        while(writeAmount < toSend.length) {
			writeAmount += a.write(toSend, writeAmount, toSend.length-writeAmount);
		}
        int readAmount = 0;
        while(readAmount < toSend.length) {
			readAmount += b.read(readData, readAmount, toSend.length-readAmount);
		}
        for(int i = 0; i < toSend.length; ++i) {
            System.out.println(readData[i]);
            Lib.assertTrue(toSend[i] == readData[i]);
        }
		System.out.println("Done");
		*/
	}

    public void init(PostOffice p) {
		if(!this.initRan) {
			this.postOffice = p;

			KThread resendThread = new KThread(new Runnable() {
                public void run() { timerTask(); }
            });
            resendThread.fork();

            KThread receiveThread = new KThread(new Runnable() {
                public void run() { receiveTask(); }
            });
            receiveThread.fork();

			this.initRan = true;
		}
	}

	public static int getSrcPort(String ID) {
    	return Integer.parseInt(ID.split(",")[0]);
	}
	public static int getDest(String ID) {
    	return Integer.parseInt(ID.split(",")[1]);
	}
	public static int getDestPort(String ID) {
    	return Integer.parseInt(ID.split(",")[2]);
	}
	public static String IDToString(int srcPort, int dest, int destPort) {
    	return srcPort + "," + dest + "," + destPort;
	}

	// Selectively retransmits data when it is required to be retransmitted
	public void timerTask() {
        Alarm resendTimer = ThreadedKernel.alarm;

        // While we havent found an element that is to be resent after the current time, then resend a packet if
        // its resend time has passed
        while(true) {
        	boolean checkAgain = true;
            while(checkAgain){
				structsLock.acquire();
                if(!resendQueue.isEmpty()) {
                	// Get the socket corresponding to the earliest resend time
                    Long resendTime = resendQueue.firstKey();
                    HashSet<Socket> resend = resendQueue.firstEntry().getValue();
                    Long thisTime = Machine.timer().getTime();
                    if (resendTime <= thisTime) {
						// Go through the list of sockets that have packets to resend at this time period, and send
                        for (Socket s : resend) {
                            s.structsLock.acquire();

                            // Retrieve the packet
                            Integer ackNum = s.unAckedResendTime.get(resendTime);
                            if(ackNum != null) {
								s.unAckedResendTime.remove(resendTime);
								MailMessage m = s.unAckEd.get(ackNum);
								if(m != null) {
									// We have a message needing to be resent, so resend it and schedule it to be resent
									// 20000 ticks from now
									postOffice.send(m);
									s.unAckedResendTime.put(thisTime + TransportFile.RTT, ackNum);
								}
							}

							// Since the packet was resent already, and it is no longer going to be sent at the time
							// associated with "resend", its socket can be removed from resend
							resend.remove(s);

                            // Determine when the soonest packet owned by this socket is supposed to be resent, and
							// schedule it to be resent then
							if(!s.unAckedResendTime.isEmpty()) {
                                Long sResendTime = s.unAckedResendTime.firstKey();
                                HashSet<Socket> newConn = resendQueue.get(sResendTime);
                                if (newConn == null) {
                                    newConn = new HashSet<Socket>();
                                } else {
                                    resendQueue.remove(sResendTime);
                                }
                                newConn.add(s);
                                resendQueue.put(sResendTime, newConn);
                            } else {
								// There are no packets to resend for this socket, so it should be removed from the
								// present list
                            	sockPresent.remove(s);
							}

                            s.structsLock.release();
                        }
                        resendQueue.remove(resendTime);
                    } else {
                        checkAgain = false;
                    }
                } else {
                	checkAgain = false;
				}
				structsLock.release();
			}

			long timeToSleep = -1;
			structsLock.acquire();
            if(!resendQueue.isEmpty()) {
                // There is an element in the resend queue, and since it is ordered, the first key is the soonest
				// time we need to resend at, so go to sleep until then
                timeToSleep = resendQueue.firstKey() - Machine.timer().getTime();
                timeToSleep = timeToSleep > 0 ? timeToSleep : 0;
            }
			if(timeToSleep == -1) {
				// No elements are in queue, go to sleep on condition
                structsEmpty.sleep();
            structsLock.release();
			} else {
            structsLock.release();
            	// sleep until the next packet retransmission
				resendTimer.waitUntil(timeToSleep);
			}
		}
	}

	// Implements the logic of what should happen to each socket on receipt of a packet, based on the state of that packet
	public void receiveTask() {
    	TCPMessage receivedMessage;
    	while(true) {
			try {
				MailMessage r = (MailMessage) recvdMessages.removeFirst();
				boolean destroyConnection = false;
				receivedMessage = new TCPMessage(r);
				String ID = IDToString(receivedMessage.dstPort, receivedMessage.packet.srcLink, receivedMessage.srcPort);

				structsLock.acquire();
				Socket s = currentConnections.get(ID);

				if (s == null && !receivedMessage.hasAck() && receivedMessage.hasFin() && !receivedMessage.hasStp() && !receivedMessage.hasSyn())
				{
				    // RECEIVED FIN for nonexistant connection
					byte[]emptyData = new byte[0];
					TCPMessage finAckMess = new TCPMessage(r.packet.srcLink,r.srcPort,
							Machine.networkLink().getLinkAddress(),r.dstPort,emptyData,0,false,true,true,false);
					postOffice.send(finAckMess);
				}
				if (s != null) {
					// Connection Exists
					s.structsLock.acquire();
					s.tcp.bufferLock.acquire();

					if (receivedMessage.hasAck() && !receivedMessage.hasFin() && !receivedMessage.hasStp() && !receivedMessage.hasSyn() && s.tcp.state == TransportFile.ConnectionState.ESTABLISHED) {
						// Received an ack for data we sent
						if (s.tcp.dataAcked(receivedMessage)) {
							// Remove data from tracking queue
							ArrayList<Long> toRemove = new ArrayList<Long>();
							for(long key : s.unAckedResendTime.keySet()) {
							    if(s.unAckedResendTime.get(key) < receivedMessage.getSeqNum()) {
							    	if(!s.unAckEd.isEmpty()) {
							    		s.unAckEd.remove(s.unAckedResendTime.get(key));
									}
							    	toRemove.add(key);
								}
							}
							for(Long toRem : toRemove) {
								s.unAckedResendTime.remove(toRem);
							}
							while(!s.unAckEd.isEmpty() && s.unAckEd.firstKey() < receivedMessage.getSeqNum()) {
								s.unAckEd.remove(s.unAckEd.firstKey());
							}
						}
					}
					if (!receivedMessage.hasAck() && !receivedMessage.hasFin() && !receivedMessage.hasStp() && !receivedMessage.hasSyn() && s.tcp.state == TransportFile.ConnectionState.ESTABLISHED) {
						// received data, pass to tcp buffer and send ack
						int nextSeq = s.tcp.fillReadBuffer(receivedMessage);
						if (nextSeq > -1) {
							try {
								TCPMessage ackMessage = new TCPMessage(receivedMessage.packet.srcLink, receivedMessage.srcPort, Machine.networkLink().getLinkAddress(), receivedMessage.dstPort, new byte[0], nextSeq, false, true, false, false);
								postOffice.send(ackMessage);
							} catch (MalformedPacketException e) { }
						}
					}
					if (!receivedMessage.hasAck() && !receivedMessage.hasFin() && !receivedMessage.hasStp() && receivedMessage.hasSyn())
					{
					    // Received a retransmitted SYN, send SYN/ACK
					    s.tcp.sendAck(true,false,false);
					}
					if (receivedMessage.hasAck() && !receivedMessage.hasFin() && !receivedMessage.hasStp() && receivedMessage.hasSyn() && s.tcp.state == TransportFile.ConnectionState.SYN_SENT)
					{
						// Received SYN/ACK
						Socket socket = currentConnections.get(ID);

						NetKernel.connectionsLock.acquire();

						socket.tcp.state = TransportFile.ConnectionState.ESTABLISHED;
						currentConnections.put(ID,socket);

						NetKernel.pendingConnectionsCond.wakeAll();

						NetKernel.connectionsLock.release();
					}
					if (!receivedMessage.hasAck() && !receivedMessage.hasFin() && receivedMessage.hasStp() && !receivedMessage.hasSyn())
					{
						// handling STP message for various connection states
						if (s.tcp.state == TransportFile.ConnectionState.ESTABLISHED)
						{
							// clear the data in the sending buffer and stop retransmissions of those packets
							// and also transition connection state to STP_RECD
							//s.unAckEd.clear();
							s.tcp.state = TransportFile.ConnectionState.STP_RCVD;
						}
						else if (s.tcp.state == TransportFile.ConnectionState.STP_SENT)
						{
							// clear the data in the sending buffer and stop retransmissions of those packets
							// send FIN packet
							// transition connection state to CLOSING
							//s.unAckEd.clear();
                            /*
							for (Integer keys : s.unAckEd.keySet())
							{
								TCPMessage message = s.unAckEd.get(keys);
								if (!message.hasAck() && !message.hasFin() && !message.hasStp() && !message.hasSyn())
									s.unAckEd.remove(keys);
							}
							*/

							byte[]emptyData = new byte[0];
							TCPMessage finMess = new TCPMessage(s.destAddr,s.destPort,Machine.networkLink().getLinkAddress(),s.srcPort
									,emptyData,s.tcp.lastByteWritten+1,false,false,true,false);
							s.tcp.bufferLock.release();
							trackPacket(finMess);
							postOffice.send(finMess);
							s.tcp.bufferLock.acquire();
							s.tcp.state = TransportFile.ConnectionState.CLOSING;
						}
						else if (s.tcp.state == TransportFile.ConnectionState.CLOSING)
						{
							// send FIN packet
							byte[]emptyData = new byte[0];
							TCPMessage finMess = new TCPMessage(s.destAddr,s.destPort,Machine.networkLink().getLinkAddress(),s.srcPort,emptyData,s.tcp.lastByteWritten+1,false,false,true,false);
							postOffice.send(finMess);
						}
					}
					if (!receivedMessage.hasAck() && receivedMessage.hasFin() && !receivedMessage.hasStp() && !receivedMessage.hasSyn())
					{
						// handling FIN messages with various state connections
						System.out.println("	RECEIVED FIN IN: " + s.tcp.state + " with seq: " + receivedMessage.getSeqNum() + " and our lastByteAcked = " + s.tcp.lastByteAcked);
						if(s.tcp.lastByteAcked+2 == receivedMessage.getSeqNum()) {
							System.out.println("USING FIN");
							if (s.tcp.state == TransportFile.ConnectionState.ESTABLISHED) {
								// send FIN/ACK packet
								// transition the connection state to CLOSED

								byte[] emptyData = new byte[0];
								s.tcp.lastByteWritten++;
								TCPMessage finAckMess = new TCPMessage(s.destAddr, s.destPort, Machine.networkLink().getLinkAddress(), s.srcPort
										, emptyData, s.tcp.lastByteWritten, false, true, true, false);
								postOffice.send(finAckMess);
								s.tcp.state = TransportFile.ConnectionState.CLOSED;
								System.out.println("Now closed");
								// POSSIBLE PLACE TO DESTROY CURRENTCONNECTION ELEMENT
								destroyConnection = true;
							} else if (s.tcp.state == TransportFile.ConnectionState.STP_SENT || s.tcp.state == TransportFile.ConnectionState.STP_RCVD) {
								// send FIN/ACK packet
								// transition connection state to CLOSED
								byte[] emptyData = new byte[0];
								TCPMessage finAckMess = new TCPMessage(s.destAddr, s.destPort, Machine.networkLink().getLinkAddress(), s.srcPort, emptyData, receivedMessage.getSeqNum() + 1, false, true, true, false);
								postOffice.send(finAckMess);
								s.tcp.state = TransportFile.ConnectionState.CLOSED;
								// POSSIBLE PLACE TO DESTROY CURRENTCONNECTION ELEMENT
								destroyConnection = true;
							} else if (s.tcp.state == TransportFile.ConnectionState.CLOSING) {
								// send FIN/ACK packet
								// transition connection state to CLOSED
								byte[] emptyData = new byte[0];
								TCPMessage finAckMess = new TCPMessage(s.destAddr, s.destPort, Machine.networkLink().getLinkAddress(), s.srcPort, emptyData, 0, false, true, true, false);
								postOffice.send(finAckMess);
								s.tcp.state = TransportFile.ConnectionState.CLOSED;
							} else if (s.tcp.state == TransportFile.ConnectionState.CLOSED) {
								byte[] emptyData = new byte[0];
								TCPMessage finAckMess = new TCPMessage(s.destAddr, s.destPort, Machine.networkLink().getLinkAddress(), s.srcPort
										, emptyData, receivedMessage.getSeqNum(), false, true, true, false);
								postOffice.send(finAckMess);
							}
						}
					}
					if (!receivedMessage.hasAck() && !receivedMessage.hasFin() && !receivedMessage.hasStp() && !receivedMessage.hasSyn() && (s.tcp.state == TransportFile.ConnectionState.CLOSING
							|| s.tcp.state == TransportFile.ConnectionState.STP_SENT || s.tcp.state == TransportFile.ConnectionState.STP_RCVD || s.tcp.state == TransportFile.ConnectionState.CLOSED))
					{
					    // Handles receiving data for states other than ESTABLISHED

						// Move data into read buffer, and send an ACK
						int nextSeq = s.tcp.fillReadBuffer(receivedMessage);
                        if (nextSeq > -1) {
                            try {
								TCPMessage ackMessage = new TCPMessage(s.destAddr, s.destPort, Machine.networkLink().getLinkAddress(), s.srcPort, new byte[0], nextSeq, false, true, false, false);
                                postOffice.send(ackMessage);
							} catch (MalformedPacketException e) { }
                        }
						if (s.tcp.state == TransportFile.ConnectionState.CLOSING)
						{
						    // Send FIN Message to signal close
							byte[]emptyData = new byte[0];
							TCPMessage finMess = new TCPMessage(s.destAddr,s.destPort,Machine.networkLink().getLinkAddress(),s.srcPort,emptyData,s.tcp.lastByteWritten,false,false,true,false);
							postOffice.send(finMess);
						}
						else if (s.tcp.state == TransportFile.ConnectionState.STP_SENT)
						{
							// Send STP Message
							byte[]emptyData = new byte[0];
							TCPMessage stpMess = new TCPMessage(s.destAddr,s.destPort,Machine.networkLink().getLinkAddress(),s.srcPort,emptyData,s.tcp.lastByteWritten,false,false,false,true);
							postOffice.send(stpMess);
						}
					}
					if (receivedMessage.hasAck() && !receivedMessage.hasFin() && !receivedMessage.hasStp() && !receivedMessage.hasSyn() && s.tcp.state != TransportFile.ConnectionState.ESTABLISHED)
					{
						// HANDLES ACKS for sockets that arent currently established
						if(s.tcp.dataAcked(receivedMessage)) {
							// Remove data from tracking queue
							ArrayList<Long> toRemove = new ArrayList<Long>();
							for(long key : s.unAckedResendTime.keySet()) {
							    if(s.unAckedResendTime.get(key) < receivedMessage.getSeqNum()) {
							    	if(!s.unAckEd.isEmpty()) {
							    		s.unAckEd.remove(s.unAckedResendTime.get(key));
									}
							    	toRemove.add(key);
								}
							}
							for(Long toRem : toRemove) {
								s.unAckedResendTime.remove(toRem);
							}
							while(!s.unAckEd.isEmpty() && s.unAckEd.firstKey() < receivedMessage.getSeqNum()) {
								s.unAckEd.remove(s.unAckEd.firstKey());
							}
						}
						if (s.tcp.state == TransportFile.ConnectionState.STP_SENT)
						{
							System.out.println("LAT BYTE WRITTEN FOR STP: " + s.tcp.lastByteWritten + " last byte acked: " + s.tcp.lastByteAcked);
								if ((s.tcp.lastByteWritten+1 == s.tcp.lastByteAcked)) {
									TCPMessage finMessage = null;
									byte[] emptyData = new byte[0];
									try {
										finMessage = new TCPMessage(s.tcp.dest, s.tcp.destPort,
												Machine.networkLink().getLinkAddress(), s.tcp.srcPort,
												emptyData, s.tcp.lastByteWritten+2, false, false, true, false);
									} catch (MalformedPacketException e) {
										e.printStackTrace();
									}

									System.out.println("		SENDING FIN MESSAGE AND TRANSITIONING TO CLOSING");
									s.tcp.state = TransportFile.ConnectionState.CLOSING;
									s.tcp.bufferLock.release();
                                    trackPacket(finMessage);
                                    postOffice.send(finMessage);
                                    s.tcp.bufferLock.acquire();
								}
						}
					}
					if (receivedMessage.hasAck() && receivedMessage.hasFin() && !receivedMessage.hasStp() && !receivedMessage.hasSyn() )
					{
						// Handles FIN/ACK messages
						// transition connection state to CLOSED
						System.out.println("	RECEIVED FIN/ACK IN: " + s.tcp.state);
						s.unAckEd.remove(receivedMessage.getSeqNum());

						if( s.tcp.state == TransportFile.ConnectionState.CLOSING) {
							s.tcp.state = TransportFile.ConnectionState.CLOSED;
							//System.out.println("	CONNECTION BEING CLOSED");
							// POSSIBLE PLACE TO DESTROY CURRENTCONNECTION ELEMENT
							destroyConnection = true;
						}

					}

					s.structsLock.release();
					s.tcp.bufferLock.release();
				}
				else if (!receivedMessage.hasAck() && !receivedMessage.hasFin() && !receivedMessage.hasStp() && receivedMessage.hasSyn())
				{
				    // Handles the initial receipt of SYN since there is no initialized socket, it is outside that socket == null check
					TransportFile tcp = new TransportFile(receivedMessage.dstPort,receivedMessage.packet.srcLink,
							receivedMessage.srcPort,postOffice);
					NetKernel.connectionsLock.acquire();
					NetKernel.pendingConnections[receivedMessage.dstPort].add(tcp);
					tcp.bufferLock.acquire();
					tcp.state = TransportFile.ConnectionState.SYN_RCVD;
					tcp.bufferLock.release();
					NetKernel.connectionsLock.release();
				}
				structsLock.release();

				structsLock.acquire();
				/*
				if (destroyConnection)
					currentConnections.remove(ID);
					*/
				structsLock.release();

			} catch(MalformedPacketException e) {
                continue;
            } }
	}

	// Registers a new socket
	public static void registerTCP(int srcPort, int destAddr, int destPort, TransportFile tcp) {
        String ID = IDToString(srcPort, destAddr, destPort);
        Socket s = new Socket(srcPort, destAddr, destPort, tcp);
    	if(!currentConnections.containsKey(ID)) {
			currentConnections.put(ID, s);
		}
	}

	// places a packet into the resend queue at the time it should be resent at
	public static void trackPacket(TCPMessage m) {
    	String ID = IDToString(m.srcPort,m.dstLink,m.dstPort);
    	int seq = m.getSeqNum();
    	boolean acquired = structsLock.isHeldByCurrentThread();
    	if(!acquired) {
			structsLock.acquire();
		}

    	if(!currentConnections.containsKey(ID)) {
			currentConnections.put(ID,new Socket(getSrcPort(ID), getDest(ID), getDestPort(ID)));
		}

		// Places the packet in the resend queue of a socket and if that socket currently isnt in the machine global
		// retransmit queue, then it is added.
		// Since RTT never changes, this packet is never going to be resent sooner than the first element in the Socket
		// resend queue, so we dont need to rebalance or move where the socket is in the global resend queue
		currentConnections.get(ID).trackPacket(seq,m);
    	if(!sockPresent.contains(currentConnections.get(ID))) {
			HashSet<Socket> resend = resendQueue.get(currentConnections.get(ID).unAckedResendTime.firstKey());
			if (resend == null) {
				resend = new HashSet<Socket>();
			}
			resend.add(currentConnections.get(ID));
			resendQueue.put(currentConnections.get(ID).unAckedResendTime.firstKey(), resend);
			sockPresent.add(currentConnections.get(ID));
		}

		structsEmpty.wake();
    	if(!acquired) {
			structsLock.release();
		}
	}

	// Used to track state pertaining to a connection that doesnt belong in the TransportFile class
    public static class Socket {
    	public Socket(int srcPort, int destAddr, int destPort) {
    		this.srcPort = srcPort;
    		this.destAddr = destAddr;
    		this.destPort = destPort;
    		tcp = null;

    		unAckEd = new TreeMap<Integer, TCPMessage>();
    		unAckedResendTime = new TreeMap<Long, Integer>();
		}

		public Socket(int srcPort, int destAddr, int destPort, TransportFile file) {
    		this.srcPort = srcPort;
    		this.destAddr = destAddr;
    		this.destPort = destPort;

    		tcp = file;
    		unAckEd = new TreeMap<Integer, TCPMessage>();
    		unAckedResendTime = new TreeMap<Long, Integer>();
		}

		// Places a packet into the internal resend queues of a socket and returns time timer should fire
        public long trackPacket(int seq, TCPMessage m) {
    		boolean isHeld = structsLock.isHeldByCurrentThread();
    		if(!isHeld) {
				structsLock.acquire();
			}
			long resendTime = Machine.timer().getTime() + TransportFile.RTT;
            unAckEd.put(seq, m);
            unAckedResendTime.put(resendTime,seq);
            if(!isHeld) {
				structsLock.release();
			}
            return resendTime;
        }

		int srcPort, destAddr, destPort;

    	Lock structsLock = new Lock();
    	TransportFile tcp;
    	// AckNum -> Packet
		TreeMap<Integer,TCPMessage> unAckEd;
		// Resend time absolute -> ackNum
		TreeMap<Long, Integer> unAckedResendTime;
	}

	public static Lock structsLock = new Lock();
    public static Condition structsEmpty = new Condition(structsLock);

    // Used to send data
	public static PostOffice postOffice;

    // localPort,remoteMachineAddr,remotePort as key, with commas
    public static HashMap<String, Socket> currentConnections = new HashMap<String, Socket>();

    // Resend time absolute -> Socket with first elem of unAckedResendTime equaling this key
	public static TreeMap<Long, HashSet<Socket>> resendQueue = new TreeMap<Long, HashSet<Socket>>();

	// Tracks which sockets currently have packets that need to be re-sent
	public static HashSet<Socket> sockPresent = new HashSet<Socket>();

	// Acts as the communication link between this and PostOffice to receive packets and multiplex them per socket
	// rather than per port
	public static SynchList recvdMessages = new SynchList();

    // dummy variables to make javac smarter
    private static NetProcess dummy1 = null;
    private static boolean initRan = false;
}
