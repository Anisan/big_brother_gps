package org.gnarf.bigbrother.gps;

import android.app.Activity;
import android.preference.PreferenceActivity;
import android.os.Bundle;

/* The preference displaying and poking around activity */
public class PreferencesActivity extends PreferenceActivity
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
	super.onCreate(savedInstanceState);
	addPreferencesFromResource(R.xml.preferences);
    }
}
