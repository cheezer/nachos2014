package nachos.filesys;

import java.util.Iterator;
import java.util.LinkedList;

import nachos.machine.Disk;
import nachos.machine.Lib;
import nachos.machine.Machine;

/**
 * FreeList is a single special file used to manage free space of the filesystem.
 * It maintains a list of sector numbers to indicate those that are available to use.
 * When there's a need to allocate a new sector in the filesystem, call allocate().
 * And you should call deallocate() to free space at a appropriate time (eg. when a file is deleted) for reuse in the future.
 * 
 * @author starforever
 */
public class FreeList extends File
{
  /** the static address */
  public static int STATIC_ADDR = 0;
  
  /** size occupied in the disk (bitmap) */
  static int size = Lib.divRoundUp(Disk.NumSectors, 8);
  
  /** maintain address of all the free sectors */
  private LinkedList<Integer> freeList;
  
  public FreeList (INode inode)
  {
    super(inode, "freeList");
    freeList = new LinkedList<Integer>();
  }
  
  public int getFreeSize()
  {
	  return freeList.size() * Disk.SectorSize;
  }
  
  public void init ()
  {
    for (int i = Lib.divRoundUp(Disk.NumSectors, Disk.SectorSize) + 1; i < Disk.NumSectors; ++i)
      freeList.add(i);
  }
  
  /** allocate a new sector in the disk */
  public int allocate ()
  {
	  if (freeList.isEmpty()) return -1;
	  else return freeList.removeFirst();
  }
  
  /** deallocate a sector to be reused */
  public void deallocate (int sec)
  {
	  freeList.add(new Integer(sec));
  }
  
	/** save the content of freelist to the disk */
  public void save ()
  {
	  int n = Disk.SectorSize;
	  int m = Disk.NumSectors;
	  byte data[] = new byte[n];
	  int free[] = new int[m];
	  for (Iterator<Integer> it = freeList.iterator(); it.hasNext();)
		  free[it.next()] = 1;
	  int addr = STATIC_ADDR, now = 0; 
	  while (now < Disk.NumSectors)
	  {
		  for (int i = 0; i < n; ++i)
		  {
			  data[i] = 0;
			  for (int j = 0; j < 8 && now < Disk.NumSectors; ++j)
			  {
				  data[i] = (byte) ((data[i] << 1) + free[now]);
				  now++;
			  }
		  }
		  Machine.synchDisk().writeSector(addr, data, 0);
		  if (addr == STATIC_ADDR) addr = 2;
		  else addr = addr + 1;
	  }  
  }
  /** load the content of freelist from the disk */
  public void load ()
  {
	  int n = Disk.SectorSize;
	  byte data[] = new byte[Disk.SectorSize];
	  int addr = STATIC_ADDR, now = 0; 
	  while (now < Disk.NumSectors)
	  {
		  Machine.synchDisk().readSector(addr, data, 0);
		  for (int i = 0; i < n && now < Disk.NumSectors; ++i)
		  {
			  int t = data[i];
			  for (int j = 0; j < 8 && now < Disk.NumSectors; ++j)
			  {
				  if ((t & 2) == 0)
					  freeList.add(new Integer(now));
				  t = t >> 1;
			  	  now++;
			  }
		  }
		  if (addr == STATIC_ADDR) addr = 2;
		  else addr = addr + 1;
	  }
  }
}
