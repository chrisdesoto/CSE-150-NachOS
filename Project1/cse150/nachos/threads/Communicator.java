package nachos.threads;

import nachos.machine.*;

import javax.swing.plaf.basic.BasicSplitPaneUI;
import java.util.LinkedList;
import java.util.List;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {
    /**
     * Allocate a new communicator.
     */
    public Communicator() {
        sound = null;
        mic = new Lock();
        speakers = new Condition2(mic);
        listeners = new Condition2(mic);
    }

    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     *
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param	word	the integer to transfer.
     */

    public void speak(int word) {
        mic.acquire();

        //While other threads are speaking we should sleep
        while(sound != null) {
            speakers.sleep();
        }

        //There arent any other speakers currently, so this thread may speak
        sound = word;

        //Wakes up the waiting listener, just in case they called listen before this thread called speak
        listeners.wake();

        //Sleep to wait for the listener to consume the word, then wait for it to wake this back up
        //Also accounts for the case where speak is called before a thread calls listen
        speakers.sleep();

        mic.release();
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return	the integer transferred.
     */    
    public int listen() {
        int toReturn;

        mic.acquire();

        //Sleeps while we are waiting for a thread to speak
        while(sound == null){
            listeners.sleep();
        }

        //We made it here, so a thread must have spoken, lets save the word they spoke
        //before we delete it, so we can return the value
        toReturn = sound.intValue();

        //Since we are only concerned with 1 to 1 speak to listen functionality, we will
        //set sound to null so other listeners dont hear it
        sound = null;

        //Wake up the speaker so they know we heard them and can return
        speakers.wakeAll();

        mic.release();

        return toReturn;
    }

    private static Integer sound;
    Lock mic;
    Condition2 speakers;
    Condition2 listeners;

    private static class CommunicatorTest implements Runnable {
        public CommunicatorTest(Communicator c)
        {
            this.c = c;
            this.toSay = null;
        }
        public CommunicatorTest(Communicator c, int toSay)
        {
            this.c = c;
            this.toSay = toSay;
        }

        public void run() {
            if(toSay == null) {
                System.out.println(KThread.currentThread() + " is listening");
                System.out.println(KThread.currentThread() + "Heard " + c.listen());
            } else {
                System.out.println(KThread.currentThread() + " is trying to say " + toSay.intValue());
                c.speak(toSay.intValue());
                System.out.println(KThread.currentThread() + " finished speaking");
            }
        }

        Communicator c;
        Integer toSay;
    }

    /**
     * Test that this module is working.
     */
    public static void selfTest() {
        Communicator c = new Communicator();
        Alarm a = new Alarm();

        System.out.println("\nCommunicator performing many listen first then one speak test");
        int toSayVal = 42;
        new KThread(new CommunicatorTest(c)).setName("ListenerThread1").fork();
        KThread.yield();

        System.out.println(KThread.currentThread() + " is trying to say " + toSayVal);
        c.speak(toSayVal);
        System.out.println(KThread.currentThread() + " finished speaking");


        KThread.yield();

        System.out.println("\nCommunicator performing many speak first then one listen speak test");
        boolean passedManySOneL = true;
        int[] toSay = {1,2,3,4,5,6,7,8,9,10,11,12,13,14,15};
        for(int i : toSay) {
            new KThread(new CommunicatorTest(c,i)).setName("SpeakerThread" + i).fork();
            a.waitUntil(500);
        }
        for(int i : toSay) {
            System.out.println(KThread.currentThread() + " is Listening");
            int heard = c.listen();
            System.out.println(KThread.currentThread() + " heard " + heard + ", looking for " + i);

            passedManySOneL &= i==heard;
            Lib.assertTrue(i == heard);

        }
        if(passedManySOneL) {
            System.out.println("[+]Passed the many concurrent speakers test");
        } else {
            System.out.println("[-]Failed the many concurrent speakers test");

        }
    }
}
