mkdir bin
javac nachos-sjtu/src/nachos/*/*.java -d bin

nachos="java -cp bin nachos.machine.Machine -s 703243 "

echo "Testing filesys_link"
timeout 20s time $nachos-[] conf/proj5.conf -- nachos.ag.FilesysGrader -x filesys_link.coff
echo "Testing filesys_dir"
timeout 20s time $nachos-[] conf/proj5.conf -- nachos.ag.FilesysGrader -x filesys_dir.coff -# output=filesys_dir.out
echo "Testing fs_num"
timeout 20s time $nachos-[] conf/proj5.conf -- nachos.ag.FilesysGrader -x fs_num.coff -# output=fs_num.out
echo "Testing filesys_file"
timeout 100s time $nachos-[] conf/proj5.conf -- nachos.ag.FilesysGrader -x filesys_file.coff -# output=filesys_file.out,coffPar0=10240,coffPar1=100
echo "Testing filesys_symlink"
timeout 20s time $nachos-[] conf/proj5.conf -- nachos.ag.FilesysGrader -x filesys_symlink.coff
echo "Testing filesys_open_unlink"
timeout 20s time $nachos-[] conf/proj5.conf -- nachos.ag.FilesysGrader -x filesys_open_unlink.coff
echo "Testing fs_dir_file"
timeout 20s time $nachos-[] conf/proj5.conf -- nachos.ag.FilesysGrader -x fs_dir_file.coff
echo "Testing fs_linuxstyle_dir_op"
timeout 20s time $nachos-[] conf/proj5.conf -- nachos.ag.FilesysGrader -x fs_linuxstyle_dir_op.coff
echo "Testing fs_size"
timeout 200s time $nachos-[] conf/proj5.conf -- nachos.ag.FilesysGrader -x fs_size.coff -# output=fs_size.out
