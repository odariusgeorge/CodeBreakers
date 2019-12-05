package com.example.codebreakers;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import it.unive.dais.legodroid.lib.EV3;
import it.unive.dais.legodroid.lib.GenEV3;
import it.unive.dais.legodroid.lib.comm.BluetoothConnection;
import it.unive.dais.legodroid.lib.plugs.LightSensor;
import it.unive.dais.legodroid.lib.plugs.Plug;
import it.unive.dais.legodroid.lib.plugs.TachoMotor;
import it.unive.dais.legodroid.lib.plugs.UltrasonicSensor;
import it.unive.dais.legodroid.lib.util.Prelude;
import it.unive.dais.legodroid.lib.util.ThrowingConsumer;

public class MainActivity extends AppCompatActivity {
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
    private int[][] matrix;
    private Integer n;
    private Integer m;
    private Integer xBallValue;
    private Integer yBallValue;
    private Integer xRobotValue;
    private Integer yRobotValue;

    private void updateStatus(@NonNull Plug p, String key, Object value) {
        Log.d(TAG, String.format("%s: %s: %s", p, key, value));
        statusMap.put(key, value);
        runOnUiThread(() -> textView.setText(statusMap.toString()));
    }
    private static class MyCustomApi extends EV3.Api {

        private MyCustomApi(@NonNull GenEV3<? extends EV3.Api> ev3) {
            super(ev3);
        }

