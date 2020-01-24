package it.unive.dais.legodroid.lib.plugs;

import android.util.Log;

import androidx.annotation.NonNull;

import it.unive.dais.legodroid.lib.EV3;
import it.unive.dais.legodroid.lib.comm.Bytecode;
import it.unive.dais.legodroid.lib.comm.Const;
import it.unive.dais.legodroid.lib.comm.Reply;
import it.unive.dais.legodroid.lib.util.Prelude;
import it.unive.dais.legodroid.lib.util.UnexpectedException;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class Motors extends Plugs<EV3.OutputPort, EV3.OutputPort> {

    private static final String TAG = Prelude.ReTAG("Motors");

    public Motors(@NonNull EV3.Api api, EV3.OutputPort port1, EV3.OutputPort port2) {
        super(api, port1, port2);
    }

    public void setStepSync(int speed, int turnRatio, int step, boolean brake) throws IOException {
        Bytecode bc = new Bytecode();
        bc.addOpCode(Const.OUTPUT_STEP_SYNC);
        bc.addParameter(Const.LAYER_MASTER);
        bc.addParameter(port1.toBitmask()+port2.toBitmask());
        bc.addParameter((byte) speed);
        bc.addParameter((short) turnRatio);
        bc.addParameter(step);
        bc.addParameter(brake ? Const.BRAKE : Const.COAST);
        api.sendNoReply(bc);
        Log.d(TAG, String.format("motor step sync: power=%d, turn=%d, step=%d, brake=%s", speed, turnRatio, step, brake));
    }

}
