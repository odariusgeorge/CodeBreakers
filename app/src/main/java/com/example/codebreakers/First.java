package com.example.codebreakers;

import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import it.unive.dais.legodroid.lib.EV3;
import it.unive.dais.legodroid.lib.GenEV3;
import it.unive.dais.legodroid.lib.comm.BluetoothConnection;
import it.unive.dais.legodroid.lib.plugs.GyroSensor;
import it.unive.dais.legodroid.lib.plugs.TachoMotor;
import it.unive.dais.legodroid.lib.plugs.UltrasonicSensor;
import it.unive.dais.legodroid.lib.util.Prelude;
import it.unive.dais.legodroid.lib.util.ThrowingConsumer;

import static java.lang.Math.abs;

public class First extends AppCompatActivity {
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
    private Integer totalBalls;
    private Integer ball_catched = 0;
    private Integer xRobotValue;
    private Integer yRobotValue;
    private Integer xSafeZone;
    private Integer ySafeZone;
    private boolean ballIsCatched = false;
    Point center;

    private void applyMotor(@NonNull ThrowingConsumer<TachoMotor, Throwable> f) {
        if (motorLeft != null)
            Prelude.trap(() -> f.call(motorLeft));
        if (motorRight != null)
            Prelude.trap(() -> f.call(motorRight));
        if (motorClaws != null)
            Prelude.trap(() -> f.call(motorClaws));
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first);
        Button start = findViewById(R.id.Start);
        LinearLayout matrixView = findViewById(R.id.matrix);
        mOpenCvCameraView = findViewById(R.id.HelloOpenCvView);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setMaxFrameSize(640, 480);
        mOpenCvCameraView.disableFpsMeter();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        MatrixMap Map = new MatrixMap(this);
        Map.setNumColumns(10);
        Map.setNumRows(10);
        matrixView.addView(Map);
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

    void catchBall() throws IOException {
        motorClaws.setStepSpeed(50,0,1000,0,true);
        motorClaws.waitCompletion();
        motorClaws.stop();
    }

    void releaseBall() throws IOException {
        motorClaws.setStepSpeed(-50,0,1000,0,true);
        motorClaws.waitCompletion();
    }

