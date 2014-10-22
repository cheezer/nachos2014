package nachos.threads;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;

import nachos.machine.Lib;
import nachos.machine.Machine;

/**
 * A scheduler that chooses threads using a lottery.
 * 
 * <p>
 * A lottery scheduler associates a number of tickets with each thread. When a
 * thread needs to be dequeued, a random lottery is held, among all the tickets
 * of all the threads waiting to be dequeued. The thread that holds the winning
 * ticket is chosen.
 * 
 * <p>
 * Note that a lottery scheduler must be able to handle a lot of tickets
 * (sometimes billions), so it is not acceptable to maintain state for every
 * ticket.
 * 
 * <p>
 * A lottery scheduler must partially solve the priority inversion problem; in
 * particular, tickets must be transferred through locks, and through joins.
 * Unlike a priority scheduler, these tickets add (as opposed to just taking the
 * maximum).
 */
public class LotteryScheduler extends PriorityScheduler {
	/**
	 * Allocate a new lottery scheduler.
	 */
	public LotteryScheduler() {
	}

	/**
	 * Allocate a new lottery thread queue.
	 * 
	 * @param transferPriority
	 *            <tt>true</tt> if this queue should transfer tickets from
	 *            waiting threads to the owning thread.
	 * @return a new lottery thread queue.
	 */

	public ThreadQueue newThreadQueue(boolean transferPriority) {
		return new LotteryQueue(transferPriority);
	}

