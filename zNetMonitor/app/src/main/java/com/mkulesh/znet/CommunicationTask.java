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
import android.os.AsyncTask;
import android.os.StrictMode;
import android.util.Base64;
import android.widget.Toast;

import com.mkulesh.znet.common.AdvancedEncryptionStandard;
import com.mkulesh.znet.common.Message;
import com.mkulesh.znet.common.Message.Type;
import com.mkulesh.znet.common.Utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.crypto.KeyGenerator;

class CommunicationTask extends AsyncTask<Void, Message, Void>
{
    private static final long CONNECTION_TIMEOUT = 5000;
    private static final int BASE64_OPTIONS = Base64.NO_WRAP | Base64.NO_PADDING;

    private final MainActivity activity;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private Boolean reportIfStopped = false;
    private SocketChannel socket = null;
    private String password = "";
    private String inputStream = "";
    private final AdvancedEncryptionStandard initialEncryptor = new AdvancedEncryptionStandard(Message.AES_KEY);
    private AdvancedEncryptionStandard sessionEncryptor = null;

    CommunicationTask(MainActivity activity)
    {
        this.activity = activity;
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
    }

    boolean connectToServer(String server, int port, String password)
    {
        final String addr = server + ":" + port;
        this.password = password;
        try
        {
            socket = SocketChannel.open();
            socket.configureBlocking(true);
            socket.connect(new InetSocketAddress(server, port));
            final long startTime = Calendar.getInstance().getTimeInMillis();
            while (!socket.finishConnect())
            {
                final long currTime = Calendar.getInstance().getTimeInMillis();
                if (currTime > startTime + CONNECTION_TIMEOUT)
                {
                    throw new Exception(activity.getResources().getString(R.string.error_connection_timeout));
                }
            }
            connected.set(true);
        }
        catch (Exception e)
        {
            String message = String.format(activity.getResources().getString(R.string.error_connection_failed), addr);
            Logging.info(this, message + ": " + e.getLocalizedMessage());
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
            for (StackTraceElement t : e.getStackTrace())
            {
                Logging.info(this, t.toString());
            }
            connected.set(false);
        }
        reportIfStopped = false;
        return connected.get();
    }

    void disconnect()
    {
        connected.set(false);
    }

    boolean isConnected()
    {
        return connected.get();
    }

    @Override
    protected Void doInBackground(Void... params)
    {
        Logging.info(this, "started");

        if (performLogin())
        {
            ByteBuffer buffer = ByteBuffer.allocate(Message.SOCKET_BUFFER);
            while (true)
            {
                try
                {
                    if (!connected.get() || isCancelled())
                    {
                        Logging.info(this, "cancelled");
                        break;
                    }

                    buffer.clear();
                    int readedSize = socket.read(buffer);
                    if (readedSize < 0)
                    {
                        Logging.info(this, "server disconnected");
                        reportIfStopped = true;
                        break;
                    }
                    else if (readedSize > 0)
                    {
                        processInputData(buffer);
                    }
                }
                catch (Exception e)
                {
                    Logging.info(this, "interrupted: " + e.getLocalizedMessage());
                    reportIfStopped = true;
                    break;
                }
            }
        }

        try
        {
            socket.close();
        }
        catch (IOException e)
        {
            // nothing to do
        }
        connected.set(false);
        Logging.info(this, "stopped");
        return null;
    }

    @SuppressLint("TrulyRandom")
    private boolean performLogin()
    {
        Message clientLogin = new Message(Type.CLIENT_LOGIN);
        clientLogin.addParameter(android.os.Build.MODEL);
        clientLogin.addParameter(password);

        try
        {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(128);
            Key key = generator.generateKey();
            final String sessionKey = Utils.bb2hex(key.getEncoded()).replaceAll("\\s", "");
            clientLogin.addParameter(sessionKey);
            sessionEncryptor = new AdvancedEncryptionStandard(sessionKey);
        }
        catch (NoSuchAlgorithmException e1)
        {
            sessionEncryptor = null;
        }

        Logging.info(this, "sending login message: " + clientLogin.toString());

        try
        {
            final String encryptedMessage = Message.START_TAG
                    + Base64.encodeToString(initialEncryptor.encrypt(clientLogin.encode()), BASE64_OPTIONS)
                    + Message.END_TAG;

            final ByteBuffer messageBuffer = ByteBuffer.wrap(encryptedMessage.getBytes(Charset.forName("UTF-8")));

            socket.write(messageBuffer);
            return true;
        }
        catch (Exception e)
        {
            Logging.info(this, "failed to send login message: " + e.getLocalizedMessage());
        }
        return false;
    }

    @Override
    protected void onPostExecute(Void result)
    {
        if (reportIfStopped)
        {
            activity.handleMessage(null);
        }
    }

    private void processInputData(ByteBuffer buffer)
    {
        buffer.flip();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        String s = new String(bytes, Charset.forName("UTF-8"));
        final String heartbitMsg = (new Message(Message.Type.HEARTBIT)).encode();
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
                if (heartbitMsg.equals(messageStr))
                {
                    Logging.info(this, "ignore message: " + messageStr);
                }
                else if (sessionEncryptor != null)
                {
                    Logging.info(this, "handle message: " + messageStr);
                    final String decryptedMessage = sessionEncryptor.decrypt(Base64.decode(messageStr, BASE64_OPTIONS));
                    publishProgress(new Message(decryptedMessage));
                }
            }
            catch (Exception e)
            {
                Logging.info(this, "can not decode message " + s + ": " + e.getLocalizedMessage());
            }
        }
    }

    @Override
    protected void onProgressUpdate(Message... message)
    {
        activity.handleMessage(message[0]);
    }

}
