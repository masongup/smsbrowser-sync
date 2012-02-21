package com.mason.smssync;

import com.mason.smssync.receiver.SMSSyncReceiver;
//import com.mason.smssync.receiver.SMSSyncService;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.support.v4.content.LocalBroadcastManager;

public class SMSSyncAppConfigActivity extends Activity {
    
	TextView IPAddressEntryBox;
	TextView passwordEntryBox;
	TextView lastSyncTime;
	Button saveButton;
	Button syncNowButton;
	ToggleButton toggleSync;
	SharedPreferences prefs;
	
	public static final String pLastSyncTime = "LastSyncTime";
	public static final String pSyncIPAddr = "SyncIPAddr";
	public static final String pSyncPassword = "SyncPassword";
	public static final String ACTION_UPDATETIME = "com.mason.smssync.UPDATETIME";
	
	BroadcastReceiver syncTimeUpdateReceiver;
	
    @Override
    public void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.main);
        
        IPAddressEntryBox = (TextView)findViewById(R.id.IPAddressEntryBox);
        passwordEntryBox = (TextView)findViewById(R.id.PasswordEntryBox);
        lastSyncTime = (TextView)findViewById(R.id.LastSyncTime);
        saveButton = (Button)findViewById(R.id.SaveButton);
        syncNowButton = (Button)findViewById(R.id.SyncNowButton);
        toggleSync = (ToggleButton)findViewById(R.id.SyncToggle);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);//getPreferences(MODE_PRIVATE);
        
        //get the actual last sync time and write it to the string
        lastSyncTime.setText(prefs.getString(pLastSyncTime, "No last sync"));
        
        //get the ip address and password and write them to the text fields
        IPAddressEntryBox.setText(prefs.getString(pSyncIPAddr, "0.0.0.0:6178"));
        passwordEntryBox.setText(prefs.getString(pSyncPassword, ""));
        
        //set the action handlers for the buttons
        syncNowButton.setOnClickListener(syncNowButtonListener);
        saveButton.setOnClickListener(new OnClickListener() { public void onClick(View v) { 
        	doSave();}});
        
        syncTimeUpdateReceiver = new TimeUpdateReciever();
    }
    
    @Override
    public void onPause()
    {
    	super.onPause();
    	doSave();
    	LocalBroadcastManager.getInstance(this).unregisterReceiver(syncTimeUpdateReceiver);
    }
    
    @Override
    public void onResume()
    {
    	super.onResume();
    	lastSyncTime.setText(prefs.getString(pLastSyncTime, "No last sync"));
    	LocalBroadcastManager.getInstance(this).registerReceiver(syncTimeUpdateReceiver, new IntentFilter(ACTION_UPDATETIME));
    }
    
    private OnClickListener syncNowButtonListener = new OnClickListener()
    {
    	public void onClick(View v)
    	{
    		Intent syncNowIntent = new Intent(SMSSyncReceiver.ACTION_SYNCSMS); //new Intent(getApplicationContext(), SMSSyncService.class);
    		syncNowIntent.setClass(getApplicationContext(), com.mason.smssync.receiver.SMSSyncReceiver.class);
    		sendBroadcast(syncNowIntent);
    	}
    };
    
    private void doSave()
    {
    	SharedPreferences.Editor prefsEditor = prefs.edit();
    	
    	//TODO: Add some validation here
    	String[] ipAddressArray = IPAddressEntryBox.getText().toString().split(":");
    	if (ipAddressArray.length == 2)
    	{
    		prefsEditor.putString(pSyncIPAddr, IPAddressEntryBox.getText().toString());
    	}
    	prefsEditor.putString(pSyncPassword, passwordEntryBox.getText().toString());
    	prefsEditor.commit();
    }
    
    private class TimeUpdateReciever extends BroadcastReceiver
    {
		@Override
		public void onReceive(Context arg0, Intent arg1)
		{
			lastSyncTime.setText(prefs.getString(pLastSyncTime, "No last sync"));
		}
    	
    }
}