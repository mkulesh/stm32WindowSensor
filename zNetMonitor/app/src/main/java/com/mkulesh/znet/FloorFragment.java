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
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.mkulesh.znet.common.DeviceState;

@SuppressLint("ClickableViewAccessibility")
public class FloorFragment extends BaseFragment implements OnTouchListener
{
    public enum FloorNr
    {
        FIRST(1),
        SECOND(2);

        private final int id;

        FloorNr(int id)
        {
            this.id = id;
        }

        private int getId()
        {
            return id;
        }

    }

    private FloorNr floorNr;
    private RelativeLayout imageLayout = null;

    public FloorFragment()
    {
        // Empty constructor required for fragment subclasses
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        initializeFragment(inflater, container, R.layout.floor_fragment);
        floorNr = (fragmentNumber == 0) ? FloorNr.FIRST : FloorNr.SECOND;

        imageLayout = rootView.findViewById(R.id.image_layout);
        imageLayout.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        CustomImageView backgroundView = imageLayout.findViewById(R.id.background_view);
        final String imageName = "floor_" + floorNr.getId() + "_background";
        backgroundView.loadImage(null, CustomImageView.ASSET_LINK_OBJECT + imageName + ".svg");
        backgroundView.setTag(imageName);

        update();
        return rootView;
    }

    @Override
    public void update()
    {
        super.update();

        for (DeviceState d : activity.getStateManager().getDevices().values())
        {
            if (!d.getConfig().getFloor().equals(Integer.toString(floorNr.getId())))
            {
                continue;
            }
            final String imageName = d.getConfig().getImageName();
            setImageVsability(imageName + "_alarm", d.isAlarm());
            if (d.isAlarm())
            {
                setImageVsability(imageName + "_warning", false);
            }
            else
            {
                setImageVsability(imageName + "_warning", d.isWarning());
            }
        }
    }

    private void setImageVsability(String imageName, boolean visible)
    {
        CustomImageView image = imageLayout.findViewWithTag(imageName);
        if (image == null && !visible)
        {
            // nothing to do
        }
        else if (image == null && visible)
        {
            // add image
            Logging.info(this, "loading " + imageName);
            image = new CustomImageView(activity);
            image.setTag(imageName);
            image.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            image.setVisibility(View.VISIBLE);
            imageLayout.addView(image);
            image.loadImage(null, CustomImageView.ASSET_LINK_OBJECT + imageName + ".svg");
            image.setOnTouchListener(this);
        }
        else if (image != null)
        {
            image.setVisibility(visible ? View.VISIBLE : View.GONE);
            image.setOnTouchListener(visible ? this : null);
        }

    }

    @Override
    public boolean onTouch(View view, MotionEvent event)
    {
        if (!(view instanceof CustomImageView))
        {
            return false;
        }
        final CustomImageView image = (CustomImageView) view;
        for (DeviceState d : activity.getStateManager().getDevices().values())
        {
            final String message = getMessageFromWarnings(d);
            if (message == null)
            {
                continue;
            }
            final String imageName = d.getConfig().getImageName();
            if (!image.getTag().toString().contains(imageName))
            {
                continue;
            }
            if (isImageEmpty(image.getBitmap(), event.getX(), event.getY()))
            {
                continue;
            }
            showToast(message, event.getX(), event.getY(), event.getY() < image.getWidth() / 2);
        }
        return false;
    }

    private String getMessageFromWarnings(DeviceState d)
    {
        if (!d.isAlarm() && !d.isWarning())
        {
            return null;
        }
        String message = "№" + d.getId();
        if (!d.getAlarmTime().isEmpty())
        {
            message += ": " + d.getAlarmTime();
        }
        boolean firstWarning = true;
        for (DeviceState.Warning w : d.getWarnings())
        {
            if (firstWarning)
            {
                message += ": ";
            }
            else
            {
                message += ", ";
            }
            switch (w)
            {
            case NO_ACTIVITY:
                message += activity.getResources().getString(R.string.warning_no_activity);
                break;
            case NOT_READY:
                message += activity.getResources().getString(R.string.warning_not_ready);
                break;
            case COVER_OPENED:
                message += activity.getResources().getString(R.string.warning_cover_opened);
                break;
            case SENSOR_RESETED:
                message += activity.getResources().getString(R.string.warning_sensor_reseted);
                break;
            case LOW_BATTERY:
                message += activity.getResources().getString(R.string.warning_low_battery);
                break;
            case UNKNOWN_MESSAGE:
                message += activity.getResources().getString(R.string.warning_unknown_message);
                break;
            case SENSOR_DISCONNECTED:
                message += activity.getResources().getString(R.string.warning_sensor_disconnected);
                break;
            }
            firstWarning = false;
        }
        return message;
    }

    private void showToast(String message, float eventX, float eventY, boolean below)
    {
        Toast t = Toast.makeText(activity, message, Toast.LENGTH_LONG);
        t.setGravity(Gravity.TOP | Gravity.START, 0, 0);
        t.getView().measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        final int x = (int) eventX - t.getView().getMeasuredWidth() / 2;
        final int y = below ? (int) eventY + 5 * t.getView().getMeasuredHeight() / 4 : (int) eventY
                - t.getView().getMeasuredHeight() / 2;
        t.setGravity(Gravity.TOP | Gravity.START, x, y);
        t.getView().setBackgroundColor(activity.getResources().getColor(R.color.panel_background_color));
        t.show();
    }

    private boolean isImageEmpty(Bitmap bitmap, float eventX, float eventY)
    {
        final float areaSize = 20.0f;
        if (bitmap != null)
        {
            for (float x = eventX - areaSize; x < eventX + areaSize; x++)
            {
                for (float y = eventY - areaSize; y < eventY + areaSize; y++)
                {
                    if (x <= 0 || x >= bitmap.getWidth() || y <= 0 || y >= bitmap.getHeight())
                    {
                        continue;
                    }
                    if (bitmap.getPixel((int) x, (int) y) != 0)
                    {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
