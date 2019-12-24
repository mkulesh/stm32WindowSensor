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

import java.util.Locale;

public class DeviceConfig
{
    private final static String SEPARATOR = "\\|";

    private final int id;
    private final String type, model, floor, room, position;

    public DeviceConfig(String cfg) throws Exception
    {
        String[] tokens = cfg.split(SEPARATOR);
        if (tokens.length != Message.Type.DEVICE_CONFIG.getParNumber())
        {
            throw new Exception("invalid number of tokens: " + tokens.length);
        }
        id = Integer.parseInt(tokens[0].trim());
        type = tokens[1].trim();
        model = tokens[2].trim();
        floor = tokens[3].trim();
        room = tokens[4].trim();
        position = tokens[5].trim();
    }

    public DeviceConfig(Message m) throws Exception
    {
        if (m.getType() != Message.Type.DEVICE_CONFIG)
        {
            throw new Exception("invalid message type");
        }
        id = Integer.parseInt(m.getParameter(0));
        type = m.getParameter(1);
        model = m.getParameter(2);
        floor = m.getParameter(3);
        room = m.getParameter(4);
        position = m.getParameter(5);
    }

    public Message getDeviceConfigMsg()
    {
        Message m = new Message(Message.Type.DEVICE_CONFIG);
        m.addParameter(Integer.toString(id));
        m.addParameter(type);
        m.addParameter(model);
        m.addParameter(floor);
        m.addParameter(room);
        m.addParameter(position);
        return m;
    }

    public String toString()
    {
        return "#" + Integer.toString(getId()) + ": " + getType() + "(" + getModel() + "); floor " + getFloor()
                + "; room " + getRoom() + "; position " + getPosition();
    }

    public int getId()
    {
        return id;
    }

    public String getType()
    {
        return type;
    }

    private String getModel()
    {
        return model;
    }

    public String getFloor()
    {
        return floor;
    }

    private String getRoom()
    {
        return room;
    }

    private String getPosition()
    {
        return position;
    }

    public String getImageName()
    {
        final Locale locale = Locale.getDefault();
        return "floor_" + floor.toLowerCase(locale) + "_" + room.toLowerCase(locale) + "_"
                + position.toLowerCase(locale);
    }
}
