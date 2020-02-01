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
import com.mkulesh.znet.common.AdvancedEncryptionStandard;
import com.mkulesh.znet.common.Message;
import com.mkulesh.znet.common.Message.Type;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientAppCommThread implements Runnable
{
    private final Logger logger;
    private final ClientAppManager parentThread;
    private final SocketChannel socket;
    private final Integer clientId;
    private final Thread thread;
    private final Timer heartbitTimer;
    private final BlockingQueue<Message> messageQueue;
    private String inputStream = "";
    private final AdvancedEncryptionStandard initialEncryptor = new AdvancedEncryptionStandard(Message.AES_KEY);
    private AdvancedEncryptionStandard sessionEncryptor = null;

    ClientAppCommThread(Logger logger, ClientAppManager parentThread, SocketChannel socket, Integer clientId)
    {
        this.logger = logger;
        this.parentThread = parentThread;
        this.socket = socket;
        this.clientId = clientId;
        thread = new Thread(this, this.getClass().getSimpleName());
        heartbitTimer = new Timer();
        messageQueue = new ArrayBlockingQueue<>(100, true);
    }

    public String toString()
    {
        String addr = " none";
        try
        {
            addr = socket.getRemoteAddress().toString();
        }
        catch (IOException e)
        {
            // nothing to do
        }
        return "client [" + clientId.toString() + addr + "]";
    }

    Integer getClientId()
    {
        return clientId;
    }

    void start()
    {
        thread.start();
    }

    @Override
    public void run()
    {
        ByteBuffer buffer = ByteBuffer.allocate(Message.SOCKET_BUFFER);
        final long startTime = System.currentTimeMillis();

        heartbitTimer.schedule(new TimerTask()
        {
            @Override
            public void run()
            {
                if (messageQueue.isEmpty())
                {
                    messageQueue.add(new Message(Message.Type.HEARTBIT));
                }
            }
        }, 0, Config.getHeartbitInterval());

        try
        {
            socket.configureBlocking(false);
        }
        catch (IOException e)
        {
            logger.log(Level.SEVERE, "can not configure non-blocking mode", e);
        }

        while (true)
        {
            try
            {
                buffer.clear();
                final int readedSize = socket.read(buffer);
                if (readedSize < 0)
                {
                    break;
                }
                else if (readedSize > 0)
                {
                    Message inputMessage = processInputData(buffer);
                    if (inputMessage != null)
                    {
                        validateLoginMessage(inputMessage);
                    }
                }

                if (sessionEncryptor == null)
                {
                    final long currentTime = System.currentTimeMillis();
                    if (currentTime - startTime > Config.getLoginWaitingTime() * 1000)
                    {
                        logger.info(toString() + ": login can not be processed");
                        break;
                    }
                    continue;
                }

                Message m = messageQueue.take();
                if (m != null)
                {
                    ByteBuffer messageBuffer;
                    if (m.getType() != Message.Type.HEARTBIT)
                    {
                        logger.info(toString() + ": sending encrypted " + m.toString());
                        final String encryptedMessage = Message.START_TAG
                                + Base64.getEncoder().encodeToString(sessionEncryptor.encrypt(m.encode()))
                                + Message.END_TAG;
                        messageBuffer = ByteBuffer.wrap(encryptedMessage.getBytes(StandardCharsets.UTF_8));
                    }
                    else
                    {
                        final String unencryptedMessage = Message.START_TAG + m.encode() + Message.END_TAG;
                        messageBuffer = ByteBuffer.wrap(unencryptedMessage.getBytes(StandardCharsets.UTF_8));
                    }
                    socket.write(messageBuffer);
                }
            }
            catch (Exception e)
            {
                logger.log(Level.SEVERE, "can not read from socket", e);
                break;
            }
        }

        logger.info(toString() + ": connection closed");
        parentThread.onClientDisconnected(this);
        try
        {
            heartbitTimer.cancel();
            socket.close();
        }
        catch (IOException e)
        {
            // nothing to do
        }
    }

    private boolean validateLoginMessage(Message inputMessage)
    {
        sessionEncryptor = null;
        if (inputMessage.getType() == Type.CLIENT_LOGIN)
        {
            final String password = inputMessage.getParameter(1);
            final String sessionKey = inputMessage.getParameter(2);
            if (Config.getPassword().equals(password))
            {
                logger.info("access for client granted with key " + sessionKey);
                sessionEncryptor = new AdvancedEncryptionStandard(sessionKey);
                return true;
            }
            logger.info("access denied due to invalid password " + password);
            return false;
        }
        logger.info("access denied due to invalid message");
        return false;
    }

    private Message processInputData(ByteBuffer buffer)
    {
        buffer.flip();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        String s = new String(bytes, StandardCharsets.UTF_8);
        inputStream += s;
        while (true)
        {
            final int startIndex = inputStream.indexOf(Message.START_TAG);
            final int endIndex = inputStream.indexOf(Message.END_TAG);
            if (startIndex < 0 || endIndex < 0)
            {
                break;
            }
            final String messageStr = inputStream.substring(startIndex + Message.START_TAG.length(), endIndex);
            inputStream = inputStream.substring(endIndex + Message.END_TAG.length(), inputStream.length());
            try
            {
                logger.info("handle input message: " + messageStr);
                final String decryptedMessage = initialEncryptor.decrypt(Base64.getDecoder().decode(messageStr));
                Message inputMessage = new Message(decryptedMessage);
                logger.info("received message: " + inputMessage.toString());
                return inputMessage;
            }
            catch (Exception e)
            {
                logger.log(Level.SEVERE, "can not decode message " + s, e);
            }
        }
        return null;
    }

    public void sendMessage(Message m)
    {
        if (m == null)
        {
            return;
        }
        messageQueue.add(m);
    }
}
