package nachos.vm;

import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.OpenFile;
import nachos.machine.Processor;
import nachos.threads.ThreadedKernel;
import nachos.userprog.UserKernel;

import java.util.HashMap;
import java.util.LinkedList;

import nachos.machine.TranslationEntry;

public class SwapFile {
	
	public SwapFile()
	{
		swapFile = ThreadedKernel.fileSystem.open(swapName, true);
		mapping = new HashMap<IPair, TranslationEntry>();
		freePages = new LinkedList<Integer>();
		usedPages = new HashMap<IPair, Integer>();
		pageSize = Machine.processor().pageSize;
		//init?
	}
	
	void close()
	{
		swapFile.close();
		UserKernel.fileSystem.remove(swapName);
	}
	
	private int allocate()
	{
		if (freePages.isEmpty())
			freePages.add(pageCount++);
		return freePages.removeFirst();
	}
	
	public boolean remove(IPair p)
	{
		if (!usedPages.containsKey(p)) return false;
		freePages.add(usedPages.remove(p));
		mapping.remove(p);
		return true;
	}
	
	public int swapToFile(int pid, int vpn, TranslationEntry entry)
	{
		if (entry == null || entry.readOnly)
			return 0;
		Lib.debug(dbgVM, "swapping (" + pid + ", " + vpn + ") to file");
		IPair p = new IPair(pid, vpn);
		int page = usedPages.containsKey(p) ? usedPages.get(p) : allocate();
		mapping.put(p, entry);
		usedPages.put(p, new Integer(page));
		return swapFile.write(page * pageSize, Machine.processor().getMemory(), Processor.makeAddress(entry.ppn, 0), pageSize);
	}
	
	public TranslationEntry swapToMemory(int pid, int vpn, int ppn)
	{
		TranslationEntry entry = mapping.get(new IPair(pid, vpn));
		if (entry == null) return null;
		Lib.debug(dbgVM, "swapping (" + pid + ", " + vpn + ") to memory");
		int page = usedPages.get(new IPair(pid, vpn));
		swapFile.read(page * pageSize, Machine.processor().getMemory(), Processor.makeAddress(ppn, 0), pageSize);
		entry.ppn = ppn;
		entry.valid = true;
		entry.dirty = false;
		entry.used = false;
		return entry;
	}
	
	private HashMap<IPair, TranslationEntry> mapping;
	private LinkedList<Integer> freePages;
	private HashMap<IPair, Integer> usedPages;
	private int pageCount = 0;
	
	private OpenFile swapFile;
	private int pageSize;
	
	private final static String swapName = "SWAP";
	private static final char dbgVM = 'v';
}
