package com.pais.cycle.powercycle;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.FloatRange;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences sp;
    private Button saveButton;
    private EditText rWeight, rHeight, bWeight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_layout);

        // set preferences
        sp = getSharedPreferences(getString(R.string.pref_key), Context.MODE_PRIVATE);
        // set ui elements up
        setUI();

        saveButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                setPreferences(sp);
            }
        });

    }

    private void getPreferences(SharedPreferences sp) {
        float prefRWeight = sp.getFloat("riderWeight", 0);
        float prefRHeight = sp.getFloat("riderHeight", 0);
        float prefBWeight = sp.getFloat("bikeWeight", 0);
        rWeight.setText(String.valueOf(prefRWeight));
        bWeight.setText(String.valueOf(prefRHeight));
        rHeight.setText(String.valueOf(prefBWeight));
    }

    private void setPreferences(SharedPreferences sp) {
        String riderW = rWeight.getText().toString();
        String riderH = rHeight.getText().toString();
        String bikeW = bWeight.getText().toString();

        // check that the input is valid at some level
        if (riderW.equals("") || riderH.equals("") || bikeW.equals("")) {
            Toast.makeText(this, "Failed to Update Preferences. Please enter values", Toast.LENGTH_LONG).show();
            Log.e("Pref", "Failed to Set Pref");
            return;
        }
        else {
            SharedPreferences.Editor spEdit = sp.edit();
            spEdit.putFloat("riderWeight", Float.parseFloat(riderW));
            spEdit.putFloat("riderHeight", Float.parseFloat(riderH));
            spEdit.putFloat("bikeWeight", Float.parseFloat(bikeW));
            spEdit.commit();
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
}
