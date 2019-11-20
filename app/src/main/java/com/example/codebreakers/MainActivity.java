package com.example.codebreakers;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.CvType;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
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
import me.dm7.barcodescanner.zbar.ZBarScannerView;

public class MainActivity extends AppCompatActivity {
    private static final int CAMERA_PERMISSION_CODE=100;
    private static final String TAG = Prelude.ReTAG("MainActivity");
    private Mat mRgba, mRgbaF, mRgbaT;
    private Scalar mBlobColorHsv;
    private Scalar CONTOUR_COLOR;
    private Scalar MARKER_COLOR;
    private Scalar TEXT_COLOR;
    private CameraBridgeViewBase mOpenCvCameraView;
    private ZBarScannerView mScannerView;
    private ColorBlobDetector mDetector;
    private TextView textView;
    private final Map<String, Object> statusMap = new HashMap<>();
    @Nullable
    private static TachoMotor motorLeft;
    private static TachoMotor motorRight;
    private static TachoMotor motorClaws;

    private void updateStatus(@NonNull Plug p, String key, Object value) {
        Log.d(TAG, String.format("%s: %s: %s", p, key, value));
        statusMap.put(key, value);
        runOnUiThread(() -> textView.setText(statusMap.toString()));
    }

    private void setupEditable(@IdRes int id, Consumer<Integer> f) {
        EditText e = findViewById(id);
        e.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                int x = 0;
                try {
                    x = Integer.parseInt(s.toString());
                } catch (NumberFormatException ignored) {
                }
                f.call(x);
            }
        });
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



    // Function to check and request permission.
    public void checkPermission(String permission, int requestCode)
    {
        if (ContextCompat.checkSelfPermission(MainActivity.this, permission)
                == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[] { permission },
                    requestCode);
        }
        else {
            Toast.makeText(MainActivity.this,
                    "Permission already granted",
                    Toast.LENGTH_SHORT)
                    .show();
        }
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView = findViewById(R.id.textView);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        checkPermission(Manifest.permission.CAMERA,CAMERA_PERMISSION_CODE);

        if (!OpenCVLoader.initDebug()) {
                            Log.e("AndroidIngSwOpenCV", "Unable to load OpenCV");
                        } else {
                            Log.d("AndroidIngSwOpenCV", "OpenCV loaded");
                        }
                        try {
                            BluetoothConnection.BluetoothChannel conn = new BluetoothConnection("Willy").connect(); // replace with your own brick name
                            GenEV3<MyCustomApi> ev3 = new GenEV3<>(conn);

                            Button stopButton = findViewById(R.id.stopButton);
                            stopButton.setOnClickListener(v -> {
                                ev3.cancel();
                            });

                            Button startButton = findViewById(R.id.startButton);
                            startButton.setOnClickListener(v -> Prelude.trap(() -> ev3.run(this::legoMainCustomApi, MyCustomApi::new)));

                            setupEditable(R.id.powerEdit, (x) -> applyMotor((m) -> {
                                m.setPower(x);
                                m.start();
                            }));
                            setupEditable(R.id.speedEdit, (x) -> applyMotor((m) -> {
                                m.setSpeed(x);
                                m.start();
                            }));
                        } catch (IOException e) {
                            Log.e(TAG, "fatal error: cannot connect to EV3");
                            e.printStackTrace();
                        }

                        mOpenCvCameraView = findViewById(R.id.HelloOpenCvView);
                        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
                        mOpenCvCameraView.setMaxFrameSize(1920, 1080);
                        mOpenCvCameraView.disableFpsMeter();

                        mOpenCvCameraView.setCvCameraViewListener(new CameraBridgeViewBase.CvCameraViewListener2() {
                            @Override
                            public void onCameraViewStarted(int width, int height) {
                                mRgba = new Mat(height, width, CvType.CV_8UC4);
                                mRgbaF = new Mat(height, width, CvType.CV_8UC4);
                                mRgbaT = new Mat(width, width, CvType.CV_8UC4);  // NOTE width,width is NOT a typo
                                mDetector = new ColorBlobDetector();
                                mBlobColorHsv = new Scalar(280/2,0.65*255,0.75*255,255); // hue in [0,180], saturation in [0,255], value in [0,255]
                                mDetector.setHsvColor(mBlobColorHsv);
                                CONTOUR_COLOR = new Scalar(255,0,0,255);
                                MARKER_COLOR = new Scalar(0,0,255,255);
                                TEXT_COLOR = new Scalar(255,255,255,255);
                                Log.d(TAG, "Camera Started");
                            }

                            @Override
                            public void onCameraViewStopped() {
                                Log.d(TAG, "Camera Stopped");
                                mRgba.release();
                            }
                            @Override
                            public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
                                mRgba = inputFrame.rgba();
                                // Rotate mRgba 90 degrees
                                Core.transpose(mRgba, mRgbaT);
                                Imgproc.resize(mRgbaT, mRgbaF, mRgbaF.size(), 0,0, 0);
                                Core.flip(mRgbaF, mRgba, 1 );
                                //

                                mDetector.process(mRgba);
                                List<MatOfPoint> contours = mDetector.getContours();
                                Imgproc.drawContours(mRgba, contours, -1, CONTOUR_COLOR);
                                Point center = mDetector.getCenterOfMaxContour();
                                double direction = 0;
                                if( center != null ) {
                                    Imgproc.drawMarker(mRgba, center, MARKER_COLOR);
                                    direction = (center.x - mRgba.cols()/2)/mRgba.cols(); // portrait orientation
                                }
                                for(MatOfPoint c: contours) {
                                    try {
                                        if(MainActivity.motorLeft != null) {
                                            Future<Float> posMLeft = MainActivity.motorLeft.getPosition();
                                        }

                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                                //saveMatToImage(mRgba,"ball");

                                return mRgba;
                            }
                        });
                        mOpenCvCameraView.enableView();
                        mScannerView = new ZBarScannerView(this);

    }

    // main program executed by EV3

    private void legoMain(EV3.Api api) {
        final String TAG = Prelude.ReTAG("legoMain");
        final LightSensor lightSensor = api.getLightSensor(EV3.InputPort._1);
        final UltrasonicSensor ultraSensor = api.getUltrasonicSensor(EV3.InputPort._2);
        motorLeft = api.getTachoMotor(EV3.OutputPort.A);
        motorRight = api.getTachoMotor(EV3.OutputPort.D);
        motorClaws = api.getTachoMotor(EV3.OutputPort.B);

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

                    Future<Float> speedMRight = motorLeft.getSpeed();
                    updateStatus(motorLeft, "motor speed", speedMRight.get());

                    motorLeft.setStepSpeed(50, 0, 1000, 0, true);
                    motorLeft.waitCompletion();
                    motorLeft.setStepSpeed(-20, 0, 1000, 0, true);
                    Log.d(TAG, "waiting for long motor operation completed...");
                    motorLeft.waitUntilReady();
                    Log.d(TAG, "long motor operation completed");
                    motorRight.setStepSpeed(50, 0, 1000, 0, true);
                    motorRight.waitCompletion();
                    motorRight.setStepSpeed(-20, 0, 1000, 0, true);
                    Log.d(TAG, "waiting for long motor operation completed...");
                    motorRight.waitUntilReady();
                    Log.d(TAG, "long motor operation completed");

                } catch (IOException | InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }

        } finally {
            applyMotor(TachoMotor::stop);
        }


    }
    @Override
    public void onResume() {
        super.onResume();
        mScannerView.startCamera();
    }

    @Override
    public void onPause() {
        super.onPause();
        mScannerView.stopCamera();
    }


    private void legoMainCustomApi(MyCustomApi api) {
        final String TAG = Prelude.ReTAG("legoMainCustomApi");
        api.mySpecialCommand();
        legoMain(api);
    }


}
