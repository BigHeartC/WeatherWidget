package com.bigheart.weatherwidget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;

public class NewAppWidget extends AppWidgetProvider {

    private static boolean isFirst = true;

    enum weatherType {
        condition, sunset, weather;
    }

    private LocationListener locationListener = null;


    private final String INFO = "AppWidget_INFO";
    private final String IS_LOCATING = "locating", FRESH_FAIL = "fresh fail", LOCATION_FAIL = "location fail ";


    private LocationManager locationManager;
    private Location location;

    private BroadcastReceiver minBroadcast = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            //update time every minute
            updateTime(context);
        }
    };

    private URL weatherUrl;


    @Override
    public void onEnabled(Context context) {
        //when attach to screen

        Log.i(INFO, "onEnabled");
        acquireLocation(context);
        updateTime(context);


        // detected minute change
        IntentFilter updateIntent = new IntentFilter();
        updateIntent.addAction("android.intent.action.TIME_TICK");
        context.getApplicationContext().registerReceiver(minBroadcast, updateIntent);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.new_app_widget);
        views.setTextViewText(R.id.appwidget_temp, IS_LOCATING);
        views.setViewVisibility(R.id.appwidget_weather, View.INVISIBLE);
        views.setViewVisibility(R.id.appwidget_date, View.INVISIBLE);
        AppWidgetManager.getInstance(context.getApplicationContext()).updateAppWidget(new ComponentName(context.getApplicationContext(), NewAppWidget.class), views);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // There may be multiple widgets active, so update all of them
        Log.i(INFO, "onUpdate");

        if (location != null) {
            new freshWeatherTask(context).execute(acquireUrl(weatherType.condition, location));
        } else if (!isFirst) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.new_app_widget);
            views.setTextViewText(R.id.appwidget_temp, LOCATION_FAIL);
            views.setViewVisibility(R.id.appwidget_weather, View.INVISIBLE);
            views.setViewVisibility(R.id.appwidget_date, View.INVISIBLE);
            AppWidgetManager.getInstance(context.getApplicationContext()).updateAppWidget(new ComponentName(context.getApplicationContext(), NewAppWidget.class), views);
        }
        isFirst = false;
    }


    @Override
    public void onDisabled(Context context) {
        // Enter relevant functionality for when the last widget is disabled
        if (minBroadcast.isOrderedBroadcast())
            context.getApplicationContext().unregisterReceiver(minBroadcast);

        if (locationManager != null && locationListener != null)
            locationManager.removeUpdates(locationListener);
    }


    /**
     * get location by GPS or NetWork
     *
     * @param context
     */
    private void acquireLocation(final Context context) {
        if (locationManager == null) {
            locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        }

        if (locationListener == null) {
            locationListener = new LocationListener() {
                @Override
                public void onLocationChanged(Location loc) {
                    if (loc != null) {
                        Log.i(INFO, "get new location");
                        location = loc;
                        new freshWeatherTask(context).execute(acquireUrl(weatherType.condition, location));
                    }
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {

                }

                @Override
                public void onProviderEnabled(String provider) {

                }

                @Override
                public void onProviderDisabled(String provider) {

                }
            };
        }


        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_COARSE);
        criteria.setAltitudeRequired(false);
        criteria.setBearingRequired(false);
        criteria.setCostAllowed(true);
        criteria.setPowerRequirement(Criteria.POWER_LOW);

        locationManager.requestLocationUpdates(locationManager.getBestProvider(criteria, true), 1000 * 600, 10, locationListener);

    }

    /**
     * get the url by different type.
     * in the example ,the type I used is condition. if you use different type , the json construct may change. see https://developer.yahoo.com/weather/
     *
     * @param type
     * @param l
     * @return
     */
    private String acquireUrl(weatherType type, Location l) {
        if (l != null) {
            switch (type) {
                case condition:
                    return "https://query.yahooapis.com/v1/public/yql?q=select%20item.condition%20from%20weather.forecast%20where%20woeid%20in%20(SELECT%20woeid%20FROM%20geo.placefinder%20WHERE%20text%3D%22" + l.getLatitude() + "%2C" + l.getLongitude() + "%22%20and%20gflags%3D%22R%22)&format=json&env=store%3A%2F%2Fdatatables.org%2Falltableswithkeys";
                case weather:
                    return "https://query.yahooapis.com/v1/public/yql?q=select%20*%20from%20weather.forecast%20where%20woeid%20in%20(SELECT%20woeid%20FROM%20geo.placefinder%20WHERE%20text%3D%22" + l.getLatitude() + "%2C" + l.getLongitude() + "%22%20and%20gflags%3D%22R%22)&format=json&env=store%3A%2F%2Fdatatables.org%2Falltableswithkeys";
                case sunset:
                    return "https://query.yahooapis.com/v1/public/yql?q=select%20astronomy.sunset%20from%20weather.forecast%20where%20woeid%20in%20(SELECT%20woeid%20FROM%20geo.placefinder%20WHERE%20text%3D%22" + l.getLatitude() + "%2C" + l.getLongitude() + "%22%20and%20gflags%3D%22R%22)&format=json&env=store%3A%2F%2Fdatatables.org%2Falltableswithkeys";
                default:
                    return null;
            }
        } else {
            return null;
        }
    }

    private void updateTime(Context context) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.new_app_widget);
        views.setTextViewText(R.id.appwidget_text, new SimpleDateFormat("hh:mm").format(System.currentTimeMillis()));

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context.getApplicationContext());
        ComponentName componentName = new ComponentName(context.getApplicationContext(), NewAppWidget.class);
        appWidgetManager.updateAppWidget(componentName, views);
    }

    class freshWeatherTask extends AsyncTask<String, String, String> {

        private Context context;
        private AppWidgetManager manager;
        private ComponentName componentName;

        freshWeatherTask(Context c) {
            context = c;
            manager = AppWidgetManager.getInstance(context.getApplicationContext());
            componentName = new ComponentName(context.getApplicationContext(), NewAppWidget.class);

        }


        @Override
        protected String doInBackground(String... params) {
            BufferedReader in = null;
            String rst = "";
            if (params.length > 0) {
                try {
                    weatherUrl = new URL(params[0]);
                    URLConnection connection = weatherUrl.openConnection();
                    connection.connect();
                    in = new BufferedReader(new InputStreamReader(connection.getInputStream()));

                    String tmpRst;
                    while ((tmpRst = in.readLine()) != null) {
                        rst += tmpRst;
                    }
                    Log.i(INFO, rst);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    try {
                        if (in != null) {
                            in.close();
                        }
                    } catch (IOException ioE) {
                        ioE.printStackTrace();
                    }
                }
            }
            return rst;
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            Log.i("Async", s);
            if (!TextUtils.isEmpty(s)) {
                try {
                    JSONObject mainJson = new JSONObject(s).getJSONObject("query");
                    JSONObject subJson;
                    if (mainJson.getInt("count") == 1) {
                        if (mainJson.has("results") && mainJson.getJSONObject("results").has("channel")) {
                            subJson = mainJson.getJSONObject("results").getJSONObject("channel");
                            if (subJson.has("item") && subJson.getJSONObject("item").has("condition")) {
                                subJson = subJson.getJSONObject("item").getJSONObject("condition");

                                RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.new_app_widget);
                                if (subJson.has("code")) {
//                                    views.setTextViewText(R.id.appwidget_temp, subJson.getString("temp"));
                                }

                                if (subJson.has("date")) {
                                    views.setViewVisibility(R.id.appwidget_date, View.VISIBLE);
                                    views.setTextViewText(R.id.appwidget_date, subJson.getString("date"));
                                }

                                if (subJson.has("temp")) {
                                    views.setViewVisibility(R.id.appwidget_temp, View.VISIBLE);
                                    float temp = (subJson.getInt("temp") - 32) / 1.8f;
                                    views.setTextViewText(R.id.appwidget_temp, (Math.round(temp * 100)) / 100f + "ºC");
                                }

                                if (subJson.has("text")) {
                                    views.setViewVisibility(R.id.appwidget_weather, View.VISIBLE);
                                    views.setTextViewText(R.id.appwidget_weather, subJson.getString("text"));
                                }


                                manager.updateAppWidget(componentName, views);
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.new_app_widget);
                views.setTextViewText(R.id.appwidget_temp, FRESH_FAIL);
                views.setViewVisibility(R.id.appwidget_weather, View.INVISIBLE);
                views.setViewVisibility(R.id.appwidget_date, View.INVISIBLE);
                manager.updateAppWidget(componentName, views);
            }
        }
    }
}