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
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.*;

import java.util.*;



public class CoffeeActivity extends Activity implements Appliance{

    public Button brewAlarmOffButton;
    public TextView coffeeHourTextView;
    public TextView coffeeMinuteTextView;
    public TextView brewHourTextView;
    public TextView brewMinuteTextView;
    public TextView brewAlarmStatusTextView;
    public TextView brewAlarmSetTimeTextView;
    public Button startBrewButton;

    BluetoothAdapter coffeeBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    BluetoothDevice coffeeDevice = null;
    BluetoothSocket coffeeSocket = null;
    OutputStream coffeeOutputStream = null;
    InputStream coffeeInputStream = null;
    public boolean coffeeBluetoothConnected = false;

    Handler handler = new Handler();
    byte delimiter = 0x2A;
    boolean stopWorker = false;
    int readBufferPosition = 0;
    byte[] readBuffer = new byte[1024];

    private static final String TAG = "CoffeeConnect";
    private static final String COFFEE_ADDRESS = "00:06:66:6C:A6:D7";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_coffee);

        brewAlarmOffButton = (Button) findViewById(R.id.alarmOffButton);
        coffeeHourTextView = (TextView) findViewById(R.id.brewAlarmHTextBox);
        coffeeMinuteTextView = (TextView) findViewById(R.id.brewAlarmMTextBox);
        brewHourTextView = (TextView) findViewById(R.id.brewAlarmHTextBox);
        brewMinuteTextView = (TextView) findViewById(R.id.brewAlarmMTextBox);
        brewAlarmStatusTextView = (TextView) findViewById(R.id.alarmStatus);
        brewAlarmSetTimeTextView = (TextView) findViewById(R.id.alarmTimeLabel);
        startBrewButton = (Button) findViewById(R.id.brewButton);

        brewAlarmOffButton.setVisibility(View.INVISIBLE);

        addEventHandlers();

        updateBrewAlarmTextBoxes();

        // connect to coffee maker bluetooth module
        if(connectTo())
        {
            coffeeBluetoothConnected = true;
        }
        else
        {
            Toast.makeText(getApplicationContext(), "Unable to connect to coffee maker",
                    Toast.LENGTH_LONG).show();

            try {
                coffeeSocket.close();
            } catch (Exception e) {
                System.out.println("Error closing socket");
            }

            coffeeBluetoothConnected = false;
        }

        // read light power status and alarm set status and time from bluetooth module
        if(coffeeBluetoothConnected)
        {
            updateStatuses();
        }
    }

    /*
     * Function to attempt to connect to coffee maker with pre-defined bluetooth address
     * @return success or failure of connect
     */
    private boolean connectTo()
    {
        boolean connected = false;

        // Set up a pointer to the remote node using it's address.
        coffeeDevice = coffeeBluetoothAdapter.getRemoteDevice(COFFEE_ADDRESS);

        try {
            coffeeSocket = coffeeDevice.createRfcommSocketToServiceRecord(MY_UUID);
        } catch (IOException e) {
            System.out.println("Error 1");
        }

        coffeeBluetoothAdapter.cancelDiscovery();

        // Establish the connection.  This will block until it connects.
        Log.d(TAG, "...Connecting to Remote...");
        try {
            coffeeSocket.connect();
            Log.d(TAG, "...Connection established and data link opened...");

            // Create a data stream so we can talk to server.
            Log.d(TAG, "...Creating Socket...");

            try {
                coffeeOutputStream = coffeeSocket.getOutputStream();
                coffeeInputStream = coffeeSocket.getInputStream();
                connected = true;
            } catch (IOException e) {
                System.out.println("Error 3");
            }
        } catch (IOException e) {
            try {
                coffeeSocket.close();
                Log.d(TAG, "...Cannot connect...");
            } catch (IOException e2) {
                System.out.println("Error 2");
            }

            connected = false;
        }

        return connected;
    }

    /*
     * Function to attempt to send byte message to coffee maker
     * @return success or failure of message send
     */
    private boolean sendMessage(String message)
    {
        boolean sent = false;

        // connect to coffee maker via bluetooth if not already connected
        if(coffeeBluetoothConnected)
        {
            try {
                coffeeOutputStream.write(message.getBytes());
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
                coffeeBluetoothConnected = true;

                // send command
                try {
                    coffeeOutputStream.write(message.getBytes());
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
                    coffeeSocket.close();
                } catch (Exception e) {
                    System.out.println("Error closing socket");
                }

                coffeeBluetoothConnected = false;
                sent = false;
            }
        }

        return sent;
    }

    // Function to update display with reading from coffee maker and other settings updated
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
                            int bytesAvailable = coffeeInputStream.available();
                            if (bytesAvailable > 0) {
                                byte[] packetBytes = new byte[bytesAvailable];
                                coffeeInputStream.read(packetBytes);
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
                                                // contents[3[ is alarm set command string and contents[4] is alarm set time
                                                String alarmSetTime = contents[4];

                                                // update brew status
                                                if(powerStatus.equals("1"))
                                                {
                                                    // brew is on
                                                    startBrewButton.setText("Stop Brew");
                                                }
                                                else
                                                {
                                                    // brew is off
                                                    startBrewButton.setText("Brew Coffee");
                                                }

                                                // update alarm status
                                                if(alarmSetTime.equals("0"))
                                                {
                                                    // alarm is off
                                                    brewAlarmStatusTextView.setText("Off");
                                                    brewAlarmSetTimeTextView.setText(" ");
                                                    brewAlarmOffButton.setVisibility(View.INVISIBLE);
                                                }
                                                else
                                                {
                                                    // alarm is on
                                                    brewAlarmOffButton.setVisibility(View.VISIBLE);
                                                    brewAlarmStatusTextView.setText("On");
                                                    brewAlarmSetTimeTextView.setText("Time: \t\t" + alarmSetTime);
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

    private void updateBrewAlarmTextBoxes()
    {
        Calendar c = Calendar.getInstance();

        // format so that always 2 digits
        DecimalFormat formatter = new DecimalFormat("00");

        String hourFormatted = formatter.format(c.get(Calendar.HOUR_OF_DAY));
        String minuteFormatted = formatter.format(c.get(Calendar.MINUTE));

        brewHourTextView.setText(hourFormatted);
        brewMinuteTextView.setText(minuteFormatted);
    }

    private void addEventHandlers()
    {
        final Button incrementBrewHButton = (Button) findViewById(R.id.incrementBrewAlarmHButton);
        incrementBrewHButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v)
            {
                // increment hour text box
                int hour = Integer.parseInt(coffeeHourTextView.getText().toString());
                if(hour == 23)
                {
                    coffeeHourTextView.setText("00");
                }
                else
                {
                    coffeeHourTextView.setText(new DecimalFormat("00").format(hour + 1));
                }

            }
        });

        final Button decrementBrewHButton = (Button) findViewById(R.id.decrementBrewAlarmHButton);
        decrementBrewHButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v)
            {
                // decrement hour text box
                int hour = Integer.parseInt(coffeeHourTextView.getText().toString());
                if(hour == 0)
                {
                    coffeeHourTextView.setText("23");
                }
                else
                {
                    coffeeHourTextView.setText(new DecimalFormat("00").format(hour - 1));
                }
            }
        });

        final Button incrementBrewMButton = (Button) findViewById(R.id.incrementBrewAlarmMButton);
        incrementBrewMButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v)
            {
                // increment minute text box
                int minute = Integer.parseInt(coffeeMinuteTextView.getText().toString());
                if(minute == 59)
                {
                    coffeeMinuteTextView.setText("00");
                }
                else
                {
                    coffeeMinuteTextView.setText(new DecimalFormat("00").format(minute + 1));
                }
            }
        });

        final Button decrementBrewMButton = (Button) findViewById(R.id.decrementBrewAlarmMButton);
        decrementBrewMButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v)
            {
                // decrement hour text box
                int minute = Integer.parseInt(coffeeMinuteTextView.getText().toString());

                if(minute == 0)
                {
                    coffeeMinuteTextView.setText("59");
                }
                else
                {
                    coffeeMinuteTextView.setText(new DecimalFormat("00").format(minute - 1));
                }

            }
        });

        final Button setBrewAlarmButton = (Button) findViewById(R.id.setBrewAlarmButton);
        setBrewAlarmButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v)
            {

                boolean success = false;

                // Build brew on alarm command
                String brewAlarmSetCommandValue = brewHourTextView.getText() + ":" + brewMinuteTextView.getText();
                String brewAlarmSetCommand = "C|ALARM|" + brewAlarmSetCommandValue + "*";

                // send brewAlarmSetCommand via bluetooth to "BDCoffee" bluetooth module
                success = sendMessage(brewAlarmSetCommand);

                // update button text
                if(success)
                {
                    // change labels and make turn off alarm button visible
                    brewAlarmOffButton.setVisibility(View.VISIBLE);
                    brewAlarmStatusTextView.setText("On");
                    brewAlarmSetTimeTextView.setText("Time: \t\t" + brewAlarmSetCommandValue);
                }
            }
        });

        // on click listener for turning brew alarm off
        brewAlarmOffButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v)
            {

                boolean success = false;

                // Build alarm off command
                String brewAlarmOffCommand = "C|Alarm|0*";

                // send brew alarm off command via bluetooth to "BDCoffee" bluetooth module
                success = sendMessage(brewAlarmOffCommand);

                // update button text
                if(success)
                {
                    // change labels
                    brewAlarmStatusTextView.setText("Off");
                    brewAlarmSetTimeTextView.setText(" ");
                    brewAlarmOffButton.setVisibility(View.INVISIBLE);
                }
            }
        });

        startBrewButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v)
            {

                boolean success = false;
                String startBrewCommand = "C|BREW|";

                if(startBrewButton.getText().equals("Brew Coffee"))
                {
                    // Build start brew command
                    startBrewCommand = startBrewCommand +  "1*";
                }
                else
                {
                    // Build stop brew command
                    startBrewCommand = startBrewCommand + "0*";
                }

                // send start/stop brew command via bluetooth to "BDCoffee" bluetooth module
                success = sendMessage(startBrewCommand);

                // update button text
                if(success)
                {
                    if(startBrewButton.getText().equals("Brew Coffee"))
                    {
                        startBrewButton.setText("Stop Brew");
                    }
                    else
                    {
                        startBrewButton.setText("Brew Coffee");
                    }
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.coffee, menu);
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
