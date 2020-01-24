package it.unive.dais.legodroid.lib.plugs;

import androidx.annotation.NonNull;

import it.unive.dais.legodroid.lib.EV3;

public abstract class Plugs<Port1,Port2> {
    @NonNull
    protected final EV3.Api api;
    @NonNull
    protected final Port1 port1;
    @NonNull
    protected final Port2 port2;


    protected Plugs(@NonNull EV3.Api api, @NonNull Port1 port1, @NonNull Port2 port2) {
        this.api = api;
        this.port1 = port1;
        this.port2 = port2;
    }

    @Override
    @NonNull
    public String toString() {
        return String.format("%s@%s", this.getClass().getSimpleName(), port1);
    }
}

