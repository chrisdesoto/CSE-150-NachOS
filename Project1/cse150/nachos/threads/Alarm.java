package nachos.threads;

import nachos.machine.*;

import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */

//Class to represent the time after which a thread should be woken and the thread to wake
class AlarmWrapper {
    public long wakeTime;
    public KThread this_thread;

    public AlarmWrapper(long wakeTime, KThread this_thread) {
        this.wakeTime = wakeTime;
        this.this_thread = this_thread;
    }
}

//Class used by the Priority Queue to order the AlarmWrapper objects
class AlarmComparator implements Comparator<AlarmWrapper> {
    public int compare(AlarmWrapper w1, AlarmWrapper w2) {
        return (int) (w1.wakeTime - w2.wakeTime);
    }
}

public class Alarm {
    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler to this
     * alarm's callback.
     * <p>
     * <p><b>Note</b>: Nachos will not function correctly with more than one
     * alarm.
     */
    public Alarm() {
        Machine.timer().setInterruptHandler(new Runnable() {
            public void run() {
                timerInterrupt();
            }
        });
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread
     * that should be run.
     */
    public void timerInterrupt() {
        //If there is a thread waiting on an alarm, then pull AlarmWrappers off the priority queue while their wake time
        //is less than our current time and set them to the ready state.
        if (!alarmQueue.isEmpty()) {
            while (!alarmQueue.isEmpty() && alarmQueue.peek().wakeTime <= Machine.timer().getTime()) {
                AlarmWrapper temp_wrapper = alarmQueue.remove();
                temp_wrapper.this_thread.ready();
            }
        }

        //Now that this thread is done doing its task, yield to give control back to other threads waiting for cycles
        KThread.currentThread().yield();
    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks,
     * waking it up in the timer interrupt handler. The thread must be
     * woken up (placed in the scheduler ready set) during the first timer
     * interrupt where
     * <p>
     * <p><blockquote>
     * (current time) >= (WaitUntil called time)+(x)
     * </blockquote>
     *
     * @param x the minimum number of clock ticks to wait.
     * @see nachos.machine.Timer#getTime()
     */
    public void waitUntil(long x) {
        boolean machine_start_status = Machine.interrupt().disabled();
        Machine.interrupt().disable();

        //Create an AlarmWrapper entry for the current thread, add it to the alarmQueue, then sleep and wait to be awoken
        long wakeTime = Machine.timer().getTime() + x;
        alarmQueue.add(new AlarmWrapper(wakeTime, KThread.currentThread()));
        KThread.sleep();

        Machine.interrupt().restore(machine_start_status);
    }


    private static class AlarmTest implements Runnable {
        public AlarmTest(long waitTime) {
            this.waitTime = waitTime;
        }

        public void run() {
            Alarm x = new Alarm();
            long wakeTime = Machine.timer().getTime() + waitTime;
            System.out.println("Going to sleep, gonna wake up after " + wakeTime);
            x.waitUntil(waitTime);
            System.out.println("Woke up at: " + Machine.timer().getTime());
            Lib.assertTrue(Machine.timer().getTime() >= wakeTime);
        }

        private long waitTime;
    }

    /**
     * Test that this module is working.
     */
    public static void selfTest() {
        System.out.println("***Beginning the Alarm selfTests***");

        KThread toAlarm = new KThread(new AlarmTest(2000));
        toAlarm.fork();
        toAlarm.join();

        KThread underBound = new KThread(new AlarmTest(200));
        underBound.fork();
        underBound.join();

        KThread slightlyOver = new KThread(new AlarmTest(501));
        slightlyOver.fork();
        slightlyOver.join();

        KThread slightlyUnder = new KThread(new AlarmTest(999));
        slightlyUnder.fork();
        slightlyUnder.join();

        KThread noWaiting = new KThread(new AlarmTest(0));
        noWaiting.fork();
        noWaiting.join();

        System.out.println("***Finished the Alarm selfTests***");
    }

    //Instantiate a comparator object for the AlarmWrappers and an alarmQueue to hold the AlarmWrappers corresponding to
    //threads waiting to be awoken
    private AlarmComparator comp = new AlarmComparator();
    private PriorityQueue<AlarmWrapper> alarmQueue = new PriorityQueue<AlarmWrapper>(1, comp);
}


