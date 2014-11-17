package nachos.filesys;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;

import nachos.machine.FileSystem;
import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.OpenFile;

/**
 * RealFileSystem provide necessary methods for filesystem syscall.
 * The FileSystem interface already define two basic methods, you should implement your own to adapt to your task.
 * 
 * @author starforever
 */
public class RealFileSystem implements FileSystem
{
  /** the free list */
  private FreeList freeList;
  
  /** the root folder */
  private Folder root_folder;
  
  /** the current folder */
  private Folder cur_folder;
  
  /** the string representation of the current folder */
  private LinkedList<String> cur_path = new LinkedList<String>();
  
  private HashMap<Integer, INode> folderMap = new HashMap<Integer, INode>();
  
  private HashMap<Integer, INode> fileMap = new HashMap<Integer, INode>();
  
  private static final char debugFlag = 'f'; 
  /**
   * initialize the file system
   * 
   * @param format
   *          whether to format the file system
   */
	public void init (boolean format)
	{
		Lib.debug(debugFlag, "initializing real file system");
		if (format)
		{
			INode inode_freeList = new INode(FreeList.STATIC_ADDR);
			freeList = new FreeList(inode_freeList);
			freeList.init();
			INode inode_root_folder = new INode(Folder.STATIC_ADDR);
			root_folder = new Folder(inode_root_folder, "rootFolder");
			root_folder.fatherAddr = root_folder.inode.addr;	
			cur_folder = root_folder;
			folderMap.put(Folder.STATIC_ADDR, root_folder.inode);
			importStub();
		}
		else
		{
			INode inode_freeList = new INode(FreeList.STATIC_ADDR);
			//inode_freeList.load();
			freeList = new FreeList(inode_freeList);
			freeList.load();
			INode inode_root_folder = new INode(Folder.STATIC_ADDR);
			inode_root_folder.load();
			root_folder = new Folder(inode_root_folder, "rootFolder");	
			root_folder.load();
			root_folder.fatherAddr = root_folder.inode.addr;	
			cur_folder = root_folder;
			folderMap.put(Folder.STATIC_ADDR, root_folder.inode);
		}
	}
  
  public void finish ()
  {
	Lib.debug(debugFlag, "finishing real file system");
    root_folder.save();
    for (INode i: folderMap.values())
    	i.save();
    for (INode i: fileMap.values())
    	i.save();
    freeList.save();
  }
  
  /** import from stub filesystem */
  private void importStub ()
  {
	Lib.debug(debugFlag, "importing stub file system");
    FileSystem stubFS = Machine.stubFileSystem();
    FileSystem realFS = FilesysKernel.realFileSystem;
    String[] file_list = Machine.stubFileList();
    for (int i = 0; i < file_list.length; ++i)
    {
      //if (!file_list[i].endsWith(".coff"))
        //continue;
      OpenFile src = stubFS.open(file_list[i], false);
      if (src == null)
      {
        continue;
      }
      OpenFile dst = realFS.open(file_list[i], true);
      int size = src.length();
      byte[] buffer = new byte[size];
      src.read(0, buffer, 0, size);
/*      if (file_list[i].equals("test_files.coff"))
      {
    	  for (int j = 0; j < buffer.length; ++j)
    		  System.out.print(buffer[j] + " ");
    	  System.out.println();
      }*/
      dst.write(0, buffer, 0, size);
      src.close();
      dst.close();
    }
  }
  
  /** get the only free list of the file system */
  public FreeList getFreeList ()
  {
    return freeList;
  }
  
  /** get the only root folder of the file system */
  public Folder getRootFolder ()
  {
    return root_folder;
  }
  
	public LinkedList<String> pathParser(String name)
	{
		LinkedList<String> ans = new LinkedList<String>();
		String st = name;
		if (st.length() > 0 && st.charAt(0) == '/')
		{
			ans.add("/");
			st = st.substring(1);
		}
		while (st.length() > 0)
		{
			int p = st.indexOf('/');
			if (p == -1)
			{
				ans.add(st);
				break;
			}
			else if (!(p == 0 || p == 1 && st.charAt(0) == '.'))
				ans.add(st.substring(0, p));
			st = st.substring(p + 1);
		}
		return ans;
	}
	
