package com.example.codebreakers;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;


import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.util.ArrayList;
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
import it.unive.dais.legodroid.lib.util.Consumer;
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
    private void legoMain(EV3.Api api) {
        final String TAG = Prelude.ReTAG("legoMain");
        final LightSensor lightSensor = api.getLightSensor(EV3.InputPort._1);
        final UltrasonicSensor ultraSensor = api.getUltrasonicSensor(EV3.InputPort._2);
        motorLeft = api.getTachoMotor(EV3.OutputPort.A);
        motorRight = api.getTachoMotor(EV3.OutputPort.D);
        motorClaws = api.getTachoMotor(EV3.OutputPort.B);
        setUpCamera();
        try {
            applyMotor(TachoMotor::resetPosition);

            while (!api.ev3.isCancelled()) {
                try {
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

                    if (center!=null) {
                        motorClaws.setStepSpeed(50, 0, 1000, 0, true);
                        motorClaws.waitCompletion();
                        motorClaws.setStepSpeed(-20, 0, 1000, 0, true);
                        Log.d(TAG, "waiting for long motor operation completed...");
                        motorClaws.waitUntilReady();
                        Log.d(TAG, "long motor operation completed");
                    }
//                    motorLeft.setStepSpeed(50, 0, 1000, 0, true);
//                    motorLeft.waitCompletion();
//                    motorLeft.setStepSpeed(-20, 0, 1000, 0, true);
//                    Log.d(TAG, "waiting for long motor operation completed...");
//                    motorLeft.waitUntilReady();
//                    Log.d(TAG, "long motor operation completed");
//                    motorRight.setStepSpeed(50, 0, 1000, 0, true);
//                    motorRight.waitCompletion();
//                    motorRight.setStepSpeed(-20, 0, 1000, 0, true);
//                    Log.d(TAG, "waiting for long motor operation completed...");
//                    motorRight.waitUntilReady();
//                    Log.d(TAG, "long motor operation completed");

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
        legoMain(api);
    }


}
