package org.petulant.smartbike;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;


public class MainActivity extends Activity
{
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void startSensing(View view){
        Intent intent = new Intent(this, SensorActivity.class);
        startActivity(intent);
    }

    public void openMap(View view){
        Intent intent = new Intent(this, MapsActivity.class);
        startActivity(intent);
    }
}