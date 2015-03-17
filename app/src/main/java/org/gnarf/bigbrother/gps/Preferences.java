package org.gnarf.bigbrother.gps;

import android.preference.PreferenceManager;
import android.content.Context;
import android.content.SharedPreferences;

public class Preferences
{
    Context ctx;

    /* The preference values */
    public String target_url;
    public String secret;
    public int send_interval;
    public int accuracy;
    public int displacement;
    public boolean continous_mode;
    public int request_interval;
    public int coordinate_format;
    public boolean start_on_boot;
    public boolean show_in_notif_bar;
    public boolean http_resp_in_notif_bar;
    public boolean send_provider;
    public boolean send_altitude;
    public boolean send_bearing;
    public boolean send_speed;
    public boolean send_time;
    public boolean send_batt_status;
    public boolean send_devid;
    public boolean send_subscrid;

    Preferences(Context ctx)
    {
	this.ctx = ctx;
    }

    public void load()
    {
	String tmp;

	SharedPreferences prefs = 
	    PreferenceManager.getDefaultSharedPreferences(this.ctx);

	this.target_url = prefs.getString("target_url", null);

	float tmpf;
	tmp = prefs.getString("send_interval","15");
	tmpf = (new Float(tmp)).floatValue();
	tmpf *= 60 * 1000;
	this.send_interval = (int)tmpf;

    tmp = prefs.getString("request_interval","10");
    tmpf = (new Float(tmp)).floatValue();
    tmpf *= 1000;
    this.request_interval = (int)tmpf;

    tmp = prefs.getString("displacement","10");
    this.displacement = (new Integer(tmp)).intValue();;

    tmp = prefs.getString("accuracy_values", "100");
	this.accuracy = (new Integer(tmp)).intValue();

	this.continous_mode = prefs.getBoolean("continous_mode", false);

	this.start_on_boot = prefs.getBoolean("start_on_boot", false);
	this.show_in_notif_bar = prefs.getBoolean("show_in_notif_bar", true);
	this.http_resp_in_notif_bar = 
	    prefs.getBoolean("http_resp_in_notif_bar", false);

	secret = prefs.getString("secret", null);
	if (secret == null || secret.length() < 1)
	    secret = null;

	tmp = prefs.getString("coordinate_format", "1");
	this.coordinate_format = (new Integer(tmp)).intValue();

	this.send_provider = prefs.getBoolean("send_provider", true);
	this.send_altitude = prefs.getBoolean("send_altitude", true);
	this.send_bearing = prefs.getBoolean("send_bearing", true);
	this.send_speed = prefs.getBoolean("send_speed", true);
	this.send_time = prefs.getBoolean("send_time", true);
	this.send_batt_status = prefs.getBoolean("send_batt_status", false);
	this.send_devid = prefs.getBoolean("send_devid", false);
	this.send_subscrid = prefs.getBoolean("send_subscrid", false);
    }
}

