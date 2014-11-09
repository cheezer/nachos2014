package nachos.vm;

import nachos.machine.TranslationEntry;

public class PagePair {
	PagePair(int pid, TranslationEntry entry)
	{
		this.pid = pid;
		this.entry = entry;
	}
	public int pid;
	public TranslationEntry entry;
}
