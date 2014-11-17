package nachos.filesys;

public class FileStat
{
  public static final int FILE_NAME_MAX_LEN = 256;
  public static final int NORMAL_FILE_TYPE = 0;
  public static final int DIR_FILE_TYPE = 1;
  public static final int LinkFileType = 2;
  
  public String name;
  public int size;
  public int sectors;
  public int type;
  public int inode;
  public int links;

  public FileStat(String name, File file)
  {
	  this.name = name;
	  size = file.inode.file_size;
	  sectors = file.inode.getNumSec();
	  type = file.inode.file_type;
	  inode = file.inode.addr;
	  links = file.inode.link_count;
  }
  public String toString()
  {
	  return name + "\t" + size + "\t" + sectors + "\t" + type + "\t" + inode + "\t" + links; 
  }
}