	public Folder getFolderFromAddr(int addr, String name)
	{
		if (addr == -1)
			return null;
		if (!folderMap.containsKey(addr))
		{
			INode inode = new INode(addr);
			inode.load();
			folderMap.put(addr, inode);
		}
		return new Folder(folderMap.get(addr), name);
	}
	
	public File getFileFromAddr(int addr, String name)
	{
		if (addr == -1)
			return null;
		if (!fileMap.containsKey(addr))
		{
			INode inode = new INode(addr);
			inode.load();
			fileMap.put(addr, inode);
		}
		return new File(fileMap.get(addr), name);
	}
	
	public Folder getFolderFromPath(String name, Folder folder)
	{
		if (name == "..")
			return getFolderFromAddr(folder.fatherAddr, name);//TBD
		else if (name == "/")
			return root_folder;
		else return getFolderFromAddr(folder.get(name), name);
	}
  
	public OpenFile open (String name, boolean create)
	{
		Lib.debug(debugFlag, "opening file: " + name);
		LinkedList<String> path = pathParser(name);
		if (path.size() == 0) return null;
		Folder folder = cur_folder;
		while (path.size() > 1)
		{
			folder = getFolderFromPath(path.removeFirst(), folder);
			if (folder == null)
				return null;
		}
		String filename = path.removeFirst();
		File file = getFileFromAddr(folder.get(filename), filename);
		if (file != null)
		{
			if (create)
				file.inode.setFileSize(0);
			return file;
		}
		else 
			if (!create) return null;
			else 
				return getFileFromAddr(folder.createFile(filename), filename);
	}
	
  
	public boolean remove (String name)
	{
		Lib.debug(debugFlag, "removing file: " + name);
		LinkedList<String> path = pathParser(name);
		if (path.size() == 0) return false;
		Folder folder = cur_folder;
		while (path.size() > 1)
		{
			folder = getFolderFromPath(path.removeFirst(), folder);
			if (folder == null)
				return false;
		}
		String filename = path.removeFirst();
		File file = getFileFromAddr(folder.get(filename), filename);
		if (file != null)
		{
			folder.removeEntry(filename);
			if (--file.inode.link_count == 0)
				file.inode.free();
			return true;
		}
		else	
			return false;
	}
  
	public boolean createFolder (String name)
	{
		Lib.debug(debugFlag, "creating file: " + name);
		LinkedList<String> path = pathParser(name);
		if (path.size() == 0) return false;
		Folder folder = cur_folder;
		while (path.size() > 1)
		{
			folder = getFolderFromPath(path.removeFirst(), folder);
			if (folder == null)
				return false;
		}
		String filename = path.removeFirst();
		Folder cre = getFolderFromAddr(folder.get(filename), filename);
		if (cre != null) return false;
		folder.createFolder(filename);
		return true;
	}
  
	public boolean removeFolder (String name)
	{
		Lib.debug(debugFlag, "removing folder: " + name);
		LinkedList<String> path = pathParser(name);
		if (path.size() == 0) return false;
		Folder folder = cur_folder;
		while (path.size() > 1)
		{
			folder = getFolderFromPath(path.removeFirst(), folder);
			if (folder == null)
				return false;
		}
		String filename = path.removeFirst();
		Folder cre = getFolderFromAddr(folder.get(filename), name);
		if (cre == null || !cre.keys().isEmpty()) return false;
		if (--cre.inode.link_count == 0)
			cre.inode.free();
		folder.removeEntry(filename);
		return true;
	}
  
