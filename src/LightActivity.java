package com.jeffjosephs.controlMyLife;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.util.Log;
import android.view.*;
import android.widget.*;
import android.os.Handler;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.Set;
import java.util.UUID;
import java.io.IOException;
import java.util.logging.*;


public class LightActivity extends Activity implements Appliance{

    public TextView hourTextView;
    public TextView minuteTextView;
    public Button lightAlarmOffButton;
    public TextView lightAlarmStatusTextView;
    public TextView lightAlarmSetTimeTextView;
    public Switch alarmPowerSwitch;

    BluetoothAdapter lightBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    BluetoothDevice lightDevice = null;
    BluetoothSocket lightSocket = null;
    OutputStream lightOutputStream = null;
    InputStream lightInputStream = null;
    public boolean lightBluetoothConnected = false;
    private boolean ignoreSwitch = false;

    private static final String TAG = "LightConnect";
    private static final String LIGHT_ADDRESS = "00:06:66:6C:A9:B6";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    Handler handler = new Handler();
    byte delimiter = 0x2A;
    boolean stopWorker = false;
    int readBufferPosition = 0;
    byte[] readBuffer = new byte[1024];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_light);

        alarmPowerSwitch = (Switch) findViewById(R.id.lightSwitch);
        hourTextView = (TextView) findViewById(R.id.hourTextBox);
        minuteTextView = (TextView) findViewById(R.id.minuteTextBox);
        lightAlarmOffButton = (Button) findViewById(R.id.lightAlarmOffButton);
        lightAlarmStatusTextView = (TextView) findViewById(R.id.alarmStatusLabel);
        lightAlarmSetTimeTextView = (TextView) findViewById(R.id.alarmSetTimeLabel);

        lightAlarmOffButton.setVisibility(View.INVISIBLE);

        addEventHandlers();

        updateLightAlarmTextBoxes();

        // connect to desk light via bluetooth
        if(connectTo())
        {
            lightBluetoothConnected = true;
        }
        else
        {
            Toast.makeText(getApplicationContext(),"Unable to connect to desk light",
                    Toast.LENGTH_LONG).show();

            try {
                lightSocket.close();
            } catch (Exception e) {
                System.out.println("Error closing socket");
            }

            lightBluetoothConnected = false;
        }

        // read light power status and alarm set status and time from bluetooth module
        if(lightBluetoothConnected)
        {
            updateStatuses();
        }

    }

    /*
     * Function to attempt to connect to light with pre-defined bluetooth address
     * @return success or failure of connect
     */
    private boolean connectTo()
    {
        boolean connected = false;

        lightDevice = lightBluetoothAdapter.getRemoteDevice(LIGHT_ADDRESS);

        try {
            lightSocket = lightDevice.createRfcommSocketToServiceRecord(MY_UUID);
        } catch (IOException e) {
            System.out.println("Error 1");
        }

        lightBluetoothAdapter.cancelDiscovery();

        // Establish the connection.  This will block until it connects.
        Log.d(TAG, "...Connecting to Remote...");
        try {
            lightSocket.connect();
            Log.d(TAG, "...Connection established and data link opened...");

            // Create a data stream so we can talk to server.
            Log.d(TAG, "...Creating Socket...");

            try {
                lightOutputStream = lightSocket.getOutputStream();
                lightInputStream = lightSocket.getInputStream();
                connected = true;
            } catch (IOException e) {
                System.out.println("Error 3");
            }
        } catch (IOException e) {
            try {
                lightSocket.close();
                Log.d(TAG, "...Cannot connect...");
            } catch (IOException e2) {
               System.out.println("Error 2");
            }

            connected = false;
        }

        return connected;
    }

    /*
     * Function to attempt to send byte message to lamp
     * @return success or failure of message send
     */
    private boolean sendMessage(String message)
    {
        boolean sent = false;

        // connect to desk light via bluetooth if not already connected
        if(lightBluetoothConnected)
        {
            try {
                lightOutputStream.write(message.getBytes());
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
                lightBluetoothConnected = true;

                // send command
                try {
                    lightOutputStream.write(message.getBytes());
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
                    lightSocket.close();
                } catch (Exception e) {
                    System.out.println("Error closing socket");
                }

                lightBluetoothConnected = false;
                sent = false;
            }
        }

        return sent;
    }

    // Function to update display with reading from lamp and other settings updated
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
                            int bytesAvailable = lightInputStream.available();
                            if (bytesAvailable > 0) {
                                byte[] packetBytes = new byte[bytesAvailable];
                                lightInputStream.read(packetBytes);
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

                                                // update switch status
                                                if(powerStatus.equals("1"))
                                                {
                                                    // light is on
                                                    alarmPowerSwitch.setChecked(true);
                                                }
                                                else
                                                {
                                                    // light is off
                                                    alarmPowerSwitch.setChecked(false);
                                                }

                                                // update alarm status
                                                if(alarmSetTime.equals("0"))
                                                {
                                                    // alarm is off
                                                    lightAlarmOffButton.setVisibility(View.INVISIBLE);
                                                    lightAlarmStatusTextView.setText("Off");
                                                    lightAlarmSetTimeTextView.setText(" ");
                                                }
                                                else
                                                {
                                                    // alarm is on
                                                    lightAlarmOffButton.setVisibility(View.VISIBLE);
                                                    lightAlarmStatusTextView.setText("On");
                                                    lightAlarmSetTimeTextView.setText("Time: \t\t" + alarmSetTime);
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

    private void updateLightAlarmTextBoxes()
    {
        Calendar c = Calendar.getInstance();

        // format so that always 2 digits
        DecimalFormat formatter = new DecimalFormat("00");

        String hourFormatted = formatter.format(c.get(Calendar.HOUR_OF_DAY));
        String minuteFormatted = formatter.format(c.get(Calendar.MINUTE));

        hourTextView.setText(hourFormatted);
        minuteTextView.setText(minuteFormatted);
    }

    private void addEventHandlers()
    {
        final Button incrementHButton = (Button) findViewById(R.id.incrementHourButton);
        incrementHButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v)
            {
                // increment hour text box
                int hour = Integer.parseInt(hourTextView.getText().toString());
                if(hour == 23)
                {
                    hourTextView.setText("00");
                }
                else
                {
                    hourTextView.setText(new DecimalFormat("00").format(hour + 1));
                }

            }
        });

        final Button decrementHButton = (Button) findViewById(R.id.decrementHourButton);
        decrementHButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v)
            {
                // decrement hour text box
                int hour = Integer.parseInt(hourTextView.getText().toString());
                if(hour == 0)
                {
                    hourTextView.setText("23");
                }
                else
                {
                    hourTextView.setText(new DecimalFormat("00").format(hour - 1));
                }
            }
        });

        final Button incrementMButton = (Button) findViewById(R.id.incrementMinButton);
        incrementMButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v)
            {
                // increment minute text box
                int minute = Integer.parseInt(minuteTextView.getText().toString());
                if(minute == 59)
                {
                    minuteTextView.setText("00");
                }
                else
                {
                    minuteTextView.setText(new DecimalFormat("00").format(minute + 1));
                }
            }
        });

        final Button decrementMButton = (Button) findViewById(R.id.decrementMinButton);
        decrementMButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v)
            {
                // decrement hour text box
                int minute = Integer.parseInt(minuteTextView.getText().toString());

                if(minute == 0)
                {
                    minuteTextView.setText("59");
                }
                else
                {
                    minuteTextView.setText(new DecimalFormat("00").format(minute - 1));
                }

            }
        });

        final Button setAlarmButton = (Button) findViewById(R.id.setAlarmButton);
        setAlarmButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v)
            {
                boolean success = false;

                // Build light on alarm command
                String alarmSetCommandValue = hourTextView.getText() + ":" + minuteTextView.getText();
                String alarmSetCommand = "L|ALARM|" + alarmSetCommandValue + "*";

                // send alarmSetCommand via bluetooth to deskLight bluetooth module
                success = sendMessage(alarmSetCommand);

                if(success) {
                    // change labels and make turn off alarm button visible
                    lightAlarmOffButton.setVisibility(View.VISIBLE);
                    lightAlarmStatusTextView.setText("On");
                    lightAlarmSetTimeTextView.setText("Time: \t\t" + alarmSetCommandValue);
                }

            }
        });

        // on click listener for turning light alarm off
        lightAlarmOffButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v)
            {
                boolean success = false;

                // Build alarm off command
                String lightAlarmOffCommand = "L|ALARM|0*";

                // send light alarm off command via bluetooth to deskLight bluetooth module
                success = sendMessage(lightAlarmOffCommand);

                if(success) {
                    // change labels and make turn off alarm button visible
                    lightAlarmOffButton.setVisibility(View.INVISIBLE);
                    lightAlarmStatusTextView.setText("Off");
                    lightAlarmSetTimeTextView.setText(" ");
                }

            }
        });

        alarmPowerSwitch.setOnClickListener(new View.OnClickListener() {
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
                    String lightPowerOn_OffCommand = "L|POWER|";

                    if (alarmPowerSwitch.isChecked()) {
                        lightPowerOn_OffCommand = lightPowerOn_OffCommand + "1*";
                    }
                    else{
                        lightPowerOn_OffCommand = lightPowerOn_OffCommand + "0*";
                    }

                    // send light power command via bluetooth to deskLight bluetooth module
                    success = sendMessage(lightPowerOn_OffCommand);

                    if (!success) {
                        ignoreSwitch = true;
                        alarmPowerSwitch.setChecked(!(alarmPowerSwitch.isChecked()));
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
        getMenuInflater().inflate(R.menu.light, menu);
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