	public int getPriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadS(thread).getPriority();
	}

	public int getEffectivePriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadS(thread).getEffectivePriority();
	}

	public void setPriority(KThread thread, int priority) {
		Lib.assertTrue(Machine.interrupt().disabled());

		Lib.assertTrue(priority >= priorityMinimum
				&& priority <= priorityMaximum);

		getThreadS(thread).setPriority(priority);
	}

	public boolean increasePriority()
	{
		boolean intStatus = Machine.interrupt().disable();

		KThread thread = KThread.currentThread();
		
		int priority = getPriority(thread);
		if (priority == priorityMaximum)
		{
		  Machine.interrupt().restore(intStatus); // bug identified by Xiao Jia @ 2011-11-04
			return false;
		}

		setPriority(thread, priority + 1);

		Machine.interrupt().restore(intStatus);
		return true;
	}

	public boolean decreasePriority()
	{
		boolean intStatus = Machine.interrupt().disable();

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMinimum)
		{
		  Machine.interrupt().restore(intStatus); // bug identified by Xiao Jia @ 2011-11-04
			return false;
		}

		setPriority(thread, priority - 1);

		Machine.interrupt().restore(intStatus);
		return true;
	}

	/**
	 * The default priority for a new thread. Do not change this value.
	 */
	public static final int priorityDefault = 1;
	/**
	 * The minimum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMinimum = 1;
	/**
	 * The maximum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMaximum = Integer.MAX_VALUE;

	/**
	 * Return the scheduling state of the specified thread.
	 * 
	 * @param thread
	 *            the thread whose scheduling state to return.
	 * @return the scheduling state of the specified thread.
	 */
	protected ThreadS getThreadS(KThread thread) {
		if (thread.schedulingState == null)
			thread.schedulingState = new ThreadS(thread);

		return (ThreadS) thread.schedulingState;
	}

	/**
	 * A <tt>ThreadQueue</tt> that sorts threads by priority.
	 */
	
	
	protected class LotteryQueue extends ThreadQueue {
		LotteryQueue(boolean transferPriority) {
			this.transferPriority = transferPriority;
		}

		public void waitForAccess(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadS(thread).waitForAccess(this);
		}

		public void acquire(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadS(thread).acquire(this);
		}

		public KThread nextThread() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// implement me
			ThreadS ts;
			if (holder != null)
			{
				holder.holdList.remove(this);
				holder.reCalEffectivePriority();
			}
			if ((ts = pickNextThread()) != null)
			{
				ts.acquire(this);
				queue.remove(ts);
				return ts.thread;
			}
			else
			{
				holder = null;
				return null;
			}
		}

		/**
		 * Return the next thread that <tt>nextThread()</tt> would return,
		 * without modifying the state of this queue.
		 * 
		 * @return the next thread that <tt>nextThread()</tt> would return.
		 */
		protected ThreadS pickNextThread() {
			// implement me
			ThreadS ans = null;
			int priorityTot = 0;
			for (Iterator<ThreadS> it = queue.iterator(); it.hasNext();)
			{
				ThreadS ts = it.next();
				priorityTot += ts.efcPriority;
				//Lib.debug('p', ts.thread.getName() + " priority " + ts.priority + "efcPriority" + ts.efcPriority);
			}
			int c = ranNum.nextInt(priorityTot);
			for (Iterator<ThreadS> it = queue.iterator(); it.hasNext();)
			{
				ThreadS ts = it.next();
				if (ts.getEffectivePriority() < c)
					return ts;
				else c -= ts.getEffectivePriority();
				//Lib.debug('p', ts.thread.getName() + " priority " + ts.priority + "efcPriority" + ts.efcPriority);
			}
			//Lib.debug('p', "\n");
			return ans;
		}

		public void print() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// implement me (if you want)
			for (Iterator<ThreadS> it = queue.iterator(); it.hasNext();)
			{
				ThreadS ts = it.next();
				System.out.println(ts.thread + ", " + ts.getPriority() + ", " + ts.getEffectivePriority());
			}
		}

		/**
		 * <tt>true</tt> if this queue should transfer priority from waiting
		 * threads to the owning thread.
		 */
		public boolean transferPriority;
		protected ThreadS holder = null;
		protected LinkedList<ThreadS> queue = new LinkedList<ThreadS>();
		protected Random ranNum = new Random();  
	}

	/**
	 * The scheduling state of a thread. This should include the thread's
	 * priority, its effective priority, any objects it owns, and the queue it's
	 * waiting for, if any.
	 * 
	 * @see nachos.threads.KThread#schedulingState
	 */
	protected class ThreadS {
		/**
		 * Allocate a new <tt>ThreadS</tt> object and associate it with the
		 * specified thread.
		 * 
		 * @param thread
		 *            the thread this state belongs to.
		 */
		public ThreadS(KThread thread) {
			this.thread = thread;
			
			setPriority(priorityDefault);
		}

		/**
		 * Return the priority of the associated thread.
		 * 
		 * @return the priority of the associated thread.
		 */
		public int getPriority() {
			return priority;
		}

		/**
		 * Return the effective priority of the associated thread.
		 * 
		 * @return the effective priority of the associated thread.
		 */
		public int getEffectivePriority() {
			// implement me
			return efcPriority;
		}
		
		public void updateEffectivePriority(int p)
		{
			if (p == 0) return;
			efcPriority += p;
			if (belong != null && belong.holder != null)
				belong.holder.updateEffectivePriority(p);
		}
		
		public int reCalEffectivePriority()
		{
			int oldefc = efcPriority;
			efcPriority = priority;
			for (LotteryQueue waitQueue: holdList)
			if (waitQueue.transferPriority)
				for (Iterator<ThreadS> it = waitQueue.queue.iterator(); it.hasNext();)
					efcPriority += it.next().getEffectivePriority();
			if (oldefc != efcPriority && belong != null && belong.holder != null)
				belong.holder.updateEffectivePriority(efcPriority - oldefc);
			return efcPriority;
		}

		/**
		 * Set the priority of the associated thread to the specified value.
		 * 
		 * @param priority
		 *            the new priority.
		 */
		public void setPriority(int priority) {
			if (this.priority == priority)
				return;
			int oldPriority = this.priority;
			this.priority = priority;
			updateEffectivePriority(priority - oldPriority);
			// implement me
		}

		/**
		 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
		 * the associated thread) is invoked on the specified priority queue.
		 * The associated thread is therefore waiting for access to the resource
		 * guarded by <tt>waitQueue</tt>. This method is only called if the
		 * associated thread cannot immediately obtain access.
		 * 
		 * @param waitQueue
		 *            the queue that the associated thread is now waiting on.
		 * 
		 * @see nachos.threads.ThreadQueue#waitForAccess
		 */
		public void waitForAccess(LotteryQueue waitQueue) {
			// implement me
			boolean intStatus = Machine.interrupt().disable();
			waitQueue.queue.add(this);
			belong = waitQueue;
			if (belong.holder != null)
				belong.holder.updateEffectivePriority(efcPriority);
			Machine.interrupt().setStatus(intStatus);
		}

		/**
		 * Called when the associated thread has acquired access to whatever is
		 * guarded by <tt>waitQueue</tt>. This can occur either as a result of
		 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
		 * <tt>thread</tt> is the associated thread), or as a result of
		 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
		 * 
		 * @see nachos.threads.ThreadQueue#acquire
		 * @see nachos.threads.ThreadQueue#nextThread
		 */
		public void acquire(LotteryQueue waitQueue) {
			// implement me
			boolean intStatus = Machine.interrupt().disable();
			waitQueue.holder = this;
			holdList.add(waitQueue);
			belong = null;
			reCalEffectivePriority();
			Machine.interrupt().setStatus(intStatus);
		}

		/** The thread with which this object is associated. */
		protected KThread thread;
		/** The priority of the associated thread. */
		protected int priority, efcPriority;
		protected long enterTime = 0;
		protected LinkedList<LotteryQueue> holdList = new LinkedList<LotteryQueue>();
		protected LotteryQueue belong = null;
	}
}
