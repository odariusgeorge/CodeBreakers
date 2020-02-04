package com.example.codebreakers;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.GridView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;

import it.unive.dais.legodroid.lib.util.Prelude;


public class MainActivity extends AppCompatActivity {
    private static final int CAMERA_PERMISSION_CODE=100;
    private static final String TAG = Prelude.ReTAG("MainActivity");
    //private GridView list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button firstRound = findViewById(R.id.FirstRound);
        Button secondRound = findViewById(R.id.SecondRound);
        Button thirdRound = findViewById(R.id.ThirdRound);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        }
        firstRound.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(MainActivity.this, First.class);
                startActivity(i);
            }
        });
        secondRound.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(MainActivity.this, Second.class);
                startActivity(i);
            }
        });
        thirdRound.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(MainActivity.this, Third.class);
                startActivity(i);
            }
        });
        // GET THE MATRIX DIMENSIONS
        int rows=5;
        int columns=5;

        // INITIALISE YOUR GRID
        GridView list=(GridView)findViewById(R.id.grid_view);
        list.setNumColumns(columns);
        ArrayList<String> data = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            for(int j=0;j<columns;j++)
                //data.add(i+"*"+j);
                if(i==j){
                    data.add("");
                }else{
                    data.add(" ");
                }

        }
        //data.clear();
        GridViewCustomAdapter adapter = new GridViewCustomAdapter(this, data);

        list = (GridView) findViewById(R.id.grid_view);
        list.setAdapter(adapter);
    }
}