        public void mySpecialCommand() {}
    }
    private void applyMotor(@NonNull ThrowingConsumer<TachoMotor, Throwable> f) {
        if (motorLeft != null)
            Prelude.trap(() -> f.call(motorLeft));
        if (motorRight != null)
            Prelude.trap(() -> f.call(motorRight));
        if (motorClaws != null)
            Prelude.trap(() -> f.call(motorClaws));
    }
    Point center;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView = findViewById(R.id.textView);
        mOpenCvCameraView = findViewById(R.id.HelloOpenCvView);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setMaxFrameSize(640, 480);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_DENIED)
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);


        try {
            BluetoothConnection.BluetoothChannel conn = new BluetoothConnection("Willy").connect(); // replace with your own brick name
            GenEV3<MyCustomApi> ev3 = new GenEV3<>(conn);

            Button stopButton = findViewById(R.id.stopButton);
            stopButton.setOnClickListener(v -> { ev3.cancel(); });
            Button startButton = findViewById(R.id.startButton);
            startButton.setOnClickListener(v -> Prelude.trap(() -> ev3.run(this::legoMainCustomApi, MyCustomApi::new)));
        } catch (IOException e) {
            Log.e(TAG, "fatal error: cannot connect to EV3");
            e.printStackTrace();
        }

    }

    int[][] constructMatrix(int n, int m) {
        int [][] matrix = new int[n][m];
        return matrix;
    }

    private void setUpCamera() {
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "Unable to load OpenCV");
        } else {
            Log.d(TAG, "OpenCV loaded");
        }

        mOpenCvCameraView.setCvCameraViewListener(new CameraBridgeViewBase.CvCameraViewListener2() {
            @Override
            public void onCameraViewStarted(int width, int height) {
                Log.d(TAG, "Camera Started");
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
                Log.d(TAG, "Camera Stopped");
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
    void catchBall() throws IOException {
        motorClaws.setStepSpeed(50,0,1000,0,true);
        motorClaws.waitCompletion();
    }
    void releaseBall() throws IOException {
        motorClaws.setStepSpeed(-50,0,1000,0,true);
        motorClaws.waitCompletion();
    }
    void goForward() throws  IOException {
        motorLeft.setStepSync(100,0,0,false);
        motorRight.setStepSync(0,0,610,false);
//        motorLeft.setStepSpeed(100, 0, 610, 0, true);
//        motorRight.setStepSpeed(100, 0, 610, 0, true);
        motorLeft.waitCompletion();
        motorRight.waitCompletion();

    }
    void goBack() throws  IOException {
        motorLeft.setStepSpeed(-100, 0, 610, 0, true);
        motorRight.setStepSpeed(-100, 0, 610, 0, true);
        motorLeft.waitCompletion();
        motorRight.waitCompletion();
    }
    void goLeft() throws  IOException {
        motorLeft.setStepSpeed(-100, 0, 160, 0, true);
        motorRight.setStepSpeed(100, 0, 160, 0, true);
        motorLeft.waitCompletion();
        motorRight.waitCompletion();
    }
    void goRight() throws  IOException {
        motorLeft.setStepSpeed(100, 0, 160, 0, true);
        motorRight.setStepSpeed(-100, 0, 160, 0, true);
        motorLeft.waitCompletion();
        motorRight.waitCompletion();
    }
    void stopMotors() throws IOException {
        motorRight.stop();
        motorLeft.stop();
        motorClaws.stop();
    }
    private void legoMain(EV3.Api api) {
        final String TAG = Prelude.ReTAG("legoMain");
        final LightSensor lightSensor = api.getLightSensor(EV3.InputPort._1);
        final UltrasonicSensor ultraSensor = api.getUltrasonicSensor(EV3.InputPort._2);
        motorLeft = api.getTachoMotor(EV3.OutputPort.A);
        motorRight = api.getTachoMotor(EV3.OutputPort.D);
        motorClaws = api.getTachoMotor(EV3.OutputPort.B);
        boolean ballChatced= false;
        setUpCamera();
        try {
            applyMotor(TachoMotor::resetPosition);
            Integer i = new Integer(0);
            while (i<1) {
                try {
                    Integer tempRobotPosition = xRobotValue;
                    while(tempRobotPosition < xBallValue) {
                        goForward();
                        tempRobotPosition++;
                    }
//                    goRight();
                    Future<Short> ambient = lightSensor.getAmbient();
                    updateStatus(lightSensor, "ambient", ambient.get());

                    Future<Short> reflected = lightSensor.getReflected();
                    updateStatus(lightSensor, "reflected", reflected.get());

                    Future<Float> distance = ultraSensor.getDistance();
                    updateStatus(ultraSensor, "distance", distance.get());

                    Future<LightSensor.Color> colf = lightSensor.getColor();
                    LightSensor.Color col = colf.get();
                    updateStatus(lightSensor, "color", col);
                    runOnUiThread(() -> findViewById(R.id.colorView).setBackgroundColor(col.toARGB32()));

                    Future<Float> posMLeft = motorLeft.getPosition();
                    updateStatus(motorLeft, "motor position", posMLeft.get());

                    Future<Float> speedMLeft = motorLeft.getSpeed();
                    updateStatus(motorLeft, "motor speed", speedMLeft.get());
                    Future<Float> posMRight = motorRight.getPosition();
                    updateStatus(motorRight, "motor position", posMRight.get());
                    Future<Float> speedMRight = motorRight.getSpeed();
                    updateStatus(motorRight, "motor speed", speedMRight.get());
                    Future<Float> postMClaws = motorClaws.getPosition();
                    updateStatus(motorClaws, "motor position", postMClaws.get());
                    Future<Float> speedMClawst = motorClaws.getSpeed();
                    updateStatus(motorRight, "motor speed", speedMClawst.get());
                    if(center != null) {
                        //catchBall();
                        //api.soundTone(100,100,3000);
//                        goForward();
//                        releaseBall();
//                        goBack();
//                        stopMotors();
                        goRight();
                        goRight();
                    }
                    while (tempRobotPosition>xRobotValue) {
                        goForward();
                        tempRobotPosition--;
                    }
                    //releaseBall();
                    i++;
                } catch (IOException | InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }

        } finally {
            applyMotor(TachoMotor::stop);
        }
    }
    private void legoMainCustomApi(MyCustomApi api) {
        final String TAG = Prelude.ReTAG("legoMainCustomApi");
        api.mySpecialCommand();
        EditText rows = findViewById(R.id.numberOfRows);
        EditText columns = findViewById(R.id.numberOfColumns);
        n = Integer.valueOf(rows.getText().toString());
        m = Integer.valueOf(columns.getText().toString());
        EditText robotXCoordinate = findViewById(R.id.xRobotStart);
        EditText robotYCoordinate = findViewById(R.id.yRobotStart);
        xRobotValue = Integer.valueOf(robotXCoordinate.getText().toString());
        yRobotValue = Integer.valueOf(robotYCoordinate.getText().toString());
        matrix = constructMatrix(n,m);
        EditText ballXCoordinate  = findViewById(R.id.xStartBall);
        EditText ballYCoordinate  = findViewById(R.id.yStartBall);
        xBallValue = Integer.valueOf(ballXCoordinate.getText().toString());
        yBallValue = Integer.valueOf(ballYCoordinate.getText().toString());
        legoMain(api);

    }


}