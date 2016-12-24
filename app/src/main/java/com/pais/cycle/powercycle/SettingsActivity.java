package com.pais.cycle.powercycle;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.util.ArrayList;

public class SettingsActivity extends AppCompatActivity {

    private static final int FINE_LOCATIONS_PERMISSION = 1;
    private static final int EXTERNAL_PERMISSION = 2;
    private static final int BOTH_PERMISSIONS = 3;

    private SharedPreferences sp;
    private Button saveButton;
    private EditText rWeight, rHeight, bWeight;


    // TODO DUPLICATE METHODS
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_layout);

        // set preferences
        sp = getSharedPreferences(getString(R.string.pref_key), Context.MODE_PRIVATE);

        String[] perms = getRequiredPerms();
        requestAppPermissions(perms);

        // set ui elements up
        setUI();

        saveButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                setPreferences(sp);
            }
        });

    }

    private void getPreferences(SharedPreferences sp) {
        float prefRWeight = sp.getFloat(getString(R.string.rider_weight), 0);
        float prefRHeight = sp.getFloat(getString(R.string.rider_height), 0);
        float prefBWeight = sp.getFloat(getString(R.string.bike_weight), 0);
        rWeight.setText(String.valueOf(prefRWeight));
        bWeight.setText(String.valueOf(prefBWeight));
        rHeight.setText(String.valueOf(prefRHeight));
    }

    private void setPreferences(SharedPreferences sp) {
        String riderW = rWeight.getText().toString();
        String riderH = rHeight.getText().toString();
        String bikeW = bWeight.getText().toString();

        // check that the input is valid at some level
        // TODO HOLY CRAP THIS IS UGLY
        if (riderW.equals("") || riderH.equals("") || bikeW.equals("") || riderW.equals("0.0") || riderH.equals("0.0") || bikeW.equals("0.0") || riderW.equals("0") || riderH.equals("0") || bikeW.equals("0")) {
            Toast.makeText(this, "Failed to Update Preferences. Please enter values", Toast.LENGTH_LONG).show();
            Log.e("Pref", "Failed to Set Pref");
        }
        else {
            SharedPreferences.Editor spEdit = sp.edit();
            spEdit.putFloat(getString(R.string.rider_weight), Float.parseFloat(riderW));
            spEdit.putFloat(getString(R.string.rider_height), Float.parseFloat(riderH));
            spEdit.putFloat(getString(R.string.bike_weight), Float.parseFloat(bikeW));
            spEdit.apply();
            Toast.makeText(this, "Updated Preferences", Toast.LENGTH_SHORT).show();
            Log.d("Pref", "Set Preferences");
        }
    }

    private void setUI() {
        saveButton = (Button) findViewById(R.id.save_pref);
        rWeight = (EditText) findViewById(R.id.rider_w);
        rHeight = (EditText) findViewById(R.id.rider_h);
        bWeight = (EditText) findViewById(R.id.bike_w);
        getPreferences(sp);
    }

    private String[] getRequiredPerms() {
        ArrayList<String> needed = new ArrayList<>();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        return needed.toArray(new String[needed.size()]);
    }

    private void requestAppPermissions(String[] permissions) {
        if (permissions.length == 0) return;
        ActivityCompat.requestPermissions(this, permissions,BOTH_PERMISSIONS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case FINE_LOCATIONS_PERMISSION: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // we set fam
                }
                else {
                    Toast.makeText(this, "Please enable GPS to get power estimate.", Toast.LENGTH_SHORT).show();
                }
                return;
            }
            case EXTERNAL_PERMISSION: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // we set fam
                }
                else {
                    Toast.makeText(this, "Writing External Permissions Denied", Toast.LENGTH_SHORT).show();
                }
                return;
            }
        }
    }
}
