package com.mason.smssync.receiver;

//import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.MessageDigest;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.SecretKeyFactory;
import javax.crypto.interfaces.PBEKey;

import android.text.format.Time;

import com.mason.smssync.SMSSyncAppConfigActivity;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

public class SMSSyncService extends IntentService
{
	public SMSSyncService()
	{
		super("SMSSyncThread");
	}

	@Override
	protected void onHandleIntent(Intent incomingIntent)
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		String ipAddress = prefs.getString(SMSSyncAppConfigActivity.pSyncIPAddr, "");
		byte[] password = prefs.getString(SMSSyncAppConfigActivity.pSyncPassword, "").getBytes(); //.toCharArray();
		byte[] salt = { (byte)0x3b, (byte)0x58, (byte)0x3a, (byte)0x8c, (byte)0x49, (byte)0xd3, (byte)0x21, (byte)0x88 };
		byte[] marker = { (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00 };
		byte[] finalPass = new byte[password.length + salt.length];
		System.arraycopy(password, 0, finalPass, 0, password.length);
		System.arraycopy(salt, 0, finalPass, password.length, salt.length);
		
		try
		{
			MessageDigest md5er = MessageDigest.getInstance("MD5");
			md5er.update(finalPass);
			byte[] keyBytes = md5er.digest();
			SecretKeySpec syncKey = new SecretKeySpec(keyBytes, "AES");
			Cipher sendCipher = Cipher.getInstance("AES/CFB8/NoPadding");
			Cipher recCipher = Cipher.getInstance("AES/CFB8/NoPadding");
			sendCipher.init(Cipher.ENCRYPT_MODE, syncKey);
			IvParameterSpec decryptIv = new IvParameterSpec(sendCipher.getIV());
			recCipher.init(Cipher.DECRYPT_MODE, syncKey, decryptIv);
			byte[] sendiv = sendCipher.getIV();
			
			InetSocketAddress serverAddrPort = new InetSocketAddress(ipAddress, 1234);
			Socket syncSocket = new Socket();
			syncSocket.connect(serverAddrPort, 500);
			OutputStream syncOut = syncSocket.getOutputStream();
			syncOut.write(sendiv);
			syncOut.write(marker);
			syncOut.flush();
			
			CipherOutputStream syncOutStr = new CipherOutputStream(syncOut, sendCipher);
			OutputStreamWriter out = new OutputStreamWriter(syncOutStr);
			Time currentTime = new Time();
			currentTime.setToNow();
			String timeString = currentTime.format3339(false);
			out.write(timeString);
			out.close();
			syncOutStr.close();
			syncSocket.close();
		}
		catch (Exception e)
		{
			Log.d("SyncService", e.getMessage());
		}
	}

}