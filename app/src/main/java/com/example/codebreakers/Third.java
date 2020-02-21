package com.example.codebreakers;


import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.Toast;

import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.Strategy;
import com.kircherelectronics.fsensor.filter.averaging.MeanFilter;
import com.kircherelectronics.fsensor.filter.gyroscope.OrientationGyroscope;

import org.apache.commons.math3.complex.Quaternion;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import it.unive.dais.legodroid.lib.EV3;
import it.unive.dais.legodroid.lib.GenEV3;
import it.unive.dais.legodroid.lib.comm.BluetoothConnection;
import it.unive.dais.legodroid.lib.plugs.TachoMotor;
import it.unive.dais.legodroid.lib.plugs.UltrasonicSensor;
import it.unive.dais.legodroid.lib.util.Prelude;

import static java.lang.Math.max;
import static java.lang.Thread.sleep;

public class Third extends ConnectionsActivity implements SensorEventListener {

    //Connection
    private int robotID;
    private static final Strategy STRATEGY = Strategy.P2P_STAR;
    private static final long ADVERTISING_DURATION = 30000;
    private static final String SERVICE_ID = "it.unive.dais.nearby.apps.SERVICE_ID";
    private State mState = State.UNKNOWN;
    private String mName;
    private String KEY;
    private boolean[] mStop;
    private final Handler mUiHandler = new Handler(Looper.getMainLooper());
    private final Runnable mDiscoverRunnable = new Runnable() {
                @Override
                public void run() {
                    setState(State.DISCOVERING);
                }
            };
    public enum State {
        UNKNOWN,
        DISCOVERING,
        ADVERTISING,
        CONNECTED
    }


    //Robot
    private Integer xRobotValue;
    private Integer yRobotValue;
    private Integer xCurrentPosition;
    private Integer yCurrentPosition;
    private Integer orientation;
    boolean flag = true;

    //Motors
    private static TachoMotor motorLeft;
    private static TachoMotor motorRight;
    private static TachoMotor motorClaws;


    //Camera
    private static final int CAMERA_PERMISSION_CODE=100;
    private CameraBridgeViewBase mOpenCvCameraView;


    //Balls
    private Integer n;
    private Integer m;
    private Integer ball_catched = 0;
    float distance;
    ArrayList<Pair<Integer,Integer>> coordinates = new ArrayList<>();
    private boolean ballIsCatched = false;

    //Map
    private static GridView list;
    private int[][] matrix;
    GridViewCustomAdapter adapter;

    //Sensor
    private OrientationGyroscope orientationGyroscope = new OrientationGyroscope();
    private SensorManager sensorManager;
    private Sensor gyroscopeSensor;
    private SensorEventListener gyroscopeSensorListener;
    private float[] fusedOrientation = new float[3];
    private float[] rotation = new float[3];
    private MeanFilter meanFilter;
    private boolean meanFilterEnabled;
    float best_angle = (float) 180.0;
    private static final String TAG = Prelude.ReTAG("MainActivity");


