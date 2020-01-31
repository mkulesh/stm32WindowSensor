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

import android.content.Context;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.ViewGroup;

import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;

class MainPagerAdapter extends FragmentStatePagerAdapter
{
    private final Context context;
    private final SparseArray<Fragment> registeredFragments = new SparseArray<>();
    private final int items;

    MainPagerAdapter(final Context context, final FragmentManager fm)
    {
        super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        this.context = context;
        this.items = 3;
    }

    @Override
    @NonNull
    public Fragment getItem(int position)
    {
        switch (position)
        {
        case 0: case 1:
            return prepareFragment(new FloorFragment(), position);
        case 2:
            return prepareFragment(new ServerFragment(), position);
        }
        return prepareFragment(new FloorFragment(), position);
    }

    private Fragment prepareFragment(Fragment fragment, int position)
    {
        Bundle args = new Bundle();
        args.putInt(BaseFragment.FRAGMENT_NUMBER, position);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public int getCount()
    {
        return items;
    }

    @Override
    public CharSequence getPageTitle(int position)
    {
        Locale l = Locale.getDefault();
        switch (position)
        {
        case 0:
            return context.getString(R.string.title_section1).toUpperCase(l);
        case 1:
            return context.getString(R.string.title_section2).toUpperCase(l);
        case 2:
            return context.getString(R.string.title_section3).toUpperCase(l);
        }
        return null;
    }

    // Register the fragment when the item is instantiated
    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position)
    {
        Fragment fragment = (Fragment) super.instantiateItem(container, position);
        registeredFragments.put(position, fragment);
        return fragment;
    }

    // Unregister when the item is inactive
    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object)
    {
        registeredFragments.remove(position);
        super.destroyItem(container, position, object);
    }

    // Returns the fragment for the position (if instantiated)
    Fragment getRegisteredFragment(int position)
    {
        return registeredFragments.get(position);
    }
}
