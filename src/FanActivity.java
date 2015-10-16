package com.jeffjosephs.controlMyLife;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;


public class FanActivity extends Activity implements Appliance{

    public Switch fanPowerSwitch;

    BluetoothAdapter fanBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    BluetoothDevice fanDevice = null;
    BluetoothSocket fanSocket = null;
    OutputStream fanOutputStream = null;
    InputStream fanInputStream = null;
    public boolean fanBluetoothConnected = false;
    private boolean ignoreSwitch = false;

    Handler handler = new Handler();
    byte delimiter = 0x2A;
    boolean stopWorker = false;
    int readBufferPosition = 0;
    byte[] readBuffer = new byte[1024];

    private static final String TAG = "FanConnect";
    private static final String FAN_ADDRESS = "00:06:66:6C:A6:56";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fan);

        fanPowerSwitch = (Switch) findViewById(R.id.fanStatusSwitch);

        addEventHandlers();

        // connect to desk fan bluetooth module
        if(connectTo())
        {
            fanBluetoothConnected = true;
        }
        else
        {
            Toast.makeText(getApplicationContext(),"Unable to connect to desk fan",
                    Toast.LENGTH_LONG).show();

            try {
                fanSocket.close();
            } catch (Exception e) {
                System.out.println("Error closing socket");
            }

            fanBluetoothConnected = false;
        }

        // read fan power status from bluetooth module?
        if(fanBluetoothConnected)
        {
            updateStatuses();
        }

    }

    /*
     * Function to attempt to connect to desk fan with pre-defined bluetooth address
     * @return success or failure of connect
     */
    private boolean connectTo()
    {
        boolean connected = false;

        // Set up a pointer to the remote node using it's address.
        fanDevice = fanBluetoothAdapter.getRemoteDevice(FAN_ADDRESS);

        try {
            fanSocket = fanDevice.createRfcommSocketToServiceRecord(MY_UUID);
        } catch (IOException e) {
            System.out.println("Error 1");
        }

        fanBluetoothAdapter.cancelDiscovery();

        // Establish the connection.  This will block until it connects.
        Log.d(TAG, "...Connecting to Remote...");
        try {
            fanSocket.connect();
            Log.d(TAG, "...Connection established and data link opened...");

            // Create a data stream so we can talk to server.
            Log.d(TAG, "...Creating Socket...");

            try {
                fanOutputStream = fanSocket.getOutputStream();
                fanInputStream = fanSocket.getInputStream();
                connected = true;
            } catch (IOException e) {
                System.out.println("Error 3");
            }
        } catch (IOException e) {
            try {
                fanSocket.close();
                Log.d(TAG, "...Cannot connect...");
            } catch (IOException e2) {
                System.out.println("Error 2");
            }

            connected = false;
        }

        return connected;
    }

    /*
     * Function to attempt to send byte message to fan
     * @return success or failure of message send
     */
    private boolean sendMessage(String message)
    {
        boolean sent = false;

        // connect to desk fan via bluetooth if not already connected
        if(fanBluetoothConnected)
        {
            try {
                fanOutputStream.write(message.getBytes());
                sent = true;
            }
            catch(Exception e) {
                System.out.println("Unable to send message");
                sent = false;
            }
        }
        else
        {
            if (connectTo())
            {
                fanBluetoothConnected = true;

                // send command
                try {
                    fanOutputStream.write(message.getBytes());
                    sent = true;
                }
                catch(Exception e) {
                    System.out.println("Unable to send message");
                    sent = false;
                }
            }
            else
            {
                Toast.makeText(getApplicationContext(), "Unable to communicate",
                        Toast.LENGTH_LONG).show();

                try {
                    fanSocket.close();
                } catch (Exception e) {
                    System.out.println("Error closing socket");
                }

                fanBluetoothConnected = false;
                sent = false;
            }
        }

        return sent;
    }

    // Function to update display with reading from fan and other settings updated
    private void updateStatuses()
    {
        // send get settings message
        boolean success = false;
        String lightSettingsCommand = "A|SETTINGS*";

        success = sendMessage(lightSettingsCommand);

        if(success) {
            // read incoming response
            Thread workerThread = new Thread(new Runnable() {
                public void run() {
                    while (!Thread.currentThread().isInterrupted() && !stopWorker) {
                        try {
                            int bytesAvailable = fanInputStream.available();
                            if (bytesAvailable > 0) {
                                byte[] packetBytes = new byte[bytesAvailable];
                                fanInputStream.read(packetBytes);
                                for (int i = 0; i < bytesAvailable; i++) {
                                    byte b = packetBytes[i];
                                    if (b == delimiter) {
                                        byte[] encodedBytes = new byte[readBufferPosition];
                                        // don't copy '*' to encoded bytes
                                        System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length - 1);
                                        final String data = new String(encodedBytes, "US-ASCII");
                                        readBufferPosition = 0;
                                        handler.post(new Runnable() {
                                            public void run() {

                                                // parse string on secondary delimiter '|'
                                                String[] contents = data.split("|");

                                                // contents[0] is appliance identifier
                                                // contents[1] is power command string and contents[2] is power value
                                                String powerStatus = contents[2];

                                                // update fan power status
                                                if(powerStatus.equals("1"))
                                                {
                                                    // fan is on
                                                    fanPowerSwitch.setChecked(true);
                                                }
                                                else
                                                {
                                                    // fan is off
                                                    fanPowerSwitch.setChecked(false);
                                                }
                                            }
                                        });
                                    } else {
                                        readBuffer[readBufferPosition++] = b;
                                    }
                                }
                            }
                        } catch (IOException ex) {
                            stopWorker = true;
                        }
                    }
                }
            });

            workerThread.start();
        }
    }

    private void addEventHandlers()
    {
        fanPowerSwitch.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v)
            {

                boolean success = false;

                if(ignoreSwitch)
                {
                    ignoreSwitch = false;
                }
                else
                {
                    // Build light on alarm command
                    String fanPowerOn_OffCommand = "F|POWER|";

                    if(fanPowerSwitch.isChecked() == true)
                    {
                        fanPowerOn_OffCommand = fanPowerOn_OffCommand + "1";
                    }
                    else if(fanPowerSwitch.isChecked() == false)
                    {
                        fanPowerOn_OffCommand = fanPowerOn_OffCommand + "0";
                    }

                    // send fan power command via bluetooth to "deskFan" bluetooth module
                    success = sendMessage(fanPowerOn_OffCommand);

                    if(!success)
                    {
                        ignoreSwitch = true;
                        fanPowerSwitch.setChecked(!(fanPowerSwitch.isChecked()));
                    }
                    else
                    {
                        ignoreSwitch = false;
                    }
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.fan, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
