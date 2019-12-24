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

import java.util.ArrayList;

public class Message
{
    public final static int SOCKET_BUFFER = 1024 * 4;
    public final static String START_TAG = "<message>";
    public final static String END_TAG = "</message>";
    public final static String START_NAME = "<name>";
    public final static String END_NAME = "</name>";
    public final static String START_PAR = "<p>";
    public final static String END_PAR = "</p>";
    public final static String AES_KEY = "my_aeg_key";

    public enum Type
    {
        HEARTBIT(0),
        CLIENT_LOGIN(3),
        DEVICE_CONFIG(6),
        DEVICE_NUMBER(1),
        DEVICE_STATE(4),
        SERVER_STATE(12),
        BOARD_STATE(2);

        private final int parNumber;

        Type(int parNumber)
        {
            this.parNumber = parNumber;
        }

        public int getParNumber()
        {
            return parNumber;
        }
    }

    private Type type;
    private final ArrayList<String> parameters = new ArrayList<>();

    public Message(Type type)
    {
        this.type = type;
    }

    public Message(String data) throws Exception
    {
        decode(data);
    }

    public void addParameter(String parameter)
    {
        parameters.add(parameter);
    }

    public String toString()
    {
        String res = type.toString();
        if (!parameters.isEmpty())
        {
            res += parameters.toString();
        }
        return res;
    }

    public Type getType()
    {
        return type;
    }

    public String getParameter(int i)
    {
        return parameters.get(i);
    }

    public String encode()
    {
        String str = START_NAME + type.toString() + END_NAME;
        switch (type)
        {
        case CLIENT_LOGIN:
        case DEVICE_CONFIG:
        case DEVICE_NUMBER:
        case DEVICE_STATE:
        case SERVER_STATE:
            for (String p : parameters)
            {
                str += START_PAR + p + END_PAR;
            }
            break;
        case HEARTBIT:
            // nothing to do
            break;
        }
        return str;
    }

    private void decode(String data) throws Exception
    {
        int startIndex = data.indexOf(START_NAME);
        int endIndex = data.indexOf(END_NAME);
        if (startIndex < 0 || endIndex < 0)
        {
            throw new Exception("name tag not found");
        }

        // name
        type = null;
        String str = data.substring(startIndex + Message.START_NAME.length(), endIndex).trim();
        for (Type t : Type.values())
        {
            if (str.equals(t.toString()))
            {
                type = t;
            }
        }
        if (type == null)
        {
            throw new Exception("unknown message name");
        }

        // parameters
        data = data.substring(endIndex + Message.END_NAME.length(), data.length());
        while (true)
        {
            startIndex = data.indexOf(Message.START_PAR);
            endIndex = data.indexOf(Message.END_PAR);
            if (startIndex < 0 || endIndex < 0)
            {
                break;
            }
            str = data.substring(startIndex + Message.START_PAR.length(), endIndex);
            parameters.add(str.trim());
            data = data.substring(endIndex + Message.END_PAR.length(), data.length());
        }
        if (parameters.size() != type.getParNumber())
        {
            throw new Exception("invalid parameter number");
        }
    }
}
