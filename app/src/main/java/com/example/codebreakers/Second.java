package com.example.codebreakers;


import android.Manifest;
import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.AssetManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.SpannableString;
import android.text.format.DateFormat;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.Pair;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewAnimationUtils;
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
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
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
import java.util.List;
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

import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import it.unive.dais.legodroid.lib.EV3;
import it.unive.dais.legodroid.lib.GenEV3;
import it.unive.dais.legodroid.lib.comm.BluetoothConnection;
import it.unive.dais.legodroid.lib.plugs.GyroSensor;
import it.unive.dais.legodroid.lib.plugs.Motors;
import it.unive.dais.legodroid.lib.plugs.TachoMotor;
import it.unive.dais.legodroid.lib.plugs.UltrasonicSensor;
import it.unive.dais.legodroid.lib.util.Prelude;

import static java.lang.Math.abs;

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
public class Second extends ConnectionsActivity {//implements SensorEventListener {

    private int robotID = 2;

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
     * Displays the previous state during animation transitions.
     */
    private TextView mPreviousStateView;

    /**
     * Displays the current state.
     */
    private TextView mCurrentStateView;

    /**
     * An animator that controls the animation from previous state to current state.
     */
    @Nullable
    private Animator mCurrentAnimator;

    /**
     * A running log of debug messages. Only visible when DEBUG=true.
     */
    private TextView mDebugLogView;

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
        setContentView(R.layout.activity_second);
        /* Comment here to generate a random name for the GroundStation */
        //mName = generateRandomName();
        mName = "CodeBreakers";
        mStop = new boolean[6];
        // all the robot are assumed to be in move
        Arrays.fill(mStop, true);

        Button start = findViewById(R.id.Start);
        LinearLayout matrixView = findViewById(R.id.matrix);
        mOpenCvCameraView = findViewById(R.id.HelloOpenCvView);
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
            GenEV3<Second.MyCustomApi> ev3 = new GenEV3<>(conn);

            Button startButton = findViewById(R.id.Start);
            startButton.setOnClickListener(v -> Prelude.trap(() -> ev3.run(this::legoMainCustomApi, Second.MyCustomApi::new)));
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

        if (mCurrentAnimator != null && mCurrentAnimator.isRunning()) {
            mCurrentAnimator.cancel();
        }

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
        Toast.makeText(
                this, getString(R.string.toast_connected, endpoint.getName()), Toast.LENGTH_SHORT)
                .show();
        setState(State.CONNECTED);
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
        if (mCurrentAnimator != null && mCurrentAnimator.isRunning()) {
            mCurrentAnimator.cancel();
        }

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

