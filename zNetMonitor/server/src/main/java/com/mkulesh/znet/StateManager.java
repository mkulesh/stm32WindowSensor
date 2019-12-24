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

import com.mkulesh.znet.common.DeviceConfig;
import com.mkulesh.znet.common.DeviceState;
import com.mkulesh.znet.common.DeviceState.Warning;
import com.mkulesh.znet.common.Message;
import com.mkulesh.znet.network.ClientAppCommThread;
import com.mkulesh.znet.network.ClientAppManager;
import com.mkulesh.znet.network.ServerState;

import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

public class StateManager
{
    private final ServerState serverState;
    private final HashMap<Integer, DeviceState> devices = new HashMap<>();
    private ClientAppManager clientAppManager = null;

    StateManager(Logger logger)
    {
        serverState = new ServerState(logger);
    }

    void setClientAppManager(ClientAppManager clientAppManager)
    {
        this.clientAppManager = clientAppManager;
    }

    void readConfigurationFile(List<String> sensors) throws Exception
    {
        final String COMMENT = "#";

        // read configuration file 
        int sensorNr = 0;
        for (String line : sensors)
        {
            sensorNr++;
            if (line.trim().isEmpty() || line.trim().startsWith(COMMENT))
            {
                continue;
            }
            try
            {
                DeviceState s = new DeviceState(new DeviceConfig(line));
                if (devices.containsKey(s.getId()))
                {
                    throw new Exception("multiply configuration for device id #" + Integer.toString(s.getId()));
                }
                devices.put(s.getId(), s);
            }
            catch (Exception e)
            {
                throw new Exception("can not sensor line " + Integer.toString(sensorNr) + ": " + e.getMessage());
            }
        }
    }

    public String toString()
    {
        String res = "";
        for (DeviceState s : devices.values())
        {
            if (!res.isEmpty())
            {
                res += "; ";
            }
            res += s.toString();
        }
        return res;
    }

    public HashMap<Integer, DeviceState> getDevices()
    {
        return devices;
    }

    public void sendConfiguration(ClientAppCommThread client)
    {
        for (DeviceState d : getDevices().values())
        {
            client.sendMessage(d.getConfig().getDeviceConfigMsg());
        }
        Message deviceNumberMsg = new Message(Message.Type.DEVICE_NUMBER);
        deviceNumberMsg.addParameter(Integer.toString(devices.size()));
        client.sendMessage(deviceNumberMsg);
    }

    public void sendDeviceState(ClientAppCommThread client)
    {
        for (DeviceState d : getDevices().values())
        {
            client.sendMessage(d.getDeviceStateMsg());
        }
        client.sendMessage(serverState.getServerStateMsg());
    }

    public void sendDeviceState(DeviceState d)
    {
        if (clientAppManager != null)
        {
            for (ClientAppCommThread client : clientAppManager.getClients().values())
            {
                client.sendMessage(d.getDeviceStateMsg());
            }
        }
    }

    public void setReady(boolean ready)
    {
        for (DeviceState d : getDevices().values())
        {
            d.setWarning(Warning.NOT_READY, !ready);
            sendDeviceState(d);
        }
    }

    void start()
    {
        setReady(false);
    }
}
