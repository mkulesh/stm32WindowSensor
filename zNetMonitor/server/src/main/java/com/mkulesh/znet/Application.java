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

import com.mkulesh.znet.common.CustomLogger;
import com.mkulesh.znet.common.DeviceState;
import com.mkulesh.znet.network.ClientAppManager;
import com.mkulesh.znet.network.IdGenerator;
import com.mkulesh.znet.serial.MessageHandler;
import com.mkulesh.znet.serial.SerialCommunication;

import java.util.logging.Level;
import java.util.logging.Logger;

public class Application
{
    public static void main(String[] args)
    {
        final CustomLogger cLogger = new CustomLogger(Config.LOGFILE_NAME, Config.LOGFILE_SIZE);
        final Logger logger = cLogger.getLogger();

        logger.log(Level.INFO, "znet server started", CustomLogger.ADD_TO_CONSOLE);
        Config.loadConfiguration(logger);

        final StateManager stateManager = new StateManager(logger);

        MessageHandler messageHandler = new MessageHandler(logger, stateManager);

        SerialCommunication serialCommunication;
        try
        {
            serialCommunication = new SerialCommunication(logger, messageHandler);
            serialCommunication.printAvailablePorts();
        }
        catch (Exception | UnsatisfiedLinkError e)
        {
            serialCommunication = null;
            logger.log(Level.SEVERE, "can not initialize znet port", e);
        }

        try
        {
            stateManager.readConfigurationFile(Config.getSensors());
            for (DeviceState d : stateManager.getDevices().values())
            {
                logger.info(d.getConfig().toString());
            }
        }
        catch (Exception e)
        {
            logger.log(Level.SEVERE, "can not read configuration", e);
        }

        IdGenerator.reset();

        final ClientAppManager clientAppManager = new ClientAppManager(logger, stateManager, Config.getNetworkInterface(),
                Config.getClientAppPort());
        stateManager.setClientAppManager(clientAppManager);
        clientAppManager.start();
        stateManager.start();
        if (serialCommunication != null)
        {
            serialCommunication.start();
        }
    }
}
