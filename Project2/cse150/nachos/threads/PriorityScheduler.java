package nachos.threads;

import nachos.machine.*;

import java.util.Comparator;
import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;

/**
 * A scheduler that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the
 * thread that has been waiting longest.
 *
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has
 * the potential to
 * starve a thread if there's always a thread waiting with higher priority.
 *
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
    /**
     * Allocate a new priority scheduler.
     */
    public PriorityScheduler() {
    }

    /**
     * Allocate a new priority thread queue.
     *
     * @param	transferPriority	<tt>true</tt> if this queue should
     *					transfer priority from waiting threads
     *					to the owning thread.
     * @return	a new priority thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
        return new PriorityQueue(transferPriority);
    }

    public int getPriority(KThread thread) {
        Lib.assertTrue(Machine.interrupt().disabled());

        return getThreadState(thread).getPriority();
    }

    public int getEffectivePriority(KThread thread) {
        Lib.assertTrue(Machine.interrupt().disabled());

        return getThreadState(thread).getEffectivePriority();
    }

    public void setPriority(KThread thread, int priority) {
        Lib.assertTrue(Machine.interrupt().disabled());

        Lib.assertTrue(priority >= priorityMinimum &&
                priority <= priorityMaximum);

        getThreadState(thread).setPriority(priority);
    }

    public boolean increasePriority() {
        boolean intStatus = Machine.interrupt().disable();

        KThread thread = KThread.currentThread();

        int priority = getPriority(thread);
        if (priority == priorityMaximum)
            return false;

        setPriority(thread, priority+1);

        Machine.interrupt().restore(intStatus);
        return true;
    }

    public boolean decreasePriority() {
        boolean intStatus = Machine.interrupt().disable();

        KThread thread = KThread.currentThread();

        int priority = getPriority(thread);
        if (priority == priorityMinimum)
            return false;

        setPriority(thread, priority-1);

        Machine.interrupt().restore(intStatus);
        return true;
    }

    /**
     * The default priority for a new thread. Do not change this value.
     */
    public int priorityDefault = 1;
    /**
     * The minimum priority that a thread can have. Do not change this value.
     */
    public int priorityMinimum = 0;
    /**
     * The maximum priority that a thread can have. Do not change this value.
     */
    public int priorityMaximum = 7;

    /**
     * Return the scheduling state of the specified thread.
     *
     * @param	thread	the thread whose scheduling state to return.
     * @return	the scheduling state of the specified thread.
     */
    protected ThreadState getThreadState(KThread thread) {
        Lib.assertTrue(Machine.interrupt().disabled());
        if (thread.schedulingState == null)
            thread.schedulingState = new ThreadState(thread);

        return (ThreadState) thread.schedulingState;
    }

    /**
     * A <tt>ThreadQueue</tt> that sorts threads by priority.
     */
    protected class PriorityQueue extends ThreadQueue {
        PriorityQueue(boolean transferPriority) {
            this.transferPriority = transferPriority;
        }
        PriorityQueue(){this.transferPriority = true;}
        public void waitForAccess(KThread thread) {
            Lib.assertTrue(Machine.interrupt().disabled());
            getThreadState(thread).waitForAccess(this);
        }

        public void acquire(KThread thread) {
            Lib.assertTrue(Machine.interrupt().disabled());
            getThreadState(thread).acquire(this);
        }
        public ThreadState popNextThread()
        {
            return threadStates.pollLast();
        }
        public KThread nextThread() {
            Lib.assertTrue(Machine.interrupt().disabled());
            if (threadStates.isEmpty())
                return null;

            //Pulls the thread of highest priority and earliest time off the treeset
            ThreadState tState = popNextThread();

            tState.placement = 0;
            KThread thread = tState.thread;


            if (thread != null)
            {
                if (this.owner != null)
                {
                    //Remove this from the old owners' queue
                    this.owner.ownedQueues.remove(this);
                    this.owner.effectivePriority = 0;

                    //Update the effective priority of the old owner
                    Iterator<PriorityQueue> it = this.owner.ownedQueues.iterator();
                    while(it.hasNext())
                    {
                        PriorityQueue temp = it.next();

                        if (temp.pickNextThread() == null)
                            continue;
                        if(temp.pickNextThread().getWinningPriority() > this.owner.getEffectivePriority())
                            this.owner.effectivePriority = temp.pickNextThread().getWinningPriority();
                    }
                }

                //Now thread is going to run, so it should acquire this waitQueue and it shouldnt be waiting on anything else
                ((ThreadState) thread.schedulingState).acquire(this);
                ((ThreadState)thread.schedulingState).waitingQueue = null;
            }

            return thread;
        }

        /**
         * Return the next thread that <tt>nextThread()</tt> would return,
         * without modifying the state of this queue.
         *
         * @return	the next thread that <tt>nextThread()</tt> would
         *		return.
         */
        protected ThreadState pickNextThread() {
            Lib.assertTrue(Machine.interrupt().disabled());
            if (threadStates.isEmpty())
                return null;

            return threadStates.last();
        }

        public void print() {
            Lib.assertTrue(Machine.interrupt().disabled());

            Iterator<ThreadState> it = threadStates.descendingIterator();
            System.out.println("*************************");
            int i = 0;
            while (it.hasNext())
            {
                ThreadState curr = it.next();
                System.out.println(curr.thread + " has priority " + curr.getWinningPriority() + " and time " + curr.time);
                i++;
            }
            //if (pickNextThread() != null)
            //System.out.println("Next thread to be popped is " + pickNextThread().thread);
            if (owner != null)
                System.out.println("Owner of queue = " + owner.thread + " with effective priority = " + owner.getWinningPriority());
            else
                System.out.println("Owner of queue = null");
            System.out.println("**************************");
        }

        /**
         * <tt>true</tt> if this queue should transfer priority from waiting
         * threads to the owning thread.
         */
        public boolean transferPriority;

        // TreeSet to hold the threadStates waiting in this queue and a variable to indicate the owner of the queue
        public TreeSet<ThreadState> threadStates = new TreeSet<ThreadState>(new ThreadComparator());
        public ThreadState owner = null;

    }


    private static class PriSchedTest implements Runnable {
        public PriSchedTest(int expectedOrder) {
            this.order = expectedOrder;
        }

        public void run() {
            System.out.println("Hi from " + this.order);
        }

        private int order;
    }

    /**
     * Test that this module is working.
     */
    public static void selfTest() {
        System.out.println("+++Beginning tests for priority scheduler+++");
        int numTests = 5;
        int count = 0;

        boolean machine_start_status = Machine.interrupt().disabled();
        Machine.interrupt().disable();

        PriorityScheduler p = new PriorityScheduler();

        ThreadQueue t = p.newThreadQueue(true);
        ThreadQueue t1 = p.newThreadQueue(true);
        ThreadQueue t2 = p.newThreadQueue(true);
        ThreadQueue masterQueue = p.newThreadQueue(true);

        KThread tOwn = new KThread();
        KThread t1Own = new KThread();
        KThread t2Own = new KThread();

        t.acquire(tOwn);
        t1.acquire(t1Own);
        t2.acquire(t2Own);


        for (int i = 0; i < 3; i++) {
            for (int j = 1; j <= 3; j++) {
                KThread curr = new KThread();

                if (i == 0)
                    t.waitForAccess(curr);
                else if (i == 1)
                    t1.waitForAccess(curr);
                else
                    t2.waitForAccess(curr);

                p.setPriority(curr, j);
            }
        }

        KThread temp = new KThread();
        t.waitForAccess(temp);
        p.getThreadState(temp).setPriority(4);

        temp = new KThread();
        t1.waitForAccess(temp);
        p.getThreadState(temp).setPriority(5);

        temp = new KThread();
        t2.waitForAccess(temp);
        p.getThreadState(temp).setPriority(7);

        temp = new KThread();

        masterQueue.waitForAccess(tOwn);
        masterQueue.waitForAccess(t1Own);
        masterQueue.waitForAccess(t2Own);

        masterQueue.acquire(temp);

        System.out.println("master queue: "); masterQueue.print();
//        System.out.println("t queue: "); t.print();
//        System.out.println("t1 queue: "); t1.print();
//        System.out.println("t2 queue: "); t2.print();
//
//        System.out.println("tOwn effective priority = " + p.getThreadState(tOwn).getEffectivePriority() + " and priority = " + p.getThreadState(tOwn).getPriority());
        System.out.println("temp's effective priority = " + p.getThreadState(temp).getEffectivePriority());

        System.out.println("After taking away max from master queue");
        KThread temp1 = masterQueue.nextThread();
        masterQueue.print();
        System.out.println("temp's effective priority = " + p.getThreadState(temp).getEffectivePriority() + " and it's priority = " + p.getThreadState(temp).getPriority());
        System.out.println("nextThread's effective priority = " + p.getThreadState(temp1).getEffectivePriority());
        Machine.interrupt().restore(machine_start_status);
//        while(count < numTests) {
//            KThread toRun = new KThread(new PriSchedTest(count++));
//            System.out.println(toRun);
//            t.waitForAccess(toRun);
//            p.getThreadState(toRun).setPriority(numTests-count);
//            toRun.fork();
//        }
//        count = 0;
//        while(count++ < numTests) {
//            KThread next = t.nextThread();
//            Lib.assertTrue(p.getThreadState(next).getPriority() == numTests-count);
//            System.out.println("Priority is " + p.getThreadState(next).getPriority());
//        }
    }



    /**
     * The scheduling state of a thread. This should include the thread's
     * priority, its effective priority, any objects it owns, and the queue
     * it's waiting for, if any.
     *
     * @see	nachos.threads.KThread#schedulingState
     */
    protected class ThreadState {
        ThreadState(){}
        /**
         * Allocate a new <tt>ThreadState</tt> object and associate it with the
         * specified thread.
         *
         * @param	thread	the thread this state belongs to.
         */
        public ThreadState(KThread thread) {
            this.thread = thread;
            this.time = 0;
            this.placement = 0;
            this.priority = priorityDefault;
            setPriority(priorityDefault);
            effectivePriority = priorityMinimum;
        }

        /**
         * Return the priority of the associated thread.
         *
         * @return	the priority of the associated thread.
         */
        public int getPriority() {
            return priority;
        }

        /**
         * Return the effective priority of the associated thread.
         *
         * @return	the effective priority of the associated thread.
         */
        public int getEffectivePriority() {
            Lib.assertTrue(Machine.interrupt().disabled());


            return getWinningPriority();
        }

        // Further implementation on this can involve caching for get effective priority
        /**
         * Set the priority of the associated thread to the specified value.
         *
         * @param	priority	the new priority.
         */
        public void setPriority(int priority) {
            Lib.assertTrue(Machine.interrupt().disabled());
            if (this.priority == priority)
                return;

            this.priority = priority;
            recalculateThreadScheduling();
            update();
        }

        public int getWinningPriority()
        {
            if (this.priority > this.effectivePriority)
                return priority;
            else
                return effectivePriority;
        }

        /**
         * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
         * the associated thread) is invoked on the specified priority queue.
         * The associated thread is therefore waiting for access to the
         * resource guarded by <tt>waitQueue</tt>. This method is only called
         * if the associated thread cannot immediately obtain access.
         *
         * @param	waitQueue	the queue that the associated thread is
         *				now waiting on.
         *
         * @see	nachos.threads.ThreadQueue#waitForAccess
         */
        public void waitForAccess(PriorityQueue waitQueue) {
            Lib.assertTrue(Machine.interrupt().disabled());
            Lib.assertTrue(waitingQueue == null);

            time = Machine.timer().getTime();
            waitQueue.threadStates.add(this);
            waitingQueue = waitQueue;

            if(placement == 0)
                placement = placementInc++;

            update();
        }

        /**
         * Called when the associated thread has acquired access to whatever is
         * guarded by <tt>waitQueue</tt>. This can occur either as a result of
         * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
         * <tt>thread</tt> is the associated thread), or as a result of
         * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
         *
         * @see	nachos.threads.ThreadQueue#acquire
         * @see	nachos.threads.ThreadQueue#nextThread
         */
        public void acquire(PriorityQueue waitQueue) {
            Lib.assertTrue(Machine.interrupt().disabled());
            if (waitQueue.owner != null)
                waitQueue.owner.ownedQueues.remove(waitQueue);

            waitQueue.owner = this;
            ownedQueues.add(waitQueue);

            if (waitQueue.pickNextThread() == null)
                return;

            if (waitQueue.pickNextThread().getEffectivePriority() > this.getEffectivePriority() && waitQueue.transferPriority)
            {
                this.effectivePriority = waitQueue.pickNextThread().getEffectivePriority();
                recalculateThreadScheduling();
                update();
            }

        }

        public void update() {
            //Re-balances the thread scheduling

            if (waitingQueue == null)
                return;
            else if (waitingQueue.owner == null)
                return;
            else if (waitingQueue.pickNextThread() == null)
                return;

            if (waitingQueue.transferPriority && waitingQueue.pickNextThread().getWinningPriority() > waitingQueue.owner.getWinningPriority())
            {
                waitingQueue.owner.effectivePriority = waitingQueue.pickNextThread().getWinningPriority();
                waitingQueue.owner.recalculateThreadScheduling();
                waitingQueue.owner.update();
            }
        }

        @Override
        public boolean equals(Object o)
        {
            ThreadState curr = (ThreadState)o;

            return (curr.placement == this.placement);
        }

        public void recalculateThreadScheduling()
        {
            Lib.assertTrue(Machine.interrupt().disabled());

            //Updates the ordering of the element in the tree
//            if (waitingQueue != null)
//            {
//                waitingQueue.threadStates.remove(this);
//                waitingQueue.threadStates.add(this);
//            }

            boolean passed = false;
            if (waitingQueue != null)
            {
                Iterator<ThreadState> it = waitingQueue.threadStates.iterator();

                while(it.hasNext())
                {
                    ThreadState curr = it.next();

                    if (curr.placement == this.placement)
                    {
                        it.remove();
                        passed = true;
                        break;
                    }
                }

                if (passed)
                    waitingQueue.threadStates.add(this);
            }

        }

        /** The thread with which this object is associated. */
        protected KThread thread;
        /** The priority of the associated thread. */
        protected int priority;

        // Added variables
        public long time = 0;
        public int effectivePriority;

        //Effectively used as a unique ID for threadStates
        public int placement;

        //Represents the queues this thread owns and the queue of the resource this thread waits on
        public HashSet<PriorityQueue> ownedQueues = new HashSet<PriorityQueue>();
        public PriorityQueue waitingQueue = null;
    }

    //Effectively represents the ID of the next ThreadState to initially call waitForAccess
    public static int placementInc = 1;

    //Used in creating the Treeset for the waitQueue
    class ThreadComparator implements Comparator<ThreadState>
    {
        public int compare(ThreadState a, ThreadState b)
        {
            if (a.getWinningPriority() == b.getWinningPriority() && a.time != b.time)
            {
                //Time is in reverse order since we want the minimum time to be ordered above the maximum time
                return (int)((b.time-a.time));
            }
            else if (a.getWinningPriority() != b.getWinningPriority())
            {
                return a.getWinningPriority() - b.getWinningPriority();
            }
            else
            {
                return a.placement-b.placement;
            }
        }
    }
}
