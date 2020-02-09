package com.example.codebreakers;


import android.Manifest;
import android.animation.Animator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.Strategy;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.annotations.Nullable;
import it.unive.dais.legodroid.lib.EV3;
import it.unive.dais.legodroid.lib.GenEV3;
import it.unive.dais.legodroid.lib.comm.BluetoothConnection;
import it.unive.dais.legodroid.lib.plugs.GyroSensor;
import it.unive.dais.legodroid.lib.plugs.TachoMotor;
import it.unive.dais.legodroid.lib.util.Prelude;

import static java.lang.Math.abs;
import static java.lang.Math.max;

public class Second extends ConnectionsActivity {
    //Conectivity
    private static final Strategy STRATEGY = Strategy.P2P_STAR;
    private static final String SERVICE_ID = "it.unive.dais.nearby.apps.SERVICE_ID";
    private State mState = State.UNKNOWN;
    private String mName;
    private TextView mCurrentStateView;

    public enum State {
        UNKNOWN,
        DISCOVERING,
        ADVERTISING,
        CONNECTED
    }

    @Nullable
    private Animator mCurrentAnimator;
    private boolean[] mStop;
    private final Handler mUiHandler = new Handler(Looper.getMainLooper());
    private final Runnable mDiscoverRunnable = new Runnable() {
        @Override
        public void run() {
            setState(State.DISCOVERING);
        }
    };

    //Robot Values
    private Integer xRobotValue;
    private Integer yRobotValue;
    private Integer xCurrentPosition;
    private Integer yCurrentPosition;
    private Integer orientation;

    //Balls Values
    private boolean ballIsCatched = false;
    ArrayList<com.example.codebreakers.Pair<Integer, Integer>> coordinates = new ArrayList<>();
    ArrayList<com.example.codebreakers.Pair<Integer, Integer>> original_coordinates = new ArrayList<>();


    //Camera
    private static GridView list;
    private static final int CAMERA_PERMISSION_CODE = 100;
    private CameraBridgeViewBase mOpenCvCameraView;

    //Motors
    private static TachoMotor motorLeft;
    private static TachoMotor motorRight;
    private static TachoMotor motorClaws;


    //Map
    private Integer n;
    private Integer m;
    GridViewCustomAdapter adapter;

    private static final String TAG = Prelude.ReTAG("MainActivity");

    //Connectivity

    @Override protected void onStart() {
        super.onStart();
        setState(State.DISCOVERING);
    }

    @Override protected void onStop() {
        setState(State.UNKNOWN);
        mUiHandler.removeCallbacksAndMessages(null);
        if (mCurrentAnimator != null && mCurrentAnimator.isRunning()) {
            mCurrentAnimator.cancel();
        }
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
        // A connection to another device has been initiated! We'll accept the connection immediately.
        acceptConnection(endpoint);
    }

