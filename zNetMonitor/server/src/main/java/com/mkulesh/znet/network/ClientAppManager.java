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
import com.mkulesh.znet.common.CustomLogger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientAppManager extends ConnectionManager
{
    private final HashMap<Integer, ClientAppCommThread> clients = new HashMap<>();

    public ClientAppManager(Logger logger, StateManager stateManager, String networkInterface, int port)
    {
        super(logger, stateManager, networkInterface, port);
    }

    public HashMap<Integer, ClientAppCommThread> getClients()
    {
        return clients;
    }

    @Override
    public void run()
    {
        ServerSocketChannel listener = null;
        try
        {
            listener = ServerSocketChannel.open();
            listener.bind(new InetSocketAddress(port));
            logger.log(Level.INFO, "client network service " + getIPAddress() + ":" + Integer.toString(port) + " is ready",
                    CustomLogger.ADD_TO_CONSOLE);
            while (true)
            {
                ClientAppCommThread client = new ClientAppCommThread(logger, this, listener.accept(),
                        IdGenerator.generateId());
                logger.info(client.toString() + ": connection established");
                onClientConnected(client);
                client.start();
            }
        }
        catch (IOException e)
        {
            logger.log(Level.SEVERE, "can not open TCP/IP port " + port, e);
        }
        if (listener != null)
        {
            try
            {
                listener.close();
            }
            catch (IOException e)
            {
                // nothing to do
            }
        }
    }

    private void onClientConnected(ClientAppCommThread client)
    {
        synchronized (clients)
        {
            clients.put(client.getClientId(), client);
            logger.info("there are " + clients.size() + " active connection(s)");
        }
        if (stateManager != null)
        {
            stateManager.sendConfiguration(client);
            stateManager.sendDeviceState(client);
        }
    }

    void onClientDisconnected(ClientAppCommThread client)
    {
        synchronized (clients)
        {
            clients.remove(client.getClientId());
            logger.info("there are " + clients.size() + " active connection(s)");
        }
    }
}
