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
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.PictureDrawable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.widget.TextView;
import android.widget.Toast;

import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

@SuppressLint("AppCompatCustomView")
public class CustomImageView extends TextView
{
    public static final String ASSET_LINK_OBJECT = "content:com.mkulesh.znet.asset.";

    public enum ImageType
    {
        NONE,
        BITMAP,
        SVG
    }

    private ImageType imageType = ImageType.NONE;
    private Bitmap bitmap = null;
    private SVG svg = null;
    private final RectF rect = new RectF();
    private int originalWidth = 0, originalHeight = 0;
    private final Paint paint = new Paint();

    /*********************************************************
     * Creating
     *********************************************************/

    public CustomImageView(Context context)
    {
        super(context);
    }

    public CustomImageView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
    }

    public CustomImageView(Context context, AttributeSet attrs, int defStyle)
    {
        super(context, attrs, defStyle);
    }

    public Bitmap getBitmap()
    {
        return bitmap;
    }

    /*********************************************************
     * Read/write interface
     *********************************************************/

    public void loadImage(File currentDirectory, String fileName)
    {
        clear();
        if (fileName.length() > 0)
        {
            loadBitmap(currentDirectory, fileName);
            if (imageType == ImageType.NONE)
            {
                loadSVG(currentDirectory, fileName);
            }
            if (imageType == ImageType.NONE)
            {
                final String error = String.format(getContext().getResources().getString(R.string.error_file_read),
                        fileName);
                Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
            }
        }
    }

    /*********************************************************
     * Painting
     *********************************************************/

    @SuppressLint("DrawAllocation")
    @Override
    protected void onDraw(Canvas c)
    {
        try
        {
            rect.set(getPaddingLeft(), getPaddingTop(), this.getRight() - this.getLeft() - getPaddingRight() - 1,
                    this.getBottom() - this.getTop() - getPaddingBottom() - 1);
            paint.setColor(getCurrentTextColor());
            if (imageType == ImageType.SVG && svg != null)
            {
                final int width = (int) rect.width();
                final int height = (int) rect.height();
                final PictureDrawable pictureDrawable = new PictureDrawable(svg.renderToPicture(width, height));
                bitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888);
                final Canvas c1 = new Canvas(bitmap);
                c1.drawPicture(pictureDrawable.getPicture());
                c.drawBitmap(bitmap, null, rect, paint);
            }
            else if (imageType == ImageType.BITMAP && bitmap != null)
            {
                c.drawBitmap(bitmap, null, rect, paint);
            }
            else
            {
                super.onDraw(c);
            }
        }
        catch (OutOfMemoryError ex)
        {
            String error = getContext().getResources().getString(R.string.error_out_of_memory);
            Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
        }
        catch (Exception ex)
        {
            String error = getContext().getResources().getString(R.string.error_out_of_memory);
            Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
        }
    }

    /*********************************************************
     * Special methods
     *********************************************************/

    private void clear()
    {
        imageType = ImageType.NONE;
        bitmap = null;
        svg = null;
        // Auto-setup of image size depending on display size
        final DisplayMetrics displayMetrics = getContext().getResources().getDisplayMetrics();
        originalHeight = Math.min(displayMetrics.heightPixels, displayMetrics.widthPixels) - 2
                * getContext().getResources().getDimensionPixelOffset(R.dimen.activity_horizontal_margin);
        originalWidth = originalHeight;
    }

    private void setBitmap(Bitmap bitmap)
    {
        this.bitmap = bitmap;
        imageType = this.bitmap == null ? ImageType.NONE : ImageType.BITMAP;
        if (bitmap != null)
        {
            originalWidth = bitmap.getWidth();
            originalHeight = bitmap.getHeight();
        }
    }

    private void setSvg(String svgData)
    {
        svg = null;
        try
        {
            svg = SVG.getFromString(svgData);
            originalWidth = (int) svg.getDocumentWidth();
            originalHeight = (int) svg.getDocumentHeight();
            svg.setDocumentWidth("100%");
            svg.setDocumentHeight("100%");
            svg.setDocumentViewBox(0, 0, originalWidth, originalHeight);
        }
        catch (SVGParseException e)
        {
            // nothing to do
        }
        imageType = this.svg == null ? ImageType.NONE : ImageType.SVG;
    }

    private InputStream loadFromStream(File currentDirectory, String fileName)
    {
        InputStream stream = null;
        // try the file name as an asset 
        if (fileName.contains(ASSET_LINK_OBJECT))
        {
            final String assetName = fileName.substring(
                    fileName.indexOf(ASSET_LINK_OBJECT) + ASSET_LINK_OBJECT.length(), fileName.length());
            final AssetManager am = getContext().getAssets();
            try
            {
                stream = am.open(assetName);
            }
            catch (IOException e)
            {
                // nothing to do
            }
        }
        if (stream != null)
        {
            return stream;
        }
        // try the file name as an absolute path
        try
        {
            stream = new FileInputStream(fileName);
        }
        catch (Exception e)
        {
            // nothing to do
        }
        if (stream != null)
        {
            return stream;
        }
        // try the file name as a relative path to the current directory
        try
        {
            stream = new FileInputStream(currentDirectory + "/" + fileName);
        }
        catch (Exception e)
        {
            // nothing to do
        }
        return stream;
    }

    private void loadBitmap(File currentDirectory, String fileName)
    {
        InputStream stream = loadFromStream(currentDirectory, fileName);
        if (stream == null)
        {
            return;
        }
        try
        {
            setBitmap(BitmapFactory.decodeStream(stream));
        }
        catch (Exception e)
        {
            // nothing to do
        }
        try
        {
            stream.close();
        }
        catch (IOException e)
        {
            // nothing to do
        }
    }

    private String getStringFromInputStream(InputStream stream) throws IOException
    {
        final BufferedReader r = new BufferedReader(new InputStreamReader(stream));
        final StringBuilder total = new StringBuilder(stream.available());
        String line;
        while ((line = r.readLine()) != null)
        {
            total.append(line);
        }
        return total.toString();
    }

    private void loadSVG(File currentDirectory, String fileName)
    {
        InputStream stream = loadFromStream(currentDirectory, fileName);
        if (stream == null)
        {
            return;
        }
        try
        {
            setSvg(getStringFromInputStream(stream));
        }
        catch (Exception e)
        {
            // nothing to do
        }
        try
        {
            stream.close();
        }
        catch (IOException e)
        {
            // nothing to do
        }
    }
}