    @Override protected void onEndpointConnected(Endpoint endpoint) {
        String x = "Benvenuto sono " + mName;
        byte[] bytes;
        Toast.makeText(
                this, getString(R.string.toast_connected, endpoint.getName()), Toast.LENGTH_SHORT)
                .show();
        setState(Second.State.CONNECTED);
        bytes = x.getBytes();
        send(Payload.fromBytes(bytes));

        Log.i("BENVENUTO ", x);

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
        if (mCurrentAnimator != null && mCurrentAnimator.isRunning()) {
            mCurrentAnimator.cancel();
        }
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
                        // no-op
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

    @Override protected void onReceive(Endpoint endpoint, Payload payload) {
        Pattern p = Pattern.compile("-?\\d+");
        if (payload.getType() == Payload.Type.BYTES) {
            byte[] bytes = payload.asBytes();
            String str_bytes = new String(bytes);

            Matcher m = p.matcher(str_bytes);
            while (m.find()) {
                System.out.println(m.group());
                Integer x = Integer.valueOf(m.group());
                m.find();
                Integer y = Integer.valueOf(m.group());
                com.example.codebreakers.Pair<Integer, Integer> pair = new Pair<>(x, y);
                coordinates.add(pair);
            }
        }
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

    @Override public String getServiceId() {
        return SERVICE_ID;
    }

    @Override protected String getName() {
        return mName;
    }

    @Override public Strategy getStrategy() {
        return STRATEGY;
    }

    protected void removeCallbacks(Runnable r) {
        mUiHandler.removeCallbacks(r);
    }

    private static <T> T pickRandomElem(Collection<T> collection) {
        return (T) collection.toArray()[new Random().nextInt(collection.size())];
    }

    //On Create

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);
        mName = "CodeBreakers";
        mStop = new boolean[6];
        Arrays.fill(mStop, true);

        mOpenCvCameraView = findViewById(R.id.HelloOpenCvView);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setMaxFrameSize(640, 480);
        mOpenCvCameraView.disableFpsMeter();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        list = findViewById(R.id.grid_view);
        list.setNumColumns(4);
        ArrayList<String> data = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++)
                data.add("");
        }
        adapter = new GridViewCustomAdapter(this, data);
        list.setAdapter(adapter);
        try {
            BluetoothConnection.BluetoothChannel conn = new BluetoothConnection("Willy").connect();
            GenEV3<Second.MyCustomApi> ev3 = new GenEV3<>(conn);

            Button startButton = findViewById(R.id.Start);
            startButton.setOnClickListener(v -> Prelude.trap(() -> ev3.run(this::legoMainCustomApi, Second.MyCustomApi::new)));
        } catch (IOException e) {
            Log.e(TAG, "fatal error: cannot connect to EV3");
            e.printStackTrace();
        }
    }

    //Robot Movement

    void goForward(EV3.Api api) throws IOException {
        if (ballIsCatched == false) {
            yCurrentPosition++;
            int i = 1;
            while (i != 5) {
                turnFront(api);
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
                turnFront(api);
                i++;
            }
            motorLeft.setSpeed(0);
            motorRight.setSpeed(0);
        } else {
            yCurrentPosition++;
            int i = 1;
            while (i != 5) {
                turnFront(api);
                if (i % 2 == 0) {
                    motorLeft.setStepSpeed(30, 0, 162, 0, true);
                    motorRight.setStepSpeed(30, 0, 162, 0, true);
                    motorLeft.waitCompletion();
                    motorRight.waitCompletion();
                } else {
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

    }

    void goBack(EV3.Api api) throws IOException {
        if (ballIsCatched == false) {
            yCurrentPosition--;
            int i = 1;
            while (i != 5) {
                turnFront(api);
                if (i % 2 == 0) {
                    motorLeft.setStepSpeed(-30, 0, 156, 0, true);
                    motorRight.setStepSpeed(-30, 0, 156, 0, true);
                    motorLeft.waitCompletion();
                    motorRight.waitCompletion();
                } else {
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
        } else {
            yCurrentPosition--;
            int i = 1;
            while (i != 5) {
                turnFront(api);
                if (i % 2 == 0) {
                    motorLeft.setStepSpeed(-27, 0, 162, 0, true);
                    motorRight.setStepSpeed(-27, 0, 162, 0, true);
                    motorLeft.waitCompletion();
                    motorRight.waitCompletion();
                } else {
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

    void goLeft(EV3.Api api, int times) throws IOException {
        xCurrentPosition--;
        turnLeft(api);
        int j = 1;
        while (j != 5) {
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
        updateMap(xCurrentPosition, yCurrentPosition);
        for (int time = 1; time < times; time++) {
            xCurrentPosition--;
            int i = 1;
            while (i != 5) {
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
            updateMap(xCurrentPosition, yCurrentPosition);
        }
        updateMap(xCurrentPosition, yCurrentPosition);

    }

    void goRight(EV3.Api api, int times) throws IOException {
        xCurrentPosition++;
        turnRight(api);
        int j = 1;
        while (j != 5) {
            turnRight(api);
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
            turnRight(api);
            j++;
        }
        for (int time = 1; time < times; time++) {
            xCurrentPosition++;
            int i = 1;
            while (i != 5) {
                turnRight(api);
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
                turnRight(api);
                i++;
            }
            motorLeft.setSpeed(0);
            motorRight.setSpeed(0);
            updateMap(xCurrentPosition, yCurrentPosition);
        }
        updateMap(xCurrentPosition, yCurrentPosition);

    }

    void catchBall() throws IOException {
        motorClaws.setStepSpeed(50, 0, 2200, 0, true);
        motorClaws.waitCompletion();
        motorClaws.stop();
    }

    void releaseBall() throws IOException {
        motorClaws.setStepSpeed(-50, 0, 2200, 0, true);
        motorClaws.waitCompletion();
    }

    void goToSafeZone(EV3.Api api) throws IOException {
        updateMap(xCurrentPosition, yCurrentPosition);
        catchBall();
        if (xCurrentPosition < xRobotValue) {
            while (yCurrentPosition > 0) {
                updateMap(xCurrentPosition, yCurrentPosition);
                goBack(api);
                updateMap(xCurrentPosition, yCurrentPosition);
            }
            while (xCurrentPosition != xRobotValue) {
                if (xCurrentPosition < xRobotValue) {
                    goRight(api, xRobotValue - xCurrentPosition);
                    updateMap(xCurrentPosition, yCurrentPosition);
                }
                if (xCurrentPosition > xRobotValue) {
                    goLeft(api, xCurrentPosition - xRobotValue);
                    updateMap(xCurrentPosition, yCurrentPosition);
                }
            }
        } else {
            while (xCurrentPosition != xRobotValue) {
                if (xCurrentPosition < xRobotValue) {
                    goRight(api, xRobotValue - xCurrentPosition);
                    updateMap(xCurrentPosition, yCurrentPosition);
                }
                if (xCurrentPosition > xRobotValue) {
                    goLeft(api, xCurrentPosition - xRobotValue);
                    updateMap(xCurrentPosition, yCurrentPosition);
                }
            }
            while (yCurrentPosition > 0) {
                updateMap(xCurrentPosition, yCurrentPosition);
                goBack(api);
                updateMap(xCurrentPosition, yCurrentPosition);
            }
        }
        turn180(api);
        releaseBall();
        int i = 1;
        while (i != 2) {
            if (i % 2 == 0) {
                turn180(api);
                motorLeft.setStepSpeed(-30, 0, 160, 0, true);
                motorRight.setStepSpeed(-30, 0, 160, 0, true);
                motorLeft.waitCompletion();
                motorRight.waitCompletion();
            } else {
                motorRight.setStepSpeed(-30, 0, 160, 0, true);
                motorLeft.setStepSpeed(-30, 0, 160, 0, true);
                motorRight.waitCompletion();
                motorLeft.waitCompletion();
                turn180(api);
            }
            i++;
        }
        turn180(api);
        turnFront(api);
        i = 1;
        while (i != 2) {
            if (i % 2 == 0) {
                turnFront(api);
                motorLeft.setStepSpeed(-30, 0, 160, 0, true);
                motorRight.setStepSpeed(-30, 0, 160, 0, true);
                motorLeft.waitCompletion();
                motorRight.waitCompletion();
            } else {
                motorRight.setStepSpeed(-30, 0, 160, 0, true);
                motorLeft.setStepSpeed(-30, 0, 160, 0, true);
                motorRight.waitCompletion();
                motorLeft.waitCompletion();
                turnFront(api);
            }
            i++;
        }
        turnFront(api);
        ballIsCatched = false;
    }

    void stopMotors() throws IOException {
        motorRight.stop();
        motorLeft.stop();
        motorClaws.stop();
    }

    void goToBall(EV3.Api api, int x, int y) throws IOException {
        if (xCurrentPosition > x) {
            updateMap(xCurrentPosition, yCurrentPosition);
            goLeft(api, xCurrentPosition - x);
            updateMap(xCurrentPosition, yCurrentPosition);
        }
        if (xCurrentPosition < x) {
            updateMap(xCurrentPosition, yCurrentPosition);
            goRight(api, x - xCurrentPosition);
            updateMap(xCurrentPosition, yCurrentPosition);
        }
        while (yCurrentPosition != y) {
            updateMap(xCurrentPosition, yCurrentPosition);
            goForward(api);
            updateMap(xCurrentPosition, yCurrentPosition);
        }


    }

    //Orientation Change Robot

    void turnLeft(EV3.Api api) {
        int speed = 1;
        final GyroSensor gyroSensor = api.getGyroSensor(EV3.InputPort._4);
        try {
            float current_angle = gyroSensor.getAngle().get() + 90;
            while (abs(current_angle) > 1) {
                if (current_angle > 1) {
                    if (current_angle > 30) {
                        motorLeft.setSpeed(-5);
                        motorRight.setSpeed(5);
                    } else {
                        motorLeft.setSpeed(-speed);
                        motorRight.setSpeed(speed);
                    }
                    motorLeft.start();
                    motorRight.start();
                    Log.i("gyrosensor", gyroSensor.getAngle().get().toString());
                    current_angle = gyroSensor.getAngle().get() + 90;
                } else if (current_angle < 1) {
                    if (current_angle > -30) {
                        motorLeft.setSpeed(speed);
                        motorRight.setSpeed(-speed);
                    } else {
                        motorLeft.setSpeed(5);
                        motorRight.setSpeed(-5);
                    }
                    motorLeft.start();
                    motorRight.start();
                    Log.i("gyrosensor", gyroSensor.getAngle().get().toString());
                    current_angle = gyroSensor.getAngle().get() + 90;
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
            float current_angle = gyroSensor.getAngle().get() - 90;
            while (abs(current_angle) > 1) {
                if (current_angle > 1) {
                    if (current_angle > 30) {
                        motorLeft.setSpeed(-5);
                        motorRight.setSpeed(5);
                    } else {
                        motorLeft.setSpeed(-speed);
                        motorRight.setSpeed(speed);
                    }
                    motorLeft.start();
                    motorRight.start();
                    Log.i("gyrosensor", gyroSensor.getAngle().get().toString());
                    current_angle = gyroSensor.getAngle().get() - 90;
                } else if (current_angle < 1) {
                    if (current_angle > -30) {
                        motorLeft.setSpeed(speed);
                        motorRight.setSpeed(-speed);
                    } else {
                        motorLeft.setSpeed(5);
                        motorRight.setSpeed(-5);
                    }
                    motorLeft.start();
                    motorRight.start();
                    Log.i("gyrosensor", gyroSensor.getAngle().get().toString());
                    current_angle = gyroSensor.getAngle().get() - 90;
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
            while (abs(current_angle) > 1) {
                if (current_angle > 1) {
                    if (current_angle > 30) {
                        motorLeft.setSpeed(-5);
                        motorRight.setSpeed(5);
                    } else {
                        motorLeft.setSpeed(-speed);
                        motorRight.setSpeed(speed);
                    }
                    motorLeft.start();
                    motorRight.start();
                    Log.i("gyrosensor", gyroSensor.getAngle().get().toString());
                    current_angle = gyroSensor.getAngle().get();
                } else if (current_angle < 1) {
                    if (current_angle > -30) {
                        motorLeft.setSpeed(speed);
                        motorRight.setSpeed(-speed);
                    } else {
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
            while (current_angle > -176) {
                motorLeft.setSpeed(-speed);
                motorRight.setSpeed(speed);
                if (current_angle < -150) {
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

    //Matrix and Display Map

    void updateMap(int x, int y) {
        int maxim = max(n, m);
        ArrayList<String> data = new ArrayList<>();
        for (int i = 0; i <= maxim + 1; i++)
            for (int j = 0; j <= maxim + 1; j++) {
                if (i == (m - y) && x == j) {
                    data.add("R");
                } else if (j > n) {
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
                int maxim = max(n, m);
                maxim += 2;
                list.setNumColumns(maxim);
                list.setAdapter(adapter);

            }
        });

    }

    void showFinal() {

        if (orientation == 1 || orientation == 3) {
            int aux = m;
            m = n;
            n = aux;
        }

        Collections.sort(original_coordinates, (p1, p2) -> {
            if (p1.b > p2.b) {
                return p2.b - p1.b;
            } else {
                return p1.a - p2.a;
            }
        });

        ArrayList<String> data = new ArrayList<>();
        int maxim = max(n, m);
        for (int i = 0; i <= maxim + 1; i++) {
            for (int j = 0; j <= maxim + 1; j++) {
                if (((m - original_coordinates.get(0).b) == i) && (original_coordinates.get(0).a == j)) {
                    data.add("O");
                    if (original_coordinates.size() > 1)
                        original_coordinates.remove(0);
                } else if (j > n) {
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
                int maxim = max(n, m);
                maxim += 2;
                list.setNumColumns(maxim);
                list.setAdapter(adapter);

            }
        });
        Intent intent = new Intent(Second.this, MainActivity.class);
        intent.putExtra("data", data);
        intent.putExtra("maxim", maxim);
        startActivity(intent);
    }

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
                ballFinder.setMinArea(1000);
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

        // Abilita la visualizzazione dell'immagine sullo schermo
        mOpenCvCameraView.enableView();
    }

    //Robot Main

    private void legoMain(EV3.Api api) throws IOException {
        final String TAG = Prelude.ReTAG("legoMain");

        motorLeft = api.getTachoMotor(EV3.OutputPort.A);
        motorRight = api.getTachoMotor(EV3.OutputPort.D);
        motorClaws = api.getTachoMotor(EV3.OutputPort.B);
        setUpCamera();
        updateMap(xCurrentPosition, yCurrentPosition);

        for (com.example.codebreakers.Pair pair : coordinates) {
            Pair<Integer, Integer> new_pair = new Pair<Integer, Integer>(0, 0);
            new_pair.a = (int) pair.a;
            new_pair.b = (int) pair.b;
            original_coordinates.add(new_pair);
        }

        Collections.sort(original_coordinates, (p1, p2) -> {
            if (p1.a != p2.a) {
                return p1.a - p2.a;
            } else {
                return p1.b - p2.b;
            }
        });

        for (com.example.codebreakers.Pair pair : coordinates) {
            if (orientation == 0)
                continue;
            else if (orientation == 1) {
                Integer aux_a = (Integer) pair.a;
                Integer aux_b = (Integer) pair.b;
                pair.a = n - aux_b;
                pair.b = aux_a;
            } else if (orientation == 2) {
                Integer aux_a = (Integer) pair.a;
                Integer aux_b = (Integer) pair.b;
                pair.a = n - aux_a;
                pair.b = m - aux_b;
            } else if (orientation == 3) {
                Integer aux_a = (Integer) pair.a;
                Integer aux_b = (Integer) pair.b;
                pair.a = aux_b;
                pair.b = m - aux_a;
            }
        }

        Collections.sort(coordinates, (p1, p2) -> {
            if (p1.a != p2.a) {
                return p1.a - p2.a;
            } else {
                return p1.b - p2.b;
            }
        });

        for (int i = 0; i < coordinates.size(); i++) {
            goToBall(api, coordinates.get(i).a, coordinates.get(i).b);
            goToSafeZone(api);
        }

        showFinal();
    }

    private static class MyCustomApi extends EV3.Api {

        private MyCustomApi(@androidx.annotation.NonNull GenEV3<? extends EV3.Api> ev3) {
            super(ev3);
        }

        public void mySpecialCommand() {
        }
    }

    private void legoMainCustomApi(Second.MyCustomApi api) throws IOException {
        final String TAG = Prelude.ReTAG("legoMainCustomApi");
        api.mySpecialCommand();
        EditText rows = findViewById(R.id.rows);
        EditText columns = findViewById(R.id.columns);
        n = Integer.valueOf(rows.getText().toString());
        m = Integer.valueOf(columns.getText().toString());
        EditText robotXCoordinate = findViewById(R.id.xRobot);
        EditText robotYCoordinate = findViewById(R.id.yRobot);
        xRobotValue = Integer.valueOf(robotXCoordinate.getText().toString());
        orientation = Integer.valueOf(robotYCoordinate.getText().toString());
        yRobotValue = 0;
        xCurrentPosition = xRobotValue;
        yCurrentPosition = yRobotValue;
        if (orientation == 1 || orientation == 3) {
            int aux = m;
            m = n;
            n = aux;
        }
        legoMain(api);

    }

}

