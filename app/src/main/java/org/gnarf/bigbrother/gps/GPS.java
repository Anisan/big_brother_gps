package org.gnarf.bigbrother.gps;

import java.util.*;

import android.app.Service;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Notification;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.os.BatteryManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.*;

import android.telephony.TelephonyManager;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;

import org.gnarf.bigbrother.gps.*;

public class GPS extends Service implements ConnectionCallbacks, OnConnectionFailedListener, LocationListener
{
    // Google client to interact with Google API
    GoogleApiClient mGoogleApiClient;
    LocationRequest mLocationRequest;
    // Location updates intervals in sec
    private static int FATEST_INTERVAL = 5000; // 5 sec

    /* Location manager params */
    AlarmManager am;
    PendingIntent amintent;
    LocAlarm recvr;

    /* Date formatting */
    SimpleDateFormat dateformatter;

    /* Notification */
    NotificationManager notman;
    Notification notif;
    PendingIntent notintent;

    /* RPC */
    LocBinder binder;
    public LocIF rpc_if;
    URL target_url;

    /* Our position data from last read */
    Location location;

    /* Battery info */
    BatteryState bat_rcvr;
    int bat_level;
    boolean charger;

    /* Prefs */
    Preferences prefs;

    /* DB history */
    DBHelper dbHelper;

    @Override public void onCreate()
    {
	    super.onCreate();
	    System.out.println("GPS Service onCreate.");

        // создаем объект для создания и управления версиями БД
        dbHelper = new DBHelper(this);

        // First we need to check availability of play services
        if (checkPlayServices()) {
            // Building the GoogleApi client
            buildGoogleApiClient();
            mGoogleApiClient.connect();
        }
        setupAlarms();
        setupBattery();

        /* Get prefs */
        this.prefs = new Preferences(this);
        this.prefs.load();
        reconfigure();

        /* Create formatter */
        dateformatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SS'Z'");
        dateformatter.setTimeZone(TimeZone.getDefault());

        /* Create binder */
        this.binder = new LocBinder(this);

    }

    @Override public void onDestroy()
    {
        super.onDestroy();
        System.out.println("GPS Service onDestroy.");

        /* Remove alarms and unhook locator */
        this.am.cancel(this.amintent);
        stopLocationUpdates();
        unregisterReceiver(this.recvr);
        unregisterReceiver(this.bat_rcvr);

        if (this.notman != null) {
            /* Remove notification */
            this.notman.cancelAll();
        }
    }


    @Override public IBinder onBind(Intent i) 
    {
	    return this.binder;
    }

    @Override public boolean onUnbind(Intent i) 
    {
        this.rpc_if = null;
        return false;
    }

