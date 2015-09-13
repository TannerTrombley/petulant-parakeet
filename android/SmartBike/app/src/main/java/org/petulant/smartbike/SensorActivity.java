package org.petulant.smartbike;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.UUID;

public class SensorActivity extends AppCompatActivity {

    ImageView circleImage;
    ImageView arrowImage;
    TextView time;
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

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensor);

        String value = "";
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            value = extras.getString("new_variable_name");
        }

        circleImage = (ImageView)findViewById(R.id.circle_image);
        arrowImage = (ImageView)findViewById(R.id.arrow_image);
        time = (TextView)findViewById(R.id.time);

        if (value != "")
            startNav(value);

    }

    private void startNav(String json){
        //check that there is a path
        //store turns and distances
        //start timerTask

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

                                    handler.post(new Runnable()
                                    {
                                        public void run()
                                        {
                                            /*
                                            if (Integer.parseInt(data) >= 0 ){
                                                if (circleImage.getDrawable() == WARNING) {
                                                    //circleImage.setImageDrawable(WARNING);
                                                }
                                                //time.setText("Estimated Time: " + data);
                                            }

                                            else if (Integer.parseInt(data) == -1){
                                                circleImage.setImageDrawable(SAFE);
                                                time.setText("");
                                            }
                                            */
                                            time.setText("Estimated Time: " + data);
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
