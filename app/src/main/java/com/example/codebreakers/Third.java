package com.example.codebreakers;


import android.Manifest;
import android.animation.Animator;
import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.Pair;
import android.view.Gravity;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.Strategy;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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
import it.unive.dais.legodroid.lib.plugs.GyroSensor;
import it.unive.dais.legodroid.lib.plugs.Motors;
import it.unive.dais.legodroid.lib.plugs.TachoMotor;
import it.unive.dais.legodroid.lib.plugs.UltrasonicSensor;
import it.unive.dais.legodroid.lib.util.Prelude;

import static java.lang.Math.abs;
import static java.lang.Thread.sleep;

/**
 * Our GroundStation Activity. This Activity has 4 {@link State}s.
 *
 * <p>{@link State#UNKNOWN}: We cannot do anything while we're in this state. The app is likely in
 * the background.
 *
 * <p>{@link State#DISCOVERING}: Our default state (after we've connected). We constantly listen for
 * a device to advertise near us.
 *
 * <p>{@link State#ADVERTISING}: If a user shakes their device, they enter this state. We advertise
 * our device so that others nearby can discover us.
 *
 * <p>{@link State#CONNECTED}: We've connected to another device. We can now talk to them by holding
 * down the volume keys and speaking into the phone. We'll continue to advertise (if we were already
 * advertising) so that more people can connect to us.
 */
public class Third extends ConnectionsActivity {//implements SensorEventListener {

    private int robotID ;

    private Set<String> otherRobots = new HashSet<>();

    ArrayList<Pair<Integer,Integer>> coordinates = new ArrayList<>();

    /**
     * If true, debug logs are shown on the device.
     */
    private static final boolean DEBUG = true;

    /**
     * The connection strategy we'll use for Nearby Connections. In this case, we've decided on
     * P2P_STAR, which is a combination of Bluetooth Classic and WiFi Hotspots.
     */
    private static final Strategy STRATEGY = Strategy.P2P_STAR;

    /**
     * Advertise for 30 seconds before going back to discovering. If a client connects, we'll continue
     * to advertise indefinitely so others can still connect.
     */
    private static final long ADVERTISING_DURATION = 30000;

    /**
     * Length of state change animations.
     */
    private static final long ANIMATION_DURATION = 600;

    /**
     * This service id lets us find other nearby devices that are interested in the same thing. Our
     * sample does exactly one thing, so we hardcode the ID.
     */
    private static final String SERVICE_ID =
            "it.unive.dais.nearby.apps.SERVICE_ID";

    /**
     * The state of the app. As the app changes states, the UI will update and advertising/discovery
     * will start/stop.
     */
    private State mState = State.UNKNOWN;

    /**
     * A random UID used as this device's endpoint name.
     */
    private String mName;

    /**
     * A running log of debug messages. Only visible when DEBUG=true.
     */

    private String KEY = "abcdefgh";
    PopupWindow popupWindow;

    /**
     * Array of mStop agents
     */
    private boolean[] mStop;


    /**
     * A Handler that allows us to post back on to the UI thread. We use this to resume discovery
     * after an uneventful bout of advertising.
     */
    private final Handler mUiHandler = new Handler(Looper.getMainLooper());

    /**
     * Starts discovery. Used in a postDelayed manor with {@link #mUiHandler}.
     */
    private final Runnable mDiscoverRunnable =
            new Runnable() {
                @Override
                public void run() {
                    setState(State.DISCOVERING);
                }
            };
    private static GridView list;
    private static final int CAMERA_PERMISSION_CODE=100;
    private static final String TAG = Prelude.ReTAG("MainActivity");
    private CameraBridgeViewBase mOpenCvCameraView;
    private ColorBlobDetector  mDetector;
    private TextView textView;
    private Mat mRgba, mRgbaF, mRgbaT;
    private Scalar mBlobColorHsv;
    private Scalar CONTOUR_COLOR;
    private Scalar MARKER_COLOR;
    private Point org;
    private final Map<String, Object> statusMap = new HashMap<>();
    private static TachoMotor motorLeft;
    private static TachoMotor motorRight;
    private static TachoMotor motorClaws;
    private static Motors motoare;
    private int[][] matrix;
    private Integer n;
    private Integer m;
    private Integer totalBalls;
    private Integer ball_catched = 0;
    private Integer xRobotValue;
    private Integer yRobotValue;
    private Integer xCurrentPosition;
    private Integer yCurrentPosition;
    private boolean ballIsCatched = false;
    Point center;
    GridViewCustomAdapter adapter;
    float distance; //distance between sensor and ball
    boolean flag = true;

