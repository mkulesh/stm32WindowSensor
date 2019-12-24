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

import com.mkulesh.znet.StateManager;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

class ConnectionManager implements Runnable
{
    final Logger logger;
    final StateManager stateManager;
    private final String networkInterface;
    final int port;
    private Thread thread = null;

    ConnectionManager(Logger logger, StateManager stateManager, String networkInterface, int port)
    {
        this.logger = logger;
        this.stateManager = stateManager;
        this.networkInterface = networkInterface;
        this.port = port;
        thread = new Thread(this, this.getClass().getSimpleName());
    }

    public void start()
    {
        thread.start();
    }

    @Override
    public void run()
    {

    }

    InetAddress getIPAddress()
    {
        try
        {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements())
            {
                NetworkInterface in = interfaces.nextElement();
                if (networkInterface.equals(in.getName()))
                {
                    Enumeration<InetAddress> addresses = in.getInetAddresses();
                    while (addresses.hasMoreElements())
                    {
                        InetAddress add = addresses.nextElement();
                        if (add instanceof Inet4Address && !add.isLoopbackAddress())
                        {
                            return add;
                        }
                    }
                }
            }
        }
        catch (SocketException e)
        {
            logger.log(Level.SEVERE, "can not access network interfaces", e);
        }

        return null;
    }

    StateManager getStateManager()
    {
        return stateManager;
    }
}