    private void setUpCamera() {
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "Unable to load OpenCV");
        } else {
            Log.d(TAG, "OpenCV loaded");
        }
        mOpenCvCameraView = findViewById(R.id.HelloOpenCvView);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setMaxFrameSize(1920, 1080);
        mOpenCvCameraView.disableFpsMeter();
        mOpenCvCameraView.setCvCameraViewListener(new CameraBridgeViewBase.CvCameraViewListener2() {
            @Override
            public void onCameraViewStarted(int width, int height) {

                Log.d(TAG, "Camera Started");
            }

            @Override
            public void onCameraViewStopped() {
                Log.d(TAG, "Camera Stopped");
            }
            @Override
            public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
                Mat frame = inputFrame.rgba();
                Mat frameT = frame.t();
                Core.flip(frame.t(), frameT, 1);
                Imgproc.resize(frameT, frameT, frame.size());

                BallFinder ballFinder = new BallFinder(frameT, true);
                ballFinder.setMinArea(5500);
                ballFinder.setViewRatio(0.4f);
                ballFinder.setOrientation("landscape");
                ArrayList<Ball> f = ballFinder.findBalls();
                for (Ball b : f) {
                    Log.e("ball", String.valueOf(b.center.x));
                    Log.e("ball", String.valueOf(b.center.y));
                    Log.e("ball", String.valueOf(b.radius));
                    Log.e("ball", b.color);
                }

                return frameT;
            }
        });
        mOpenCvCameraView.enableView();
    }

    //Connectivity

    @Override protected void onStart() {
        super.onStart();
        setState(State.DISCOVERING);
    }

    @Override protected void onStop() {

        setState(State.UNKNOWN);

        mUiHandler.removeCallbacksAndMessages(null);

        super.onStop();
    }

    @Override public void onBackPressed() {
        if (getState() == State.CONNECTED || getState() == State.ADVERTISING) {
            setState(State.DISCOVERING);
            return;
        }
        super.onBackPressed();
    }

    @Override protected void onEndpointDiscovered(Endpoint endpoint) {
        if (!isConnecting()) {
            connectToEndpoint(endpoint);
        }
    }

    @Override protected void onConnectionInitiated(Endpoint endpoint, ConnectionInfo connectionInfo) {
        acceptConnection(endpoint);
    }

    @Override protected void onEndpointConnected(Endpoint endpoint) {
        String x = "Benvenuto sono "+mName;
        byte[] bytes;
        Toast.makeText(
                this, getString(R.string.toast_connected, endpoint.getName()), Toast.LENGTH_SHORT)
                .show();
        setState(State.CONNECTED);
        bytes = x.getBytes();
        send(Payload.fromBytes(bytes));
    }

    @Override protected void onEndpointDisconnected(Endpoint endpoint) {
        Toast.makeText(
                this, getString(R.string.toast_disconnected, endpoint.getName()), Toast.LENGTH_SHORT)
                .show();
        if (getConnectedEndpoints().isEmpty()) {
            setState(State.DISCOVERING);
        }
    }

    @Override protected void onConnectionFailed(Endpoint endpoint) {
        if (getState() == State.DISCOVERING && !getDiscoveredEndpoints().isEmpty()) {
            connectToEndpoint(pickRandomElem(getDiscoveredEndpoints()));
        }
    }

    private void setState(State state) {
        if (mState == state) {
            logW("State set to " + state + " but already in that state");
            return;
        }

        logD("State set to " + state);
        State oldState = mState;
        mState = state;
        onStateChanged(oldState, state);
    }

    private State getState() {
        return mState;
    }

    private void onStateChanged(State oldState, State newState) {
        switch (newState) {
            case DISCOVERING:
                if (isAdvertising()) {
                    stopAdvertising();
                }
                disconnectFromAllEndpoints();
                startDiscovering();
                break;
            case ADVERTISING:
                if (isDiscovering()) {
                    stopDiscovering();
                }
                disconnectFromAllEndpoints();
                startAdvertising();
                break;
            case CONNECTED:
                if (isDiscovering()) {
                    stopDiscovering();
                } else if (isAdvertising()) {
                    removeCallbacks(mDiscoverRunnable);
                }
                break;
            case UNKNOWN:
                stopAllEndpoints();
                break;
            default:
                break;
        }
        switch (oldState) {
            case UNKNOWN:
                break;
            case DISCOVERING:
                switch (newState) {
                    case UNKNOWN:
                        break;
                    case ADVERTISING:
                    case CONNECTED:
                        break;
                    default:
                        break;
                }
                break;
            case ADVERTISING:
                switch (newState) {
                    case UNKNOWN:
                    case DISCOVERING:

                        break;
                    case CONNECTED:
                        break;
                    default:
                        // no-op
                        break;
                }
                break;
            case CONNECTED:
                // Connected is our final state. Whatever new state we move to,
                // we're transitioning backwards.

                break;
            default:
                // no-op
                break;
        }
    }

    public void sendUpdate( int xBall, int yBall) {
        String x;
        byte[] ballBytes;
        x = "Coordinate recupero" + xBall + ";" + yBall;
        ballBytes = x.getBytes();
        send(Payload.fromBytes(ballBytes));
    }

    public void sendUpdateMessage(int xRobot, int yRobot, int xBall, int yBall, boolean status){
        String x;
        Calendar calendar = Calendar.getInstance();
        Long time_long = calendar.getTimeInMillis();
        byte[] bytes;
        if(status) {
            x = "Operazione in corso:" + xBall + ";" + yBall + ";[" + time_long.toString() + "];";
            bytes = x.getBytes();
            try {
                SecretKeySpec key = new SecretKeySpec(KEY.getBytes(), "DES");
                Cipher c = Cipher.getInstance("DES/ECB/ISO10126Padding");
                c.init(c.ENCRYPT_MODE, key);
                byte[] ciphertext = c.doFinal(bytes);
                send(Payload.fromBytes(ciphertext));
                System.out.println("CASO TRUE: Mando messaggio cifrato" + x);

            } catch (NoSuchAlgorithmException | InvalidKeyException | NoSuchPaddingException | BadPaddingException | IllegalBlockSizeException e) {
                e.printStackTrace();
            }
        } else {
            x = "Operazione completata" + xRobot + ";" + yRobot + ";[" + time_long.toString() + "];";
            bytes = x.getBytes();
            try {
                SecretKeySpec key = new SecretKeySpec(KEY.getBytes(), "DES");
                Cipher c = Cipher.getInstance("DES/ECB/ISO10126Padding");
                c.init(c.ENCRYPT_MODE, key);
                byte[] ciphertext = c.doFinal(bytes);
                System.out.println("ELSE : Mando messaggio cifrato" + x);
                send(Payload.fromBytes(ciphertext));

            } catch (NoSuchAlgorithmException | InvalidKeyException |  NoSuchPaddingException | BadPaddingException | IllegalBlockSizeException e) {
                e.printStackTrace();
            }
            sendUpdate(xBall,yBall);
        }
    }

    protected void receiveCoordinates (Payload payload){
        Pattern p = Pattern.compile("-?\\d+");
        if(payload.getType() == Payload.Type.BYTES){
            byte[] bytes = payload.asBytes();
            String str_bytes = new String(bytes);

            Matcher m = p.matcher(str_bytes);
            while (m.find()) {
                System.out.println(m.group());
                Integer x = Integer.valueOf(m.group());
                m.find();
                Integer y = Integer.valueOf(m.group());
                Pair<Integer,Integer> pair = new Pair<>(x,y);
                coordinates.add(pair);
            }
        }
    }

    @Override protected void onReceive(Endpoint endpoint, Payload payload) {
        if (payload.getType() == Payload.Type.BYTES) {
            byte[] bytes = payload.asBytes();
            String str_bytes = new String(bytes);
            Integer aux = Character.getNumericValue(str_bytes.charAt(0));
            if((aux >= 0 && aux <=6) && ((str_bytes.charAt(1)=='S') || (str_bytes.charAt(1)=='R'))){
                if(aux == 0 || aux == robotID) {
                    if(str_bytes.contains("STOP")){
                        logD(String.format("STOP message intercepted %s", str_bytes));
                        String x;
                        Calendar calendar = Calendar.getInstance();
                        Long time_long = calendar.getTimeInMillis();
                        byte[] message;
                        x = "Operazione annullata:" + xCurrentPosition + ";" + yCurrentPosition + ";[" + time_long.toString() + "];";
                        message = x.getBytes();
                        try {
                            SecretKeySpec key = new SecretKeySpec(KEY.getBytes(), "DES");
                            Cipher c = Cipher.getInstance("DES/ECB/ISO10126Padding");
                            c.init(c.ENCRYPT_MODE, key);
                            byte[] ciphertext = c.doFinal(message);
                            send(Payload.fromBytes(ciphertext));
                            System.out.println("CASO TRUE: Mando messaggio cifrato" + x);

                        } catch (NoSuchAlgorithmException | InvalidKeyException | NoSuchPaddingException | BadPaddingException | IllegalBlockSizeException e) {
                            e.printStackTrace();
                        }
                        flag = false;
                        return;
                    } else if(str_bytes.contains("RESUME")) {
                        flag = true;
                        return;
                    }
                } else {
                    logD(String.format("STOP/RESUME message ignored %s", str_bytes));
                    return;
                }
            }


            if (str_bytes.toLowerCase().contains("recupero")) {
                logD(String.format("Recovery message: %s",str_bytes));
                receiveCoordinates(payload);
                return;
            }



            if (str_bytes.toLowerCase().contains("benvenuto")) {
                logD(String.format("Welcome message: %s", str_bytes));
                // messaggio di benvenuto
                return;
            }

            try {
                SecretKeySpec key = new SecretKeySpec(KEY.getBytes(), "DES");
                Cipher c = Cipher.getInstance("DES/ECB/ISO10126Padding");
                c.init(c.DECRYPT_MODE, key);

                byte[] plaintext = c.doFinal(bytes);
                String s = new String(plaintext);

                logD(
                        String.format(
                                "BYTE received %s from endpoint %s",
                                s, endpoint.getName()));
                System.out.println("BYTE received %s from endpoint %s");
                 if(s.contains("corso")){
                    logD(
                            String.format(
                                    "OPERAZIONE IN CORSO received %s from endpoint %s",
                                    s, endpoint.getName()));


                }else if(s.contains("annullata")){
                    logD(
                            String.format(
                                    "OPERAZIONE ANNULLATA received %s from endpoint %s",
                                    s, endpoint.getName()));


                }if(s.contains("completata")){
                    logD(
                            String.format(
                                    "OPERAZIONE COMPLETATA received %s from endpoint %s",
                                    s, endpoint.getName()));


                }

            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (InvalidKeyException e) {
                logD(
                        String.format(
                                "BYTE (crypted) received from %s unreadable (InvalidKeyException)",
                                endpoint.getName()));
                e.printStackTrace();
            } catch (NoSuchPaddingException e) {
                logD(
                        String.format(
                                "BYTE (crypted) received from %s unreadable (NoSuchPaddingException)",
                                endpoint.getName()));
                e.printStackTrace();
            } catch (BadPaddingException e) {
                logD(
                        String.format(
                                "BYTE (crypted) received from %s unreadable (BadPaddingException)",
                                endpoint.getName()));
                e.printStackTrace();
            } catch (IllegalBlockSizeException e) {
                logD(
                        String.format(
                                "BYTE (crypted) received from %s unreadable (IllegalBlockSizeException)",
                                endpoint.getName()));
                e.printStackTrace();
            }

        }


    }

    private void motion_stop (Integer n){
        String str;
        if (mStop[n]) {
            str = n.toString()+"STOP";
            mStop[n] = false;
            if(n == 0){ Arrays.fill(mStop, false);}
        }
        else
        {
            str = n.toString()+"RESUME";
            mStop[n] = true;
            if(n == 0){ Arrays.fill(mStop, true);}
        }
        byte[] bytes = str.getBytes();
        send(Payload.fromBytes(bytes));

    }

    public void stop_stop_click(View view) {
        Integer value =  Integer.valueOf((view.getTag().toString()));
        motion_stop(value);
    }

    @Override protected String[] getRequiredPermissions() {
        return join(
                super.getRequiredPermissions(),
                Manifest.permission.RECORD_AUDIO);
    }

    private static String[] join(String[] a, String... b) {
        String[] join = new String[a.length + b.length];
        System.arraycopy(a, 0, join, 0, a.length);
        System.arraycopy(b, 0, join, a.length, b.length);
        return join;
    }

    @Override protected String getName() {
        return mName;
    }

    @Override public String getServiceId() {
        return SERVICE_ID;
    }

    @Override public Strategy getStrategy() {
        return STRATEGY;
    }

    protected void removeCallbacks(Runnable r) {
        mUiHandler.removeCallbacks(r);
    }

    @SuppressWarnings("unchecked")
    private static <T> T pickRandomElem(Collection<T> collection) {
        return (T) collection.toArray()[new Random().nextInt(collection.size())];
    }

    //On Create

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_third);
        Button start = findViewById(R.id.Start);
        mOpenCvCameraView = findViewById(R.id.HelloOpenCvView);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setMaxFrameSize(640, 480);
        mOpenCvCameraView.disableFpsMeter();
        sensorManager =(SensorManager) getSystemService(SENSOR_SERVICE);
        gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if(gyroscopeSensor == null) {
            Log.e(TAG, "Gyroscope sensor not available.");
            finish();
        }

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
            BluetoothConnection.BluetoothChannel conn = new BluetoothConnection("Willy").connect();
            GenEV3<Third.MyCustomApi> ev3 = new GenEV3<>(conn);
            Button startButton = findViewById(R.id.Start);
            startButton.setOnClickListener(v -> Prelude.trap(() -> ev3.run(this::legoMainCustomApi, Third.MyCustomApi::new)));
        } catch (IOException e) {
            Log.e(TAG, "fatal error: cannot connect to EV3");
            e.printStackTrace();
        }
        mName = "CodeBreakers";
        mStop = new boolean[6];
        Arrays.fill(mStop, true);

    }

    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this,
                gyroscopeSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            System.arraycopy(event.values, 0, rotation, 0, event.values.length);
            if(!orientationGyroscope.isBaseOrientationSet()) {
                orientationGyroscope.setBaseOrientation(Quaternion.IDENTITY);
            } else {
                fusedOrientation = orientationGyroscope.calculateOrientation(rotation, event.timestamp);
            }
            if(meanFilterEnabled) {
                fusedOrientation = meanFilter.filter(fusedOrientation);
            }
            Log.d("GYRO COORDINATES",String.format(Locale.getDefault(),"%.1f", (Math.toDegrees(fusedOrientation[2]) + 180) % 360));
            best_angle = (float)(((Math.toDegrees(fusedOrientation[2])) +180) % 360);
        }
    }
    @Override
    public void onAccuracyChanged(Sensor sensor, int i) { }

    @Override
    protected void onPause() { super.onPause();}

    //Robot Movement

    void goForward(EV3.Api api) throws IOException, InterruptedException, ExecutionException {
        final UltrasonicSensor ultraSensor = api.getUltrasonicSensor(EV3.InputPort._1);
        distance = ultraSensor.getDistance().get();
        if(distance >= 15 && distance <=40)
        {
            ballIsCatched = true;
        }
        else {
            ballIsCatched = false;
        }
        if(ballIsCatched == false) {
            yCurrentPosition++;
            int i = 1;
            while(i!=5) {
                Thread.sleep(100);
                while (flag == false) { sleep(100); }

                turnFront();
                Thread.sleep(100);
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
                Thread.sleep(100);
                turnFront();
                Thread.sleep(100);
                i++;
            }
            motorLeft.setSpeed(0);
            motorRight.setSpeed(0);
        }
        else {
            yCurrentPosition++;
            int i = 1;
            while(i!=5) {
                Thread.sleep(100);
                while (flag == false) { sleep(100); }

                turnFront();
                Thread.sleep(100);
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
                Thread.sleep(100);
                turnFront();
                Thread.sleep(100);
                i++;
            }
            motorLeft.setSpeed(0);
            motorRight.setSpeed(0);
        }

    }

    void goBack(EV3.Api api) throws  IOException, InterruptedException {
        if(ballIsCatched == false) {
            yCurrentPosition--;
            int i = 1;
            while(i!=5) {
                Thread.sleep(100);
                while (flag == false) { sleep(100); }
                turnFront();
                Thread.sleep(100);
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
                Thread.sleep(100);
                turnFront();
                Thread.sleep(100);
                i++;
            }
            motorLeft.setSpeed(0);
            motorRight.setSpeed(0);
        }
        else {
            yCurrentPosition--;
            int i = 1;
            while(i!=5) {
                Thread.sleep(100);
                while (flag == false) { sleep(100); }

                turnFront();
                Thread.sleep(100);
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
                Thread.sleep(100);
                turnFront();
                Thread.sleep(100);
                i++;
            }
            motorLeft.setSpeed(0);
            motorRight.setSpeed(0);
        }


    }

    void goLeft(EV3.Api api, int times) throws  IOException, InterruptedException {
        if(times!=0) {
            xCurrentPosition--;
            turnLeft(api);
            updateMap(xCurrentPosition, yCurrentPosition);
            int j = 1;
            while (j != 5) {
                while (flag == false) {
                    sleep(100);
                }
                turnLeft(api);
                if (j % 2 == 0) {
                    motorLeft.setStepSpeed(30, 0, 160, 0, true);
                    motorRight.setStepSpeed(30, 0, 160, 0, true);
                    motorLeft.waitCompletion();
                    motorRight.waitCompletion();
                } else {
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
            markZone(xCurrentPosition, yCurrentPosition);
            for (int time = 1; time < times; time++) {
                xCurrentPosition--;
                updateMap(xCurrentPosition, yCurrentPosition);
                int i = 1;
                while (i != 5) {
                    while (flag == false) {
                        sleep(100);
                    }
                    turnLeft(api);
                    if (i % 2 == 0) {
                        motorLeft.setStepSpeed(30, 0, 161, 0, true);
                        motorRight.setStepSpeed(30, 0, 161, 0, true);
                        motorLeft.waitCompletion();
                        motorRight.waitCompletion();
                    } else {
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
                markZone(xCurrentPosition, yCurrentPosition);

            }
            turnFront();
        }
    }

    void goRight(EV3.Api api, int times) throws  IOException, InterruptedException {
        if(times!=0) {
            xCurrentPosition++;
            turnRight(api);
            updateMap(xCurrentPosition,yCurrentPosition);
            int j = 1;
            while(j!=5) {
                while (flag == false) { sleep(100); }
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
                updateMap(xCurrentPosition,yCurrentPosition);
                int i = 1;
                while(i!=5) {
                    while (flag == false) { sleep(100); }
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
            turnFront();
            markZone(xCurrentPosition,yCurrentPosition);
        }
    }

    /*void goToSafeZone(EV3.Api api) throws  IOException, InterruptedException {
        markZone(xCurrentPosition,yCurrentPosition);
        catchBall();
        Pair<Integer,Integer> ball_posistion = new Pair<>(xCurrentPosition,yCurrentPosition);
        balls_position.add(ball_posistion);
        while (yCurrentPosition > 0) {
            Thread.sleep(100);
            goBack(api);
            Thread.sleep(100);
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
                motorLeft.setStepSpeed(-30, 0, 160, 0, true);
                motorRight.setStepSpeed(-30, 0, 160, 0, true );
                motorLeft.waitCompletion();
                motorRight.waitCompletion();
            }
            else {
                motorRight.setStepSpeed(-30, 0, 160, 0, true);
                motorLeft.setStepSpeed(-30, 0, 160, 0, true);
                motorRight.waitCompletion();
                motorLeft.waitCompletion();
                turn180(api);
            }
            i++;
        }
        turn180(api);
        turnFront();
        i = 1;
        while(i!=2) {
            if(i%2==0) {
                turnFront();
                motorLeft.setStepSpeed(-30, 0, 160, 0, true);
                motorRight.setStepSpeed(-30, 0, 160, 0, true );
                motorLeft.waitCompletion();
                motorRight.waitCompletion();
            }
            else {
                motorRight.setStepSpeed(-30, 0, 160, 0, true);
                motorLeft.setStepSpeed(-30, 0, 160, 0, true);
                motorRight.waitCompletion();
                motorLeft.waitCompletion();
                turnFront();
            }
            i++;
        }
        turnFront();
        ballIsCatched = false;
    }*/

    void catchBall() throws IOException {
        motorClaws.setStepSpeed(50,0,2200,0,true);
        motorClaws.waitCompletion();
        motorClaws.stop();
    }

    void releaseBall() throws IOException {
        motorClaws.setStepSpeed(-50,0,2200,0,true);
        motorClaws.waitCompletion();
    }
    //TODO: goBack in sleep
    void goToSafeZone(EV3.Api api) throws  IOException, InterruptedException {
        int xBall = xCurrentPosition;
        int yBall = yCurrentPosition;
        if(orientation==1){
            Integer aux_a = (Integer)xBall;
            Integer aux_b = (Integer)yBall;
            xBall = aux_b;
            yBall = n-aux_a;
        }
        else if(orientation==2){
            Integer aux_a = (Integer)xBall;
            Integer aux_b = (Integer)yBall;
            xBall = n-aux_a;
            yBall = m-aux_b;
        }
        else if(orientation==3){
            Integer aux_a = (Integer)xBall;
            Integer aux_b = (Integer)yBall;
            xBall = m-aux_b;
            yBall = aux_a;
        }
        Pair<Integer,Integer> p1 = new Pair<>(xBall,yBall);
        markZone(xCurrentPosition,yCurrentPosition);
        catchBall();
        coordinates.add(p1);
        sendUpdateMessage(xCurrentPosition,yCurrentPosition,xBall,yBall,true);
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
        sendUpdateMessage(xCurrentPosition,yCurrentPosition,xBall,yBall,false);
        int i = 1;
        while(i!=2) {
            if(i%2==0) {
                turn180(api);
                motorLeft.setStepSpeed(-30, 0, 160, 0, true);
                motorRight.setStepSpeed(-30, 0, 160, 0, true );
                motorLeft.waitCompletion();
                motorRight.waitCompletion();
            }
            else {
                motorRight.setStepSpeed(-30, 0, 160, 0, true);
                motorLeft.setStepSpeed(-30, 0, 160, 0, true);
                motorRight.waitCompletion();
                motorLeft.waitCompletion();
                turn180(api);
            }
            i++;
        }
        turn180(api);
        turnFront();
        i = 1;
        while(i!=2) {
            if(i%2==0) {
                turnFront();
                motorLeft.setStepSpeed(-30, 0, 160, 0, true);
                motorRight.setStepSpeed(-30, 0, 160, 0, true );
                motorLeft.waitCompletion();
                motorRight.waitCompletion();
            }
            else {
                motorRight.setStepSpeed(-30, 0, 160, 0, true);
                motorLeft.setStepSpeed(-30, 0, 160, 0, true);
                motorRight.waitCompletion();
                motorLeft.waitCompletion();
                turnFront();
            }
            i++;
        }
        turnFront();
        ballIsCatched = false;
    }

    void stopMotors() throws IOException {
        motorRight.stop();
        motorLeft.stop();
        motorClaws.stop();

    }

    //Orientation Change Robot

    void turnLeft(EV3.Api api) {
        int speed = 1;
        try {
            float current_angle = best_angle;
            while ( current_angle < 88 || current_angle > 92 )  {
                if (current_angle > 91) {
                    if(current_angle > 121) {
                        motorLeft.setSpeed(-3);
                        motorRight.setSpeed(3);
                    }
                    else {
                        motorLeft.setSpeed(-speed);
                        motorRight.setSpeed(speed);
                    }
                    motorLeft.start();
                    motorRight.start();
                    sleep(100);
                    current_angle = best_angle;
                } else if (current_angle < 89 ) {
                    if(current_angle > -59) {
                        motorLeft.setSpeed(speed);
                        motorRight.setSpeed(-speed);
                    }
                    else {
                        motorLeft.setSpeed(3);
                        motorRight.setSpeed(-3);
                    }
                    motorLeft.start();
                    motorRight.start();
                    sleep(100);
                    current_angle = best_angle;
                }
            }
            stopMotors();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    void turnRight(EV3.Api api) {
        int speed = 1;
        try {
            float current_angle = best_angle;
            while ( current_angle < 268 || current_angle > 272 )  {
                if (current_angle > 271) {
                    if(current_angle > 250) {
                        motorLeft.setSpeed(-3);
                        motorRight.setSpeed(3);
                    }
                    else {
                        motorLeft.setSpeed(-speed);
                        motorRight.setSpeed(speed);
                    }
                    motorLeft.start();
                    motorRight.start();
                    sleep(100);
                    current_angle = best_angle;
                } else if (current_angle < 269 ) {
                    if(current_angle > 300) {
                        motorLeft.setSpeed(speed);
                        motorRight.setSpeed(-speed);
                    }
                    else {
                        motorLeft.setSpeed(3);
                        motorRight.setSpeed(-3);
                    }
                    motorLeft.start();
                    motorRight.start();
                    sleep(100);
                    current_angle = best_angle;
                }
            }
            stopMotors();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    void turnFront() throws IOException, InterruptedException {
        while (flag == false) { sleep(100); }
        if(ballIsCatched == false) {
            int speed = 1;
            int angle = (int) best_angle;
            while (angle < 179 || angle > 181) {
                while (flag == false) { sleep(100); }
                sleep(100);
                if (angle > 181) {
                    //motorLeft.setStepSpeed(-speed, 0, 10, 0, false);
                    //motorRight.setStepSpeed(speed, 0, 10, 0, false);
                    motorLeft.setSpeed(-speed);
                    motorRight.setSpeed(speed);
                    motorLeft.start();
                    motorRight.start();
                    sleep(100);
                    angle = (int) best_angle;

                } else if (angle < 179) {
                    //motorLeft.setStepSpeed(speed, 0, 10, 0, false);
                    //motorRight.setStepSpeed(-speed, 0, 10, 0, false);
                    motorLeft.setSpeed(speed);
                    motorRight.setSpeed(-speed);
                    motorLeft.start();
                    motorRight.start();
                    sleep(100);
                    angle = (int) best_angle;
                }

            }

        } else {
            int speed = 2;
            int angle = (int) best_angle;
            while (angle < 179 || angle > 181) {
                while (flag == false) { sleep(100); }
                sleep(100);
                if (angle > 181) {
                    //motorLeft.setStepSpeed(-speed, 0, 10, 0, false);
                    //motorRight.setStepSpeed(speed, 0, 10, 0, false);
                    motorLeft.setSpeed(-speed);
                    motorRight.setSpeed(speed);
                    motorLeft.start();
                    motorRight.start();
                    sleep(100);
                    angle = (int) best_angle;
                } else if (angle < 179) {
                    //motorLeft.setStepSpeed(speed, 0, 10, 0, false);
                    //motorRight.setStepSpeed(-speed, 0, 10, 0, false);
                    motorLeft.setSpeed(speed);
                    motorRight.setSpeed(-speed);
                    motorLeft.start();
                    motorRight.start();
                    sleep(100);
                    angle = (int) best_angle;
                }

            }
        }
    }

    void turnBackFront() throws IOException, InterruptedException {
        if(ballIsCatched == false) {
            int speed = 1;
            int angle = (int)best_angle;
            while ( angle < 179 || angle > 181 )  {
                sleep(100);
                if (angle > 181) {
                    motorLeft.setStepSpeed(-speed,0,10,0,false);
                    motorRight.setStepSpeed(speed,0,10,0,false);
                    sleep(100);
                    angle = (int)best_angle;
                } else if (angle < 179 ) {
                    motorLeft.setStepSpeed(speed,0,10,0,false);
                    motorRight.setStepSpeed(-speed,0,10,0,false);
                    sleep(100);
                    angle = (int)best_angle;
                }

            }
        } else {
            int speed = 2;
            int angle = (int)best_angle;
            while ( angle < 179 || angle > 181 )  {
                sleep(100);
                if (angle > 181) {
                    motorLeft.setStepSpeed(-speed,0,10,0,false);
                    motorRight.setStepSpeed(speed,0,10,0,false);
                    sleep(100);
                    angle = (int)best_angle;
                } else if (angle < 179 ) {
                    motorLeft.setStepSpeed(speed,0,10,0,false);
                    motorRight.setStepSpeed(-speed,0,10,0,false);
                    sleep(100);
                    angle = (int)best_angle;
                }

            }
        }



    }

    void turn180(EV3.Api api) {
        int speed = 5;
        try {
            sleep(100);
            float current_angle = best_angle;
            while (current_angle > 4) {
                motorLeft.setSpeed(-speed);
                motorRight.setSpeed(speed);
                motorLeft.start();
                motorRight.start();
                sleep(100);
                current_angle = best_angle;
            }
            stopMotors();
        } catch (IOException | InterruptedException  e) {
            e.printStackTrace();
        }
    }

    //Matrix and Display Map

    void updateMap(int x, int y) {
        int maxim = max(n, m);
        ArrayList<String> data = new ArrayList<>();
        for (int i = 0; i <= maxim + 1; i++)
            for (int j = 0; j <= maxim + 1; j++) {
                if (i == (m - y) && x == j) {
                    data.add("R");
                }
                else if (j > n) {
                    data.add("\\");
                } else if (i > m && j <= n) {
                    data.add("\\");
                } else {
                    data.add("");
                }
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

    boolean checkLine(int x) {
        for(int y=0;y<=m;y++)
            if(matrix[y][x]==0)
                return false;
        return true;
    }

    void showFinal() {
        if(orientation == 1 || orientation == 3) {
            int aux = m;
            m = n;
            n = aux;
        }

        ArrayList<String> data = new ArrayList<>();
        int maxim = max(n, m);
        for (int i = 0; i <= maxim+1; i++) {
            for(int j=0; j <= maxim+1;j++) {
                if ( ((m-coordinates.get(0).second) == i) && (coordinates.get(0).first == j)) {
                    data.add("O");
                    if(coordinates.size()>1)
                        coordinates.remove(0);
                }
                else if (j > n) {
                    data.add("\\");
                } else if (i > m && j <= n) {
                    data.add("\\");
                } else {
                    data.add("");
                }
            }
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
        Intent intent = new Intent(Third.this, MainActivity.class);
        intent.putExtra("data",data);
        intent.putExtra("maxim",maxim);
        startActivity(intent);
    }

    //Distance Sensor

    int getDistance(EV3.Api api) throws IOException,ExecutionException, InterruptedException {
        final UltrasonicSensor ultraSensor = api.getUltrasonicSensor(EV3.InputPort._1);
        Log.i("",ultraSensor.getDistance().get().toString());
        distance = Math.round(ultraSensor.getDistance().get());
        return Math.round(ultraSensor.getDistance().get());
    }

    //Robot Main TODO: la sfarsit nu se opreste contiuna sa se invarta motoarele ?!? ahaahhah asa si in first
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
            for (int line = xCurrentPosition; line >= 0; line--) {
                while (checkLine(xCurrentPosition) != true) {
                    distance = ultraSensor.getDistance().get();
                    if(distance >= 15 && distance <=40)
                    {
                        ballIsCatched = true;
                    }
                    markZone(xCurrentPosition, yCurrentPosition);
                    goForward(api);
                    if(ballIsCatched) {
                        goToSafeZone(api);
                        line = xCurrentPosition;
                        markZone(xCurrentPosition,yCurrentPosition);
                    }
                    markZone(xCurrentPosition, yCurrentPosition);
                }
                while(yCurrentPosition!=0) {
                    goBack(api);
                    updateMap(xCurrentPosition,yCurrentPosition);
                }
                if(xCurrentPosition==0) {
                    break;
                }
                goLeft(api, 1);
                updateMap(xCurrentPosition,yCurrentPosition);
            }
            turnFront();
            goRight(api,xRobotValue);
            turnFront();
            updateMap(xCurrentPosition,yCurrentPosition);
            for (int line = xCurrentPosition; line <= n; line++) {
                while (checkLine(xCurrentPosition) != true) {
                    distance = ultraSensor.getDistance().get();
                    if(distance >= 15 && distance <=40)
                    {
                        ballIsCatched = true;
                    }
                    markZone(xCurrentPosition, yCurrentPosition);
                    goForward(api);
                    if(ballIsCatched) {
                        goToSafeZone(api);
                        line = xCurrentPosition;
                        markZone(xCurrentPosition,yCurrentPosition);

                    }
                    markZone(xCurrentPosition, yCurrentPosition);
                }
                while(yCurrentPosition!=0) {
                    goBack(api);
                    markZone(xCurrentPosition,yCurrentPosition);
                }
                if(xCurrentPosition==n) {
                    break;
                }
                goRight(api, 1);
                markZone(xCurrentPosition,yCurrentPosition);
            }
            goLeft(api,xCurrentPosition-xRobotValue);
            turnFront();
            ball_catched++;
        }
        Collections.sort(coordinates, (p1, p2) -> {
            if (p1.first != p2.first) {
                return p1.first - p2.first;
            } else {
                return p1.second - p2.second;
            }
        });
        Collections.sort(coordinates, (p1, p2) -> {
            if (p1.second > p2.second) {
                return p2.second - p1.second;
            } else {
                return p1.first - p2.first;
            }
        });
        showFinal();
    }

    private static class MyCustomApi extends EV3.Api {

        private MyCustomApi(@androidx.annotation.NonNull GenEV3<? extends EV3.Api> ev3) {
            super(ev3);
        }

        public void mySpecialCommand() {}
    }

    private void legoMainCustomApi(Third.MyCustomApi api) throws InterruptedException, ExecutionException, IOException {
        final String TAG = Prelude.ReTAG("legoMainCustomApi");
        api.mySpecialCommand();
        EditText rows = findViewById(R.id.rows);
        EditText columns = findViewById(R.id.columns);
        EditText robotId = findViewById(R.id.idoftherobot);
        EditText password = findViewById(R.id.password);
        EditText robotXCoordinate = findViewById(R.id.xRobot);
        EditText robotYCoordinate = findViewById(R.id.yRobot);
        robotID=Integer.valueOf(robotId.getText().toString());
        n = Integer.valueOf(rows.getText().toString());
        m = Integer.valueOf(columns.getText().toString());
        xRobotValue = Integer.valueOf(robotXCoordinate.getText().toString());
        yRobotValue = Integer.valueOf(robotYCoordinate.getText().toString());
        KEY = password.getText().toString();
        orientation = yRobotValue;
        yRobotValue = 0;
        xCurrentPosition = xRobotValue;
        yCurrentPosition = yRobotValue;
        if(orientation == 1 || orientation == 3) {
            int aux = m;
            m = n;
            n = aux;
        }
        matrix = constructMatrix(m,n);
        legoMain(api);

    }
}

