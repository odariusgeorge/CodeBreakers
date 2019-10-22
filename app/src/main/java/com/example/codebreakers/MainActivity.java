package com.example.codebreakers;


import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;

import it.unive.dais.legodroid.lib.EV3;
import it.unive.dais.legodroid.lib.comm.BluetoothConnection;

public class MainActivity extends AppCompatActivity {
    public String deviceName = "Mark7";
    public BluetoothConnection bt = new BluetoothConnection(deviceName);
    public BluetoothConnection.BluetoothChannel channel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        try {
           channel =  bt.connect();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
    @Override
    protected void onDestroy(){
        super.onDestroy();
        channel.close();

    }


}
