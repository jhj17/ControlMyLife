package com.jeffjosephs.controlMyLife;

import android.app.TabActivity;
import android.os.Bundle;
import android.view.*;
import android.widget.TabHost;
import android.content.*;
import android.bluetooth.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Set;
import java.util.UUID;


public class MainActivity extends TabActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        final TabHost tabControl = (TabHost) getTabHost();

        TabHost.TabSpec homeTabSpec = tabControl.newTabSpec("home");
        homeTabSpec.setIndicator("", getResources().getDrawable(R.drawable.home1));
        Intent homeIntent = new Intent(this, HomeActivity.class);
        homeTabSpec.setContent(homeIntent);

        TabHost.TabSpec coffeeTabSpec = tabControl.newTabSpec("Coffee");
        coffeeTabSpec.setIndicator("", getResources().getDrawable(R.drawable.coffee1));
        Intent coffeeIntent = new Intent(this, CoffeeActivity.class);
        coffeeTabSpec.setContent(coffeeIntent);

        TabHost.TabSpec fanTabSpec = tabControl.newTabSpec("Fan");
        fanTabSpec.setIndicator("", getResources().getDrawable(R.drawable.fan1));
        Intent fanIntent = new Intent(this, FanActivity.class);
        fanTabSpec.setContent(fanIntent);

        TabHost.TabSpec lightTabSpec = tabControl.newTabSpec("Light");
        lightTabSpec.setIndicator("",getResources().getDrawable(R.drawable.light));
        Intent lightIntent = new Intent(this, LightActivity.class);
        lightTabSpec.setContent(lightIntent);

        tabControl.addTab(homeTabSpec);
        tabControl.addTab(coffeeTabSpec);
        tabControl.addTab(fanTabSpec);
        tabControl.addTab(lightTabSpec);

        tabControl.setCurrentTab(0);

        if(!mBluetoothAdapter.isEnabled())
        {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth, 0);
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
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
