How to build and install server module and Android app:

1. On the build host:
- Install and setup AndroidStudio, import zNetMonitor project
- In the terminal, go to zNetMonitor directory and enter 

> ./gradlew buildServer

- File znet-server-X.X.jar appears in the _release_ directory. 
- Build the app from AndroidStudio
- File znet-monitor-1.1-release.apk appears in the _release_ directory
- Attach Android device via USB and install the app

> adb install release/znet-monitor-1.1-release.apk


2. On the CentOS 7 server:
- Ensure that the "FT232 Serial" exists and loaded:

> lsusb


> Bus 001 Device 091: ID 0403:6001 Future Technology Devices International, Ltd FT232 Serial (UART) IC

- Add port 5017 fo the firewall:

> firewall-cmd --zone=internal --add-port=5017/tcp --permanent

> firewall-cmd --reload

> firewall-cmd --list-all-zones 
  
- Copy znet ADE (release directory) that includes: znet-server-X.X.jar, znet.cfg, librxtxSerial.so library, startup.sh file
- As root, run startup.sh  
