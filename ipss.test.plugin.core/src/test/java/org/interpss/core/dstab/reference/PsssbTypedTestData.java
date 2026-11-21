package org.interpss.core.dstab.reference;

import com.interpss.dstab.controller.pss.psssb.PsssbStabilizerData;

/** Explicit typed profiles, never interpreted as a native dynamic record. */
public final class PsssbTypedTestData {
    private PsssbTypedTestData() {}

    public static PsssbStabilizerData benchmark() {
        var d = new PsssbStabilizerData();
        d.setIcs1(1); d.setRemoteBus1(0); d.setIcs2(3); d.setRemoteBus2(0);
        d.setM(5); d.setN(1);
        d.setTw1(10); d.setTw2(10); d.setTw3(10); d.setTw4(0);
        d.setT6(0); d.setT7(10); d.setKs2(1.47); d.setKs3(1); d.setKs4(1);
        d.setT8(.5); d.setT9(.1); d.setKs1(2.4);
        d.setT1(.16); d.setT2(.02); d.setT3(.16); d.setT4(.02);
        d.setVstmax(.1); d.setVstmin(-.1);
        d.setSw1(1); d.setTd1(.05); d.setTd2(.2);
        d.setVtl(.01); d.setVk(.005); d.setVcutoff(.2);
        return d;
    }

    public static PsssbStabilizerData switchedBoost() {
        var d = benchmark();
        d.setTw2(9); d.setTw3(8); d.setTw4(7); d.setT6(.6); d.setT7(.5);
        d.setKs3(1.1); d.setKs4(.9); d.setT8(.4); d.setT3(.12); d.setT4(.03);
        d.setSw1(0); d.setVtl(.9); d.setVk(.05); d.setVcutoff(-.02);
        return d;
    }

    public static PsssbStabilizerData voltageCutout() {
        var d = benchmark();
        d.setM(0); d.setN(0);
        d.setTw1(1); d.setTw2(0); d.setTw3(1); d.setTw4(0);
        d.setT6(0); d.setT7(0); d.setKs2(1); d.setKs1(1);
        d.setT8(0); d.setT9(0); d.setT1(0); d.setT2(0); d.setT3(0); d.setT4(0);
        d.setVstmax(1); d.setVstmin(-1); d.setSw1(0);
        d.setVtl(1); d.setVk(.05); d.setVcutoff(.02);
        return d;
    }
}
