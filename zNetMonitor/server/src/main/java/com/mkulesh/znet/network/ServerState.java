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

package com.mkulesh.znet.network;

import com.mkulesh.znet.Config;
import com.mkulesh.znet.common.CustomLogger;
import com.mkulesh.znet.common.Message;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerState
{
    private final Logger logger;

    public ServerState(Logger logger)
    {
        this.logger = logger;
    }

    private HashMap<String, String> readSensors()
    {
        HashMap<String, String> sensorData = new HashMap<>();
        Runtime rt = java.lang.Runtime.getRuntime();
        try
        {
            Process p = rt.exec(Config.SENSOR_READING_CMD);
            p.waitFor();
            InputStream is = p.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String s;
            while ((s = reader.readLine()) != null)
            {
                String[] tockens = s.split(Config.SENSOR_DATA_SEPARATOR);
                if (tockens.length > 2)
                {
                    try
                    {
                        final int val = (int) Float.parseFloat(tockens[1].trim());
                        String dim = tockens[2].trim();
                        if ("degrees C".equals(dim))
                        {
                            dim = "°C";
                        }
                        sensorData.put(tockens[0].trim(), Integer.toString(val) + dim);
                    }
                    catch (Exception e)
                    {
                        sensorData.put(tockens[0].trim(), tockens[1].trim());
                    }
                }
            }
            is.close();
            return sensorData;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    public Message getServerStateMsg()
    {
        final HashMap<String, String> sensorData = readSensors();
        if (sensorData == null)
        {
            logger.log(Level.SEVERE, "can not read sensors state", CustomLogger.ADD_TO_CONSOLE);
            return null;
        }
        Message m = new Message(Message.Type.SERVER_STATE);
        for (String sensorId : Config.getServerSensorID())
        {
            final String val = sensorData.get(sensorId);
            m.addParameter(sensorId);
            m.addParameter(val == null ? "N/A" : val);
        }
        for (int i = 0; i < Config.getDiskSpaceNumber(); i++)
        {
            try
            {
                FileStore store = Files.getFileStore(Paths.get(Config.getDiskSpacePath()[i]));
                final double total = store.getTotalSpace();
                final double free = store.getUsableSpace();
                final double used = total - free;
                final int percent = (int) (100.0 * used / total);
                m.addParameter(Config.getDiskSpaceLabel()[i]);
                m.addParameter(Integer.toString(percent) + "%");
            }
            catch (IOException e)
            {
                logger.log(Level.SEVERE, "error querying disk space", e);
            }
        }
        return m;
    }

}
