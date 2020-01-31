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
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Locale;

import androidx.fragment.app.Fragment;

abstract public class BaseFragment extends Fragment
{
    /**
     * Constants used to save/restore the instance state.
     */
    public static final String FRAGMENT_NUMBER = "fragment_number";
    public static final String SERVER_NAME = "server_name";
    public static final String SERVER_PORT = "server_port";

    protected MainActivity activity;
    protected SharedPreferences preferences;
    protected View rootView = null;
    protected int fragmentNumber = -1;

    private TextView serverState, sensorsState;

    public BaseFragment()
    {
        // Empty constructor required for fragment subclasses
    }

    public void initializeFragment(LayoutInflater inflater, ViewGroup container, int layoutId)
    {
        activity = (MainActivity) getActivity();
        preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        rootView = inflater.inflate(layoutId, container, false);
        Bundle args = getArguments();
        fragmentNumber = args.getInt(FRAGMENT_NUMBER);
        serverState = (TextView) rootView.findViewById(R.id.server_state);
        sensorsState = (TextView) rootView.findViewById(R.id.sensors_state);
    }

    public void update()
    {
        Drawable icon1 = null, icon2 = null;
        String text1 = preferences.getString(ServerFragment.SERVER_NAME, "");
        String text2 = "";

        if (!activity.isServerConnected())
        {
            icon1 = getDrawable(activity, R.drawable.ball_red);
        }
        else
        {
            icon1 = getDrawable(activity, R.drawable.ball_green);
            if (activity.getStateManager().isAlarm())
            {
                icon2 = getDrawable(activity, R.drawable.ball_red);
                text2 = activity.getResources().getString(R.string.sensors_alarm);
            }
            else if (activity.getStateManager().isWarning())
            {
                icon2 = getDrawable(activity, R.drawable.ball_yellow);
                text2 = activity.getResources().getString(R.string.sensors_warning);
            }
            else
            {
                icon2 = getDrawable(activity, R.drawable.ball_green);
                text2 = activity.getResources().getString(R.string.sensors_ok);
            }
        }

        final Locale locale = Locale.getDefault();
        serverState.setCompoundDrawablesWithIntrinsicBounds(icon1, null, null, null);
        serverState.setText(" " + text1.toUpperCase(locale));
        sensorsState.setCompoundDrawablesWithIntrinsicBounds(icon2, null, null, null);
        sensorsState.setText(" " + text2.toUpperCase(locale));
    }

    @SuppressWarnings("deprecation")
    @SuppressLint("NewApi")
    public static Drawable getDrawable(Context context, int icon)
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
        {
            return context.getResources().getDrawable(icon, context.getTheme());
        }
        else
        {
            return context.getResources().getDrawable(icon);
        }
    }
}
