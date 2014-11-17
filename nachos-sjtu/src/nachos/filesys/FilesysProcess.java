package nachos.filesys;

import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.Processor;
import nachos.vm.VMProcess;
/**
 * FilesysProcess is used to handle syscall and exception through some callback methods.
 * 
 * @author starforever
 */
public class FilesysProcess extends VMProcess
{
  protected static final int SYSCALL_MKDIR = 14;
  protected static final int SYSCALL_RMDIR = 15;
  protected static final int SYSCALL_CHDIR = 16;
  protected static final int SYSCALL_GETCWD = 17;
  protected static final int SYSCALL_READDIR = 18;
  protected static final int SYSCALL_STAT = 19;
  protected static final int SYSCALL_LINK = 20;
  protected static final int SYSCALL_SYMLINK = 21;
  
  public static RealFileSystem realFileSystem;
  

	byte[] getByteArrayFromString(String st)
	{
		char[] data = st.toCharArray();
		byte[] data2 = new byte[data.length];
		System.arraycopy(data, 0, data2, 0, data.length);
		return data2;
	}
  
  public int handleSyscall (int syscall, int a0, int a1, int a2, int a3)
  {
	if (realFileSystem == null)
		realFileSystem = FilesysKernel.realFileSystem;
    switch (syscall)
    {
      case SYSCALL_MKDIR:
      {
    	  String name = readVirtualMemoryString(a0, 1 << 30);
    	  if (realFileSystem.createFolder(name))
    		  return 0;
    	  else return -1;
      }
        
      case SYSCALL_RMDIR:
      {
    	  String name = readVirtualMemoryString(a0, 1 << 30);
    	  if (realFileSystem.removeFolder(name))
    		  return 0;
    	  else return -1;
    	  
      }
        
      case SYSCALL_CHDIR:
      {
    	  String name = readVirtualMemoryString(a0, 1 << 30);
    	  if (realFileSystem.changeCurFolder(name))
    		  return 0;
    	  else return -1;
      }
        
      case SYSCALL_GETCWD:
      {
    	  String name = realFileSystem.getCurPath();
    	  return writeVirtualMemory(a0, getByteArrayFromString(name), 0, Math.min(a1, name.length()));
      }
        
      case SYSCALL_READDIR:
      {
    	  String name = readVirtualMemoryString(a0, 1 << 30);
    	  String[] st = realFileSystem.readDir(name);
    	  if (st == null) return -1;
    	  int size = a2, nameSize = a3;
    	  byte[] ans = new byte[size * nameSize];
    	  if (st.length > size) return -1;
    	  for (int i = 0; i < st.length; ++i)
    	  {
    		  byte[] now = getByteArrayFromString(st[i]);
    		  if (now.length > nameSize) return -1;
    		  System.arraycopy(now, 0, ans, i * nameSize, now.length);
    	  }
    	  writeVirtualMemory(a1, ans, 0, st.length * nameSize);
    	  return st.length;
      }
        
      case SYSCALL_STAT:
      {
    	  String name = readVirtualMemoryString(a0, 1 << 30);
    	  FileStat stat = realFileSystem.getStat(name);
    	  byte[] data = new byte[FileStat.FILE_NAME_MAX_LEN + 5 * 4];
    	  byte[] data2 = getByteArrayFromString(stat.name);
    	  System.arraycopy(data2, 0, data, 0, data2.length);
    	  Lib.bytesFromInt(data, FileStat.FILE_NAME_MAX_LEN, stat.size);
    	  Lib.bytesFromInt(data, FileStat.FILE_NAME_MAX_LEN + 4, stat.sectors);
    	  Lib.bytesFromInt(data, FileStat.FILE_NAME_MAX_LEN + 8, stat.type);
    	  Lib.bytesFromInt(data, FileStat.FILE_NAME_MAX_LEN + 12, stat.inode);
    	  Lib.bytesFromInt(data, FileStat.FILE_NAME_MAX_LEN + 16, stat.links);
    	  writeVirtualMemory(a2, data, 0, data.length);
      }
       
      case SYSCALL_LINK:
      {
    	  String nameS = readVirtualMemoryString(a0, 1 << 30);
    	  String nameT = readVirtualMemoryString(a1, 1 << 30);
    	  if (realFileSystem.createLink(nameS, nameT))
    		  return 0;
    	  else return -1;
      }
      
      case SYSCALL_SYMLINK:
      {
    	  String nameS = readVirtualMemoryString(a0, 1 << 30);
    	  String nameT = readVirtualMemoryString(a1, 1 << 30);
    	  if (realFileSystem.createSymlink(nameS, nameT))
    		  return 0;
    	  else return -1;
      }
      
      default:
        return super.handleSyscall(syscall, a0, a1, a2, a3);
    }
  }
  
  public void handleException (int cause)
  {
    if (cause == Processor.exceptionSyscall)
    {
		Processor processor = Machine.processor();
		int result = handleSyscall(processor.readRegister(Processor.regV0),
				processor.readRegister(Processor.regA0), processor
						.readRegister(Processor.regA1), processor
						.readRegister(Processor.regA2), processor
						.readRegister(Processor.regA3));
		processor.writeRegister(Processor.regV0, result);
		processor.advancePC();
    }
    else
      super.handleException(cause);
  }
}
