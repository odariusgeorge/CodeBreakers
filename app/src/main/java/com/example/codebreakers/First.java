package com.example.codebreakers;

import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

import it.unive.dais.legodroid.lib.EV3;
import it.unive.dais.legodroid.lib.GenEV3;
import it.unive.dais.legodroid.lib.comm.BluetoothConnection;
import it.unive.dais.legodroid.lib.plugs.GyroSensor;
import it.unive.dais.legodroid.lib.plugs.TachoMotor;
import it.unive.dais.legodroid.lib.plugs.UltrasonicSensor;
import it.unive.dais.legodroid.lib.util.Prelude;

import static java.lang.Math.abs;
import static java.lang.Math.max;

public class First extends AppCompatActivity {

    //MOTORS
    private static TachoMotor motorLeft;
    private static TachoMotor motorRight;
    private static TachoMotor motorClaws;

    //Robot Starting
    private Integer xRobotValue;
    private Integer yRobotValue;

    //Robot Movement Position
    protected Integer xCurrentPosition;
    protected Integer yCurrentPosition;

    //Matrix
    private int[][] matrix;
    private Integer n; //number of columns
    private Integer m; //number of rows

    //Displayed Map
    private static GridView list;
    GridViewCustomAdapter adapter;

    //Balls
    private Integer totalBalls;
    protected Integer ball_catched = 0;
    private boolean ballIsCatched = false;
    float distance; //distance between sensor and ball

    //Voice Recognition
    private TextView txvResult;

    //Camera
    private static final int CAMERA_PERMISSION_CODE=100;
    private CameraBridgeViewBase mOpenCvCameraView;
    private ColorBlobDetector  mDetector;
    private Mat mRgba, mRgbaF, mRgbaT;
    private Scalar mBlobColorHsv;
    private Scalar CONTOUR_COLOR;
    private Scalar MARKER_COLOR;
    private Point org;
    Point center;

    private static final String TAG = Prelude.ReTAG("MainActivity");

