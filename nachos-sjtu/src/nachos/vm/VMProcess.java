package nachos.vm;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Random;
import nachos.threads.Lock;

import nachos.machine.Kernel;
import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.Processor;
import nachos.machine.TranslationEntry;
import nachos.userprog.UserProcess;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
		if (pageInfo == null)
		{
			lock = new Lock();
			pageInfo = new PagePair[Machine.processor().getNumPhysPages()];
			freePages = new LinkedList<Integer>();
			phyPageTot = Machine.processor().getNumPhysPages();
			for (int i = 0; i < phyPageTot; ++i)
				freePages.add(new Integer(i));
			swapFile = new SwapFile();
			random = new Random();
			tlbSize = Machine.processor().getTLBSize();
			pageTable = new HashMap<IPair, TranslationEntry>();
			
			/*maxCount = 10 * phyPageTot;
			useCount = new int[phyPageTot];
			useRecord = new LinkedList<IPair>();
			enterTime = new int[phyPageTot];*/
		}
	}
	
	private boolean removePage(int ppn)
	{
		Lib.debug(dbgVM, "Removing Page ppn:" + ppn);
		if (pageInfo[ppn].pid == processID)
			invalidate(pageInfo[ppn].entry.vpn);
		freePages.add(new Integer(ppn));
		PagePair pp = pageInfo[ppn];
		pageTable.remove(new IPair(pp.pid, pp.entry.vpn));
		swapFile.swapToFile(pp.pid, pp.entry.vpn, pp.entry);
		return true;
	}
	
	private boolean removePages()
	{
		for (int i = 0; i < numPages; ++i)
		{
			IPair p = new IPair(processID, i);
			if (pageTable.containsKey(p))
			{
				TranslationEntry entry = pageTable.get(p);
				pageTable.remove(p);
				freePages.add(entry.ppn);
			}
			swapFile.remove(p);
		}
		return true;
	}

	private int allocatePage()
	{
		if (freePages.isEmpty())
		{
			int ppn = getPageVictim();
			removePage(ppn);
		}
		return freePages.removeFirst();

	}
	
	public void writeBackPageTable(TranslationEntry entry)
	{
		pageTable.put(new IPair(processID, entry.vpn), entry);
	}
	
	@Override
	public TranslationEntry getPageEntry(int vpn)
	{
		TranslationEntry t = pageTable.get(new IPair(processID, vpn));
		if (t != null) return t;
		handlePageFault(vpn);
		t = pageTable.get(new IPair(processID, vpn));
		//int ppn = t.ppn;
		//useCount[ppn]++;
		//useRecord.addLast(new IPair(ppn, (int)Machine.timer().getTime()));
		return t;
	}
	
	private int getPageVictim()
	{
		return random.nextInt(phyPageTot);
		/*while (useRecord.size() > 0 && useRecord.pollFirst().second > Machine.timer().getTime() - maxCount)
		{
			IPair p = useRecord.removeFirst();
			int ppn = p.first, time = p.second;
			if (time >= enterTime[ppn])
				useCount[ppn]--;
		}
		int j = 0;
		for (int i = 1; i < phyPageTot; i++)
			if (useCount[i] < useCount[j])
				j = i;
		return j;*/
	}
	
	void handlePageFault(int vpn)
	{
		lock.acquire();
		pageFaultCount++;
		int ppn = allocatePage();
		Lib.debug(dbgVM, "Handling page fault pid: " + processID + " vpn: " + vpn);
		TranslationEntry entry = swapFile.swapToMemory(processID, vpn, ppn);
		if (entry == null)
			entry = loader.loadSection(vpn, ppn);
		pageTable.put(new IPair(processID, vpn), entry);
		pageInfo[ppn] = new PagePair(processID, entry);
		/*enterTime[ppn] = (int)Machine.timer().getTime();
		useCount[ppn] = 0;*/
		lock.release();
		return;
	}

	public void invalidate(int vpn)
	{
		for (int i = 0; i < tlbSize; ++i)
		{
			TranslationEntry entry = Machine.processor().readTLBEntry(i);
			if (entry.valid && entry.vpn == vpn)
			{
				writeBackTLBEntry(i);
				entry.valid = false;
				Machine.processor().writeTLBEntry(i, entry);
			}
		}
	}
	
	public void invalidateAll()
	{
		for (int i = 0; i < tlbSize; ++i)
		{
			TranslationEntry entry = Machine.processor().readTLBEntry(i);
			if (entry.valid)
			{
				writeBackTLBEntry(i);
				entry.valid = false;
				Machine.processor().writeTLBEntry(i, entry);
			}
		}
	}
	
	
	public void writeBackTLBEntry(int i)
	{
		TranslationEntry entry = Machine.processor().readTLBEntry(i);
		if (!entry.valid) 
			return;
		writeBackPageTable(entry);
	}
	

	public boolean handleTLBMiss(int vpn)
	{
		Lib.debug(dbgVM, "Handling TLB Miss pid: " + processID + ", vpn:" + vpn);
		TranslationEntry entry = getPageEntry(vpn);
		tlbMissCount++;
		if (entry == null) 
		{
			return false;
		}
		int i = getTLBVictim(); 
		writeBackTLBEntry(i);
		Machine.processor().writeTLBEntry(i, entry);
		return true;
	}
	

	private int getTLBVictim()
	{
		for (int i = 0; i < tlbSize; ++i)
		{
			TranslationEntry entry = Machine.processor().readTLBEntry(i);
			if (!entry.valid)
				return i;
		}
		//return random.nextInt(tlbSize);
		while (true)
		{
			TranslationEntry entry = Machine.processor().readTLBEntry(tlbVicHead);
			if (!entry.used)
				return tlbVicHead;
			entry.used = false;
			Machine.processor().writeTLBEntry(tlbVicHead, entry);
			tlbVicHead = (tlbVicHead + 1) % tlbSize;
		}
	}
	
	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	@Override
	public void saveState() {
		invalidateAll();
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		//System.out.println("restoreState " + processID);
	}
	
	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	@Override
	protected boolean loadSections() {
		loader = new LazyLoader(coff);
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	@Override
	protected void unloadSections() {
		Lib.debug(dbgVM, "unloading process " + processID);
		invalidateAll();
		removePages();
		coff.close();
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause
	 *            the user exception that occurred.
	 */
	public void handleException(int cause) {
		switch (cause) {
		case Processor.exceptionTLBMiss:
			handleTLBMiss(Processor.pageFromAddress(Machine.processor().readRegister(Processor.regBadVAddr)));
			break;
		case Processor.exceptionPageFault:
			handlePageFault(Processor.pageFromAddress(Machine.processor().readRegister(Processor.regBadVAddr)));
			break;
		default:
			super.handleException(cause);
			break;
		}
	}
	private static final int pageSize = Processor.pageSize;
	private static final char dbgProcess = 'a';
	private static final char dbgVM = 'v';
	private LazyLoader loader;
	static private Random random;
	static private HashMap<IPair, TranslationEntry> pageTable;
	static private PagePair pageInfo[];
	
/*	static private int useCount[];
	static private LinkedList<IPair> useRecord;
	static private int enterTime[];
	static private int maxCount;*/
	
	static private int phyPageTot;
	static private LinkedList<Integer> freePages;
	static private int tlbSize;
	static SwapFile swapFile;
	static Lock lock;
	static public int tlbMissCount = 0, pageFaultCount = 0, tlbVicHead = 0; 
}
