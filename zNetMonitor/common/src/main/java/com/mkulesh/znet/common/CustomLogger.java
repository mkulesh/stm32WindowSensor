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

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.logging.ErrorManager;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class CustomLogger
{
    private static final class AddToConsole extends Object
    {
        private final boolean flag;

        public AddToConsole(boolean flag)
        {
            this.flag = flag;
        }

        public boolean isFlag()
        {
            return flag;
        }
    }

    public static final AddToConsole ADD_TO_CONSOLE = new AddToConsole(true);

    private final Logger logger;

    private static final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss_SSS");

    private final SimpleFormatter formatter = new SimpleFormatter()
    {
        @Override
        public synchronized String format(final LogRecord record)
        {
            final String time = timeFormat.format(new Date(record.getMillis()));
            final String level = record.getLevel().toString();
            final String thread = "[" + Integer.toString(record.getThreadID()) + "]";
            final String source = "(" + record.getSourceClassName() + ")";
            String message = time + " " + level + thread + ": " + record.getMessage();
            if (record.getThrown() != null)
            {
                message += ": " + record.getThrown().getLocalizedMessage();
            }
            message += " " + source + "\n";
            return message;
        }
    };

    private class CustomHandler extends Handler
    {
        private final String filePattern;
        private final int pid;
        private final long maxSize;
        private PrintWriter output = null;
        private long outputSize = 0;

        public CustomHandler(String filePattern, long maxSize)
        {
            this.filePattern = filePattern;
            this.pid = getPID();
            this.maxSize = maxSize;
        }

        private int getPID()
        {
            try
            {
                return Integer.parseInt(new File("/proc/self").getCanonicalFile().getName());
            }
            catch (Exception e)
            {
                return 0;
            }
        }

        private void openOutput()
        {
            final String time = timeFormat.format(Calendar.getInstance().getTime());
            final String outputName = String.format(filePattern, Integer.toString(pid) + "_" + time);
            try
            {
                output = new PrintWriter(new FileWriter(outputName, true));
                outputSize = 0;
            }
            catch (Exception e)
            {
                // We don't want to throw an exception here, but we
                // report the exception to any registered ErrorManager.
                reportError("can not open " + outputName, e, ErrorManager.OPEN_FAILURE);
                output = null;
                return;
            }
        }

        @Override
        public void close() throws SecurityException
        {
            if (output != null)
            {
                output.close();
                output = null;
            }
        }

        @Override
        public void flush()
        {
            if (output != null)
            {
                output.flush();
            }
        }

        @Override
        public synchronized void publish(LogRecord record)
        {
            if (!isLoggable(record))
            {
                return;
            }
            String msg;
            try
            {
                msg = getFormatter().format(record);
            }
            catch (Exception e)
            {
                // We don't want to throw an exception here, but we
                // report the exception to any registered ErrorManager.
                reportError(null, e, ErrorManager.FORMAT_FAILURE);
                return;
            }
            if (record.getParameters() != null)
            {
                for (final Object o : record.getParameters())
                {
                    if ((o instanceof AddToConsole) && (((AddToConsole) o).isFlag()))
                    {
                        System.out.print(msg);
                    }
                }
            }
            if (outputSize >= maxSize)
            {
                close();
            }
            if (output == null)
            {
                openOutput();
            }
            if (output != null)
            {
                output.print(msg);
                outputSize += msg.length();
                flush();
            }
            else
            {
                System.out.print("ERROR: can not log into output file");
            }
        }
    }

    public CustomLogger(String filePattern, long fileSize)
    {
        logger = Logger.getLogger(filePattern);
        logger.setUseParentHandlers(false);
        CustomHandler ch = new CustomHandler(filePattern, fileSize);
        ch.setFormatter(formatter);
        logger.addHandler(ch);
    }

    public Logger getLogger()
    {
        return logger;
    }
}
