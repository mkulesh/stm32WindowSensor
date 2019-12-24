#!/bin/csh

setenv LD_LIBRARY_PATH .
rm -f /var/lock/LCK..ttyS4
java -jar znet-server-1.2.jar