    @NonNull
    private Animator createAnimator(boolean reverse) {
        Animator animator;
        if (Build.VERSION.SDK_INT >= 21) {
            int cx = mCurrentStateView.getMeasuredWidth() / 2;
            int cy = mCurrentStateView.getMeasuredHeight() / 2;
            int initialRadius = 0;
            int finalRadius = Math.max(mCurrentStateView.getWidth(), mCurrentStateView.getHeight());
            if (reverse) {
                int temp = initialRadius;
                initialRadius = finalRadius;
                finalRadius = temp;
            }
            animator =
                    ViewAnimationUtils.createCircularReveal(
                            mCurrentStateView, cx, cy, initialRadius, finalRadius);
        } else {
            float initialAlpha = 0f;
            float finalAlpha = 1f;
            if (reverse) {
                float temp = initialAlpha;
                initialAlpha = finalAlpha;
                finalAlpha = temp;
            }
            mCurrentStateView.setAlpha(initialAlpha);
            animator = ObjectAnimator.ofFloat(mCurrentStateView, "alpha", finalAlpha);
        }
        animator.addListener(
                new AnimatorListener() {
                    @Override
                    public void onAnimationCancel(Animator animator) {
                        mPreviousStateView.setVisibility(View.GONE);
                        mCurrentStateView.setAlpha(1);
                    }

                    @Override
                    public void onAnimationEnd(Animator animator) {
                        mPreviousStateView.setVisibility(View.GONE);
                        mCurrentStateView.setAlpha(1);
                    }
                });
        animator.setDuration(ANIMATION_DURATION);
        return animator;
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

    public void sendUpdateMessage(int xRobot, int yRobot, int xBall, int yBall, boolean status){
        String x,y;
        Calendar calendar = Calendar.getInstance();
        Long time_long = calendar.getTimeInMillis();
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
                send(Payload.fromBytes(ciphertext),otherRobots);
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

    //The onRecive of the second round
    @Override
    protected void onReceive(Endpoint endpoint, Payload payload){
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


    private void appendToLogs(CharSequence msg) {
        mDebugLogView.append("\n");
        mDebugLogView.append(DateFormat.format("hh:mm", System.currentTimeMillis()) + ": ");
        mDebugLogView.append(msg);
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

    void catchBall() throws IOException {
        motorClaws.setStepSpeed(50,0,850,0,true);
        motorClaws.waitCompletion();
        motorClaws.stop();
    }

    void releaseBall() throws IOException {
        motorClaws.setStepSpeed(-50,0,850,0,true);
        motorClaws.waitCompletion();
    }

    void goForward(EV3.Api api) throws  IOException {
        yCurrentPosition++;
        int i = 1;
        while(i!=5) {
            turnFrontOneMotorDown(api);
            if(i%2==0) {
                motorLeft.setStepSpeed(50, 0, 175, 0, true);
                motorRight.setStepSpeed(50, 0, 175, 0, true );
                motorLeft.waitCompletion();
                motorRight.waitCompletion();
            }
            else {
                motorRight.setStepSpeed(50, 0, 171, 0, true);
                motorLeft.setStepSpeed(50, 0, 171, 0, true);
                motorRight.waitCompletion();
                motorLeft.waitCompletion();
            }
            turnFrontOneMotorDown(api);
            i++;
        }
        motorLeft.setSpeed(0);
        motorRight.setSpeed(0);
    }

    void goBack(EV3.Api api) throws  IOException {
        yCurrentPosition--;
        int i = 1;
        while(i!=5) {
            turnFrontOneMotorDown(api);
            if(i%2==0) {
                motorLeft.setStepSpeed(-50, 0, 175, 0, true);
                motorRight.setStepSpeed(-50, 0, 175, 0, true );
                motorLeft.waitCompletion();
                motorRight.waitCompletion();
            }
            else {
                motorRight.setStepSpeed(-50, 0, 171, 0, true);
                motorLeft.setStepSpeed(-50, 0, 171, 0, true);
                motorRight.waitCompletion();
                motorLeft.waitCompletion();
            }
            turnFrontOneMotorDown(api);
            i++;
        }
        motorLeft.setSpeed(0);
        motorRight.setSpeed(0);

    }

    void goLeft(EV3.Api api, int times) throws  IOException {
        xCurrentPosition--;
        turnLeft(api);
        for(int time=1;time<=times;time++) {
            int i = 1;
            while(i!=5) {
                turnLeft(api);
                if(i%2==0) {
                    motorLeft.setStepSpeed(50, 0, 175, 0, true);
                    motorRight.setStepSpeed(50, 0, 175, 0, true );
                    motorLeft.waitCompletion();
                    motorRight.waitCompletion();
                }
                else {
                    motorRight.setStepSpeed(50, 0, 171, 0, true);
                    motorLeft.setStepSpeed(50, 0, 171, 0, true);
                    motorRight.waitCompletion();
                    motorLeft.waitCompletion();
                }
                turnLeft(api);
                i++;
            }
            motorLeft.setSpeed(0);
            motorRight.setSpeed(0);
            turnFrontOneMotorDown(api);
        }
        markZone(xCurrentPosition,yCurrentPosition);
    }

    void turnLeft(EV3.Api api) {
        int speed = 5;
        final GyroSensor gyroSensor = api.getGyroSensor(EV3.InputPort._4);
        try {
            float current_angle = gyroSensor.getAngle().get();
            while (current_angle >= -88){
                motorLeft.setSpeed(-speed);
                motorRight.setSpeed(speed);
                if(current_angle < -65) {
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

    void turnRight(EV3.Api api) {
        int speed = 5;
        final GyroSensor gyroSensor = api.getGyroSensor(EV3.InputPort._4);
        try {
            float current_angle = gyroSensor.getAngle().get();
            while (current_angle <= 87){
                motorLeft.setSpeed(speed);
                motorRight.setSpeed(-speed);
                if(current_angle > 65) {
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

    void goRight(EV3.Api api, int times) throws  IOException {
        xCurrentPosition++;
        turnRight(api);
        for(int time=1;time<=times;time++) {
            int i = 1;
            while(i!=5) {
                if(i%2==0) {
                    motorLeft.setStepSpeed(50, 0, 150, 0, true);
                    motorRight.setStepSpeed(50, 0, 150, 0, true );
                    motorLeft.waitCompletion();
                    motorRight.waitCompletion();
                }
                else {
                    motorRight.setStepSpeed(50, 0, 150, 0, true);
                    motorLeft.setStepSpeed(50, 0, 150, 0, true);
                    motorRight.waitCompletion();
                    motorLeft.waitCompletion();
                }
                i++;
            }
            motorLeft.setSpeed(0);
            motorRight.setSpeed(0);
        }
        markZone(xCurrentPosition,yCurrentPosition);

    }

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

    void turnFrontOneMotorDown(EV3.Api api) {
        if(ballIsCatched == false) {
            int speed = 1;
            final GyroSensor gyroSensor = api.getGyroSensor(EV3.InputPort._4);
            try {
                float current_angle = gyroSensor.getAngle().get();
                while ( abs(current_angle) > 1 )  {
                    if (current_angle > 1) {
                        motorLeft.setSpeed(0);
                        motorRight.setSpeed(speed);
                        motorLeft.start();
                        motorRight.start();
                        Log.i("gyrosensor", gyroSensor.getAngle().get().toString());
                        current_angle = gyroSensor.getAngle().get();
                    } else if (current_angle < 1 ) {
                        motorLeft.setSpeed(speed);
                        motorRight.setSpeed(0);
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
    }


    void turn180OneMotorDown(EV3.Api api) {
        int speed = 5;
        final GyroSensor gyroSensor = api.getGyroSensor(EV3.InputPort._4);
        try {
            float current_angle = gyroSensor.getAngle().get();
            while (current_angle > -180){
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

    void stopMotors() throws IOException {
        motorRight.stop();
        motorLeft.stop();
        motorClaws.stop();
    }

    void goToSafeZone(EV3.Api api) throws  IOException {
        catchBall();
        while (yCurrentPosition > 0) {
            goBack(api);
            markZone(xCurrentPosition,yCurrentPosition);
        }
        while (xCurrentPosition>xRobotValue) {
            goLeft(api, xCurrentPosition-xRobotValue);
            markZone(xCurrentPosition,yCurrentPosition);
        }
        while (xCurrentPosition<xRobotValue) {
            goRight(api,xRobotValue-xCurrentPosition);
        }
        turn180OneMotorDown(api);
        releaseBall();
        int i = 1;
        while(i!=2) {
            if(i%2==0) {
                motorLeft.setStepSpeed(-50, 0, 175, 0, true);
                motorRight.setStepSpeed(-50, 0, 175, 0, true );
                motorLeft.waitCompletion();
                motorRight.waitCompletion();
            }
            else {
                motorRight.setStepSpeed(-50, 0, 171, 0, true);
                motorLeft.setStepSpeed(-50, 0, 171, 0, true);
                motorRight.waitCompletion();
                motorLeft.waitCompletion();
            }
            i++;
        }
        turnFrontOneMotorDown(api);
        i = 1;
        while(i!=2) {
            if(i%2==0) {
                motorLeft.setStepSpeed(-50, 0, 175, 0, true);
                motorRight.setStepSpeed(-50, 0, 175, 0, true );
                motorLeft.waitCompletion();
                motorRight.waitCompletion();
            }
            else {
                motorRight.setStepSpeed(-50, 0, 171, 0, true);
                motorLeft.setStepSpeed(-50, 0, 171, 0, true);
                motorRight.waitCompletion();
                motorLeft.waitCompletion();
            }
            i++;
        }
    }

    int[][] constructMatrix(int n, int m) {
        int [][] matrix = new int[n+1][m+1];
        for(int i=0;i<=n;i++)
            for(int j=0;j<=m;j++)
                matrix[i][j] = 0;
        return matrix;
    }

    boolean checkLine(int x) {
        for(int y=0;y<=m;y++)
            if(matrix[y][x]==0)
                return false;
        return true;
    }

    void markZone(int x,int y) {
        matrix[y][x] = 1;
        updateMap(xCurrentPosition,yCurrentPosition);
    }

    int getDistance(EV3.Api api) throws IOException,ExecutionException, InterruptedException {
        final UltrasonicSensor ultraSensor = api.getUltrasonicSensor(EV3.InputPort._2);
        return Math.round(ultraSensor.getDistance().get());
    }

    void goToBall(EV3.Api api, int x, int y) throws IOException {
        while(yCurrentPosition!=y) {
            markZone(xCurrentPosition,yCurrentPosition);
            goForward(api);
            markZone(xCurrentPosition,yCurrentPosition);
        }
        if(xCurrentPosition>x) {
            markZone(xCurrentPosition,yCurrentPosition);
            goLeft(api,xCurrentPosition-x);
            markZone(xCurrentPosition,yCurrentPosition);
        }
        if(xCurrentPosition<x) {
            markZone(xCurrentPosition,yCurrentPosition);
            goRight(api,x-xCurrentPosition);
            markZone(xCurrentPosition,yCurrentPosition);
        }


    }
    private void legoMain(EV3.Api api) throws  IOException, InterruptedException, ExecutionException {
        final String TAG = Prelude.ReTAG("legoMain");

        motorLeft = api.getTachoMotor(EV3.OutputPort.A);
        motorRight = api.getTachoMotor(EV3.OutputPort.D);
        motorClaws = api.getTachoMotor(EV3.OutputPort.B);
        setUpCamera();
        ball_catched = 0;
        markZone(xCurrentPosition,yCurrentPosition);
        for(int i=0;i<coordinates.size();i++) {
            goToBall(api,coordinates.get(i).first,coordinates.get(i).second);
            goToSafeZone(api);
        }

        }


    private static class MyCustomApi extends EV3.Api {

        private MyCustomApi(@androidx.annotation.NonNull GenEV3<? extends EV3.Api> ev3) {
            super(ev3);
        }

        public void mySpecialCommand() {}
    }

    private void legoMainCustomApi(Second.MyCustomApi api) throws InterruptedException, ExecutionException, IOException {
        final String TAG = Prelude.ReTAG("legoMainCustomApi");
        api.mySpecialCommand();
        EditText rows = findViewById(R.id.rows);
        EditText columns = findViewById(R.id.columns);
        n = 3;
        m = 3;
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
        matrix = constructMatrix(n,m);

        EditText numberOfBalls  = findViewById(R.id.balls);
//        totalBalls = Integer.valueOf(numberOfBalls.getText().toString());
        totalBalls = 1;
        legoMain(api);

    }
}