    /**
     * Method to verify google play services on the device
     * */
    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            String error =  "GooglePlayServices not available: ErrorCode() = " + resultCode;
            System.out.println("BigBrotherGPS: "+ error);
            if (this.rpc_if != null)
                this.rpc_if.onError(error);
            return false;
        }
        return true;
    }

    /**
     * Creating google api client object
     * */
    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API).build();
    }

    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(this.prefs.request_interval);
        mLocationRequest.setFastestInterval(FATEST_INTERVAL);
        mLocationRequest.setPriority(this.prefs.accuracy);
        mLocationRequest.setSmallestDisplacement(this.prefs.displacement);
    }

     /**
     * Starting the location updates
     * */
    protected void startLocationUpdates()
    throws IllegalArgumentException
    {
        if (mGoogleApiClient.isConnected())
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    /**
     * Stopping location updates
     */
    protected void stopLocationUpdates()
    throws IllegalArgumentException
    {
        if (mGoogleApiClient.isConnected())
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
    }

    public void triggerUpdate() 
    {
        if (this.prefs.continous_mode) {
            /* If we are in continous mode just send last location */
            locationUpdate();
        }
        else {
            /* Polling mode */
            try {
                startLocationUpdates();
            }
            catch (IllegalArgumentException e) {
                System.out.println("BigBrotherGPS: "+e.toString());
                return;
            }
        }
    }

    private void setupNotif()
    {
	if (this.prefs.show_in_notif_bar) {
	    /* Show state in notif bar */

	    if (this.notman != null) {
		/* Already set up */
		return;
	    }

	    /* Set up a persistent notification */
	    this.notman = (NotificationManager)
		getSystemService(Context.NOTIFICATION_SERVICE);
	    this.notif = new Notification(R.drawable.notif_icon,
					  "BigBrother GPS Waiting for location",
					  System.currentTimeMillis());
	    this.notif.flags = notif.FLAG_ONGOING_EVENT;
	    this.notintent = 
		PendingIntent.getActivity(this, 0, 
					  new Intent(this, BigBrotherGPS.class),0);
	    this.notif.setLatestEventInfo(this, getString(R.string.app_name),
					  "Waiting for initial location", 
					  notintent);
	    this.notman.notify(0, notif);
	} else {
	    if (this.notman != null) {
		/* Remove notification */
		this.notman.cancelAll();
		this.notman = null;
	    }	    
	}
    }

    private void setupAlarms()
    {
        /* Prepare alarm manager */
        this.am = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
            /* Setup location update alarm */
        this.recvr = new LocAlarm();
        registerReceiver(this.recvr,
                 new IntentFilter(LocAlarm.class.toString()),
                 null, null);
        Intent i = new Intent(LocAlarm.class.toString());
        this.amintent = PendingIntent.getBroadcast(this, 0, i, 0);
    }

    private void setupBattery()
    {
        /* Setup location update alarm */
        this.bat_rcvr = new BatteryState();
        registerReceiver(this.bat_rcvr,
                 new IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                 null, null);
    }
    
    public void reloadPrefs()
    {
        this.prefs.load();
        reconfigure();
    }

    private void reconfigure()
    {
        System.out.println("BigBrotherGPS doing reconfig");
        /* Update the request times */
        stopLocationUpdates();
        createLocationRequest();

        /* Reset update alarms */
        this.am.setRepeating(this.am.RTC_WAKEUP,
                     System.currentTimeMillis() + 1000,
                     this.prefs.send_interval, this.amintent);

        /* Fix notifs */
        setupNotif();

        /* Set URL */
        this.target_url = null;
        try {
            this.target_url = new URL(this.prefs.target_url);
        }
        catch (MalformedURLException e) {
            System.out.println("BigBrotherGPS: "+e.toString());
            this.target_url = null;
        }

        /* For continous mode, start updating */
        if (this.prefs.continous_mode) {
            try {
                startLocationUpdates();
            }
            catch (IllegalArgumentException e) {
                System.out.println("BigBrotherGPS: "
                           +"Can't start locator in continous mode: "
                           +e.toString());
                return;
            }
        }
    }

    protected void addHistory(Location loc, int bat_level, boolean charger)
    {
        // подключаемся к БД
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        // создаем объект для данных
        ContentValues cv = new ContentValues();
        cv.put("latitude", loc.getLatitude());
        cv.put("longitude", loc.getLongitude());
        cv.put("accuracy", loc.getAccuracy());
        cv.put("altitude", loc.getAltitude());
        cv.put("provider", loc.getProvider());
        cv.put("bearing", loc.getBearing());
        cv.put("speed", loc.getSpeed());
        cv.put("time", loc.getTime());
        cv.put("battlevel", bat_level);
        cv.put("charging", charger);

        // вставляем запись и получаем ее ID
        long rowID = db.insert("history", null, cv);
        System.out.println("BigBrotherGPS: row inserted, ID = " + rowID);

        final String DATABASE_COMPARE = "select count(*) from history";
        int total = (int) DatabaseUtils.longForQuery(db, DATABASE_COMPARE, null);

        // закрываем подключение к БД
        dbHelper.close();

        if (this.rpc_if != null)
            this.rpc_if.onHistory(total);
    }

    protected void sendHistory()
    {
        // подключаемся к БД
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        // делаем запрос всех данных из таблицы history, получаем Cursor
        Cursor c = db.query("history", null, null, null, null, null, null);
        // ставим позицию курсора на первую строку выборки
        // если в выборке нет строк, вернется false
        if (c.moveToFirst()) {
            do {
                int id = c.getInt(c.getColumnIndex("id"));
                Location loc = new Location(c.getString(c.getColumnIndex("provider")));
                loc.setLatitude(c.getDouble(c.getColumnIndex("latitude")));
                loc.setLongitude(c.getDouble(c.getColumnIndex("longitude")));
                loc.setAccuracy(c.getFloat(c.getColumnIndex("accuracy")));
                loc.setAltitude(c.getDouble(c.getColumnIndex("altitude")));
                loc.setBearing(c.getFloat(c.getColumnIndex("bearing")));
                loc.setSpeed(c.getFloat(c.getColumnIndex("speed")));
                loc.setTime(c.getLong(c.getColumnIndex("time")));
                int bat_level = c.getInt(c.getColumnIndex("battlevel"));
                boolean charger = c.getInt(c.getColumnIndex("charging"))==1;

                if (postLocation(loc, bat_level, charger))
                    db.delete("history","id=?",new String[] {Integer.toString(id)});
                else
                    break;

                // переход на следующую строку
                // а если следующей нет (текущая - последняя), то false - выходим из цикла
            } while (c.moveToNext());
        } else
            System.out.println("BigBrotherGPS: 0 rows");
        c.close();

        final String DATABASE_COMPARE = "select count(*) from history";
        int total = (int) DatabaseUtils.longForQuery(db, DATABASE_COMPARE, null);
        if (this.rpc_if != null)
            this.rpc_if.onHistory(total);
        // закрываем подключение к БД
        dbHelper.close();
    }

    protected boolean postLocation(Location loc, int bat_level, boolean charger)
    {
        boolean do_notif = false;

        /* Prepare connection and request */
        HttpURLConnection con;
        try {
            con = (HttpURLConnection)this.target_url.openConnection();
        }
        catch (IOException e) {
            System.out.println("BigBrotherGPS: "+e.toString());
            if (this.rpc_if != null)
                this.rpc_if.onError(e.toString());
            return false;
        }

        try {
            con.setRequestMethod("POST");
        }
        catch (ProtocolException e) {
            System.out.println("BigBrotherGPS: "+e.toString());
            if (this.rpc_if != null)
                this.rpc_if.onError(e.toString());
            return false;
        }

        con.setUseCaches(false);
        con.setDoOutput(true);
        con.setDoInput(true);

        /* If HTTP response is to be used in notif bar */
        if (this.prefs.show_in_notif_bar &&
                this.prefs.http_resp_in_notif_bar) {
            this.setupNotif();
            do_notif = true;
        }

        /* Build request data */
        StringBuffer req = new StringBuffer();
        req.append("latitude=");
        req.append(loc.getLatitude());

        req.append("&longitude=");
        req.append(loc.getLongitude());

        req.append("&accuracy=");
        req.append(loc.getAccuracy());

        if (this.prefs.send_altitude) {
            req.append("&altitude=");
            req.append(loc.getAltitude());
        }

        if (this.prefs.send_provider) {
            req.append("&provider=");
            req.append(loc.getProvider());
        }

        if (this.prefs.send_bearing) {
            req.append("&bearing=");
            req.append(loc.getBearing());
        }

        if (this.prefs.send_speed) {
            req.append("&speed=");
            req.append(loc.getSpeed());
        }

        if (this.prefs.send_time) {
            Date date = new Date(loc.getTime());
            req.append("&time=");
            req.append(this.dateformatter.format(date));
        }

        /* Add battery status if configured */
        if (this.prefs.send_batt_status) {
            req.append("&battlevel=");
            req.append(bat_level);
            if (charger) {
                req.append("&charging=1");
            } else {
                req.append("&charging=0");
            }
        }

        /* Add secret if configured */
        if (this.prefs.secret != null) {
            req.append("&secret=");
            req.append(this.prefs.secret);
        }

        /* Add device id */
        if (this.prefs.send_devid) {
            TelephonyManager tm = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
            req.append("&deviceid=");
            req.append(tm.getDeviceId());
        }

        /* Add subscriber id */
        if (this.prefs.send_subscrid) {
            TelephonyManager tm = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
            req.append("&subscriberid=");
            req.append(tm.getSubscriberId());
        }

        con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        con.setRequestProperty("Content-Length", ""+req.length());

        /* Connect and write */
        StringBuffer response = new StringBuffer();
        try {
            con.connect();

            DataOutputStream wr;
            wr = new DataOutputStream(con.getOutputStream());
            wr.writeBytes(req.toString());
            wr.flush();
            wr.close();

            DataInputStream rd;
            rd = new DataInputStream(con.getInputStream());
            if (do_notif) {
                response.append(rd.readLine());
            }
            rd.close();
        }
        catch (IOException e) {
            System.out.println("BigBrotherGPS: "+e.toString());
            if (this.rpc_if != null)
                this.rpc_if.onError(e.toString());
            return false;
        }
        con.disconnect();

        System.out.println("BigBrotherGPS sent HTTP poke");

        /* Set notification if we have it */
        if (this.notif != null && do_notif) {
            this.notif.when = System.currentTimeMillis();
            this.notif.setLatestEventInfo(this,
                    getString(R.string.app_name),
                    response.toString(),
                    this.notintent);
            this.notman.notify(0, this.notif);
        }

        return true;
    }

    /* Send a request to the URL and post some data */
    protected void postLocation()
    {
        /* No url, don't do anything */
        if (this.target_url == null)
            return;

        if (postLocation(this.location,this.bat_level, this.charger))
            sendHistory();
        else
            addHistory(this.location, this.bat_level, this.charger);
    }

    private void locationUpdate()
    {
        /* Location update triggered */
        Location loc = this.location;
        if (loc == null)
            return;

        /* Change notification */
        if (this.prefs.show_in_notif_bar &&
            !this.prefs.http_resp_in_notif_bar) {
            this.setupNotif();
            String txt = loc.getLatitude()+", "
            +loc.getLongitude()+", "
            +(int)loc.getAccuracy()+"m";
            this.notif.when = System.currentTimeMillis();
            this.notif.setLatestEventInfo(GPS.this,
                              getString(R.string.app_name),
                              txt, GPS.this.notintent);
            this.notman.notify(0, GPS.this.notif);
        }

        /* Call to UI */
        if (this.rpc_if != null) {
            this.rpc_if.onLocation(loc.getProvider(), loc,
                       this.bat_level,
                       this.charger);
        }

        /* Post to server */
        this.postLocation();
    }

    @Override
    public void onLocationChanged(Location loc) {
        System.out.println("BigBrotherGPS got loc from " +loc.getProvider());
        GPS.this.location = loc;
        GPS.this.locationUpdate();
        if (!GPS.this.prefs.continous_mode) {
        /* Stop waiting for locations. Will be restarted by alarm */
        stopLocationUpdates();
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        System.out.println("BigBrotherGPS API Connected");
        location = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        locationUpdate();
    }

    @Override
    public void onConnectionSuspended(int i) {
        System.out.println("BigBrotherGPS API Connection Suspended");
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        String error =  "Connection failed: ConnectionResult.getErrorCode() = " + result.getErrorCode();
        System.out.println("BigBrotherGPS: " + error);
        if (this.rpc_if != null)
            this.rpc_if.onError(error);
    }


    /**************************************************************************
     * Helper classes for timed locator interaction
     *************************************************************************/
    class LocAlarm extends BroadcastReceiver
    {
	@Override public void onReceive(Context ctx, Intent i)
	{
	    System.out.println("BigBrotherGPS: Alarm!");
	    GPS.this.triggerUpdate();
	}
    }

    class BatteryState extends BroadcastReceiver
    {
	@Override public void onReceive(Context ctx, Intent i)
	{
	    float level;
	    level = i.getIntExtra("level", 0);
	    level /= i.getIntExtra("scale", 100);
	    GPS.this.bat_level = (int)(level*100);
	    GPS.this.charger = i.getIntExtra("plugged",1)!=0;
	    System.out.printf("BigBrotherGPS: Battery state change: %d%% %b\n",
			      GPS.this.bat_level, GPS.this.charger);
	}
    }

    class DBHelper extends SQLiteOpenHelper {

        public DBHelper(Context context) {
            // конструктор суперкласса
            super(context, "myDB", null, 1);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            System.out.println("BigBrotherGPS: --- onCreate database ---");
            // создаем таблицу с полями
            db.execSQL("CREATE TABLE [history] ("
                    + "[id] INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "[latitude] DOUBLE,"
                    + "[longitude] DOUBLE,"
                    + "[accuracy] FLOAT,"
                    + "[altitude] DOUBLE,"
                    + "[provider] TEXT,"
                    + "[bearing] FLOAT,"
                    + "[speed] FLOAT,"
                    + "[time] DATETIME,"
                    + "[battlevel] INTEGER,"
                    + "[charging] BOOLEAN);");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        }
    }


}
