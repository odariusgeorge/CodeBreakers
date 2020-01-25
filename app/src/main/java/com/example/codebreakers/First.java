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
import it.unive.dais.legodroid.lib.plugs.Motors;
import it.unive.dais.legodroid.lib.plugs.TachoMotor;
import it.unive.dais.legodroid.lib.plugs.UltrasonicSensor;
import it.unive.dais.legodroid.lib.util.Prelude;
import it.unive.dais.legodroid.lib.util.ThrowingConsumer;

import static it.unive.dais.legodroid.lib.comm.Const.DIRECT_COMMAND_NOREPLY;
import static it.unive.dais.legodroid.lib.comm.Const.LAYER_MASTER;
import static it.unive.dais.legodroid.lib.comm.Const.OUTPUT_SPEED;
import static it.unive.dais.legodroid.lib.comm.Const.OUTPUT_START;
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
    private static Motors motoare;
    private int[][] matrix;
    private Integer n;
    private Integer m;
    private Integer totalBalls;
    private Integer ball_catched = 0;
    private Integer xRobotValue;
    private Integer yRobotValue;
    private Integer xSafeZone;
    private Integer ySafeZone;
    private Integer xCurrentPosition;
    private Integer yCurrentPosition;
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
            yCurrentPosition++;
            int i = 1;
            while(i!=4) {
                turnFrontOneMotorDown(api);
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
                turnFrontOneMotorDown(api);
                i++;
            }
            motorLeft.setSpeed(0);
            motorRight.setSpeed(0);
    }

    void goBack(EV3.Api api) throws  IOException {
        yCurrentPosition--;
        int i = 1;
        while(i!=4) {
            turnFrontOneMotorDown(api);
            if(i%2==0) {
                motorLeft.setStepSpeed(-50, 0, 300, 0, true);
                motorRight.setStepSpeed(-50, 0, 300, 0, true );
                motorLeft.waitCompletion();
                motorRight.waitCompletion();
            }
            else {
                motorRight.setStepSpeed(-50, 0, 300, 0, true);
                motorLeft.setStepSpeed(-50, 0, 300, 0, true);
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
            while(i!=4) {
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
                i++;
            }
            motorLeft.setSpeed(0);
            motorRight.setSpeed(0);
            turnFrontOneMotorDown(api);
        }
    }

    void turnLeft(EV3.Api api) {
        final GyroSensor gyroSensor = api.getGyroSensor(EV3.InputPort._4);
        try {
            float current_angle = gyroSensor.getAngle().get();
            while (current_angle > -90){
                motorLeft.setSpeed(-15);
                motorRight.setSpeed(15);
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

    void turnRight(EV3.Api api) {
        final GyroSensor gyroSensor = api.getGyroSensor(EV3.InputPort._4);
        try {
            float current_angle = gyroSensor.getAngle().get();
            while (current_angle < 85) {
                motorLeft.setSpeed(15);
                motorRight.setSpeed(-15);
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

    void goRight(EV3.Api api) throws  IOException {
        final GyroSensor gyroSensor = api.getGyroSensor(EV3.InputPort._4);
        try {
            float current_angle = gyroSensor.getAngle().get();
            while (current_angle < 85) {
                motorLeft.setSpeed(15);
                motorRight.setSpeed(-15);
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
            goLeft(api,1);
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
        xSafeZone = xRobotValue;
        ySafeZone = yRobotValue-1;
    }

    int[][] constructMatrix(int n, int m) {
        int [][] matrix = new int[n][m];
        for(int i=0;i<n;i++)
            for(int j=0;j<m;j++)
                matrix[i][j] = 0;
        return matrix;
    }

    boolean checkLine(int x) {
        for(int y=0;y<m;y++)
            if(matrix[x][y]==0)
                return false;
            return true;
    }

    void markZone(int x,int y) {
        matrix[x][y] = 1;
    }

    void afisare() {
        for (int i=0;i<n;i++){
            for(int j=0;j<m;j++)
                System.out.print(matrix[j][i]);
        System.out.println();
        }


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
        motoare = api.getMotors(EV3.OutputPort.A, EV3.OutputPort.D);
        computeSafeZone();
        setUpCamera();
        markZone(xCurrentPosition, yCurrentPosition);
        ball_catched = 0;
            while (ball_catched!=1) {

                for (int line = xCurrentPosition; line >= 0; line--) {
                    while (checkLine(xCurrentPosition) != true) {
                        goForward(api);
                        markZone(xCurrentPosition, yCurrentPosition);
                    }
                    for (int j = 1; j < 2; j++) {
                        goBack(api);
                    }
                    goLeft(api, 1);
                }
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
        n = 3;
        m = 3;
//        n = Integer.valueOf(rows.getText().toString());
//        m = Integer.valueOf(columns.getText().toString());
        EditText robotXCoordinate = findViewById(R.id.xRobot);
        EditText robotYCoordinate = findViewById(R.id.yRobot);
//        xRobotValue = Integer.valueOf(robotXCoordinate.getText().toString());
//        yRobotValue = Integer.valueOf(robotYCoordinate.getText().toString());
        xRobotValue = 2;
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
