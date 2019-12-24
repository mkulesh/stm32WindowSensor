/*
 * stm32WindowSensor: RF window sensors: STM32L + RFM69 + Android
 *
 * Copyright (C) 2019. Mikhail Kulesh
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details. You should have received a copy of the GNU General
 * Public License along with this program.
 */

package com.mkulesh.znet.serial;

import com.mkulesh.znet.Config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;

public class SerialCommunication
{
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final char END_OF_MESSAGE = '.';

    private final Logger logger;
    private final MessageHandlerIf messageHandler;
    private Thread readerThread = null;

    public SerialCommunication(Logger logger, MessageHandlerIf m)
    {
        this.logger = logger;
        this.messageHandler = m;
    }

    @SuppressWarnings("unchecked")
    public void printAvailablePorts()
    {
        Enumeration<CommPortIdentifier> thePorts = CommPortIdentifier.getPortIdentifiers();
        while (thePorts.hasMoreElements())
        {
            CommPortIdentifier com = thePorts.nextElement();
            switch (com.getPortType())
            {
            case CommPortIdentifier.PORT_SERIAL:
                try
                {
                    CommPort thePort = com.open("CommUtil", 50);
                    thePort.close();
                    logger.info("found port " + thePort.getName());
                }
                catch (PortInUseException e)
                {
                    logger.log(Level.SEVERE, "port " + com.getName() + " is in use", e);
                }
                catch (Exception e)
                {
                    logger.log(Level.SEVERE, "failed to open port " + com.getName(), e);
                    e.printStackTrace();
                }
            }
        }
    }

    public void start()
    {
        final Timer timer = new Timer();
        timer.schedule(new TimerTask()
        {
            @Override
            public void run()
            {
                periodicProcessing();
            }
        }, 0, 1000);
    }

    private void periodicProcessing()
    {
        if (readerThread != null && readerThread.isAlive())
        {
            return;
        }
        try
        {
            openPort(Config.getSerialPort());
        }
        catch (Exception e)
        {
            logger.log(Level.SEVERE, "can not initialize z-wave stick", e);
            readerThread = null;
        }
    }

    private void openPort(String portName) throws Exception
    {
        CommPortIdentifier portIdentifier;
        try
        {
            portIdentifier = CommPortIdentifier.getPortIdentifier(portName);
        }
        catch (NoSuchPortException e)
        {
            throw new Exception("port " + portName + " is not found");
        }
        if (portIdentifier.isCurrentlyOwned())
        {
            throw new Exception("port " + portName + " is in use");
        }
        CommPort commPort;
        try
        {
            commPort = portIdentifier.open(this.getClass().getName(), 2000);
        }
        catch (PortInUseException e)
        {
            throw new Exception("port " + portName + " is in use");
        }
        catch (Exception e)
        {
            throw new Exception("unknown exception");
        }
        if (!(commPort instanceof SerialPort))
        {
            commPort.close();
            throw new Exception("only serial ports can be handled");
        }
        SerialPort serialPort = (SerialPort) commPort;
        serialPort.setSerialPortParams(Config.getSerialPortSpeed(), SerialPort.DATABITS_8, SerialPort.STOPBITS_1,
                SerialPort.PARITY_NONE);

        readerThread = new Thread(new SerialReader(logger, serialPort, messageHandler), "serial port reader");
        readerThread.start();
    }

    public static class SerialReader implements Runnable
    {
        private final Logger logger;
        private final InputStream in;
        private final MessageHandlerIf messageHandler;

        SerialReader(Logger logger, SerialPort serialPort, MessageHandlerIf m) throws IOException
        {
            this.logger = logger;
            this.in = serialPort.getInputStream();
            this.messageHandler = m;
        }

        public void run()
        {
            messageHandler.connected();
            logger.info("started " + Thread.currentThread().getName());
            byte[] buffer = new byte[1024];
            StringBuilder sensorData = new StringBuilder();
            try
            {
                while (true)
                {
                    final int len = this.in.read(buffer);
                    if (len < 0)
                    {
                        messageHandler.disconnected();
                        break;
                    }
                    else if (len == 0)
                    {
                        continue;
                    }
                    byte[] message = Arrays.copyOfRange(buffer, 0, len);
                    sensorData.append(new String(message, UTF_8));
                    final String currData = sensorData.toString();
                    int endIdx = currData.indexOf(END_OF_MESSAGE);
                    if (endIdx > 0)
                    {
                        // skip all non-alphabetic characters at the beginning
                        int startIdx = 0;
                        for (; startIdx < currData.length(); startIdx++)
                        {
                            if (Character.isAlphabetic(currData.charAt(startIdx)))
                            {
                                break;
                            }
                        }
                        messageHandler.handle(currData.substring(startIdx, endIdx));

                        // skip all non-alphabetic characters at the end
                        for (; endIdx < currData.length(); endIdx++)
                        {
                            final char c = currData.charAt(endIdx);
                            if (Character.isAlphabetic(c) && c != END_OF_MESSAGE)
                            {
                                break;
                            }
                        }

                        // store the remaining message
                        sensorData = new StringBuilder();
                        if (endIdx < currData.length())
                        {
                            sensorData.append(currData.substring(endIdx));
                        }
                    }
                }
            }
            catch (IOException e)
            {
                messageHandler.disconnected();
            }
            logger.info("stopped " + Thread.currentThread().getName());
        }
    }
}
