package com.pais.cycle.powercycle;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

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

import org.w3c.dom.Text;

import java.sql.Time;
import java.text.DecimalFormat;
import java.util.ArrayList;

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

    // Input Parameters As Statics Temp
    private static double RIDER_WEIGHT_KG = 62.5;
    private static double BIKE_WEIGHT_KG = 9.5;
    private static double COMBINED_WEIGHT = RIDER_WEIGHT_KG + BIKE_WEIGHT_KG;
    private static double RIDER_HEIGHT_M = 1.7;
    private static double XSECTION_AREA = ((.0276) * Math.pow(RIDER_HEIGHT_M, .725)) * (Math.pow(RIDER_WEIGHT_KG, .425)) + .1647;

    // Constants
    private static double DRIVE_EFFICIENCY = 0.97698;
    private static double BIKE_DRAG_COEFFICIENT = 0.84;
    private static double RR_COEFFICIENT = 0.0032;
    private static double GRAVITY = 9.8067;
    private static double SPECIFIC_GAS_CONST = 287.058;

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

    public ArrayList<Float> pressArr = new ArrayList<Float>(20);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setUI();
        setGAPI();

        sensorMan = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        // really only need temp and barometric pressure, with accelerometer as an optimization of power estimation
        pressureSensor = sensorMan.getDefaultSensor(Sensor.TYPE_PRESSURE);

        if (pressureSensor != null) {
            sensorMan.registerListener(this, pressureSensor, SensorManager.SENSOR_DELAY_UI);
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
                lastElevation = currElevation;
                currElevation = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, presAvg);
                Log.d("Elevation avg", SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, presAvg) +"");
            }
            airDensity = (event.values[0] * 100)/(SPECIFIC_GAS_CONST * 291); // TODO 291 is 64F
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        // TODO SET EVERYTHING
        if (mLastLocation != null) {
            latlongText.setText("LAT " + mLastLocation.getLatitude() + " LONG " + mLastLocation.getLongitude());
        }
        startLocationUpdates();
    }

    protected void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
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
        mLastLocation = mCurrentLocation;
        mCurrentLocation = location;
        if (mCurrentLocation != null && mLastLocation != null) {
            updateUI();
        }
    }

    private void updateUI() {
        double velocity = getSpeed();
        Log.d("UPDATE", "UPDATING UI FROM LOCATION CHANGED " + mCurrentLocation.getTime());
        double power = calcPower(velocity);
        speedView.setText("" + velocity);
        altText.setText("" + mCurrentLocation.getAltitude());
        distanceText.setText("" + mCurrentLocation.distanceTo(mLastLocation));
        latlongText.setText(mCurrentLocation.getLatitude() + "\n" + mCurrentLocation.getLongitude());
        powerText.setText((int)power + "W");
        speedText.setText(Math.round((velocity*2.23694)*1e1)/1e1 + "MPH");
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
        double grade = (currElevation-lastElevation)/mCurrentLocation.distanceTo(mLastLocation);
        if (grade > .27 || grade < -.27) {
            grade = 0;
        }
        Log.d("GRADE", grade + "");
        return grade;
    }

    private double calcPower(double velocity) {
        double grade = calcGrade(velocity);

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

    protected void onPause() {
        sensorMan.unregisterListener(this);
        mGoogleApiClient.disconnect();
        super.onPause();
    }
}
