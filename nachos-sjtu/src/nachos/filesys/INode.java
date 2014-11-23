package nachos.filesys;

import java.util.Iterator;
import java.util.LinkedList;

import nachos.machine.Disk;
import nachos.machine.Lib;
import nachos.machine.Machine;

/**
 * INode contains detail information about a file.
 * Most important among these is the list of sector numbers the file occupied, 
 * it's necessary to find all the pieces of the file in the filesystem.
 * 
 * @author starforever
 */
public class INode
{
  /** represent a system file (free list) */
  public static int TYPE_SYSTEM = 0;
  
  /** represent a folder */
  public static int TYPE_FOLDER = 1;
  
  /** represent a normal file */
  public static int TYPE_FILE = 2;
  
  /** represent a normal file that is marked as delete */
  public static int TYPE_FILE_DEL = 3;
  
  /** represent a symbolic link file */
  public static int TYPE_SYMLINK = 4;
  
  /** represent a folder that are not valid */
  public static int TYPE_FOLDER_DEL = 5;
  
  /** the reserve size (in byte) in the first sector */
  private static final int FIRST_SEC_RESERVE = 16;
  
  /** size of the file in bytes */
  int file_size;
  
  /** the type of the file */
  public int file_type;
  
  /** the number of programs that have access on the file */
  int use_count;
  
  /** the number of links on the file */
  int link_count;
  
  /** maintain all the sector numbers this file used in order */
  public LinkedList<Integer> sec_addr;
  
  /** the first address */
  public int addr;
  
  /** the extended address */
  private LinkedList<Integer> addr_ext;
  
  private FreeList freeList;
  
  public INode (int addr)
  {
    file_size = 0;
    file_type = TYPE_FILE;
    use_count = 0;
    link_count = 0;
    sec_addr = new LinkedList<Integer>();
    this.addr = addr;
    addr_ext = new LinkedList<Integer>();
    freeList = FilesysKernel.realFileSystem.getFreeList();
  }
  
  /** get the sector number of a position in the file  */
	public int getSector (int pos)
	{
		for (Iterator<Integer> it = sec_addr.iterator(); it.hasNext(); it.next())
		{
			if (pos < Disk.SectorSize)
				return it.next();
			pos -= Disk.SectorSize;
		}
		return -1;
	}
	
	public int getNumSec()
	{
		return sec_addr.size();
	}
	
	public int newSector()
	{
		//Lib.debug('f', "allocating");
		int i = freeList.allocate();
		Lib.assertTrue(i != -1);
		sec_addr.add(i);
		return i;
	}
  
  /** change the file size and adjust the content in the inode accordingly */
	public void setFileSize (int size)
	{
		while (sec_addr.size() * Disk.SectorSize < size)
			newSector();
		while ((sec_addr.size() - 1) * Disk.SectorSize >=size)
			freeList.deallocate(sec_addr.removeLast());
		file_size = size;
	}
  
  /** free the disk space occupied by the file (including inode) */
	public void free ()
	{
		while (!sec_addr.isEmpty())
		{
			//Lib.debug('f', "deallocating");
			freeList.deallocate(sec_addr.removeFirst());
		}
		while (!addr_ext.isEmpty())
			freeList.deallocate(addr_ext.removeFirst());
		freeList.deallocate(addr);
	}
  
  /** load inode content from the disk */
	public void load ()
	{
		int n = Disk.SectorSize;
		byte[] data = new byte[n];
		Machine.synchDisk().readSector(addr, data, 0);
		file_size = Lib.bytesToInt(data, 4);
		file_type = Lib.bytesToInt(data, 8);
		link_count = Lib.bytesToInt(data, 12);
		use_count = Lib.bytesToInt(data, 16);
		int secNum = Lib.bytesToInt(data, 20);
		int offset = 24;
		int i = addr, j = 0;
		while (true)
		{
			for (; offset < n && j < secNum; offset += 4, j++)
				sec_addr.add(Lib.bytesToInt(data, offset));
			if (i != addr)
				addr_ext.add(i);
			if (j < secNum)
			{
				int next = Lib.bytesToInt(data, 0);
				i = next;
				Machine.synchDisk().readSector(i, data, 0);
				offset = 4;
			}
			else break;
		}
	}
  
  /** save inode content to the disk */
	public void save ()
	{
		int n = Disk.SectorSize;
		byte[] data = new byte[n];
		Lib.bytesFromInt(data, 4, file_size);
		Lib.bytesFromInt(data, 8, file_type);
		Lib.bytesFromInt(data, 12, link_count);
		Lib.bytesFromInt(data, 16, use_count);
		Lib.bytesFromInt(data, 20, sec_addr.size());
		int offset = 24;
		Iterator<Integer> it = sec_addr.iterator();
		Iterator<Integer> ext = addr_ext.iterator();
		int i = addr;
		boolean ok = true;
		Machine.synchDisk().writeSector(i, data, 0);
		while (it.hasNext())
		{
			for (; offset < n && it.hasNext(); offset += 4)
				Lib.bytesFromInt(data, offset, it.next());
			if (it.hasNext())
			{
				int next;
				if (ok && ext.hasNext())
					next = ext.next();
				else 
					{
						next = freeList.allocate();
						addr_ext.add(next);
						ok = false;
					}
				Lib.assertTrue(next != -1);
				Lib.bytesFromInt(data, 0, next);
			}
			else Lib.bytesFromInt(data, 0, -1);
			Machine.synchDisk().writeSector(i, data, 0);
			offset = 4;
		}
		while (ok && ext.hasNext())
		{
			ext.next();
			ext.remove();
		}
	}
}
