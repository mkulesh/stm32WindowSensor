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
import com.mkulesh.znet.StateManager;
import com.mkulesh.znet.common.CustomLogger;
import com.mkulesh.znet.common.DeviceState;
import com.mkulesh.znet.common.DeviceState.Warning;
import com.mkulesh.znet.common.Utils;

import java.text.DecimalFormat;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MessageHandler implements MessageHandlerIf
{
    private final Logger logger;
    private final StateManager stateManager;

    public MessageHandler(Logger logger, StateManager stateManager)
    {
        this.logger = logger;
        this.stateManager = stateManager;
    }

    public void handle(final String data)
    {
        if (data.isEmpty())
        {
            return;
        }

        String[] tokens =  data.split(";");
        if (tokens.length != 8 && tokens.length != 4)
        {
            logger.warning("invalid tokens number in the input message: " + data);
            return;
        }

        if (!tokens[0].equals("GW"))
        {
            logger.warning("invalid header in the input message: " + data);
            return;
        }

        switch (parseToken(tokens[1], data))
        {
        case 1:
            logger.info("gateway startup message: " + data);
            break;
        case 2:
            logger.info("gateway error: " + data);
            return;
        case 3:
            processWindowSensorMessage(tokens, data);
            break;
        }
    }

    @Override
    public void connected()
    {
        logger.log(Level.INFO, "sensor gateway " + Config.getSerialPort() + " is connected", CustomLogger.ADD_TO_CONSOLE);
        stateManager.setReady(true);
    }

    @Override
    public void disconnected()
    {
        logger.log(Level.SEVERE, "sensor gateway is disconnected", CustomLogger.ADD_TO_CONSOLE);
        stateManager.setReady(false);
    }

    private void processWindowSensorMessage(final String[] tokens, final String data)
    {
        if (stateManager == null)
        {
            return;
        }

        int nodeId = parseToken(tokens[2], data);
        DeviceState d = stateManager.getDevices().get(nodeId);
        if (d == null)
        {
            logger.warning("can not process window sensor event: device #" + nodeId + " is not configured");
            return;
        }

        logger.info(">> " + data);

        // >> GW;3;7;-67;0;385;33;18
        int state = parseToken(tokens[4], data);
        boolean changed = (state == 0)? d.setAlarm(true) : d.setAlarm(false);
        try
        {
            final DecimalFormat df = Utils.getDecimalFormat("0.0 V");
            d.setBatteryState(df.format((float)Integer.parseInt(tokens[6]) / 10.0));
        }
        catch (Exception ex)
        {
            d.setBatteryState("");
        }

        changed |= d.setWarning(Warning.NO_ACTIVITY, false);
        if (changed)
        {
            logger.info("new state: " + stateManager.toString());
            stateManager.sendDeviceState(d);
        }
    }

    private int parseToken(final String token, final String data)
    {
        try
        {
            return Integer.parseInt(token);
        }
        catch (Exception e)
        {
            logger.warning("invalid token " + token + " in the message: " + data);
            return -1;
        }
    }

}