    private void setUpCamera() {
        // Carica le librerie di OpenCV in maniera sincrona
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "Unable to load OpenCV");
        } else {
            Log.d(TAG, "OpenCV loaded");
        }

        // Configura l'elemento della camera
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

            // Viene eseguito ad ogni frame, con inputFrame l'immagine corrente
            @Override
            public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
                // Salva il frame corrente su un oggetto Mat, ossia una matrice bitmap
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


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_third);
        /* Comment here to generate a random name for the GroundStation */
        //mName = generateRandomName();
        mName = "CodeBreakers";
        mStop = new boolean[6];
        // all the robot are assumed to be in move
        Arrays.fill(mStop, true);
        Button start = findViewById(R.id.Start);
        LinearLayout matrixView = findViewById(R.id.matrix);
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
            GenEV3<Third.MyCustomApi> ev3 = new GenEV3<>(conn);

            Button startButton = findViewById(R.id.Start);
            startButton.setOnClickListener(v -> Prelude.trap(() -> ev3.run(this::legoMainCustomApi, Third.MyCustomApi::new)));
        } catch (IOException e) {
            Log.e(TAG, "fatal error: cannot connect to EV3");
            e.printStackTrace();
        }
    }

    private static String generateRandomName() {
        String name = "";
        Random random = new Random();
        for (int i = 0; i < 5; i++) {
            name += random.nextInt(10);
        }
        return name;
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Swap the two functions below if you want to start on Discovering rather than Advertising.
        setState(State.DISCOVERING);
        //setState(State.ADVERTISING);
    }

    @Override
    protected void onStop() {

        setState(State.UNKNOWN);

        mUiHandler.removeCallbacksAndMessages(null);

        super.onStop();
    }

    @Override
    public void onBackPressed() {
        if (getState() == State.CONNECTED || getState() == State.ADVERTISING) {
            setState(State.DISCOVERING);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onEndpointDiscovered(Endpoint endpoint) {
        // We found an advertiser!
        if (!isConnecting()) {
            connectToEndpoint(endpoint);
        }
    }

    @Override
    protected void onConnectionInitiated(Endpoint endpoint, ConnectionInfo connectionInfo) {
        // A connection to another device has been initiated! We'll accept the connection immediately.
        acceptConnection(endpoint);
    }

    @Override
    protected void onEndpointConnected(Endpoint endpoint) {
        String x = "Benvenuto sono "+mName;
        byte[] bytes;
        Toast.makeText(
                this, getString(R.string.toast_connected, endpoint.getName()), Toast.LENGTH_SHORT)
                .show();
        setState(State.CONNECTED);
        bytes = x.getBytes();
        send(Payload.fromBytes(bytes));
    }

    @Override
    protected void onEndpointDisconnected(Endpoint endpoint) {
        Toast.makeText(
                this, getString(R.string.toast_disconnected, endpoint.getName()), Toast.LENGTH_SHORT)
                .show();

        // If we lost all our endpoints, then we should reset the state of our app and go back
        // to our initial state (discovering).
        if (getConnectedEndpoints().isEmpty()) {
            setState(State.DISCOVERING);
        }
    }

    @Override
    protected void onConnectionFailed(Endpoint endpoint) {
        // Let's try someone else.
        if (getState() == State.DISCOVERING && !getDiscoveredEndpoints().isEmpty()) {
            connectToEndpoint(pickRandomElem(getDiscoveredEndpoints()));
        }
    }

    /**
     * The state has changed. I wonder what we'll be doing now.
     *
     * @param state The new state.
     */
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

    /**
     * @return The current state.
     */
    private State getState() {
        return mState;
    }

    /**
     * State has changed.
     *
     * @param oldState The previous state we were in. Clean up anything related to this state.
     * @param newState The new state we're now in. Prepare the UI for this state.
     */
    private void onStateChanged(State oldState, State newState) {

        // Update Nearby Connections to the new state.
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
                    // Continue to advertise, so others can still connect,
                    // but clear the discover runnable.
                    removeCallbacks(mDiscoverRunnable);
                }
                break;
            case UNKNOWN:
                stopAllEndpoints();
                break;
            default:
                // no-op
                break;
        }

        // Update the UI.
        switch (oldState) {
            case UNKNOWN:
                // Unknown is our initial state. Whatever state we move to,
                // we're transitioning forwards.

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

    public void start_advertise(View view) {

        setState(State.ADVERTISING);
        postDelayed(mDiscoverRunnable, ADVERTISING_DURATION);
        Toast toast = Toast.makeText(this, "Starting Advertising", Toast.LENGTH_LONG);
        toast.show();
    }

    /**
     * Test function for all the possible strings of the protocol
     */
    public void send_Byte(View view) {

        // passive protocol
        String x = "Coordinate recupero:3;6;";
        byte[] bytes = x.getBytes();
        send(Payload.fromBytes(bytes));


        // test encrypted
        x = "Operazione in corso:4;8;";
        Calendar calendar = Calendar.getInstance();
        Long time_long = calendar.getTimeInMillis();
        x = x+time_long.toString()+";";

        bytes = x.getBytes();
        try {
            SecretKeySpec key = new SecretKeySpec(KEY.getBytes(), "DES");
            Cipher c = Cipher.getInstance("DES/ECB/ISO10126Padding");
            c.init(c.ENCRYPT_MODE, key);

            byte[] ciphertext = c.doFinal(bytes);
            send(Payload.fromBytes(ciphertext));

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        }

        x = "Benvenuto sono " + mName;
        bytes = x.getBytes();
        send(Payload.fromBytes(bytes));

        x = "0STOP";
        bytes = x.getBytes();
        send(Payload.fromBytes(bytes));

        x = "1STOP";
        bytes = x.getBytes();
        send(Payload.fromBytes(bytes));
    }
    //TODO: SENDUPDATE de inteles cum putem sa punem in otherRobots ceilalti.
    public void sendUpdateMessage(int xRobot, int yRobot, int xBall, int yBall, boolean status){
        String x,y;
        Calendar calendar = Calendar.getInstance();
        Long time_long = calendar.getTimeInMillis();

        //nu bine asa
        otherRobots.add("2");

        byte[] bytes;
        byte[] ballBytes;
        if(status){
            x = "Operazione in corso:" + xBall + ";" + yBall + ";[" + time_long.toString() + "];";
            bytes = x.getBytes();
            try {
                SecretKeySpec key = new SecretKeySpec(KEY.getBytes(), "DES");
                Cipher c = Cipher.getInstance("DES/ECB/ISO10126Padding");
                c.init(c.ENCRYPT_MODE, key);

                byte[] ciphertext = c.doFinal(bytes);
                send(Payload.fromBytes(ciphertext));
                System.out.println("CASO TRUE: Mando messaggio cifrato" + x);

            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (InvalidKeyException e) {
                e.printStackTrace();
            } catch (NoSuchPaddingException e) {
                e.printStackTrace();
            } catch (BadPaddingException e) {
                e.printStackTrace();
            } catch (IllegalBlockSizeException e) {
                e.printStackTrace();
            }
        } else {
            x = "Operazione completata" + xRobot + ";" + yRobot + ";[" + time_long.toString() + "];";
            y = "Coordinate recupero" + xBall + ";" + yBall;

            bytes = x.getBytes();
            ballBytes = y.getBytes();

            try {
                SecretKeySpec key = new SecretKeySpec(KEY.getBytes(), "DES");
                Cipher c = Cipher.getInstance("DES/ECB/ISO10126Padding");
                c.init(c.ENCRYPT_MODE, key);

                byte[] ciphertext = c.doFinal(bytes);

                System.out.println("ELSE : Mando messaggio cifrato" + x);
                System.out.println("ELSE : Mando messaggio plaintext" + y);

                send(Payload.fromBytes(ciphertext),otherRobots);
                send(Payload.fromBytes(ballBytes));

            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (InvalidKeyException e) {
                e.printStackTrace();
            } catch (NoSuchPaddingException e) {
                e.printStackTrace();
            } catch (BadPaddingException e) {
                e.printStackTrace();
            } catch (IllegalBlockSizeException e) {
                e.printStackTrace();
            }

        }
    }



    /**
     * Show on screen a Popup for modifying the secret key
     */
    public void edit_secret_key(View view) {
        //test sendUpdateMessage function
        sendUpdateMessage(5,5,5,5,false);
        //showPopup();
    }

    // move along.. this function is a mess
    public void showPopup() {

        // Container layout to hold other components
        LinearLayout llContainer = new LinearLayout(this);

        // Set its orientation to vertical to stack item
        llContainer.setOrientation(LinearLayout.VERTICAL);

        // Container layout to hold EditText and Button
        LinearLayout llContainerInline = new LinearLayout(this);

        // Set its orientation to horizontal to place components next to each other
        llContainerInline.setOrientation(LinearLayout.HORIZONTAL);

        // EditText to get input
        final EditText etInput = new EditText(this);

        // TextView to show an error message when the user does not provide input
        final TextView tvError = new TextView(this);

        // For when the user is done
        Button bDone = new Button(this);

        // If tvError is showing, make it disappear
        etInput.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                tvError.setVisibility(View.GONE);
            }
        });

        // This is what will show in etInput when the Popup is first created
        etInput.setHint("Insert new Password");
        etInput.setTextColor(Color.WHITE);
        // Input type allowed: Numbers
        //etInput.setRawInputType(Configuration.KEYBOARD_12KEY);

        // Center text inside EditText
        etInput.setGravity(Gravity.CENTER);

        // tvError should be invisible at first
        tvError.setVisibility(View.GONE);

        bDone.setText("Done");

        bDone.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                // If user didn't input anything, show tvError
                if (etInput.getText().toString().equals("")) {
                    //tvError.setText("Please enter a valid value");
                    tvError.setVisibility(View.VISIBLE);
                    etInput.setText("");

                    // else, call method `doneInput()` which we will define later
                } else {
                    doneInput(etInput.getText().toString());
                    popupWindow.dismiss();
                }
            }
        });

        // Define LayoutParams for tvError
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);

        layoutParams.topMargin = 20;

        // Define LayoutParams for InlineContainer
        LinearLayout.LayoutParams layoutParamsForInlineContainer = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);

        layoutParamsForInlineContainer.topMargin = 30;

        // Define LayoutParams for EditText
        LinearLayout.LayoutParams layoutParamsForInlineET = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);

        // Set ET's weight to 1 // Take as much space horizontally as possible
        layoutParamsForInlineET.weight = 1;

        // Define LayoutParams for Button
        LinearLayout.LayoutParams layoutParamsForInlineButton = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        // Set Button's weight to 0
        layoutParamsForInlineButton.weight = 0;

        // Add etInput to inline container
        llContainerInline.addView(etInput, layoutParamsForInlineET);

        // Add button with layoutParams // Order is important
        llContainerInline.addView(bDone, layoutParamsForInlineButton);

        // Add tvError with layoutParams
        llContainer.addView(tvError, layoutParams);

        // Finally add the inline container to llContainer
        llContainer.addView(llContainerInline, layoutParamsForInlineContainer);

        // Set gravity
        llContainer.setGravity(Gravity.CENTER);

        // Set any color to Container's background
        llContainer.setBackgroundColor(0x95000000);

        // Create PopupWindow
        popupWindow = new PopupWindow(llContainer,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);

        // Should be focusable
        popupWindow.setFocusable(true);

        // Show the popup window
        popupWindow.showAtLocation(llContainer, Gravity.CENTER, 0, 0);

    }

    // function called by the pop-up written above ... move along
    public void doneInput(String input) {
        KEY = input;
        ((TextView) findViewById(R.id.password)).setText(KEY);
        // Do anything else with input!
    }
    /**
     * Send coordinate for the second task. The function takes the values from 'test.csv' and
     * creates a thread.. therefore, the UI is not busy
     */
    public void send_coordinate(View view) {

        Thread_Coordinate task = new Thread_Coordinate(this);
        task.execute();
    }

    public class Thread_Coordinate extends AsyncTask<Void, Void, Void> {

        private Context mContext;
        private int i;

        public Thread_Coordinate (Context context){
            mContext = context;
            i = 0;
        }

        @Override
        protected void onPreExecute() {
            /*
             *    do things before doInBackground() code runs
             *    such as preparing and showing a Dialog or ProgressBar
             */
        }

        @Override
        protected void onProgressUpdate(Void... values) {
            /*
             *    updating data
             *    such a Dialog or ProgressBar
             */
            String str = "Sent coordinate number "+i;
            Toast.makeText(mContext,str, Toast.LENGTH_SHORT).show();

        }

        @Override
        protected Void doInBackground(Void... params) {
            BufferedReader br = null;
            AssetManager am = mContext.getAssets();
            String coordinate;
            try {
                String sCurrentLine;
                br = new BufferedReader(new InputStreamReader(am.open("test.csv")));
                while ((sCurrentLine = br.readLine()) != null) {
                    i++;
                    String[] mines = sCurrentLine.split(",");
                    coordinate = "Coordinate obiettivo:" + mines[1] + ";" + mines[2] + ";";
                    byte[] bytes = coordinate.getBytes();
                    send(Payload.fromBytes(bytes));
                    publishProgress();
                    SystemClock.sleep(4000);
                }
            } catch (IOException e) {
                e.printStackTrace();

            } finally {
                try {
                    if (br != null) br.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            Toast.makeText(mContext,"Coordinate sending completed", Toast.LENGTH_LONG).show();
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
                Pair<Integer,Integer> pair = new Pair<Integer, Integer>(x,y);
                coordinates.add(pair);
            }
        }
    }


    /** {@see ConnectionsActivity#onReceive(Endpoint, Payload)} */
    @Override
    protected void onReceive(Endpoint endpoint, Payload payload) {
        if (payload.getType() == Payload.Type.BYTES) {
            byte[] bytes = payload.asBytes();
            // comment this send if we are not the Groundstation anymore
            //send(payload);
            String str_bytes = new String(bytes);

            // those are needed if you are a robot!

            Integer aux = Character.getNumericValue(str_bytes.charAt(0));
            if((aux >= 0 && aux <=6) && ((str_bytes.charAt(1)=='S'))){
                if(aux == 0 || aux == robotID) {
                    if(str_bytes.contains("STOP")){
                        logD(String.format("STOP message intercepted %s", str_bytes));
                        //TODO: ONRECIVE motor stop, operazione annullata, coordinate e TIMESTAMP
                        flag = false;
                        //mai avem de trimis coordonatele
                        return;
                    }else if(str_bytes.contains("START")){
                        logD(String.format("RESUME message intercepted %s", str_bytes));
                        //TODO:ONRECIVE maybe this is the resume ? because on the pdf it says resume not start
                        flag = true;
                        return;
                    }
                }
                else {
                    logD(String.format("STOP/RESUME message ignored %s", str_bytes));
                    // altrimenti lo ignoriamo
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
                    //TODO: ONRECIVE nu stiu

                }else if(s.contains("annullata")){
                    logD(
                            String.format(
                                    "OPERAZIONE ANNULLATA received %s from endpoint %s",
                                    s, endpoint.getName()));
                    //TODO: ONRECIVE nus situ

                }if(s.contains("completata")){
                    logD(
                            String.format(
                                    "OPERAZIONE COMPLETATA received %s from endpoint %s",
                                    s, endpoint.getName()));
                    //TODO: ONRECIVE nu stiu

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
            str = n.toString()+"START";
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

    /** {@see ConnectionsActivity#getRequiredPermissions()} */
    @Override
    protected String[] getRequiredPermissions() {
        return join(
                super.getRequiredPermissions(),
                Manifest.permission.RECORD_AUDIO);
    }

    /** Joins 2 arrays together. */
    private static String[] join(String[] a, String... b) {
        String[] join = new String[a.length + b.length];
        System.arraycopy(a, 0, join, 0, a.length);
        System.arraycopy(b, 0, join, a.length, b.length);
        return join;
    }

    /**
     * Queries the phone's contacts for their own profile, and returns their name. Used when
     * connecting to another device.
     */
    @Override
    protected String getName() {
        return mName;
    }

    /** {@see ConnectionsActivity#getServiceId()} */
    @Override
    public String getServiceId() {
        return SERVICE_ID;
    }

    /** {@see ConnectionsActivity#getStrategy()} */
    @Override
    public Strategy getStrategy() {
        return STRATEGY;
    }

    /** {@see Handler#post()} */
    protected void post(Runnable r) {
        mUiHandler.post(r);
    }

    /** {@see Handler#postDelayed(Runnable, long)} */
    protected void postDelayed(Runnable r, long duration) {
        mUiHandler.postDelayed(r, duration);
    }

    /** {@see Handler#removeCallbacks(Runnable)} */
    protected void removeCallbacks(Runnable r) {
        mUiHandler.removeCallbacks(r);
    }

    private static CharSequence toColor(String msg, int color) {
        SpannableString spannable = new SpannableString(msg);
        spannable.setSpan(new ForegroundColorSpan(color), 0, msg.length(), 0);
        return spannable;
    }

    @SuppressWarnings("unchecked")
    private static <T> T pickRandomElem(Collection<T> collection) {
        return (T) collection.toArray()[new Random().nextInt(collection.size())];
    }

    /**
     * Provides an implementation of Animator.AnimatorListener so that we only have to override the
     * method(s) we're interested in.
     */

    private abstract static class AnimatorListener implements Animator.AnimatorListener {
        @Override
        public void onAnimationStart(Animator animator) {}

        @Override
        public void onAnimationEnd(Animator animator) {}

        @Override
        public void onAnimationCancel(Animator animator) {}

        @Override
        public void onAnimationRepeat(Animator animator) {}
    }

    /** States that the UI goes through. */
    public enum State {
        UNKNOWN,
        DISCOVERING,
        ADVERTISING,
        CONNECTED
    }

    //Robot Movement

    void goForward(EV3.Api api) throws IOException, InterruptedException{
        if(ballIsCatched == false) {
            yCurrentPosition++;
            int i = 1;
            while(i!=5) {
                while (flag == false) { sleep(100); }
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
                while (flag == false) { sleep(100); }
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

    }

    void goBack(EV3.Api api) throws  IOException, InterruptedException {
        if(ballIsCatched == false) {
            yCurrentPosition--;
            int i = 1;
            while(i!=5) {
                while (flag == false) { sleep(100); }
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
                while (flag == false) { sleep(100); }
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

    void goLeft(EV3.Api api, int times) throws  IOException, InterruptedException {
        xCurrentPosition--;
        turnLeft(api);
        int j = 1;
        while(j!=5) {
            while (flag == false) { sleep(100); }
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
                while (flag == false) { sleep(100); }
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

    void goRight(EV3.Api api, int times) throws  IOException, InterruptedException {
        xCurrentPosition++;
        turnRight(api);
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
        turnFront(api);
        markZone(xCurrentPosition,yCurrentPosition);

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

    void goToSafeZone(EV3.Api api) throws  IOException, InterruptedException {
        int xBall = xCurrentPosition;
        int yball = yCurrentPosition;
        markZone(xCurrentPosition,yCurrentPosition);
        catchBall();
        sendUpdateMessage(xCurrentPosition,yCurrentPosition,xBall,yball,true);
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
        sendUpdateMessage(xCurrentPosition,yCurrentPosition,xBall,yball,false);
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
        turnFront(api);
        i = 1;
        while(i!=2) {
            if(i%2==0) {
                turnFront(api);
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
                turnFront(api);
            }
            i++;
        }
        turnFront(api);
        ballIsCatched = false;
    }

    void stopMotors() throws IOException, InterruptedException {
        motorRight.stop();
        motorLeft.stop();
        motorClaws.stop();

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

    //Matrix and Display Map

    void updateMap(int x, int y) {
        ArrayList<String> data = new ArrayList<>();
        for (int i = 0; i <= n+1; i++) {
            for(int j=0;j <= m+1;j++)
                if(i==(n-y) && x==j){
                    data.add("O");
                } else if(i==n+1){
                    data.add("S");
                } else if(j>=m+1) {
                    data.add("\\");
                }
                else{
                    data.add("");
                }

        }
        adapter = new GridViewCustomAdapter(this, data);
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                list.setNumColumns(n+2);
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

    //Distance Sensor

    int getDistance(EV3.Api api) throws IOException,ExecutionException, InterruptedException {
        final UltrasonicSensor ultraSensor = api.getUltrasonicSensor(EV3.InputPort._1);
        Log.i("",ultraSensor.getDistance().get().toString());
        distance = Math.round(ultraSensor.getDistance().get());
        return Math.round(ultraSensor.getDistance().get());
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
            turnFront(api);
            goRight(api,xRobotValue);
            turnFront(api);
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
            turnFront(api);
            ball_catched++;
        }
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
        n = 2;
        m = 2;
        robotID = 2;
    //    robotID=Integer.valueOf(robotId.getText().toString());
//        n = Integer.valueOf(rows.getText().toString());
//        m = Integer.valueOf(columns.getText().toString());
        EditText robotXCoordinate = findViewById(R.id.xRobot);
        EditText robotYCoordinate = findViewById(R.id.yRobot);
//        xRobotValue = Integer.valueOf(robotXCoordinate.getText().toString());
//        yRobotValue = Integer.valueOf(robotYCoordinate.getText().toString());
        xRobotValue = 0;
        yRobotValue = 0;
        xCurrentPosition = xRobotValue;
        yCurrentPosition = yRobotValue;
        matrix = constructMatrix(m,n);

        EditText numberOfBalls  = findViewById(R.id.balls);
//        totalBalls = Integer.valueOf(numberOfBalls.getText().toString());
        totalBalls = 1;
        legoMain(api);

    }
}

