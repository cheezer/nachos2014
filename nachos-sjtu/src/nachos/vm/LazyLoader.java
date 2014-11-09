package nachos.vm;

import nachos.machine.Coff;
import nachos.machine.CoffSection;
import nachos.machine.Lib;
import nachos.machine.TranslationEntry;

public class LazyLoader {
	public LazyLoader(Coff coff)
	{
		this.coff = coff;
		numSections = coff.getNumSections();
		for (int i = 0; i < numSections; ++i)
		{
			numPages += coff.getSection(i).getLength();
		}
		sectionBelong = new int[numPages];
		sectionOffset = new int[numPages];
		
		for (int i = 0; i < numSections; ++i)
		{
			CoffSection section = coff.getSection(i);
			for (int j = 0; j < section.getLength(); ++j)
			{
				int vpn = section.getFirstVPN() + j;
				sectionBelong[vpn] = i;
				sectionOffset[vpn] = j;
			}
		}
	}
	
	public TranslationEntry loadSection(int vpn, int ppn)
	{
		TranslationEntry entry;
		if (vpn < numPages)
		{
			//System.out.println(numPages + " " + vpn);
			CoffSection section = coff.getSection(sectionBelong[vpn]);
			entry = new TranslationEntry(vpn, ppn, true, section.isReadOnly(), false, false);
			Lib.debug(dbgVM, "loading section vpn: " + vpn + " ppn: " + ppn + " (" + sectionBelong[vpn] + ", " + sectionOffset[vpn] + ")");
			section.loadPage(sectionOffset[vpn], ppn);
		}
		else 
		{
			entry = new TranslationEntry(vpn, ppn, true, false, false, false);
			Lib.debug(dbgVM, "loading page vpn: " + vpn + " ppn: " + ppn);
		}
		return entry;
	}
	Coff coff;
	private int numPages, numSections;
	private int sectionBelong[];
	private int sectionOffset[]; 
	private static final char dbgVM = 'v';

}
