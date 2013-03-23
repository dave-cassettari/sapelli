package uk.ac.ucl.excites.sender;

import uk.ac.ucl.excites.collector.R;
import uk.ac.ucl.excites.sender.util.Constants;
import uk.ac.ucl.excites.sender.util.ServiceChecker;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * This class contains various utilities methods
 * 
 * @author Michalis Vitos
 * 
 */
public class DataSenderPreferences extends PreferenceActivity implements OnSharedPreferenceChangeListener
{

	public static final String PREFERENCES = "ExCiteS_Data_Sender_Preferences";
	public static final String TAG = "DataSenderPreferences";

	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.background_preferences);
		PreferenceManager.setDefaultValues(DataSenderPreferences.this, R.xml.background_preferences, false);

		// Register a listener
		SharedPreferences mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		mSharedPreferences.registerOnSharedPreferenceChangeListener(this);

		// Debug the Preferences
		if(Constants.DEBUG_LOG)
			printPreferences(getApplicationContext());

		// Start Button
		Preference startButton = (Preference) findPreference("startButton");
		startButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
		{
			@Override
			public boolean onPreferenceClick(Preference arg0)
			{
				// Call the Service
				Intent mIntent = new Intent(DataSenderPreferences.this, DataSenderService.class);
				startService(mIntent);
				return true;
			}
		});
	}

	/**
	 * Check if the phone should upload to Dropbox
	 * 
	 * @param mContext
	 * @return
	 */
	public static boolean getDropboxUpload(Context mContext)
	{
		SharedPreferences mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
		return mSharedPreferences.getBoolean("dropboxUpload", true);
	}

	/**
	 * Check if the phone should get in Airplane Mode
	 * 
	 * @param mContext
	 * @return
	 */
	public static boolean getAirplaneMode(Context mContext)
	{
		SharedPreferences mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
		return mSharedPreferences.getBoolean("airplaneMode", true);
	}

//	/**
//	 * Get the Phone Number of the centre phone that works as a relay
//	 * 
//	 * @param mContext
//	 * @return
//	 */
//	public static String getCenterPhoneNumber(Context mContext)
//	{
//		SharedPreferences mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
//		return mSharedPreferences.getString("centerPhoneNumber", "");
//	}

	/**
	 * Get the number of minutes that the service is checking for connectivity
	 * 
	 * @param mContext
	 * @return
	 */
	public static int getTimeSchedule(Context mContext)
	{
		SharedPreferences mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
		return Integer.parseInt(mSharedPreferences.getString("timeSchedule", "1"));
	}

	/**
	 * Get the number of seconds that the service will wait for connectivity.
	 * 
	 * @param mContext
	 * @return
	 */
	public static int getMaxAttempts(Context mContext)
	{
		SharedPreferences mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
		return Integer.parseInt(mSharedPreferences.getString("maxAttempts", "1"));
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{

		if(Constants.DEBUG_LOG)
		{
			Log.i(TAG, "onSharedPreferenceChanged(): key = " + key);
			printPreferences(getApplicationContext());
		}

		if(DataSenderService.class.getName() != null && ServiceChecker.isMyServiceRunning(getApplicationContext(), DataSenderService.class.getName()))
		{
			// Call the Service
			Intent mIntent = new Intent(this, DataSenderService.class);
			startService(mIntent);
		}
	}

	public void printPreferences(Context mContext)
	{
		Log.d(TAG, "------------ Preferences: -------------");
		Log.d(TAG, "DropboxUpload: " + (getDropboxUpload(mContext) ? "true" : "false"));
		Log.d(TAG, "AirplaneMode: " + (getAirplaneMode(mContext) ? "true" : "false"));
		//Log.d(TAG, "CenterPhoneNumber: " + getCenterPhoneNumber(mContext));
		Log.d(TAG, "TimeSchedule: " + getTimeSchedule(mContext));
		Log.d(TAG, "MaxAttempts: " + getMaxAttempts(mContext));
	}
}