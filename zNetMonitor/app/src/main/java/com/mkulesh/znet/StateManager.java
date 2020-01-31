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

import android.annotation.SuppressLint;
import android.util.Pair;

import com.mkulesh.znet.common.DeviceConfig;
import com.mkulesh.znet.common.DeviceState;
import com.mkulesh.znet.common.Message;

import java.util.ArrayList;
import java.util.HashMap;

import androidx.annotation.NonNull;

public class StateManager
{
    @SuppressLint("UseSparseArrays")
    private final HashMap<Integer, DeviceState> devices = new HashMap<>();
    private final ArrayList<Pair<String, String>> serverState = new ArrayList<>();

    StateManager()
    {
        // empty
    }

    @NonNull
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

    HashMap<Integer, DeviceState> getDevices()
    {
        return devices;
    }

    ArrayList<Pair<String, String>> getServerState()
    {
        return serverState;
    }

    void handleMessage(Message m)
    {
        switch (m.getType())
        {
        case DEVICE_CONFIG:
            handleDeviceConfigMsg(m);
            break;
        case DEVICE_NUMBER:
            handleDeviceNumberMsg(m);
            break;
        case DEVICE_STATE:
            handleDeviceStateMsg(m);
            break;
        case SERVER_STATE:
            handleServerStateMsg(m);
            break;
        case HEARTBIT:
            // nothing to do
            break;
        default:
            break;
        }
    }

    boolean isAlarm()
    {
        for (DeviceState s : devices.values())
        {
            if (s.isAlarm())
            {
                return true;
            }
        }
        return false;
    }

    boolean isWarning()
    {
        for (DeviceState s : devices.values())
        {
            if (s.isWarning())
            {
                return true;
            }
        }
        return false;
    }

    private void handleDeviceConfigMsg(Message m)
    {
        Logging.info(this, "handle device configuration message: " + m.toString());
        try
        {
            DeviceState s = new DeviceState(new DeviceConfig(m));
            if (devices.containsKey(s.getId()))
            {
                throw new Exception("multiply configuration for device id #" + s.getId());
            }
            devices.put(s.getId(), s);
        }
        catch (Exception e)
        {
            Logging.info(this, "can not create device: " + e.getLocalizedMessage());
        }
    }

    private void handleDeviceNumberMsg(Message m)
    {
        Logging.info(this, "handle device number message: " + m.toString());
        final int size = Integer.parseInt(m.getParameter(0));
        if (devices.size() != size)
        {
            Logging.info(this, "number of devices is invalid");
            return;
        }
        for (DeviceState d : getDevices().values())
        {
            Logging.info(this, d.getConfig().toString());
        }
        Logging.info(this, "initial devices state: " + toString());
    }

    private void handleDeviceStateMsg(Message m)
    {
        Logging.info(this, "handle device state message: " + m.toString());
        final int id = Integer.parseInt(m.getParameter(0));
        DeviceState d = devices.get(id);
        if (d == null)
        {
            Logging.info(this, "devices #" + id + "is not configured");
            return;
        }
        d.updateFromMessage(m);
        Logging.info(this, "devices state: " + toString());
    }

    private void handleServerStateMsg(Message m)
    {
        Logging.info(this, "handle server state message: " + m.toString());
        serverState.clear();
        for (int i = 0; i < Message.Type.SERVER_STATE.getParNumber(); i += 2)
        {
            serverState.add(new Pair<>(m.getParameter(i), m.getParameter(i + 1)));
        }
    }
}
