javac nachos/*/*.java

echo "-----------------boad test-----------------"

java nachos.machine.Machine -- nachos.ag.BoatGrader -# adults=4,children=4 -[] ../../conf/proj1r.conf

echo "-----------------donation test-------------"

java nachos.machine.Machine -- nachos.ag.DonationGrader -[] ../../conf/proj1p.conf

echo "--------------Nachos - JoinGrader-----------"

java nachos.machine.Machine -- nachos.ag.JoinGrader -# waitTicks=1000,times=10 -[] ../../conf/proj1r.conf

echo "--------------Nachos - LockGrader11---------"

java nachos.machine.Machine -- nachos.ag.LockGrader11 -[] ../../conf/proj1r.conf

echo "--------------Nachos - PriorityGrader-------"

java nachos.machine.Machine -- nachos.ag.PriorityGrader -# threads=5,times=10,length=1000 -[] ../../conf/proj1p.conf

echo "--------------Nachos - ThreadGrader1--------"

java nachos.machine.Machine -- nachos.ag.ThreadGrader1 -[] ../../conf/proj1r.conf

echo "--------------Nachos - ThreadGrader2--------"

java nachos.machine.Machine -- nachos.ag.ThreadGrader2 -[] ../../conf/proj1r.conf

rm nachos/*/*.class