	public boolean changeCurFolder (String name)
	{
		Lib.debug(debugFlag, "changing current folder: " + name);
		LinkedList<String> path = pathParser(name);
		LinkedList<String> path2 = new LinkedList<String>(cur_path);
		Folder folder = cur_folder;
		while (path.size() > 0)
		{
			String st = path.removeFirst();
			folder = getFolderFromPath(st, folder);
			if (folder == null)
				return false;
			if (st == "..")
			{
				if (path2.size() > 0) path2.removeLast();
			}
			else if (st == "/")
				path2.clear();
			else {
				path2.add(st);
			}
		}
		cur_folder = folder;
		cur_path = path2;
		return true;
	}
	
	public String getCurPath()
	{
		Lib.debug(debugFlag, "getting current path");
		String st = "/";
		if (cur_path.isEmpty()) return st;
		else 
		{
			for (Iterator<String> it = cur_path.iterator(); it.hasNext();)
				st += it.next() + "/";
		}
		return st;
	}
  
	public String[] readDir (String name)
	{
		Lib.debug(debugFlag, "reading dir: " + name);
		LinkedList<String> path = pathParser(name);
		if (path.size() == 0) 
		{
			Set<String> key = cur_folder.keys();
			return key.toArray(new String[key.size()]);
		}
		Folder folder = cur_folder;
		while (path.size() > 0)
		{
			folder = getFolderFromPath(path.removeFirst(), folder);
			if (folder == null)
				return null;
		}
		Set<String> key = folder.keys();
		return key.toArray(new String[key.size()]);
	}
  
	public FileStat getStat (String name)
	{	
		Lib.debug(debugFlag, "getting status: " + name);
		LinkedList<String> path = pathParser(name);
		if (path.size() == 0)
		{
			return new FileStat(".", (File)cur_folder);
		}
		Folder folder = cur_folder;
		while (path.size() > 1)
		{
			folder = getFolderFromPath(path.removeFirst(), folder);
			if (folder == null)
				return null;
		}
		String filename = path.removeFirst();
		File file = getFileFromAddr(folder.get(filename), filename);
		if (file == null) return null;
		return new FileStat(filename, file);
	}
  
	public boolean createLink (String src, String dst)
	{
		Lib.debug(debugFlag, "creating link from: " + src + " to " + dst);
		LinkedList<String> pathS = pathParser(src);
		LinkedList<String> pathT = pathParser(dst);
		Folder S = cur_folder, T = cur_folder;
		String filename;
		int fileAddr;
		if (pathS.size() == 0) return false;
		else {
			while (pathS.size() > 1)
			{
				S = getFolderFromPath(pathS.removeFirst(), S);
				if (S == null)
					return false;
			}
			filename = pathS.removeFirst();
		}
		if (pathT.size() != 0)
		{
			while (pathT.size() > 1)
			{
				T = getFolderFromPath(pathT.removeFirst(), T);
				if (T == null)
					return false;
			}
			fileAddr = T.get(pathT.removeFirst());
		}
		else fileAddr = cur_folder.inode.addr;
		if (filename == "/") return false;
		if (S.get(filename) != -1)
			return false;
		S.addEntry(filename, fileAddr);
		getFileFromAddr(fileAddr, filename).inode.link_count++;
		return true;
	}
  
	public boolean createSymlink (String src, String dst)
	{
		Lib.debug(debugFlag, "creating symbol link from: " + src + " to " + dst);
		LinkedList<String> pathS = pathParser(src);
		Folder S = cur_folder;
		String filename;
		if (pathS.size() == 0) return false;
		else {
			while (pathS.size() > 1)
			{
				S = getFolderFromPath(pathS.removeFirst(), S);
				if (S == null)
					return false;
			}
			filename = pathS.removeFirst();
		}
		int addr = S.createFile(filename);
		File file = getFileFromAddr(addr, filename);
		char[] data = dst.toCharArray();
		byte[] data2 = new byte[data.length];
		System.arraycopy(data, 0, data2, 0, dst.length());
		file.write(0, data2, 0, dst.length());
		return false;
	}
  
	public int getFreeSize()
	{
		return freeList.getFreeSize();
	}
  
	public int getSwapFileSectors()
	{
		
		return 0;
	}
}