    private void setUpCamera() {
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "Unable to load OpenCV");
        } else {
            Log.d(TAG, "OpenCV loaded");
        }

        mOpenCvCameraView.setCvCameraViewListener(new CameraBridgeViewBase.CvCameraViewListener2() {
            @Override
            public void onCameraViewStarted(int width, int height) {
                mRgba = new Mat(height, width, CvType.CV_8UC4);
                mRgbaF = new Mat(height, width, CvType.CV_8UC4);
                mRgbaT = new Mat(width, width, CvType.CV_8UC4);
                mDetector = new ColorBlobDetector();
                mBlobColorHsv = new Scalar(280/2,0.65*255,0.75*255,255);
                mDetector.setHsvColor(mBlobColorHsv);
                CONTOUR_COLOR = new Scalar(255,0,0,255);
                MARKER_COLOR = new Scalar(0,0,255,255);
                org = new Point(1,20);
            }
            @Override
            public void onCameraViewStopped() {

            }
            @Override
            public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
                mRgba = inputFrame.rgba();
                mDetector.process(mRgba);
                List<MatOfPoint> contours = mDetector.getContours();
                Imgproc.drawContours(mRgba, contours, -1, CONTOUR_COLOR);
                center = mDetector.getCenterOfMaxContour();
                double direction = 0;
                return mRgba;
            }
        });
        mOpenCvCameraView.enableView();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first);
        Button start = findViewById(R.id.Start);
        LinearLayout matrixView = findViewById(R.id.matrix);
        mOpenCvCameraView = findViewById(R.id.HelloOpenCvView);
        txvResult = findViewById(R.id.txvResult);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setMaxFrameSize(640, 480);
        mOpenCvCameraView.disableFpsMeter();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        list = findViewById(R.id.grid_view);
        list.setNumColumns(4);
        ArrayList<String> data = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            for(int j=0; j < 4;j++)
                data.add("");

        }
        adapter = new GridViewCustomAdapter(this, data);
        list.setAdapter(adapter);
        try {
            BluetoothConnection.BluetoothChannel conn = new BluetoothConnection("Willy").connect(); // replace with your own brick name
            GenEV3<MyCustomApi> ev3 = new GenEV3<>(conn);

            Button startButton = findViewById(R.id.Start);
            startButton.setOnClickListener(v -> Prelude.trap(() -> ev3.run(this::legoMainCustomApi, MyCustomApi::new)));
        } catch (IOException e) {
            Log.e(TAG, "fatal error: cannot connect to EV3");
            e.printStackTrace();
        }


    }


    //Robot Movement

    void goForward(EV3.Api api) throws IOException, InterruptedException, ExecutionException {
        if(ballIsCatched == false) {
            yCurrentPosition++;
            int i = 1;
            while(i!=5) {
                turnFront(api);
                if(i%2==0) {
                    motorLeft.setStepSpeed(30, 0, 161, 0, true);
                    motorRight.setStepSpeed(30, 0, 161, 0, true );
                    motorLeft.waitCompletion();
                    motorRight.waitCompletion();
                }
                else {
                    motorRight.setStepSpeed(30, 0, 161, 0, true);
                    motorLeft.setStepSpeed(30, 0, 161, 0, true);
                    motorRight.waitCompletion();
                    motorLeft.waitCompletion();
                }
                turnFront(api);
                i++;
            }
            motorLeft.setSpeed(0);
            motorRight.setSpeed(0);
        }
        else {
            yCurrentPosition++;
            int i = 1;
            while(i!=5) {
                turnFront(api);
                if(i%2==0) {
                    motorLeft.setStepSpeed(30, 0, 162, 0, true);
                    motorRight.setStepSpeed(30, 0, 162, 0, true );
                    motorLeft.waitCompletion();
                    motorRight.waitCompletion();
                }
                else {
                    motorRight.setStepSpeed(30, 0, 158, 0, true);
                    motorLeft.setStepSpeed(30, 0, 158, 0, true);
                    motorRight.waitCompletion();
                    motorLeft.waitCompletion();
                }
                turnFront(api);
                i++;
            }
            motorLeft.setSpeed(0);
            motorRight.setSpeed(0);
        }
        distance = getDistance(api);

    }

    void goBack(EV3.Api api) throws  IOException {
        if(ballIsCatched == false) {
            yCurrentPosition--;
            int i = 1;
            while(i!=5) {
                turnFront(api);
                if(i%2==0) {
                    motorLeft.setStepSpeed(-30, 0, 156, 0, true);
                    motorRight.setStepSpeed(-30, 0, 156, 0, true );
                    motorLeft.waitCompletion();
                    motorRight.waitCompletion();
                }
                else {
                    motorRight.setStepSpeed(-30, 0, 155, 0, true);
                    motorLeft.setStepSpeed(-30, 0, 155, 0, true);
                    motorRight.waitCompletion();
                    motorLeft.waitCompletion();
                }
                turnFront(api);
                i++;
            }
            motorLeft.setSpeed(0);
            motorRight.setSpeed(0);
        }
        else {
            yCurrentPosition--;
            int i = 1;
            while(i!=5) {
                turnFront(api);
                if(i%2==0) {
                    motorLeft.setStepSpeed(-27, 0, 162, 0, true);
                    motorRight.setStepSpeed(-27, 0, 162, 0, true );
                    motorLeft.waitCompletion();
                    motorRight.waitCompletion();
                }
                else {
                    motorRight.setStepSpeed(-27, 0, 158, 0, true);
                    motorLeft.setStepSpeed(-27, 0, 158, 0, true);
                    motorRight.waitCompletion();
                    motorLeft.waitCompletion();
                }
                turnFront(api);
                i++;
            }
            motorLeft.setSpeed(0);
            motorRight.setSpeed(0);
        }


    }

    void goLeft(EV3.Api api, int times) throws  IOException {
        if(times != 0) {
            xCurrentPosition--;
            turnLeft(api);
            int j = 1;
            while(j!=5) {
                turnLeft(api);
                if(j%2==0) {
                    motorLeft.setStepSpeed(30, 0, 160, 0, true);
                    motorRight.setStepSpeed(30, 0, 160, 0, true );
                    motorLeft.waitCompletion();
                    motorRight.waitCompletion();
                }
                else {
                    motorRight.setStepSpeed(30, 0, 155, 0, true);
                    motorLeft.setStepSpeed(30, 0, 155, 0, true);
                    motorRight.waitCompletion();
                    motorLeft.waitCompletion();
                }
                turnLeft(api);
                j++;
            }
            motorLeft.setSpeed(0);
            motorRight.setSpeed(0);
            markZone(xCurrentPosition,yCurrentPosition);
            for(int time=1;time<times;time++) {
                xCurrentPosition--;
                int i = 1;
                while(i!=5) {
                    turnLeft(api);
                    if(i%2==0) {
                        motorLeft.setStepSpeed(30, 0, 161, 0, true);
                        motorRight.setStepSpeed(30, 0, 161, 0, true );
                        motorLeft.waitCompletion();
                        motorRight.waitCompletion();
                    }
                    else {
                        motorRight.setStepSpeed(30, 0, 161, 0, true);
                        motorLeft.setStepSpeed(30, 0, 161, 0, true);
                        motorRight.waitCompletion();
                        motorLeft.waitCompletion();
                    }
                    turnLeft(api);
                    i++;
                }
                motorLeft.setSpeed(0);
                motorRight.setSpeed(0);
                markZone(xCurrentPosition,yCurrentPosition);

            }
            turnFront(api);
        }
    }

    void goRight(EV3.Api api, int times) throws  IOException {
        if(times!=0) {
            xCurrentPosition++;
            turnRight(api);
            int j = 1;
            while(j!=5) {
                turnRight(api);
                if(j%2==0) {
                    motorLeft.setStepSpeed(30, 0, 160, 0, true);
                    motorRight.setStepSpeed(30, 0, 160, 0, true );
                    motorLeft.waitCompletion();
                    motorRight.waitCompletion();
                }
                else {
                    motorRight.setStepSpeed(30, 0, 155, 0, true);
                    motorLeft.setStepSpeed(30, 0, 155, 0, true);
                    motorRight.waitCompletion();
                    motorLeft.waitCompletion();
                }
                turnRight(api);
                j++;
            }
            for(int time=1;time<times;time++) {
                xCurrentPosition++;
                int i = 1;
                while(i!=5) {
                    turnRight(api);
                    if(i%2==0) {
                        motorLeft.setStepSpeed(30, 0, 161, 0, true);
                        motorRight.setStepSpeed(30, 0, 161, 0, true );
                        motorLeft.waitCompletion();
                        motorRight.waitCompletion();
                    }
                    else {
                        motorRight.setStepSpeed(30, 0, 161, 0, true);
                        motorLeft.setStepSpeed(30, 0, 161, 0, true);
                        motorRight.waitCompletion();
                        motorLeft.waitCompletion();
                    }
                    turnRight(api);
                    i++;
                }
                motorLeft.setSpeed(0);
                motorRight.setSpeed(0);
            }
            turnFront(api);
            markZone(xCurrentPosition,yCurrentPosition);
        }
    }

    void goToSafeZone(EV3.Api api) throws  IOException {
        markZone(xCurrentPosition,yCurrentPosition);
        catchBall();
        while (yCurrentPosition > 0) {
            goBack(api);
            markZone(xCurrentPosition,yCurrentPosition);
        }
        while(xCurrentPosition!=xRobotValue) {
            if(xCurrentPosition > xRobotValue) {
                goLeft(api,xCurrentPosition-xRobotValue);
                markZone(xCurrentPosition,yCurrentPosition);
            }
            if(xCurrentPosition < xRobotValue) {
                goRight(api,xRobotValue-xCurrentPosition);
                markZone(xCurrentPosition,yCurrentPosition);
            }
        }
        turn180(api);
        releaseBall();
        int i = 1;
        while(i!=2) {
            if(i%2==0) {
                turn180(api);
                motorLeft.setStepSpeed(-30, 0, 175, 0, true);
                motorRight.setStepSpeed(-30, 0, 175, 0, true );
                motorLeft.waitCompletion();
                motorRight.waitCompletion();
            }
            else {
                motorRight.setStepSpeed(-30, 0, 171, 0, true);
                motorLeft.setStepSpeed(-30, 0, 171, 0, true);
                motorRight.waitCompletion();
                motorLeft.waitCompletion();
                turn180(api);
            }
            i++;
        }
        turn180(api);
        turnFront(api);
        i = 1;
        while(i!=2) {
            if(i%2==0) {
                turnFront(api);
                motorLeft.setStepSpeed(-30, 0, 175, 0, true);
                motorRight.setStepSpeed(-30, 0, 175, 0, true );
                motorLeft.waitCompletion();
                motorRight.waitCompletion();
            }
            else {
                motorRight.setStepSpeed(-30, 0, 171, 0, true);
                motorLeft.setStepSpeed(-30, 0, 171, 0, true);
                motorRight.waitCompletion();
                motorLeft.waitCompletion();
                turnFront(api);
            }
            i++;
        }
        turnFront(api);
        ballIsCatched = false;
    }

    void catchBall() throws IOException {
        motorClaws.setStepSpeed(50,0,2150,0,true);
        motorClaws.waitCompletion();
        motorClaws.stop();
    }

    void releaseBall() throws IOException {
        motorClaws.setStepSpeed(-50,0,2150,0,true);
        motorClaws.waitCompletion();
    }

    void stopMotors() throws IOException {
        motorRight.stop();
        motorLeft.stop();
        motorClaws.stop();
    }

    //Robot Movement Voice Command

    void forwardVoiceRecogniton() {
        try {
            motorLeft.setStepSpeed(50, 0, 175, 0, true);
            motorRight.setStepSpeed(50, 0, 175, 0, true );
            motorLeft.waitCompletion();
            motorRight.waitCompletion();
        } catch (IOException ex) {

        }

    }

    void backVoiceRecogniton() {
        try {
            motorLeft.setStepSpeed(-50, 0, 175, 0, true);
            motorRight.setStepSpeed(-50, 0, 175, 0, true );
            motorLeft.waitCompletion();
            motorRight.waitCompletion();
        } catch (IOException ex) {

        }

    }

    void rightVoiceRecogniton() {
        int speed = 30;
        try {
            motorLeft.setStepPower(-speed,0,333,0,false);
            motorLeft.setStepPower(speed,0,333,0,false);
            motorLeft.waitCompletion();
            motorRight.waitCompletion();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    void leftVoiceRecogniton() {
        int speed = 30;
        try {
            motorLeft.setStepPower(speed,0,333,0,false);
            motorLeft.setStepPower(-speed,0,333,0,false);
            motorLeft.waitCompletion();
            motorRight.waitCompletion();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    void catchBallVoiceRecogniton() {
        try {
            motorClaws.setStepSpeed(50,0,850,0,true);
            motorClaws.waitCompletion();
            motorClaws.stop();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    void releaseBallVoiceRecogniton() {
        try {
            motorClaws.setStepSpeed(-50,0,850,0,true);
            motorClaws.waitCompletion();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    //Orientation Change Robot

    void turnLeft(EV3.Api api) {
        int speed = 1;
        final GyroSensor gyroSensor = api.getGyroSensor(EV3.InputPort._4);
        try {
            float current_angle = gyroSensor.getAngle().get()+90;
            while ( abs(current_angle) > 1 )  {
                if (current_angle > 1) {
                    if(current_angle > 30) {
                        motorLeft.setSpeed(-5);
                        motorRight.setSpeed(5);
                    }
                    else {
                        motorLeft.setSpeed(-speed);
                        motorRight.setSpeed(speed);
                    }
                    motorLeft.start();
                    motorRight.start();
                    Log.i("gyrosensor", gyroSensor.getAngle().get().toString());
                    current_angle = gyroSensor.getAngle().get()+90;
                } else if (current_angle < 1 ) {
                    if(current_angle > -30) {
                        motorLeft.setSpeed(speed);
                        motorRight.setSpeed(-speed);
                    }
                    else {
                        motorLeft.setSpeed(5);
                        motorRight.setSpeed(-5);
                    }
                    motorLeft.start();
                    motorRight.start();
                    Log.i("gyrosensor", gyroSensor.getAngle().get().toString());
                    current_angle = gyroSensor.getAngle().get()+90;
                }
            }
            stopMotors();
        } catch (IOException | InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    void turnRight(EV3.Api api) {
        int speed = 1;
        final GyroSensor gyroSensor = api.getGyroSensor(EV3.InputPort._4);
        try {
            float current_angle = gyroSensor.getAngle().get()-90;
            while ( abs(current_angle) > 1 )  {
                if (current_angle > 1) {
                    if(current_angle > 30) {
                        motorLeft.setSpeed(-5);
                        motorRight.setSpeed(5);
                    }
                    else {
                        motorLeft.setSpeed(-speed);
                        motorRight.setSpeed(speed);
                    }
                    motorLeft.start();
                    motorRight.start();
                    Log.i("gyrosensor", gyroSensor.getAngle().get().toString());
                    current_angle = gyroSensor.getAngle().get()-90;
                } else if (current_angle < 1 ) {
                    if(current_angle > -30) {
                        motorLeft.setSpeed(speed);
                        motorRight.setSpeed(-speed);
                    }
                    else {
                        motorLeft.setSpeed(5);
                        motorRight.setSpeed(-5);
                    }
                    motorLeft.start();
                    motorRight.start();
                    Log.i("gyrosensor", gyroSensor.getAngle().get().toString());
                    current_angle = gyroSensor.getAngle().get()-90;
                }
            }
            stopMotors();
        } catch (IOException | InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    void turnFront(EV3.Api api) {
        int speed = 1;
        final GyroSensor gyroSensor = api.getGyroSensor(EV3.InputPort._4);
        try {
            float current_angle = gyroSensor.getAngle().get();
            while ( abs(current_angle) > 1 )  {
                if (current_angle > 1) {
                    if(current_angle > 30) {
                        motorLeft.setSpeed(-5);
                        motorRight.setSpeed(5);
                    }
                    else {
                        motorLeft.setSpeed(-speed);
                        motorRight.setSpeed(speed);
                    }
                    motorLeft.start();
                    motorRight.start();
                    Log.i("gyrosensor", gyroSensor.getAngle().get().toString());
                    current_angle = gyroSensor.getAngle().get();
                } else if (current_angle < 1 ) {
                    if(current_angle > -30) {
                        motorLeft.setSpeed(speed);
                        motorRight.setSpeed(-speed);
                    }
                    else {
                        motorLeft.setSpeed(5);
                        motorRight.setSpeed(-5);
                    }
                    motorLeft.start();
                    motorRight.start();
                    Log.i("gyrosensor", gyroSensor.getAngle().get().toString());
                    current_angle = gyroSensor.getAngle().get();
                }
            }
            stopMotors();
        } catch (IOException | InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    void turn180(EV3.Api api) {
        int speed = 5;
        final GyroSensor gyroSensor = api.getGyroSensor(EV3.InputPort._4);
        try {
            float current_angle = gyroSensor.getAngle().get();
            while (current_angle > -176){
                motorLeft.setSpeed(-speed);
                motorRight.setSpeed(speed);
                if(current_angle < -150) {
                    speed = 1;
                }
                motorLeft.start();
                motorRight.start();
                current_angle = gyroSensor.getAngle().get();
                Log.i("gyrosensor", gyroSensor.getAngle().get().toString());
            }
            stopMotors();
        } catch (IOException | InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    //Matrix & Display Map Operations

    int[][] constructMatrix(int n, int m) {
        int [][] matrix = new int[n+1][m+1];
        for(int i=0;i<=n;i++)
            for(int j=0;j<=m;j++)
                matrix[i][j] = 0;
        return matrix;
    }

    void markZone(int x,int y) {
        matrix[y][x] = 1;
        updateMap(xCurrentPosition,yCurrentPosition);
    }

    void updateMap(int x, int y) {
        int maxim = max(n,m);
        ArrayList<String> data = new ArrayList<>();
        for (int i = 0; i <= maxim+1; i++)
            for(int j=0; j <= maxim+1;j++) {
                if( i == (m-y) && x == j)  { data.add("O");}
                else if (j > n) { data.add("\\");}
                else if (i > m && j<=n) { data.add("S"); }
                else { data.add(""); }
            }
        adapter = new GridViewCustomAdapter(this, data);
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                int maxim = max(n,m);
                maxim+=2;
                list.setNumColumns(maxim);
                list.setAdapter(adapter);

            }
        });

    }

    boolean checkLine(int x) {
        for(int y=0;y<=m;y++)
            if(matrix[y][x]==0)
                return false;
        return true;
    }

    //Distance Sensor

    int getDistance(EV3.Api api) throws IOException,ExecutionException, InterruptedException {
        final UltrasonicSensor ultraSensor = api.getUltrasonicSensor(EV3.InputPort._1);
        Log.i("",ultraSensor.getDistance().get().toString());
        distance = Math.round(ultraSensor.getDistance().get());
        return Math.round(ultraSensor.getDistance().get());
    }

    //Voice Control

    public void getSpeechInput(View view) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());

        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, 10);
        } else {
            Toast.makeText(this, "Your Device Don't Support Speech Input", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case 10:
                if (resultCode == RESULT_OK && data != null) {
                    ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    txvResult.setText(result.get(0));
                    if(result.get(0).equals("forward") || result.get(0).equals("Forward") || result.get(0).equals("avanti") || result.get(0).equals("Avanti")|| result.get(0).equals("Vai avanti")|| result.get(0).equals("vai avanti"))
                        forwardVoiceRecogniton();
                    if(result.get(0).equals("back")|| result.get(0).equals("Back") || result.get(0).equals("indietro") || result.get(0).equals("Indietro")|| result.get(0).equals("Vai indietro")|| result.get(0).equals("vai indietro"))
                        backVoiceRecogniton();
                    if(result.get(0).equals("right") || result.get(0).equals("Right") || result.get(0).equals("destra") || result.get(0).equals("Destra")|| result.get(0).equals("vai a destra") || result.get(0).equals("Vai a destra"))
                        rightVoiceRecogniton();
                    if(result.get(0).equals("left") || result.get(0).equals("Left") || result.get(0).equals("sinistra") | result.get(0).equals("Sinistra")|| result.get(0).equals("vai a sinistra") | result.get(0).equals("Vai a sinistra"))
                        leftVoiceRecogniton();
                    if(result.get(0).equals("catch")|| result.get(0).equals("Catch") || result.get(0).equals("raccogli") | result.get(0).equals("Raccogli"))
                        catchBallVoiceRecogniton();
                    if(result.get(0).equals("release")|| result.get(0).equals("Release") || result.get(0).equals("deposita") | result.get(0).equals("Deposita"))
                        releaseBallVoiceRecogniton();
                }
                break;
        }
    }

    //Robot Main 

    private void legoMain(EV3.Api api) throws  IOException, InterruptedException, ExecutionException {
        final String TAG = Prelude.ReTAG("legoMain");
        final UltrasonicSensor ultraSensor = api.getUltrasonicSensor(EV3.InputPort._1);
        Log.i("",ultraSensor.getDistance().get().toString());
        distance = ultraSensor.getDistance().get();
        motorLeft = api.getTachoMotor(EV3.OutputPort.A);
        motorRight = api.getTachoMotor(EV3.OutputPort.D);
        motorClaws = api.getTachoMotor(EV3.OutputPort.B);
        setUpCamera();
        ball_catched = 0;
            while (ball_catched!=1) {
//                for (int line = xCurrentPosition; line >= 0; line--) {
//                    while (checkLine(xCurrentPosition) != true) {
//                        distance = ultraSensor.getDistance().get();
//                        if(distance >= 15 && distance <=40)
//                        {
//                            ballIsCatched = true;
//                        }
//                        markZone(xCurrentPosition, yCurrentPosition);
//                        goForward(api);
//                        if(ballIsCatched) {
//                            goToSafeZone(api);
//                            line = xCurrentPosition;
//                            markZone(xCurrentPosition,yCurrentPosition);
//                        }
//                        markZone(xCurrentPosition, yCurrentPosition);
//                    }
//                    while(yCurrentPosition!=0) {
//                        goBack(api);
//                        updateMap(xCurrentPosition,yCurrentPosition);
//                    }
//                    if(xCurrentPosition==0) {
//                        break;
//                    }
//                    goLeft(api, 1);
//                    updateMap(xCurrentPosition,yCurrentPosition);
//                }
//                turnFront(api);
//                goRight(api,xRobotValue);
//                turnFront(api);
//                updateMap(xCurrentPosition,yCurrentPosition);
//                for (int line = xCurrentPosition; line <= n; line++) {
//                    while (checkLine(xCurrentPosition) != true) {
//                        distance = ultraSensor.getDistance().get();
//                        if(distance >= 15 && distance <=40)
//                        {
//                            ballIsCatched = true;
//                        }
//                        markZone(xCurrentPosition, yCurrentPosition);
//                        goForward(api);
//                        if(ballIsCatched) {
//                            goToSafeZone(api);
//                            line = xCurrentPosition;
//                            markZone(xCurrentPosition,yCurrentPosition);
//
//                        }
//                        markZone(xCurrentPosition, yCurrentPosition);
//                    }
//                    while(yCurrentPosition!=0) {
//                        goBack(api);
//                        markZone(xCurrentPosition,yCurrentPosition);
//                    }
//                    if(xCurrentPosition==n) {
//                        break;
//                    }
//                    goRight(api, 1);
//                    markZone(xCurrentPosition,yCurrentPosition);
//                }
//                goLeft(api,xCurrentPosition-xRobotValue);
//                turnFront(api);
                markZone(xRobotValue,yRobotValue);
                ball_catched++;

            }
    }

    private static class MyCustomApi extends EV3.Api {

        private MyCustomApi(@NonNull GenEV3<? extends EV3.Api> ev3) {
            super(ev3);
        }

        public void mySpecialCommand() {}
    }

    private void legoMainCustomApi(First.MyCustomApi api) throws InterruptedException, ExecutionException, IOException {
        final String TAG = Prelude.ReTAG("legoMainCustomApi");
        api.mySpecialCommand();
        EditText rows = findViewById(R.id.rows);
        EditText columns = findViewById(R.id.columns);
        n = Integer.valueOf(rows.getText().toString());
        m = Integer.valueOf(columns.getText().toString());
        EditText robotXCoordinate = findViewById(R.id.xRobot);
        EditText robotYCoordinate = findViewById(R.id.yRobot);
        xRobotValue = Integer.valueOf(robotXCoordinate.getText().toString());
        yRobotValue = Integer.valueOf(robotYCoordinate.getText().toString());
        xCurrentPosition = xRobotValue;
        yCurrentPosition = yRobotValue;
        matrix = constructMatrix(m,n);

        EditText numberOfBalls  = findViewById(R.id.balls);
//        totalBalls = Integer.valueOf(numberOfBalls.getText().toString());
        totalBalls = 1;
        legoMain(api);
    }
}
