package nachos.threads;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;

import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.threads.PriorityScheduler.PriorityQueue;
import nachos.threads.PriorityScheduler.ThreadState;

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
		super();
	}

	/**
	 * Allocate a new lottery thread queue.
	 * 
	 * @param transferPriority
	 *            <tt>true</tt> if this queue should transfer tickets from
	 *            waiting threads to the owning thread.
	 * @return a new lottery thread queue.
	 */
	@Override
	public ThreadQueue newThreadQueue(boolean transferPriority) {
		return new LotteryQueue(transferPriority);
	}
	@Override
	public void setPriority(KThread thread, int priority) {
		Lib.assertTrue(Machine.interrupt().disabled());

		Lib.assertTrue(priority >= priorityMinimum
				&& priority <= priorityMaximum);

		getThreadState(thread).setPriority(priority);
	}

	@Override
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

	@Override
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
	@Override
	protected ThreadState getThreadState(KThread thread) {
		if (thread.schedulingState == null)
			thread.schedulingState = new ThreadS(thread);

		return (ThreadState) thread.schedulingState;
	}

	/**
	 * A <tt>ThreadQueue</tt> that sorts threads by priority.
	 */
	
	
	protected class LotteryQueue extends PriorityScheduler.PriorityQueue {
		public LotteryQueue(boolean trans)
		{
			super(trans);
		}
		@Override
		public KThread nextThread() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// implement me
			ThreadS ts;
			if (holder != null)
			{
				holder.holdList.remove(this);
				holder.reCalEffectivePriority();
				Lib.debug('l', "New efc: " + holder.thread.getName() + ": " + holder.getEffectivePriority());
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
		@Override
		protected ThreadS pickNextThread() {	
			int priorityTot = 0;
			for (Iterator<ThreadState> it = queue.iterator(); it.hasNext();)
			{
				ThreadState ts = it.next();
				priorityTot += ts.efcPriority;
				//Lib.debug('p', ts.thread.getName() + " priority " + ts.priority + "efcPriority" + ts.efcPriority);
			}
			if (priorityTot == 0) return null;
			int c = ranNum.nextInt(priorityTot);
			for (Iterator<ThreadState> it = queue.iterator(); it.hasNext();)
			{
				ThreadState ts = it.next();
				if (c < ts.getEffectivePriority())
					return (ThreadS)ts;
				else c -= ts.getEffectivePriority();
				//Lib.debug('p', ts.thread.getName() + " priority " + ts.priority + "efcPriority" + ts.efcPriority);
			}
			//Lib.debug('p', "\n");
			return null;
		}
		protected Random ranNum = new Random();  
	}

	/**
	 * The scheduling state of a thread. This should include the thread's
	 * priority, its effective priority, any objects it owns, and the queue it's
	 * waiting for, if any.
	 * 
	 * @see nachos.threads.KThread#schedulingState
	 */
	protected class ThreadS extends PriorityScheduler.ThreadState {
		public ThreadS(KThread thread) {
			super(thread);
		}
		private void update2(ThreadS last, int p)
		{
			
			//System.out.println(this.thread.getName());
			efcPriority += p;
			if (belong != null && belong.holder != null && belong.transferPriority)
				((ThreadS)belong.holder).update2(last, p);
		}
		@Override
		public void updateEffectivePriority(int p)
		{
			Lib.debug('l', "updating the effective priority of " + this.thread.getName());
			if (p == 0) return;
			update2(this, p);
			Lib.debug('l', "New effective priority: " + this.thread.getName() + ":" + efcPriority);
		}
		@Override
		public int reCalEffectivePriority()
		{
			int oldefc = efcPriority;
			efcPriority = priority;
			for (PriorityQueue waitQueue: holdList)
			if (waitQueue.transferPriority)
				for (Iterator<ThreadState> it = waitQueue.queue.iterator(); it.hasNext();)
					efcPriority += it.next().getEffectivePriority();
			if (oldefc != efcPriority && belong != null && belong.holder != null && belong.transferPriority)
				((ThreadS)belong.holder).updateEffectivePriority(efcPriority - oldefc);
			return efcPriority;
		}

		/**
		 * Set the priority of the associated thread to the specified value.
		 * 
		 * @param priority
		 *            the new priority.
		 */
		@Override
		public void setPriority(int priority) {
			if (this.priority == priority)
				return;
			int oldPriority = this.priority;
			this.priority = priority;
			this.updateEffectivePriority(priority - oldPriority);
			// implement me
		}

		@Override
		public void waitForAccess(PriorityQueue waitQueue) {
			// implement me
			boolean intStatus = Machine.interrupt().disable();
			waitQueue.queue.add(this);
			belong = waitQueue;
			if (belong.holder != null && belong.transferPriority)
				((ThreadS)belong.holder).updateEffectivePriority(efcPriority);
			Machine.interrupt().setStatus(intStatus);
		}
		
		public void acquire(PriorityQueue waitQueue) {
			// implement me
			boolean intStatus = Machine.interrupt().disable();
			waitQueue.holder = this;
			holdList.add(waitQueue);
			belong = null;
			this.reCalEffectivePriority();
			Machine.interrupt().setStatus(intStatus);
		}

	}
	static protected int debugCount = 0; 
}