    void goForward(EV3.Api api) throws  IOException {
            int i = 1;
            while(i!=4) {
                turnFrontOneMotorDown(api);
                if(i%2==0) {
                    motorLeft.setStepSpeed(50, 0, 300, 0, true);
                    motorRight.setStepSpeed(50, 0, 300, 0, true );
                    motorLeft.waitCompletion();
                    motorRight.waitCompletion();
                }
                else {
                    motorRight.setStepSpeed(50, 0, 300, 0, true);
                    motorLeft.setStepSpeed(50, 0, 300, 0, true);
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
        if(ballIsCatched == false) {
            int i = 1;
            turnFront(api);
            while(i!=3) {
                turnFront(api);
                motorLeft.setStepSpeed(-50, 0, 200, 0, false);
                motorRight.setStepSpeed(-50, 0, 200, 0, false);
                motorLeft.waitCompletion();
                motorRight.waitCompletion();
                turnFront(api);
                i++;
            }
            turnFront(api);
        } else {
            int i = 1;
            turnFront(api);
            while(i!=6) {
                motorLeft.setStepSpeed(-40, 0, 100, 0, false);
                motorRight.setStepSpeed(-40, 0, 100, 0, false);
                motorLeft.waitCompletion();
                motorRight.waitCompletion();
                turnFront(api);
                i++;
            }
            turnFront(api);
        }
    }

    void goLeft(EV3.Api api) throws  IOException {
        final GyroSensor gyroSensor = api.getGyroSensor(EV3.InputPort._4);
        try {
            float current_angle = gyroSensor.getAngle().get();
            while (current_angle > -80){
                motorLeft.setSpeed(-10);
                motorRight.setSpeed(10);
                motorLeft.start();
                motorRight.start();
                current_angle = gyroSensor.getAngle().get();
                Log.i("gyrosensor", gyroSensor.getAngle().get().toString());
                stopMotors();
            }
        } catch (IOException | InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

    }

    void goRight(EV3.Api api) throws  IOException {
        final GyroSensor gyroSensor = api.getGyroSensor(EV3.InputPort._4);
        try {
            float current_angle = gyroSensor.getAngle().get();
            while (current_angle < 80) {
                motorLeft.setSpeed(10);
                motorRight.setSpeed(-10);
                motorLeft.start();
                motorRight.start();
                current_angle = gyroSensor.getAngle().get();
                Log.i("gyrosensor", gyroSensor.getAngle().get().toString());
                stopMotors();
            }
            motorRight.setStepSpeed(50,0,1000,0,false);
            motorLeft.setStepSpeed(50,0,1000,0,false);
        } catch (IOException | InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

    }

    void turnFront(EV3.Api api) {
        if(ballIsCatched == false) {
            int speed = 1;
            final GyroSensor gyroSensor = api.getGyroSensor(EV3.InputPort._4);
            try {
                float current_angle = gyroSensor.getAngle().get();
                while ( abs(current_angle) > 1 )  {
                        if (current_angle > 1) {
                            motorLeft.setSpeed(-speed);
                            motorRight.setSpeed(speed);
                            motorLeft.start();
                            motorRight.start();
                            Log.i("gyrosensor", gyroSensor.getAngle().get().toString());
                            current_angle = gyroSensor.getAngle().get();
                        } else if (current_angle < 1 ) {
                            motorLeft.setSpeed(speed);
                            motorRight.setSpeed(-speed);
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

    void stopMotors() throws IOException {
        motorRight.stop();
        motorLeft.stop();
        motorClaws.stop();
    }

    void goToSafeZone(EV3.Api api) throws  IOException {
        if(yRobotValue>ySafeZone) {
            while(yRobotValue!=ySafeZone) {
                goBack(api);
                yRobotValue--;
            }
        }
        if (yRobotValue<ySafeZone) {
            while(yRobotValue!=ySafeZone) {
                goForward(api);
                yRobotValue++;
            }
        }
        if (xRobotValue>xSafeZone) {
            goLeft(api);
            xRobotValue--;
            while (xRobotValue!=xSafeZone) {
                goForward(api);
                xRobotValue--;
            }
        }
        if (xRobotValue<xSafeZone) {
            goRight(api);
            xRobotValue++;
            while(xRobotValue!=xSafeZone) {
                goBack(api);
                xRobotValue++;
            }
        }
    }

    void computeSafeZone() {
        if(xRobotValue <= m && yRobotValue == 0) {
            xSafeZone = xRobotValue;
            ySafeZone = yRobotValue-1;}
        if(xRobotValue == m && yRobotValue > 0) {
            xSafeZone = xRobotValue+1;
            ySafeZone = yRobotValue;
        }
        if(xRobotValue == 0 && yRobotValue > 1) {
            xSafeZone = xRobotValue-1;
            ySafeZone = yRobotValue;
        }
        if(xRobotValue > 0 && yRobotValue == n) {
            xSafeZone = xRobotValue;
            ySafeZone = yRobotValue+1;
        }
    }

    int[][] constructMatrix(int n, int m) {
        int [][] matrix = new int[n][m];
        for(int i=0;i<=n;i++)
            for(int j=0;j<=m;j++)
                matrix[n][m] = 0;
        return matrix;
    }

    void updateMatrix() {
        matrix[xRobotValue][yRobotValue] = 1;
    }
    int getDistance(EV3.Api api) throws IOException,ExecutionException, InterruptedException {
        final UltrasonicSensor ultraSensor = api.getUltrasonicSensor(EV3.InputPort._2);
        return Math.round(ultraSensor.getDistance().get());
    }

    private void legoMain(EV3.Api api) throws  IOException, InterruptedException, ExecutionException {
        final String TAG = Prelude.ReTAG("legoMain");

        motorLeft = api.getTachoMotor(EV3.OutputPort.A);
        motorRight = api.getTachoMotor(EV3.OutputPort.D);
        motorClaws = api.getTachoMotor(EV3.OutputPort.B);
        computeSafeZone();
        setUpCamera();
        ball_catched = 0;

            while (ball_catched < totalBalls) {
                /*
                while (yRobotValue != n) {
                    goForward(api);
                    yRobotValue++;
                    turnFrontOneMotorDown(api);
                }
                ball_catched++;
            }
         */
                syncMotors();
                ball_catched++;
            }
        stopMotors();
    }

    void syncMotors() throws  IOException {
        motorRight.setStepSync(50,0,500,false);
        motorLeft.setStepSync(50,0,500,false);
        motorRight.waitCompletion();
        motorLeft.waitCompletion();
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
        n = 10;
        m = 10;
//        n = Integer.valueOf(rows.getText().toString());
//        m = Integer.valueOf(columns.getText().toString());
        EditText robotXCoordinate = findViewById(R.id.xRobot);
        EditText robotYCoordinate = findViewById(R.id.yRobot);
//        xRobotValue = Integer.valueOf(robotXCoordinate.getText().toString());
//        yRobotValue = Integer.valueOf(robotYCoordinate.getText().toString());
        xRobotValue = 0;
        yRobotValue = 0;
//        matrix = constructMatrix(n,m);
        EditText numberOfBalls  = findViewById(R.id.balls);
//        totalBalls = Integer.valueOf(numberOfBalls.getText().toString());
        totalBalls = 1;
        legoMain(api);

    }

}
