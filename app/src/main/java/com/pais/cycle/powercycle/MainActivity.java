package com.pais.cycle.powercycle;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStates;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements SensorEventListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {

    // Google API SHIT
    private static int REQUEST_CODE_RECOVER_PLAY_SERVICES = 200;
    private GoogleApiClient mGoogleApiClient;
    private Location mLastLocation;
    private Location mCurrentLocation;
    private LocationRequest mLocationRequest;

    private static double disregardSpeedUnder = 2.235;

    private double lastPower;
    private float lastElevation;
    private float currElevation;
    private float currTemp = 0.0F;

    // Input Parameters As Statics Temp
    private static float RIDER_WEIGHT_KG;
    private static float BIKE_WEIGHT_KG;
    private static float COMBINED_WEIGHT;
    private static float RIDER_HEIGHT_M;
    private static float XSECTION_AREA;

    // Constants
    private static final double DRIVE_EFFICIENCY = 0.97698;
    private static final double BIKE_DRAG_COEFFICIENT = 0.84;
    private static final double RR_COEFFICIENT = 0.0032;
    private static final double GRAVITY = 9.8067;
    private static final double SPECIFIC_GAS_CONST = 287.058;

    private double airDensity;

    private SensorManager sensorMan;
    private Sensor pressureSensor;

    public TextView speedView;
    public TextView altText;
    public TextView distanceText;
    public TextView latlongText;
    public TextView powerText;
    public TextView gravText;
    public TextView rrText;
    public TextView dragText;
    public TextView speedText;
    public TextView gradeText;

    public ArrayList<Float> pressArr = new ArrayList<Float>(20);

    private boolean outputFile = true;
    private File file;
    private String currFile;

    private static final int FINE_LOCATIONS_PERMISSION = 1;
    private static final int EXTERNAL_PERMISSION = 2;

    private SharedPreferences sp;
    private JSONObject weather;

    private static final String URL = "http://api.openweathermap.org/data/2.5/weather?lat=%1$f&lon=%2$f&appid=8d7396e82f9d3184596213c682c8cc45";
    private String url;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sp = getSharedPreferences("pcprefs", Context.MODE_PRIVATE);

        setPreferences(sp);
        setUI();
        setGAPI();

        sensorMan = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        // really only need temp and barometric pressure, with accelerometer as an optimization of power estimation
        pressureSensor = sensorMan.getDefaultSensor(Sensor.TYPE_PRESSURE);

        if (pressureSensor != null) {
            sensorMan.registerListener(this, pressureSensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.settings_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.settings:
                Intent settingsIntent = new Intent(this, SettingsActivity.class);
                this.startActivity(settingsIntent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    protected void onStart() {
        mGoogleApiClient.connect();
        super.onStart();
    }

    protected void onStop() {
        sensorMan.unregisterListener(this);
        mGoogleApiClient.disconnect();
        super.onStop();
    }


    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_PRESSURE) {
            if (pressArr.size() < 20) {
                pressArr.add(event.values[0]);
            }
            else {
                pressArr.remove(0);
                pressArr.add(event.values[0]);

                float presAvg = 0;
                // average the array
                for (int i = 0; i < pressArr.size(); i++) {
                    presAvg += pressArr.get(i);
                }
                presAvg /= 20;
                //lastElevation = currElevation;
                currElevation = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, presAvg);
                //Log.d("Elevation avg", SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, presAvg) +"");
            }
            airDensity = (event.values[0] * 100)/(SPECIFIC_GAS_CONST * currTemp); // TODO 291 is 64F
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // do nothing
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {

            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},FINE_LOCATIONS_PERMISSION);
            }
            return;
        }
        //mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        // TODO SET EVERYTHING
        //if (mLastLocation != null) {
        //    latlongText.setText("LAT " + mLastLocation.getLatitude() + " LONG " + mLastLocation.getLongitude());
        //}
        startLocationUpdates();
    }

    protected void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {

            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},FINE_LOCATIONS_PERMISSION);
            }
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case FINE_LOCATIONS_PERMISSION: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // we set fam
                }
                else {
                    // TODO toast to the user fuck you
                }
                return;
            }
            case EXTERNAL_PERMISSION: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // we set fam
                }
                else {
                    // TODO TOAST FUCK YOU
                }
                return;
            }
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(2000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    @Override
    public void onLocationChanged(Location location) {
        if (currTemp == 0.0F) {
            getWeather(location.getLatitude(), location.getLongitude());
        }
        mLastLocation = mCurrentLocation;
        mCurrentLocation = location;
        if (mCurrentLocation != null && mLastLocation != null) {
            updateUI();
        }
    }

    private void updateUI() {
        boolean firstRun = false;
        if (currFile == null) {
            DateFormat dFormat = new SimpleDateFormat("HH-mm-ss-MM-dd-yyyy");
            Date date = new Date(mCurrentLocation.getTime());
            currFile = dFormat.format(date);
            firstRun = true;
        }
        double velocity = getSpeed();
        double grade = calcGrade(velocity);
        Log.d("UPDATE", "UPDATING UI FROM LOCATION CHANGED " + mCurrentLocation.getTime());
        double power = calcPower(velocity, grade);
        speedView.setText("" + velocity);
        altText.setText("" + currElevation);
        distanceText.setText("" + mCurrentLocation.distanceTo(mLastLocation));
        latlongText.setText(mCurrentLocation.getLatitude() + "\n" + mCurrentLocation.getLongitude());
        powerText.setText((int)power + "W");
        speedText.setText(Math.round((velocity*2.23694)*1e1)/1e1 + "MPH");
        writeCSV(currFile, velocity, grade, power, firstRun);
    }

    private void setUI() {
        speedView = (TextView) findViewById(R.id.velocityText);
        altText = (TextView) findViewById(R.id.altText);
        distanceText = (TextView) findViewById(R.id.distText);
        latlongText = (TextView) findViewById(R.id.latLongText);
        powerText = (TextView) findViewById(R.id.powerText);

        gravText = (TextView) findViewById(R.id.gravText);
        rrText = (TextView) findViewById(R.id.rrText);
        dragText = (TextView) findViewById(R.id.dragText);
        speedText = (TextView) findViewById(R.id.speedText);
        gradeText = (TextView) findViewById(R.id.gradeText);
    }

    private void setGAPI() {
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
        createLocationRequest();
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(mLocationRequest);
        PendingResult<LocationSettingsResult> result = LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient, builder.build());
        result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(@NonNull LocationSettingsResult result) {
                final Status status = result.getStatus();
                final LocationSettingsStates LocationSettingsStates = result.getLocationSettingsStates();
                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        Log.e("FAILED", "FAIDHFKSKDS");
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        break;
                }
            }
        });
    }

    private double getSpeed() {
        double timeDiff = (mCurrentLocation.getTime() - mLastLocation.getTime()) / 1000;
        return mCurrentLocation.distanceTo(mLastLocation) / timeDiff;
    }

    private double calcGrade(double velocity) {
        if (velocity < disregardSpeedUnder) {
            return 0;
        }
        // TODO SHOULD BE LAST ELEVATION USED
        double grade = (currElevation-lastElevation)/mCurrentLocation.distanceTo(mLastLocation);
        if (grade > .27 || grade < -.27) {
            grade = 0;
        }
        gradeText.setText("" + grade + " " + currElevation);
        Log.d("GRADE", grade + "");
        lastElevation = currElevation;
        return grade;
    }

    private double calcPower(double velocity, double grade) {
        if (velocity < disregardSpeedUnder) {
            lastPower = 0;
            return lastPower;
        }
        double forceGrav = COMBINED_WEIGHT * GRAVITY * grade; // * slope TODO NEED SLOPE
        double forceRR = COMBINED_WEIGHT * GRAVITY * RR_COEFFICIENT;
        double forceDrag = (.5 * XSECTION_AREA * BIKE_DRAG_COEFFICIENT * velocity * velocity * airDensity);

        gravText.setText(forceGrav + "");
        rrText.setText(forceRR + "");
        dragText.setText(forceDrag + "");

        lastPower = Math.floor((forceGrav + forceRR + forceDrag) * velocity) / DRIVE_EFFICIENCY;

        return lastPower;
    }

    private void writeCSV(String filename, double velocity, double grade, double power, boolean firstRun) {
        // TODO DONT CALL THIS ALL THE TIME
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {

            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},EXTERNAL_PERMISSION);
            }
            return;
        }


        File root = Environment.getExternalStorageDirectory();
        File dir = new File(root.getAbsolutePath() + "/powerCycle");
        dir.mkdirs();
        File output = new File(dir, filename + ".csv");

        try {
            FileOutputStream fos = new FileOutputStream(output, true);
            PrintWriter pw = new PrintWriter(fos);
            if (firstRun) pw.write("time,lat,long,velocity,elevation,temp,dist,grade,power\n");
            pw.write(mCurrentLocation.getTime() + ","
                    + mCurrentLocation.getLatitude() + ","
                    + mCurrentLocation.getLongitude() + ","
                    + velocity + ","
                    + currElevation + ","
                    + currTemp + ","
                    + mCurrentLocation.distanceTo(mLastLocation) + ","
                    + grade
                    + power + ","
                    + "\n");
            pw.flush();
            pw.close();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setPreferences(SharedPreferences sp) {
        RIDER_WEIGHT_KG = sp.getFloat("riderWeight", 0);
        BIKE_WEIGHT_KG = sp.getFloat("bikeWeight", 0);
        RIDER_HEIGHT_M = sp.getFloat("riderHeight", 0);
        COMBINED_WEIGHT = RIDER_WEIGHT_KG + BIKE_WEIGHT_KG;
        XSECTION_AREA = (float)(((.0276) * Math.pow(RIDER_HEIGHT_M, .725))
                               * (Math.pow(RIDER_WEIGHT_KG, .425)) + .1647);
        Log.d("Preferences", "Rider W " + RIDER_WEIGHT_KG + " Bike Weight " + BIKE_WEIGHT_KG + " Rider Height " + RIDER_HEIGHT_M);
    }

    private void getWeather(double lat, double lon) {
        // check connectivity
        url = String.format(URL, lat, lon);
        ConnectivityManager connMgr = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected()) {
            new getWebpage().execute(url);
        } else {
            Toast.makeText(this, "Failed to Get Page", Toast.LENGTH_SHORT).show();
        }
    }

    private void setTemp() {
        try {
            currTemp = Float.valueOf(weather.getJSONObject("main").get("temp").toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private class getWebpage extends AsyncTask<String, Void, JSONObject> {
        @Override
        protected JSONObject doInBackground(String... urls) {
            try {
                return getJSON(urls[0]);
            } catch (IOException e) {
                return null;
            } catch (JSONException e) {
                return null;
            }
        }
        @Override
        protected void onPostExecute(JSONObject result) {
            weather = result;
            setTemp();
        }

        private JSONObject getJSON(String mUrl) throws IOException, JSONException {
            HttpURLConnection conn = null;
            URL url = new URL(mUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setReadTimeout(10000 /* milliseconds */);
            conn.setConnectTimeout(15000 /* milliseconds */);
            conn.setDoOutput(true);
            // connect to web
            conn.connect();

            BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
            char[] buff = new char[1024];
            String line;
            StringBuilder sb = new StringBuilder();
            while ((line = br.readLine()) != null) {
                sb.append(line+"\n");
            }
            br.close();

            String jsonString = sb.toString();
            return new JSONObject(jsonString);
        }
    }


    protected void onPause() {
        sensorMan.unregisterListener(this);
        mGoogleApiClient.disconnect();
        super.onPause();
    }
}
