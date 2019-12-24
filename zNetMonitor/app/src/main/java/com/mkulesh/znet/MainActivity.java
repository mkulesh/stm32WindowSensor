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

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v13.app.FragmentStatePagerAdapter;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;

import com.mkulesh.znet.common.DeviceState;
import com.mkulesh.znet.common.Message;

import java.util.Locale;

public class MainActivity extends FragmentActivity implements OnPageChangeListener
{
    SectionsPagerAdapter pagerAdapter;
    ViewPager viewPager;
    CommunicationTask communicationThread = null;
    private final StateManager stateManager = new StateManager();

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        pagerAdapter = new SectionsPagerAdapter(getFragmentManager());

        // Set up the ViewPager with the sections adapter.
        viewPager = (ViewPager) findViewById(R.id.pager);
        viewPager.setAdapter(pagerAdapter);
        viewPager.addOnPageChangeListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings)
        {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentStatePagerAdapter
    {
        private SparseArray<Fragment> registeredFragments = new SparseArray<Fragment>();

        public SectionsPagerAdapter(FragmentManager fm)
        {
            super(fm);
        }

        @Override
        public Fragment getItem(int position)
        {
            Fragment fragment = null;
            switch (position)
            {
            case 0:
                fragment = new FloorFragment();
                break;
            case 1:
                fragment = new FloorFragment();
                break;
            case 2:
                fragment = new ServerFragment();
                break;
            }
            Bundle args = new Bundle();
            args.putInt(BaseFragment.FRAGMENT_NUMBER, position);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public int getCount()
        {
            // Show 3 total pages.
            return 3;
        }

        @Override
        public CharSequence getPageTitle(int position)
        {
            Locale l = Locale.getDefault();
            switch (position)
            {
            case 0:
                return getString(R.string.title_section1).toUpperCase(l);
            case 1:
                return getString(R.string.title_section2).toUpperCase(l);
            case 2:
                return getString(R.string.title_section3).toUpperCase(l);
            }
            return null;
        }

        // Register the fragment when the item is instantiated
        @Override
        public Object instantiateItem(ViewGroup container, int position)
        {
            Fragment fragment = (Fragment) super.instantiateItem(container, position);
            registeredFragments.put(position, fragment);
            return fragment;
        }

        // Unregister when the item is inactive
        @Override
        public void destroyItem(ViewGroup container, int position, Object object)
        {
            registeredFragments.remove(position);
            super.destroyItem(container, position, object);
        }

        // Returns the fragment for the position (if instantiated)
        public Fragment getRegisteredFragment(int position)
        {
            return registeredFragments.get(position);
        }
    }

    public boolean connectToServer(String server, int port)
    {
        boolean res = false;
        if (communicationThread != null)
        {
            communicationThread.disconnect();
        }
        communicationThread = new CommunicationTask(this);
        if (communicationThread.connectToServer(server, port))
        {
            communicationThread.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[]) null);
            res = true;
        }
        updateCurrentFragment();
        return res;
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        final String serverName = preferences.getString(ServerFragment.SERVER_NAME, "");
        final int serverPort = preferences.getInt(ServerFragment.SERVER_PORT, 0);
        if (!serverName.isEmpty() && serverPort > 0)
        {
            connectToServer(serverName, serverPort);
        }
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        if (communicationThread != null)
        {
            communicationThread.disconnect();
        }
    }

    private void updateCurrentFragment()
    {
        final BaseFragment f = (BaseFragment) (pagerAdapter.getRegisteredFragment(viewPager.getCurrentItem()));
        if (f != null)
        {
            f.update();
        }
    }

    public StateManager getStateManager()
    {
        return stateManager;
    }

    public void handleMessage(Message message)
    {
        if (message != null)
        {
            boolean prevAlarm = stateManager.isAlarm();
            stateManager.handleMessage(message);
            if (!prevAlarm && stateManager.isAlarm())
            {
                for (DeviceState d : getStateManager().getDevices().values())
                {
                    if (d.isAlarm())
                    {
                        if ("1".equals(d.getConfig().getFloor()))
                        {
                            viewPager.setCurrentItem(0, true);
                        }
                        else if ("2".equals(d.getConfig().getFloor()))
                        {
                            viewPager.setCurrentItem(1, true);
                        }
                    }
                }
            }
        }
        updateCurrentFragment();
    }

    @Override
    public void onPageScrollStateChanged(int arg0)
    {
        // empty

    }

    @Override
    public void onPageScrolled(int arg0, float arg1, int arg2)
    {
        // empty
    }

    @Override
    public void onPageSelected(int p)
    {
        updateCurrentFragment();
    }

    public boolean isServerConnected()
    {
        return communicationThread != null && communicationThread.isConnected();
    }
}
