package nachos.filesys;

import nachos.machine.Disk;
import nachos.machine.Machine;
import nachos.machine.OpenFile;

/**
 * File provide some basic IO operations.
 * Each File is associated with an INode which stores the basic information for the file.
 * 
 * @author starforever
 */
public class File extends OpenFile
{
	INode inode;
	  
	private int pos;
	
	public File(String file)
	{
		super(FilesysKernel.realFileSystem, file);
		inode = new INode(FilesysKernel.realFileSystem.getFreeList().allocate());
		pos = 0;
	}
	  
	public File (INode inode, String file)
	{
		super(FilesysKernel.realFileSystem, file);
		this.inode = inode;
		pos = 0;
	}
  
	public int length ()
	{
		return inode.file_size;
	}
	  
	public void close ()
	{
	  //TODO implement this
	}
	  
	public void seek (int pos)
	{
		this.pos = pos;
	}
	  
	public int tell ()
	{
		return pos;
	}
	  
	public int read (byte[] buffer, int start, int limit)
	{
		int ret = read(pos, buffer, start, limit);
		pos += ret;
		return ret;
	}
	  
	public int write (byte[] buffer, int start, int limit)
	{
		int ret = write(pos, buffer, start, limit);
		pos += ret;
		return ret;
	}
	  
	public int read (int pos, byte[] buffer, int start, int limit)
	{
		//if (limit <= 0 || pos >= inode.file_size)
			//return -1;
		int count = 0;
		int n = Disk.SectorSize;
		int offset = pos % n;
		byte data[] = new byte[n];
		while (limit > 0 && pos + count < inode.file_size)
		{
			int i = inode.getSector(pos + count);
			Machine.synchDisk().readSector(i, data, 0);
			int readCount = Math.min(Math.min(limit, n - offset), inode.file_size - (pos + count));
			System.arraycopy(data, offset, buffer, start + count, readCount);
			limit -= readCount;
			count += readCount;
			offset = 0;
		}
		return count;
	}
	  
	public int write (int pos, byte[] buffer, int start, int limit)
	{
		int count = 0;
		int n = Disk.SectorSize;
		int offset = pos % n;
		byte data[] = new byte[n];
		if (pos + limit > inode.file_size)
			inode.setFileSize(pos + limit);
		while (limit > 0)
		{
			int i = inode.getSector(pos + count);
			int writeCount = Math.min(limit, n - offset);
			System.arraycopy(buffer, start + count, data, offset, writeCount);
			//System.out.println(i);
			Machine.synchDisk().writeSector(i, data, 0);
			limit -= writeCount;
			count += writeCount;
			offset = 0;
		}
		//System.out.println();
		return count;
	}
}
