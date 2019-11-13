package com.example.codebreakers;

import android.os.Bundle;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import it.unive.dais.legodroid.lib.EV3;
import it.unive.dais.legodroid.lib.GenEV3;
import it.unive.dais.legodroid.lib.comm.BluetoothConnection;
import it.unive.dais.legodroid.lib.plugs.GyroSensor;
import it.unive.dais.legodroid.lib.plugs.LightSensor;
import it.unive.dais.legodroid.lib.plugs.Plug;
import it.unive.dais.legodroid.lib.plugs.TachoMotor;
import it.unive.dais.legodroid.lib.plugs.TouchSensor;
import it.unive.dais.legodroid.lib.plugs.UltrasonicSensor;
import it.unive.dais.legodroid.lib.util.Consumer;
import it.unive.dais.legodroid.lib.util.Prelude;
import it.unive.dais.legodroid.lib.util.ThrowingConsumer;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = Prelude.ReTAG("MainActivity");

    private TextView textView;
    private final Map<String, Object> statusMap = new HashMap<>();
    @Nullable
    private TachoMotor motor1;
    private TachoMotor motor2;
    // this is a class field because we need to access it from multiple methods

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

    // quick wrapper for accessing field 'motor' only when not-null; also ignores any exception thrown
    private void applyMotor(@NonNull ThrowingConsumer<TachoMotor, Throwable> f) {
        if (motor1 != null)
            Prelude.trap(() -> f.call(motor1));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView = findViewById(R.id.textView);

        try {
            BluetoothConnection.BluetoothChannel conn = new BluetoothConnection("Willy").connect(); // replace with your own brick name

            // connect to EV3 via bluetooth
            GenEV3<MyCustomApi> ev3 = new GenEV3<>(conn);
//            EV3 ev3 = new EV3(conn);  // alternatively an EV3 subclass

            Button stopButton = findViewById(R.id.stopButton);
            stopButton.setOnClickListener(v -> {
                ev3.cancel();   // fire cancellation signal to the EV3 task
            });

            Button startButton = findViewById(R.id.startButton);
            startButton.setOnClickListener(v -> Prelude.trap(() -> ev3.run(this::legoMainCustomApi, MyCustomApi::new)));
            // alternatively with plain EV3
//            startButton.setOnClickListener(v -> Prelude.trap(() -> ev3.run(this::legoMain)));

            setupEditable(R.id.powerEdit, (x) -> applyMotor((m) -> {
                m.setPower(x);
                m.start();      // setPower() and setSpeed() require call to start() afterwards
            }));
            setupEditable(R.id.speedEdit, (x) -> applyMotor((m) -> {
                m.setSpeed(x);
                m.start();
            }));
        } catch (IOException e) {
            Log.e(TAG, "fatal error: cannot connect to EV3");
            e.printStackTrace();
        }
    }

    // main program executed by EV3

    private void legoMain(EV3.Api api) {
        final String TAG = Prelude.ReTAG("legoMain");

        // get sensors
        // don't know if we will need the GyroSensor, for sure we don't need the touch... ()
        final LightSensor lightSensor = api.getLightSensor(EV3.InputPort._1);
        final UltrasonicSensor ultraSensor = api.getUltrasonicSensor(EV3.InputPort._2);
        //final TouchSensor touchSensor = api.getTouchSensor(EV3.InputPort._3);
        //final GyroSensor gyroSensor = api.getGyroSensor(EV3.InputPort._4);

        // get motors
        motor1 = api.getTachoMotor(EV3.OutputPort.A);
        motor2 = api.getTachoMotor(EV3.OutputPort.D);

        try {
            applyMotor(TachoMotor::resetPosition);

            while (!api.ev3.isCancelled()) {    // loop until cancellation signal is fired
                try {
                    // values returned by getters are boxed within a special Future object
                    /*
                    Future<Float> gyro = gyroSensor.getAngle();
                    updateStatus(gyroSensor, "gyro angle", gyro.get()); // call get() for actually reading the value - this may block!
                    */
                    Future<Short> ambient = lightSensor.getAmbient();
                    updateStatus(lightSensor, "ambient", ambient.get());

                    Future<Short> reflected = lightSensor.getReflected();
                    updateStatus(lightSensor, "reflected", reflected.get());

                    Future<Float> distance = ultraSensor.getDistance();
                    updateStatus(ultraSensor, "distance", distance.get());

                    Future<LightSensor.Color> colf = lightSensor.getColor();
                    LightSensor.Color col = colf.get();
                    updateStatus(lightSensor, "color", col);
                    // when you need to deal with the UI, you must do it within a lambda passed to runOnUiThread()
                    runOnUiThread(() -> findViewById(R.id.colorView).setBackgroundColor(col.toARGB32()));
                    /*
                    Future<Boolean> touched = touchSensor.getPressed();
                    updateStatus(touchSensor, "touch", touched.get() ? 1 : 0);
                    */
                    Future<Float> pos = motor1.getPosition();
                    updateStatus(motor1, "motor position", pos.get());

                    Future<Float> speed = motor1.getSpeed();
                    updateStatus(motor1, "motor speed", speed.get());

                    motor1.setStepSpeed(50, 0, 1000, 0, true);
                    motor1.waitCompletion();
                    motor1.setStepSpeed(-20, 0, 1000, 0, true);
                    Log.d(TAG, "waiting for long motor operation completed...");
                    motor1.waitUntilReady();
                    Log.d(TAG, "long motor operation completed");
                    motor2.setStepSpeed(50, 0, 1000, 0, true);
                    motor2.waitCompletion();
                    motor2.setStepSpeed(-20, 0, 1000, 0, true);
                    Log.d(TAG, "waiting for long motor operation completed...");
                    motor2.waitUntilReady();
                    Log.d(TAG, "long motor operation completed");

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
        // specialized methods can be safely called
        api.mySpecialCommand();
        // stub the other main
        legoMain(api);
    }


}
