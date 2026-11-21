package org.interpss.core.dstab.dynLoad;

import org.interpss.dstab.dynLoad.impl.LD1PACImpl;

/** Timer integration for recorded-input kernel experiments; no model equations or trip decisions live here. */
final class CompressorTimerReplay {
    private final LD1PACImpl model;
    private final boolean shared;
    private final double[] state, rates, left, history;
    private boolean event;

    CompressorTimerReplay(LD1PACImpl model, boolean shared) {
        this.model=model;this.shared=shared;
        if(model.staggeredStateCount()!=9)throw new IllegalArgumentException("The legacy compressor has nine integration coordinates");
        state=new double[9];rates=new double[9];left=new double[9];history=new double[9];
    }
    double[] current() {model.readStaggeredState(state,0);return state;}
    double[] advance(double interval) {
        model.readStaggeredState(state,0);model.evaluateStaggered(interval,rates,0);
        for(int i=4;i<9;i++) {
            if(shared) {
                double rate=event?.5*(rates[i]+left[i]):rates[i];
                state[i]+=interval*(1.5*rate-.5*history[i]);history[i]=rate;
            } else {
                // A held pickup has a constant 0/1 rate throughout this interval:
                // its exact time integral and Heun update are identical.
                state[i]+=interval*rates[i];
            }
        }
        event=false;return state;
    }
    void beforeEvent(double interval) {model.evaluateStaggered(interval,left,0);event=true;}
    void acceptEvent(double time) {
        model.acceptNetworkEventInput(time);
        if(model.providesNetworkEventLeftRates())model.readNetworkEventLeftRates(left,0);
    }
}
