package nachos.filesys;

import java.util.Hashtable;
import java.util.Set;

import nachos.machine.Lib;

/**
 * Folder is a special type of file used to implement hierarchical filesystem.
 * It maintains a map from filename to the address of the file.
 * There's a special folder called root folder with pre-defined address.
 * It's the origin from where you traverse the entire filesystem.
 * 
 * @author starforever
 */
public class Folder extends File
{
  /** the static address for root folder */
  public static final int STATIC_ADDR = 1;
  public int fatherAddr;
  private int size;
  
  /** mapping from filename to folder entry */
  private Hashtable<String, FolderEntry> entry;
  
  public Folder (INode inode, String name)
  {
    super(inode, name);
    inode.file_type = INode.TYPE_FOLDER;
    size = 4;
    entry = new Hashtable<String, FolderEntry>();
  }
  
  /** open a file in the folder and return its address */
	/*public int open (String filename)
	{
		FolderEntry e = entry.get(filename);
		if (e == null) return -1;
		else return e.addr;
	}*/
    public int get (String filename)
	{
		FolderEntry e = entry.get(filename);
		if (e == null) return -1;
		else return e.addr;
	}
    
    public Set<String> keys()
    {
    	return entry.keySet();
    }
  
  /** create a new file in the folder and return its address */
	public int createFile (String filename)
	{
		File file = new File(filename);
		entry.put(filename, new FolderEntry(filename, file.inode.addr));
		file.inode.save();
		return file.inode.addr;
	}
	
	public int createFolder (String folderName)
	{
		Folder folder = new Folder(new INode(FilesysKernel.realFileSystem.getFreeList().allocate()), folderName);
		folder.inode.file_type = INode.TYPE_FOLDER;
		folder.fatherAddr = inode.addr;
		inode.save();
		entry.put(folderName, new FolderEntry(folderName, folder.inode.addr));
		return folder.inode.addr;
	}
  
  /** add an entry with specific filename and address to the folder */
	public void addEntry (String filename, int addr)
	{
		entry.put(filename, new FolderEntry(filename, addr));
	}
  
  /** remove an entry from the folder */
	public void removeEntry (String filename)
	{
		entry.remove(filename);
	}
  
  /** save the content of the folder to the disk */
	public void save ()
	{
		int n = 8;
		for (String key: entry.keySet())
			n += 8 + key.length();
		byte[] data = new byte[n];
		Lib.bytesFromInt(data, 0, entry.size());
		Lib.bytesFromInt(data, 4, fatherAddr);
		int offset = 8;
		for (String key: entry.keySet())
		{
			Lib.bytesFromInt(data, offset, entry.get(key).addr);
			Lib.bytesFromInt(data, offset + 4, key.length());
			offset += 8;
			for (int j = 0; j < key.length(); ++j, ++offset)
				data[offset] = (byte)key.charAt(j);
		}
		write(0, data, 0, n);
		inode.setFileSize(n);
	}
  
  /** load the content of the folder from the disk */
	public void load ()
	{
		int n = inode.file_size;
		byte[] data = new byte[n];
		Lib.assertTrue(read(0, data, 0, n) == n);
		int m = Lib.bytesToInt(data, 0);
		fatherAddr = Lib.bytesToInt(data, 4);
		int offset = 8;
		for (int i = 0; i < m; ++i)
		{
			int addr = Lib.bytesToInt(data, offset);
			int len = Lib.bytesToInt(data, offset + 4);
			offset += 8; 
			String st = "";
			for (int j = 0; j < len; j++, offset++)
				st += (char)data[offset];
			entry.put(st, new FolderEntry(st, addr));
		}
	}
}
