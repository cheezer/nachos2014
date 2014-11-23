package nachos.filesys;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;

import nachos.machine.Disk;
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
  
  private HashMap<Integer, Folder> folderMap = new HashMap<Integer, Folder>();
  
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
			root_folder = new Folder(inode_root_folder, "");
			//root_folder = getFolderFromAddr(Folder.STATIC_ADDR, "rootFolder");
			root_folder.fatherAddr = root_folder.inode.addr;	
			folderMap.put(Folder.STATIC_ADDR, root_folder);
			cur_folder = root_folder;
			folderMap.put(Folder.STATIC_ADDR, root_folder);
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
			root_folder = new Folder(inode_root_folder, "");	
			root_folder.load();	
			folderMap.put(Folder.STATIC_ADDR, root_folder);
			root_folder.fatherAddr = root_folder.inode.addr;	
			cur_folder = root_folder;
			folderMap.put(Folder.STATIC_ADDR, root_folder);
		}
	}
  
  public void finish ()
  {
	Lib.debug(debugFlag, "finishing real file system");
    root_folder.save();
    for (Folder i: folderMap.values())
    {
    	i.save();
    	i.inode.save();
    }
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
			if (inode.file_type != INode.TYPE_FOLDER)
				return null;
			Folder folder = new Folder(inode, name);
			folder.load();
			folderMap.put(addr, folder);
		}
		//System.out.println(addr + " " + folderMap.get(addr).fatherAddr);
		return folderMap.get(addr);
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
		if (name.equals(".."))
		{
			String st = folder.getName();
			int p = st.lastIndexOf('/');
			Lib.assertTrue(p != -1 || st == "");
			return getFolderFromAddr(folder.fatherAddr, st.substring(0, p));
		}
		else if (name.equals("/"))
			return root_folder;
		else {
			return getFolderFromAddr(folder.get(name), folder.getName() + "/" + name);
		}
	}
	
	public String getAbsolutePath(String name)
	{
		LinkedList<String> path2 = new LinkedList<String>(cur_path);
		LinkedList<String> path = pathParser(name);
		while (path.size() > 0)
		{
			String st = path.removeFirst();
			if (st.equals(".."))
			{
				if (path2.size() > 0) path2.removeLast();
			}
			else if (st.equals("/"))
				path2.clear();
			else {
				path2.add(st);
			}
		}
		String st = "";
		if (path2.isEmpty()) return "/";
		else 
		{
			for (Iterator<String> it = path2.iterator(); it.hasNext();)
				st += "/" + it.next();
		}
		return st;
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
		File file = getFileFromAddr(folder.get(filename), getAbsolutePath(name));
		if (file != null && file.inode.file_type == INode.TYPE_FOLDER) 
			return null;
		if (file != null && file.inode.file_type == INode.TYPE_SYMLINK)
		{
			int n = file.inode.file_size;
			byte[] data = new byte[n];
			file.read(0, data, 0, n);
			String st = "";
			for (int i = 0; i < n; ++i)
				st += (char)data[i];
			//System.out.println(st);
			return open(st, create);
		}
		if (file != null)
		{
			if (create)
				file.inode.setFileSize(0);
			return file;
		}
		else 
			if (!create) return null;
			else
			{
				File f = getFileFromAddr(folder.createFile(filename), getAbsolutePath(name));
				//System.out.println(f.getName());
				return f;
			}
		
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
		File file = getFileFromAddr(folder.get(filename), getAbsolutePath(name));
		if (file != null)
		{
			folder.removeEntry(filename);
			if (--file.inode.link_count == 0)
			{
				fileMap.remove(file.inode.addr);
				file.inode.free();
			}
			return true;
		}
		else	
			return false;
	}
	public boolean removeSymLink (String name)
	{
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
		File file = getFileFromAddr(folder.get(filename), getAbsolutePath(name));
		if (file != null && file.inode.file_type == INode.TYPE_SYMLINK)
		{
			Lib.debug(debugFlag, "removing symbol link: " + name);
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
		Lib.debug(debugFlag, "creating folder: " + name);
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
		if (getFileFromAddr(folder.get(filename), getAbsolutePath(name)) != null)
			return false;
		Folder cre = getFolderFromAddr(folder.get(filename), getAbsolutePath(name));
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
			{
				Lib.debug(debugFlag, name + "not moved");
				return false;
			}
		}
		String filename = path.removeFirst();
		Folder cre = getFolderFromAddr(folder.get(filename), getAbsolutePath(name));
		if (cre == null || !cre.keys().isEmpty()) 
		{
			Lib.debug(debugFlag, name + "not moved");
			return false;
		}
		if (--cre.inode.link_count == 0)
		{
			folderMap.remove(cre.inode.addr);
			fileMap.remove(cre.inode.addr);
			cre.inode.free();	
		}
		folder.removeEntry(filename);
		Lib.debug(debugFlag, name + "successfully moved");
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
			if (st.equals(".."))
			{
				if (path2.size() > 0) path2.removeLast();
			}
			else if (st.equals("/"))
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
		String st = "";
		if (cur_path.isEmpty()) return "/";
		else 
		{
			for (Iterator<String> it = cur_path.iterator(); it.hasNext();)
				st += "/" + it.next();
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
  
	public boolean createLink (String oldName, String newName)//old name, new name
	{
		Lib.debug(debugFlag, "creating link from: " + newName + " to " + oldName);
		LinkedList<String> pathS = pathParser(newName);
		LinkedList<String> pathT = pathParser(oldName);
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
  
	public boolean createSymlink (String dst, String src)
	{
		Lib.debug(debugFlag, "creating symbol link of: " + dst + " using " + src);
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
		int fileAddr = S.createSymFile(filename);
		File file = getFileFromAddr(fileAddr, getAbsolutePath(src));
		char[] data = getAbsolutePath(dst).toCharArray();
		byte[] data2 = new byte[data.length];
		for (int i = 0; i < data.length; ++i)
			data2[i] = (byte)data[i];
		file.write(0, data2, 0, dst.length());
		return true;
	}
  
	public int getFreeSize()
	{
		int swapSize = getFileFromAddr(root_folder.get("SWAP"), "SWAP").inode.file_size;
		if (swapSize == 0)
			return freeList.getFreeSize();
		else
			return freeList.getFreeSize() + ((swapSize - 1) / Disk.SectorSize + 1) * Disk.SectorSize;
	}
  
	public int getSwapFileSectors()
	{
		
		return 0;
	}
}
