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

package com.mkulesh.znet.common;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class DeviceState
{
    public enum Warning
    {
        NO_ACTIVITY,
        NOT_READY,
        LOW_BATTERY,
        UNKNOWN_MESSAGE
    }

    private final DeviceConfig config;
    private boolean alarm = false;
    private final ArrayList<Warning> warnings = new ArrayList<>();
    private String batteryState = "";
    private String alarmTime = "";
    private static final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.US);

    public DeviceState(DeviceConfig config)
    {
        this.config = config;
    }

    public String toString()
    {
        String res = "#" + getId() + ": ";
        if (isAlarm() || isWarning())
        {
            if (isAlarm())
            {
                res += "ALARM/" + alarmTime;
            }
            if (isWarning())
            {
                res += warnings.toString();
            }
        }
        else
        {
            res += "OK";
        }
        return res;
    }

    public DeviceConfig getConfig()
    {
        return config;
    }

    public int getId()
    {
        return config.getId();
    }

    public boolean isAlarm()
    {
        return alarm;
    }

    public boolean isWarning()
    {
        return !warnings.isEmpty();
    }

    public ArrayList<Warning> getWarnings()
    {
        return warnings;
    }

    public String getAlarmTime()
    {
        return alarmTime;
    }

    public boolean setAlarm(boolean alarm)
    {
        boolean changed = this.alarm != alarm;
        this.alarm = alarm;
        if (changed)
        {
            alarmTime = isAlarm() ? timeFormat.format(new Date(System.currentTimeMillis())) : "";
        }
        return changed;
    }

    public String getBatteryState()
    {
        return batteryState;
    }

    public void setBatteryState(String batteryState)
    {
        this.batteryState = batteryState;
    }

    public boolean setWarning(Warning warning, boolean value)
    {
        boolean changed = false;
        if (!warnings.contains(warning) && value)
        {
            changed = true;
            warnings.add(warning);
        }
        else if (warnings.contains(warning) && !value)
        {
            changed = true;
            warnings.remove(warning);
        }
        return changed;
    }

    public Message getDeviceStateMsg()
    {
        Message m = new Message(Message.Type.DEVICE_STATE);
        m.addParameter(Integer.toString(getId()));
        m.addParameter(Boolean.toString(alarm));
        m.addParameter(alarmTime);
        m.addParameter(batteryState);
        m.addParameter(warnings.toString());
        return m;
    }

    public void updateFromMessage(Message m)
    {
        alarm = Boolean.valueOf(m.getParameter(1));
        alarmTime = m.getParameter(2);
        batteryState = m.getParameter(3);
        warnings.clear();
        final String warningsStr = m.getParameter(4);
        for (Warning w : Warning.values())
        {
            if (warningsStr.contains(w.toString()))
            {
                warnings.add(w);
            }
        }
    }
}
