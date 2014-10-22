package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;

import java.io.EOFException;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
		processTotLock.acquire();
		processID = processTot++;
		processTotLock.release();
		descriptor  = new Descriptor();
		aliveLock.acquire();
		aliveTot++;
		aliveLock.release();
		
		descriptor.put(UserKernel.console.openForReading(), 0);
        descriptor.put(UserKernel.console.openForWriting(), 1);
		
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
		return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name
	 *            the name of the file containing the executable.
	 * @param args
	 *            the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
		{
			/*aliveLock.acquire();
			aliveTot--;
			aliveLock.release();*/
			return false;
		}

		thread = new UThread(this).setName(name);
		thread.fork();

		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr
	 *            the starting virtual address of the null-terminated string.
	 * @param maxLength
	 *            the maximum number of characters in the string, not including
	 *            the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 *         found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr
	 *            the first byte of virtual memory to read.
	 * @param data
	 *            the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr
	 *            the first byte of virtual memory to read.
	 * @param data
	 *            the array where the data will be stored.
	 * @param offset
	 *            the first byte to write in the array.
	 * @param length
	 *            the number of bytes to transfer from virtual memory to the
	 *            array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);
		byte[] memory = Machine.processor().getMemory();
		if (vaddr < 0 || vaddr + length > memory.length)
			return 0;
		int t = Processor.offsetFromAddress(vaddr);
		int vpn = Processor.pageFromAddress(vaddr);
		int tot = 0;
		for (; length > 0; vpn++)
		{
			int count = Math.min(length, pageSize - t);
			if (vpn > numPages)
				return tot;
			TranslationEntry tranEntry = pageTable[vpn];
			if (!tranEntry.valid) continue;
			tranEntry.used = true;
			int pAddr = Processor.makeAddress(tranEntry.ppn, t);
			System.arraycopy(memory, pAddr, data, offset, count);
			length -= count;
			offset += count;
			tot += count;
			t = 0;
		}
		return tot;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr
	 *            the first byte of virtual memory to write.
	 * @param data
	 *            the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr
	 *            the first byte of virtual memory to write.
	 * @param data
	 *            the array containing the data to transfer.
	 * @param offset
	 *            the first byte to transfer from the array.
	 * @param length
	 *            the number of bytes to transfer from the array to virtual
	 *            memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();
		if (vaddr < 0 || vaddr + length > memory.length)
			return 0;
		int t = Processor.offsetFromAddress(vaddr);
		int vpn = Processor.pageFromAddress(vaddr);
		int amount = 0;
		for (; length > 0; vpn++)
		{
			int count = Math.min(length, pageSize - t);
			if (vpn > numPages)
				return amount;
			TranslationEntry tranEntry = pageTable[vpn];
			if (!tranEntry.valid) continue;
			tranEntry.used = true;
			tranEntry.dirty = true;
			int pAddr = Processor.makeAddress(tranEntry.ppn, t);
			System.arraycopy(data, offset, memory, pAddr, count);
			length -= count;
			offset += count;
			amount += count;
			t = 0;
		}
		return amount;
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name
	 *            the name of the file containing the executable.
	 * @param args
	 *            the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		} catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib
					.assertTrue(writeVirtualMemory(entryOffset,
							stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib
					.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib
					.assertTrue(writeVirtualMemory(stringOffset,
							new byte[] { 0 }) == 1);
			stringOffset += 1;
		}
		executable.close();
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\") complete");
		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		int[] phyPages = UserKernel.allocatePages(numPages);
		if (phyPages == null) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}
		pageTable = new TranslationEntry[numPages];
		int count = 0;
		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;
				pageTable[vpn] = new TranslationEntry(vpn, phyPages[count++], true, section.isReadOnly(), false, false);
				section.loadPage(i, phyPages[count - 1]);
			}
		}
		for (int i = 0; i < stackPages + 1; ++i)
		{
			pageTable[count] = new TranslationEntry(count, phyPages[count], true, false, false, false);
			count++;
		}
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		for (int i = 0; i < numPages; i++)
			UserKernel.releasePage(pageTable[i].ppn);
		pageTable = null;
		coff.close();
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < Processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {
		Lib.debug(dbgProcess, "syscallHalt");
		if (UserKernel.rootProcess != this)
			return 0;
		unloadSections();
		for (int i = 2; i < maxDescriptor; ++i)
			if (descriptor.get(i) != null)
				handleClose(i);
		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}
	
	private int handleCreate(int namePos)
	{
		Lib.debug(dbgProcess, "syscallCreate");
		String name = readVirtualMemoryString(namePos, maxLength);
		if (name == null || name.length() > maxLength || !descriptor.hasFree()) return -1;
		if (toBeRemoved.contains(name))
			return -1;//!
		OpenFile file = UserKernel.fileSystem.open(name, true);
		if (file == null) return -1;
		fileOpenNum.put(name);
		int des = descriptor.getFreeDescriptor();
		descriptor.put(file, des);

/*		for (int i = 2; i < maxDescriptor; ++i)
			if (descriptor.get(i) != null)
				Lib.debug(dbgProcess, i + ":" + descriptor.get(i).getName());*/
		return des;
	}
	
	private int handleOpen(int namePos)
	{
		Lib.debug(dbgProcess, "syscallOpen");
		String name = readVirtualMemoryString(namePos, maxLength);
		if (name == null || name.length() > maxLength || !descriptor.hasFree()) return -1;
		OpenFile file = UserKernel.fileSystem.open(name, false);
		if (file == null) return -1;
		int des = descriptor.getFreeDescriptor();
		fileOpenNum.put(name);
		descriptor.put(file, des);

		for (int i = 2; i < maxDescriptor; ++i)
			if (descriptor.get(i) != null)
				Lib.debug(dbgProcess, i + ":" + descriptor.get(i).getName());
		return des;
	}
	
	private int handleRead(int fd, int addr, int size)
	{
		Lib.debug(dbgProcess, "syscallRead");
		OpenFile file = descriptor.get(fd);
		if (file == null)
			return -1;
		byte[] a = new byte[size];
		int len = file.read(a, 0, size);
		int len2 = writeVirtualMemory(addr, a, 0, len);
		return len2;
	}
	
	private int handleWrite(int fd, int addr, int size)
	{
		Lib.debug(dbgProcess, "syscallWrite");
		OpenFile file = descriptor.get(fd);
		if (file == null)
			return -1;
		byte[] a = new byte[size];
		int len = readVirtualMemory(addr, a, 0, size);
		if (len < size)
			return -1;
		int len2 = file.write(a, 0, len);
		if (len2 < len)
			return -1;
		return len2;
	}

	private int handleClose(int fd)
	{
		Lib.debug(dbgProcess, "syscallClose");
		OpenFile file = descriptor.get(fd);
		if (file == null)
			return -1;
		/*for (int i = 2; i < maxDescriptor; ++i)
			if (descriptor.get(i) != null)
				Lib.debug(dbgProcess, i + ":" + descriptor.get(i).getName());*/
		descriptor.remove(fd);
		fileOpenNum.remove(file.getName());
		file.close();
		//System.out.println(file.getName() + " " + descriptor.get(fd));
		if (toBeRemoved.contains(file.getName()) && fileOpenNum.get(file.getName()) == 0)
		{
			//Lib.assertNotReached(file.getName());
			if (!UserKernel.fileSystem.remove(file.getName()))
				return -1;
			toBeRemoved.remove(file.getName());
		}
		return 0;
	}
	
	private int handleUnlink(int namePos)
	{
		Lib.debug(dbgProcess, "syscallUnlink");
		String name = readVirtualMemoryString(namePos, maxLength);
		if (name == null || name.length() > maxLength) return -1;
		if (fileOpenNum.get(name) == 0){
			if (!UserKernel.fileSystem.remove(name))
				return -1;
		}
		else toBeRemoved.add(name);
		return 0;
	}
	
	private int handleExit(int returnValue)
	{
		Lib.debug(dbgProcess, "syscallExit");
		this.status = returnValue;
		unloadSections();
		for (int i = 2; i < maxDescriptor; ++i)
			if (descriptor.get(i) != null)
			{
				//System.out.println(descriptor.get(i).getName());
				handleClose(i);
			}
		aliveLock.acquire();
		aliveTot--;
		if (aliveTot == 0)
			Kernel.kernel.terminate();
		aliveLock.release();
		UThread.finish();
		return 0;
	}
	
	private int handleExec(int fileP, int argc, int argvP)
	{
		String name = this.readVirtualMemoryString(fileP, maxLength);
		if (argc < 0 || name == null || !name.endsWith(".coff")) return -1;
		int len = argc * 4;
		byte[] a = new byte[len];
		if (this.readVirtualMemory(argvP, a, 0, len) < len)
			return -1;
		String[] b = new String[argc];
		for (int i = 0; i < argc; ++i)
		{
			int p = Lib.bytesToInt(a, i * 4, 4);
			b[i] = readVirtualMemoryString(p, maxLength);
			if (b[i] == null)
				return -1;
		}
		UserProcess c = newUserProcess();
		childs.put(new Integer(c.processID), c);
		if (!c.execute(name, b))
		{
			childs.remove(new Integer(c.processID));
			aliveLock.acquire();
			aliveTot--;
			aliveLock.release();
			return -1;
		}
		return c.processID;
	}
	
	private int handleJoin(int processID, int statusP)
	{
		if (!childs.containsKey(new Integer(processID)))
			return -1;
		UserProcess c = childs.get(new Integer(processID));
		c.thread.join();
		byte[] a = Lib.bytesFromInt(c.status);
		this.writeVirtualMemory(statusP, a);
		if (c.status == -1)
			return 0;
		return 1;
	}
	
	
	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;
	private static final int maxLength = 256; 

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
     * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
     *								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
     *								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall
	 *            the syscall number.
	 * @param a0
	 *            the first syscall argument.
	 * @param a1
	 *            the second syscall argument.
	 * @param a2
	 *            the third syscall argument.
	 * @param a3
	 *            the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		//Lib.debug(dbgProcess, syscall + " ");
		switch (syscall) {
			case syscallHalt:
				return handleHalt();
			case syscallCreate:
				return handleCreate(a0);
			case syscallOpen:
				return handleOpen(a0);
			case syscallRead:
				return handleRead(a0, a1, a2);
			case syscallWrite:
				return handleWrite(a0, a1, a2);
			case syscallClose:
				return handleClose(a0);
			case syscallUnlink:
				return handleUnlink(a0);
			case syscallExit:
				return handleExit(a0);
			case syscallExec:
				return handleExec(a0, a1, a2);
			case syscallJoin:
				return handleJoin(a0, a1);

		default:
			//Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			//Lib.assertNotReached("Unknown system call!");
			System.out.println(syscall);
			this.handleExit(-1);
		}
		return 0;
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
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0), processor
							.readRegister(Processor.regA1), processor
							.readRegister(Processor.regA2), processor
							.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;
			case Processor.exceptionPageFault :
			case Processor.exceptionTLBMiss :
			case Processor.exceptionReadOnly :
			case Processor.exceptionBusError :
			case Processor.exceptionAddressError :
			case Processor.exceptionOverflow :
			case Processor.exceptionIllegalInstruction :
				this.handleExit(-1);
				break;		

		default:
			Lib.debug(dbgProcess, "Unexpected exception: "
					+ Processor.exceptionNames[cause]);
			//Lib.assertNotReached("Unexpected exception");
			this.handleExit(-1);
		}
	}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;
	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = Config.getInteger("Processor.numStackPages", 8);

	private int initialPC, initialSP;
	private int argc, argv;
	private int processID;
	
	protected static int maxDescriptor = 16;
	protected Descriptor descriptor;
	
	private class Descriptor
	{
		Descriptor()
		{
			for (int i = 0; i < maxDescriptor; ++i)
				free.add(new Integer(i));
		}
		
		boolean hasFree()
		{
			return !free.isEmpty();
		}
		
		OpenFile get(int descriptor)
		{
			return fileTable.get(descriptor);
		}
		
		int get(OpenFile file)
		{
			return descrTable.get(file);
		}
		
		int getFreeDescriptor()
		{
			Lib.assertTrue(hasFree());
			return free.iterator().next();
		}
		
		void put(OpenFile file, int descriptor)
		{
			//System.out.println("hey!");
			Lib.assertTrue(free.contains(descriptor));
			descrTable.put(file, new Integer(descriptor));
			fileTable.put(new Integer(descriptor), file);
			free.remove(new Integer(descriptor));
		}
		
		void remove(int descriptor)
		{
			descrTable.remove(get(descriptor));
			fileTable.remove(new Integer(descriptor));
			free.add(descriptor);
		}
		
		HashSet<Integer> free = new HashSet<Integer>();
		HashMap<OpenFile, Integer> descrTable = new HashMap<OpenFile, Integer>();
		HashMap<Integer, OpenFile> fileTable = new HashMap<Integer, OpenFile>();
	}

	private static class FileOpenNum
	{
		public FileOpenNum() 
		{
		}
		void put(String name)
		{
			if (table.get(name) == null) table.put(name, new Integer(1));
			else {
				int ci = table.get(name);
				table.put(name, new Integer(ci + 1));
			}
		}
		void remove(String name)
		{
			Lib.assertTrue(table.get(name) != null);
			int ci = table.get(name);
			table.put(name, new Integer(ci - 1));
		}
		int get(String name)
		{
			if (table.get(name) == null) return 0;
			else return table.get(name);
		}
		private HashMap<String, Integer> table = new HashMap<String, Integer>();
	}
	private static FileOpenNum fileOpenNum = new FileOpenNum();
	private static HashSet<String> toBeRemoved = new HashSet<String>(); 
	private static final int pageSize = Processor.pageSize;
	private static final char dbgProcess = 'a';
	public static Lock processTotLock = new Lock(), aliveLock = new Lock();
	public static int processTot = 0, aliveTot = 0;
	private KThread thread;
	private int status;
	private HashMap<Integer, UserProcess> childs = new HashMap();
}
