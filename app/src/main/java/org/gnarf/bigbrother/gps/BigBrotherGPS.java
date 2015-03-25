package org.gnarf.bigbrother.gps;

import java.util.*;

import java.text.SimpleDateFormat;

import android.app.Activity;
import android.os.Bundle;
import android.os.IBinder;
import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.content.ServiceConnection;

import android.view.Menu;
import android.view.MenuItem;

import android.view.View;
import android.widget.TextView;
import android.widget.Button;

import android.location.*;

import org.gnarf.android.Helper;
import org.gnarf.bigbrother.gps.*;

public class BigBrotherGPS extends Activity
{
    Bundle state;

    /* Prefs */
    Preferences prefs;
    
    /* References to our service */
    Intent srvint;
    ComponentName srv;
    Con servicecon;
    LocBinder binder;

    /* Our UI components */
    private TextView time;
    private TextView prov, lat, lon, alt, acc, age;
    private TextView brg, spd;
    private TextView batlev, chrgr;
    private TextView history_record;
    private TextView address;
    private TextView log;

    SimpleDateFormat dateformatter;


    /** Called when the activity is first created. */
    @Override public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

	System.out.println("onCreate called");
	this.state = savedInstanceState;

	/* Get prefs */
	this.prefs = new Preferences(this);
	this.prefs.load();

	startTheGPS();

