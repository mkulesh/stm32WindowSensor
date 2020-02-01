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

package com.mkulesh.znet;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Config
{
    private final static String CONFIGFILE_NAME = "znet.cfg";

    final static String LOGFILE_NAME = "znet_%s.log";
    final static long LOGFILE_SIZE = 10 * 1024 * 1024;
    public final static String SENSOR_READING_CMD = "ipmitool sensor";
    public final static String SENSOR_DATA_SEPARATOR = "\\|";
    private final static String PATH_SEPARATOR = "\\|";

    private static String serialPort = "/dev/ttyS4";
    private static int serialPortSpeed = 57600;
    private static String networkInterface = "enp5s0f0";
    private static int clientAppPort = 5017;
    private static int heartbitInterval = 1000;

    // sensor configuration
    private static List<String> sensors = new ArrayList<>();

    // server state configuration
    private static String[] serverSensorID = null;
    private static int diskSpaceNumber = 0;
    private static String[] diskSpacePath = null;
    private static String[] diskSpaceLabel = null;

    // login parameters
    private static String password = null;
    private static int loginWaitingTime = 5;

    /**
     * Load configuration from file.
     */
    static void loadConfiguration(Logger logger)
    {
        Properties properties = new Properties();
        try
        {
            FileInputStream fis = new FileInputStream(CONFIGFILE_NAME);
            properties.load(fis);
            fis.close();
        }
        catch (Exception e)
        {
            logger.log(Level.SEVERE, "failed to read configuration file " + CONFIGFILE_NAME, e);
        }

        logger.info("loading configuration file: " + CONFIGFILE_NAME);

        // general configuration
        serialPort = getStringProperty(logger, properties, "serialPort", serialPort);
        serialPortSpeed = getIntProperty(logger, properties, "serialPortSpeed", serialPortSpeed);
        networkInterface = getStringProperty(logger, properties, "networkInterface", networkInterface);
        clientAppPort = getIntProperty(logger, properties, "clientAppPort", clientAppPort);
        heartbitInterval = getIntProperty(logger, properties, "heartbitInterval", heartbitInterval);

        // sensor configuration
        for (int i = 1; i < 256; i++)
        {
            final String propertyValue = properties.getProperty("sensor" + i);
            if (propertyValue == null)
            {
                break;
            }
            sensors.add(propertyValue);
        }

        // disk space configuration
        diskSpacePath = getStringListProperty(logger, properties, "diskSpacePath");
        diskSpaceLabel = getStringListProperty(logger, properties, "diskSpaceLabel");
        if (diskSpacePath == null || diskSpaceLabel == null)
        {
            logger.log(Level.SEVERE, "empty disk space configuration");
        }
        else if (diskSpacePath.length != diskSpaceLabel.length)
        {
            logger.log(Level.SEVERE, "invalid disk space configuration");
        }
        else
        {
            diskSpaceNumber = diskSpacePath.length;
        }

        // temperature sensors configuration
        serverSensorID = getStringListProperty(logger, properties, "serverSensorID");

        // login parameters
        password = getStringProperty(logger, properties, "password", "");
        loginWaitingTime = getIntProperty(logger, properties, "loginWaitingTime", loginWaitingTime);
    }

    private static String[] getStringListProperty(Logger logger, Properties properties, String propertyName)
    {
        final String value = getStringProperty(logger, properties, propertyName, null);
        String[] retValue = null;
        if (value != null)
        {
            retValue = value.split(PATH_SEPARATOR);
            for (int i = 0; i < retValue.length; i++)
            {
                retValue[i] = retValue[i].trim();
            }
        }
        return retValue;
    }

    private static String getStringProperty(Logger logger, Properties properties, String propertyName,
                                            String defaultValue)
    {
        String value = defaultValue;
        final String propertyValue = properties.getProperty(propertyName);
        if (propertyValue != null)
        {
            value = propertyValue;
        }
        logger.info(propertyName + ": " + value);
        return value;
    }

    private static int getIntProperty(Logger logger, Properties properties, String propertyName, int defaultValue)
    {
        int value = defaultValue;
        final String propertyValue = properties.getProperty(propertyName);
        if (propertyValue != null)
        {
            try
            {
                value = Integer.parseInt(propertyValue);
            }
            catch (NumberFormatException e)
            {
                logger.log(Level.SEVERE, "failed to parse " + propertyName + " \"" + propertyValue
                        + "\", using default value", e);
            }
        }
        logger.info(propertyName + ": " + value);
        return value;
    }

    public static String getSerialPort()
    {
        return serialPort;
    }

    public static int getSerialPortSpeed()
    {
        return serialPortSpeed;
    }

    static String getNetworkInterface()
    {
        return networkInterface;
    }

    static int getClientAppPort()
    {
        return clientAppPort;
    }

    public static int getHeartbitInterval()
    {
        return heartbitInterval;
    }

    static List<String> getSensors()
    {
        return sensors;
    }

    public static int getDiskSpaceNumber()
    {
        return diskSpaceNumber;
    }

    public static String[] getDiskSpacePath()
    {
        return diskSpacePath;
    }

    public static String[] getDiskSpaceLabel()
    {
        return diskSpaceLabel;
    }

    public static String[] getServerSensorID()
    {
        return serverSensorID;
    }

    public static String getPassword()
    {
        return password;
    }

    public static int getLoginWaitingTime()
    {
        return loginWaitingTime;
    }

}
