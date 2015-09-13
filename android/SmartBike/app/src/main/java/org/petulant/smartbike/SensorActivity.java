package org.petulant.smartbike;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.CountDownTimer;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;


import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class SensorActivity extends Activity {

    ImageView circleImage;
    ImageView arrowImage;
    TextView time;
    TextView distanceText;
    BluetoothAdapter mBluetoothAdapter;
    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice;
    InputStream mmInputStream;
    Thread workerThread;
    byte[] readBuffer;
    int readBufferPosition;
    volatile boolean stopWorker;
    int[] directions;
    int[] distances;
    int LEFT = 0;
    int RIGHT = 1;
    double longitude;
    double latitude;
    LocationManager lm;
    int distTraveled = 0;
    int currentStep = 0;
    Timer timer;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensor);

        String value = "";
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            value = extras.getString("DIRECTIONS");

            lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            String locationProvider = LocationManager.NETWORK_PROVIDER;
            Location location = lm.getLastKnownLocation(locationProvider);

            longitude = location.getLongitude();
            latitude = location.getLatitude();
        }

        circleImage = (ImageView) findViewById(R.id.circle_image);
        arrowImage = (ImageView) findViewById(R.id.arrow_image);
        time = (TextView) findViewById(R.id.time);
        distanceText = (TextView) findViewById(R.id.distance);



        if (value != "" && value != null) {
            try {
                startNav(value);
            }
            catch(Exception e){
                Log.i("blgh", "error", e);
            }
        }

    }

    private void startNav(String value) throws Exception{
        JSONObject json = new JSONObject(value);
        JSONArray routes =  json.getJSONArray("routes");
        JSONArray legs = routes.getJSONObject(0).getJSONArray("legs");
        JSONArray steps =  legs.getJSONObject(0).getJSONArray("steps");
        directions = new int[steps.length()];
        distances = new int[steps.length()];
        for (int i = 0; i < steps.length(); ++i){
            JSONObject obj = steps.getJSONObject(i);
            JSONObject distance = obj.getJSONObject("distance");
            distances[i] = distance.getInt("value");
            if (steps.getJSONObject(i).has("maneuver")){
                String maneuver = steps.getJSONObject(i).getString("maneuver");
                if (maneuver.contains("left")){
                    directions[i] = LEFT;
                }
                else if (maneuver.contains("right")){
                    directions[i] = RIGHT;
                }
            }
            else {
                directions[i] = -1;
            }
        }
        //start timerTask
        TimerTask task = new TimerTask() {
            public void run() {
                Location location = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                double newLongitude = location.getLongitude();
                double newLatitude = location.getLatitude();
                float[] result = new float[1];
                location.distanceBetween(latitude, longitude, newLatitude, newLongitude, result);
                distTraveled += result[0];
                if (currentStep == (distances.length - 1) && distTraveled >= (distances[currentStep]-5)){
                    timer.cancel();
                    arrowImage.setImageDrawable(null);
                }
                else if (distTraveled >= (distances[currentStep]-5)){
                    distTraveled = 0;
                    currentStep++;
                    distanceText.setText("");
                    if (directions[currentStep] == LEFT){
                        arrowImage.setImageDrawable(getDrawable(R.drawable.turnleft));
                        distanceText.setText("Distance to Turn: " + (distances[currentStep] - distTraveled));
                    }
                    else if (directions[currentStep] == RIGHT){
                        arrowImage.setImageDrawable(getDrawable(R.drawable.turnright));
                        distanceText.setText("Distance to Turn: " + (distances[currentStep] - distTraveled));
                    }
                    else {
                        arrowImage.setImageDrawable(getDrawable(R.drawable.turnstraight));
                        distanceText.setText("Distance to Turn: " + (distances[currentStep] - distTraveled));

                    }
                }
            }
        };
        timer = new Timer();
        timer.scheduleAtFixedRate(task, 700, 700);
    }

    @Override
    public void onStart(){
        super.onStart();
        try
        {
            findBT();
            openBT();
        }
        catch (IOException ex) {
        Log.i("i", "i");
        }
    }

    @Override
    public void onStop(){
        super.onStop();
        try
        {
            closeBT();
        }
        catch (IOException ex) { }
    }




    void findBT()
    {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBluetoothAdapter == null)
        {
        }

        else if(!mBluetoothAdapter.isEnabled())
        {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth, 0);
        }

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if(pairedDevices.size() > 0)
        {
            for(BluetoothDevice device : pairedDevices)
            {
                if(device.getName().equals("ArcBotics"))
                {
                    mmDevice = device;
                    break;
                }
            }
        }
    }




    void openBT() throws IOException
    {
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //Standard SerialPortService ID
        mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
        new CountDownTimer(4000, 1000) {

            public void onTick(long millisUntilFinished) {

            }

            public void onFinish() {

            }
        }.start();
        mmSocket.connect();
        mmInputStream = mmSocket.getInputStream();

        beginListenForData();

    }




    void beginListenForData()
    {
        final Handler handler = new Handler();
        final byte delimiter = 10; //This is the ASCII code for a newline character

        stopWorker = false;
        readBufferPosition = 0;
        readBuffer = new byte[1024];
        workerThread = new Thread(new Runnable()
        {
            public void run()
            {
                while(!Thread.currentThread().isInterrupted() && !stopWorker)
                {
                    try
                    {
                        int bytesAvailable = mmInputStream.available();
                        if(bytesAvailable > 0)
                        {
                            byte[] packetBytes = new byte[bytesAvailable];
                            mmInputStream.read(packetBytes);
                            for(int i=0;i<bytesAvailable;i++)
                            {
                                byte b = packetBytes[i];
                                if(b == delimiter)
                                {
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    final String data = new String(encodedBytes, "US-ASCII");
                                    readBufferPosition = 0;

                                    handler.post(new Runnable() {
                                        public void run()
                                        {

                                            if (Float.parseFloat(data) >= 0 ){
                                                if (circleImage.getDrawable() != getDrawable(R.drawable.circlecaution)) {
                                                    circleImage.setImageDrawable(getDrawable(R.drawable.circlecaution));
                                                }
                                                time.setText("Estimated Time: " + data);
                                            }

                                            else if (Float.parseFloat(data) == -1){
                                                circleImage.setImageDrawable(getDrawable(R.drawable.circlesafe));
                                                time.setText("");
                                            }
                                        }
                                    });
                                }
                                else
                                {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }
                    }
                    catch (IOException ex)
                    {
                        stopWorker = true;
                    }
                }
            }
        });

        workerThread.start();
    }







    void closeBT() throws IOException
    {
        stopWorker = true;
        mmInputStream.close();
        mmSocket.close();
    }
}
