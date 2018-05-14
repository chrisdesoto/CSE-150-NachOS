package nachos.network;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import nachos.network.*;
import java.util.LinkedList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

/**
 * A kernel with network support.
 */
public class NetKernel extends UserKernel{
    /**
     * Allocate a new networking kernel.
     */
    public NetKernel() {
        super();
        availPorts = new ArrayList<Integer>();
        for(int i = 0; i < 128; ++i) {
            availPorts.add(i);
        }
    }

    /**
     * Initialize this kernel.
     */
    public void initialize(String[] args) {
	super.initialize(args);
    //System.out.println("RUNNING NET INITIALIZE");


    portLock = new Lock();

	postOffice = new PostOffice();
	TransportReliability t = new TransportReliability();
	t.init(postOffice);
	//t.selfTest();
	    
	connectionsLock = new Lock();

	pendingConnectionsCond = new Condition(connectionsLock);
	pendingConnections = new LinkedList[MailMessage.portLimit];

	for(int i = 0; i < MailMessage.portLimit; i++)
		pendingConnections[i] = new LinkedList<TransportFile>();
    }

    /**
     * Test the network. Create a server thread that listens for pings on port
     * 1 and sends replies. Then ping one or two hosts. Note that this test
     * assumes that the network is reliable (i.e. that the network's
     * reliability is 1.0).
     */
    public void selfTest() {
        super.selfTest();

        /*
        KThread serverThread = new KThread(new Runnable() {
            public void run() { pingServer(); }
        });


        System.out.println("Press any key to start the network test...");
		serverThread.fork();
        console.readByte(true);
        NetProcess net1 = new NetProcess();
        int local = Machine.networkLink().getLinkAddress();
        if (local == 0)
        {
			System.out.println(local);
			int fd = -1;
            while(fd == -1) {
                fd = net1.handleSyscall(12,42,0,0,0);
            }
            System.out.println("Connection established on node 0!!");
            TransportFile tcp = (TransportFile) net1.openFiles[fd];
            byte data[] = new byte[42];
            int numRead = 0;
            while(true) {
                int thisRead = 0;
                if((thisRead = tcp.read(data,numRead,1)) > 0) {
                    for(int i = numRead; i < numRead+thisRead; ++i) {
                        System.out.println("Read Byte: " + data[i]);
                    }
                    if(thisRead > 0) {
                        numRead += thisRead;
                    }
                }
            }
        }
        else if (local == 1)
        {
			System.out.println(local);
            net1.handleSyscall(11,0,0,0,0);
            System.out.println("Connection established on node 1!!");
        }
    //         // ping this machine first
//         ping(local);

//         // if we're 0 or 1, ping the opposite
//         if (local <= 1)
//             ping(1-local);
*/
    }

    private void ping(int dstLink) {
	int srcLink = Machine.networkLink().getLinkAddress();
	
	System.out.println("PING " + dstLink + " from " + srcLink);

	long startTime = Machine.timer().getTime();
	
	MailMessage ping;

	try {
	    ping = new MailMessage(dstLink, 1,
				   Machine.networkLink().getLinkAddress(), 0,
				   new byte[0]);
	}
	catch (MalformedPacketException e) {
	    Lib.assertNotReached();
	    return;
	}

	postOffice.send(ping);

	MailMessage ack = postOffice.receive(0);
	
	long endTime = Machine.timer().getTime();

	System.out.println("time=" + (endTime-startTime) + " ticks");	
    }

    private void pingServer() {

    NetProcess net1 = new NetProcess();
    TransportFile tcp = (TransportFile)net1.openFiles[net1.handleSyscall(11,0,42,0,0)];
    byte toSend[] = new byte[4];
    Lib.bytesFromInt(toSend,0,0x01020304);
    int sent = 0;
    while(sent < toSend.length) {
        sent += tcp.write(toSend, sent, toSend.length-sent);
        System.out.println("SENT: " + sent + " bytes");
    }

    }

    public static int reservePort() {
        int toRet;
    	portLock.acquire();
    	if(availPorts.isEmpty()) {
    		toRet = -1;
		} else {
			toRet = availPorts.remove(0);
		}
    	portLock.release();
    	return toRet;
	}

	public static void releasePort(int portNum) {
    	portLock.acquire();
    	availPorts.add(portNum);
		portLock.release();
	}

    /**
     * Start running user programs.
     */
    public void run() {
	super.run();
		System.out.println("TEST");
    }
    
    /**
     * Terminate this kernel. Never returns.
     */
    public void terminate() {
	super.terminate();
    }

    private PostOffice postOffice;
public static Condition pendingConnectionsCond;
	public static Lock connectionsLock;
	public static LinkedList<TransportFile>[] pendingConnections;
    // dummy variables to make javac smarter
    private static NetProcess dummy1 = null;
    private static ArrayList<Integer> availPorts = null;
    public static Lock portLock;
}