	startUI();
    }


    @Override public void onDestroy()
    {
	super.onDestroy();
	System.out.println("onDestroy called");

	/* Disconnect from service */
	unbindService(this.servicecon);
    }    

    
    @Override public boolean onCreateOptionsMenu(Menu menu)
    {
	menu.add(0, 0, 0, "Update location now");
	menu.add(0, 1, 1, "Changelog");
	menu.add(0, 100, 100, "Settings")
	    .setIcon(android.R.drawable.ic_menu_preferences);
	return true;
    }

    @Override public boolean onOptionsItemSelected(MenuItem item)
    {
	switch(item.getItemId()) {
	case 0:
	    if (this.binder != null)
		this.binder.triggerUpdate();
	    break;
	case 1:
	    Intent changelog = new Intent(this, Changelog.class);
	    startActivity(changelog);
	    break;
	case 100:
	    Intent prefA = new Intent(this, PreferencesActivity.class);
	    startActivityForResult(prefA, 0);
	    break;
	}

	return true;
    }

    @Override public void onActivityResult(int req, int res, Intent i)
    {
	/* Get prefs */
	this.prefs.load();

	/* Notify the service that preferences might have changed */
	if (this.binder != null)
	    this.binder.updatePrefs();
    }

    private void startTheGPS()
    {
	/* Start GPS service */
	this.srvint = new Intent(this, GPS.class);
	this.srv = startService(this.srvint);
	this.servicecon = new Con();
	boolean bind = bindService(this.srvint, this.servicecon, 0);
	if (this.srv == null && bind) {
	    Helper.ok_dialog(this, "Service", "Failed starting GPS service");
	    finish();
	}
    }

    private void startUI()
    {
	/* Create the UI */
        setContentView(R.layout.main);

	/* Date/time formatting */
	this.dateformatter = new SimpleDateFormat("HH:mm:ss.SS");

	/* Lookup our text fields */
	this.time = (TextView)findViewById(R.id.main_time);

	this.prov = (TextView)findViewById(R.id.main_provider);
	this.lat = (TextView)findViewById(R.id.main_latitude);
	this.lon = (TextView)findViewById(R.id.main_longitude);
	this.alt = (TextView)findViewById(R.id.main_altitude);
	this.acc = (TextView)findViewById(R.id.main_accuracy);
    this.age = (TextView)findViewById(R.id.main_age);

	this.brg = (TextView)findViewById(R.id.main_bearing);
	this.spd = (TextView)findViewById(R.id.main_speed);

	this.batlev = (TextView)findViewById(R.id.main_bat_level);
	this.chrgr = (TextView)findViewById(R.id.main_bat_charger);

    this.address = (TextView)findViewById(R.id.main_address);

    this.history_record = (TextView)findViewById(R.id.main_history_record);

	this.log = (TextView)findViewById(R.id.main_log);

	/* Hook the button */
        Button btn;

	/* end button */
	btn = (Button)findViewById(R.id.main_stop);
	if (btn != null)
	    btn.setOnClickListener(new View.OnClickListener() {
		    public void onClick(View v) {
			stopService(BigBrotherGPS.this.srvint);
			BigBrotherGPS.this.finish();
		    }
		});

	/* update button */
        btn = (Button)findViewById(R.id.main_triggerupdate);
	if (btn != null)
	    btn.setOnClickListener(new View.OnClickListener() {
		    public void onClick(View v) {
			if (BigBrotherGPS.this.binder != null)
			    BigBrotherGPS.this.binder.triggerUpdate();
		    }
		});
    }

    void logError(String err)
    {
	/* Add error message at top. */
	this.log.setText(err+"\n"+this.log.getText());

	/* Keep only last 10 */
	String oldlog = this.log.getText().toString();
	String log[] = oldlog.split("\n"); 
	if (log.length > 10) {
	    StringBuffer newlog = new StringBuffer();
	    for (int i = 0; i < 10; i++) 
		newlog.append(log[i]+"\n");
	    this.log.setText(newlog);
	}
	
    }


    /************************************************************************ 
     * Helper classes for communication with the service
     ************************************************************************/

    /* Wrapper so we get connected to the service on bind */
    class Con implements ServiceConnection
    {
	@Override public void onServiceConnected(ComponentName name, 
						 IBinder service)
	{
	    LocBinder lb = (LocBinder)service;
	    CallBackIF cb = new CallBackIF();

	    /* Read last position from locator */
	    cb.onLocation("init", lb.getLocation(),"init",
			  lb.getBattery(), lb.getCharger());
	    

	    /* Bind for updates */
	    BigBrotherGPS.this.binder = lb;
	    lb.setCallback(new CallBackIF());
	}

	@Override public void onServiceDisconnected(ComponentName name) {};
    }
    


    /* The actual connection interface class */
    class CallBackIF implements LocIF
    {
	@Override public void onError(String err)
	{
	    BigBrotherGPS.this.logError(err);
	}

        @Override
        public void onHistory(int count) {
            BigBrotherGPS.this.history_record.setText((new Integer(count)).toString());
        }

        @Override public void onStateChange(String prov, int state) {}

        @Override public void onLocation(String prov, Location loc,String adress,
                         int bat_level, boolean charger)
        {
            if (loc == null)
            return;

            Double latitude = new Double(loc.getLatitude());
            Double longitude = new Double(loc.getLongitude());
            Integer accuracy = new Integer((int)loc.getAccuracy());
            Double altitude = new Double(loc.getAltitude());
            Double bearing = new Double(loc.getBearing());
            Double speed = new Double(loc.getSpeed());
            Long age = System.currentTimeMillis() - loc.getTime();

            Date date = new Date(loc.getTime());
            String df = BigBrotherGPS.this.dateformatter.format(date);

            /* Set format */
            int cf;
            switch (BigBrotherGPS.this.prefs.coordinate_format) {
            case 2:
            cf = loc.FORMAT_MINUTES;
            break;
            case 3:
            cf = loc.FORMAT_SECONDS;
            break;
            default:
            cf = loc.FORMAT_DEGREES;
            break;
            }

            BigBrotherGPS.this.address.setText(adress);
            BigBrotherGPS.this.time.setText(df);
            BigBrotherGPS.this.prov.setText(loc.getProvider());
            BigBrotherGPS.this.lat.setText(loc.convert(latitude, cf));
            BigBrotherGPS.this.lon.setText(loc.convert(longitude, cf));
            BigBrotherGPS.this.alt.setText(altitude.toString());
            BigBrotherGPS.this.acc.setText(accuracy.toString());
            BigBrotherGPS.this.brg.setText(bearing.toString());
            BigBrotherGPS.this.spd.setText(speed.toString());
            BigBrotherGPS.this.batlev.setText((new Integer(bat_level)).toString());
            BigBrotherGPS.this.age.setText(age.toString());
            if (charger)
            BigBrotherGPS.this.chrgr.setText("(charging)");
            else
            BigBrotherGPS.this.chrgr.setText("");
        }
    }
}
