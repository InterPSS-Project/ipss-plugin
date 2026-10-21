package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.control.exc.ieee.y2005.st4b.IEEE2005ST4BExciter;
import org.junit.jupiter.api.Test;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.impl.DStabBusImpl;
import com.interpss.dstab.mach.MachineIfdBase;
import com.interpss.dstab.mach.impl.RoundRotorMachineImpl;

class St4bBridgeInputReuseTest {
    static class Bus extends DStabBusImpl {
        Complex voltage=new Complex(.95,.12);int reads;
        @Override public Complex getVoltage(){reads++;return voltage;}
    }
    static class Machine extends RoundRotorMachineImpl {
        Complex current=new Complex(.7,-.2);double field=.4;
        int currentReads,fieldReads;
        Runnable beforeCurrent=()->{},beforeField=()->{};
        @Override public Complex getIxy(){currentReads++;beforeCurrent.run();return current;}
        @Override public double calculateIfd(MachineIfdBase base){fieldReads++;beforeField.run();return field;}
    }
    record Fixture(IEEE2005ST4BExciter e,Bus bus,Machine machine) {}
    static Fixture fixture(boolean enabled) {
        var e=new IEEE2005ST4BExciter();e.setBridgeInputReuseEnabled(enabled);
        var bus=new Bus();var gen=DStabObjectFactory.createDStabGen("1");
        bus.getContributeGenList().add(gen);
        var machine=new Machine();machine.setParentGen(gen);gen.setDynamicGenDevice(machine);
        machine.getControllerList().add(e);e.customBlock.eulerStep1(.8,0.);
        return new Fixture(e,bus,machine);
    }
    static double same(Fixture ordinary,Fixture cached) throws Exception {
        double a=ordinary.e.customBlock.getY(),b=cached.e.customBlock.getY();
        assertEquals(Double.doubleToRawLongBits(a),Double.doubleToRawLongBits(b));
        var field=ordinary.e.customBlock.getClass().getDeclaredField("VB");field.setAccessible(true);
        assertEquals(Double.doubleToRawLongBits(field.getDouble(ordinary.e.customBlock)),
                Double.doubleToRawLongBits(field.getDouble(cached.e.customBlock)));
        assertEquals(ordinary.bus.reads,cached.bus.reads);
        assertEquals(ordinary.machine.currentReads,cached.machine.currentReads);
        assertEquals(ordinary.machine.fieldReads,cached.machine.fieldReads);
        return b;
    }
    @Test void reuseRetainsEveryMachineReadAndLiveFieldCurrent() throws Exception {
        var ordinary=fixture(false);var cached=fixture(true);
        double first=same(ordinary,cached);
        for(int i=0;i<10;i++)same(ordinary,cached);
        assertTrue(cached.e.getBridgeInputReuseCount()>=10);
        ordinary.machine.field=1.2;cached.machine.field=1.2;
        assertNotEquals(first,same(ordinary,cached));
        assertTrue(cached.e.getBridgeInputReuseCount()>=11);
        assertEquals(12,cached.machine.fieldReads);
    }
    @Test void everyVoltageCurrentAndParameterChangeInvalidatesTheAffectedValue() throws Exception {
        var ordinary=fixture(false);var cached=fixture(true);
        same(ordinary,cached);same(ordinary,cached);
        for(int key=0;key<8;key++) {
            long before=cached.e.getBridgeInputReuseCount();
            for(var f:List.of(ordinary,cached))switch(key) {
                case 0->f.e.kp+=.2;
                case 1->f.e.angKp_deg+=7.;
                case 2->f.e.xl+=.03;
                case 3->f.e.ki+=.1;
                case 4->f.bus.voltage=f.bus.voltage.add(.01);
                case 5->f.bus.voltage=f.bus.voltage.add(new Complex(0,.02));
                case 6->f.machine.current=f.machine.current.add(.05);
                case 7->f.machine.current=f.machine.current.add(new Complex(0,.04));
            }
            same(ordinary,cached);assertEquals(before,cached.e.getBridgeInputReuseCount());
            same(ordinary,cached);assertEquals(before+1,cached.e.getBridgeInputReuseCount());
        }
        for(var f:List.of(ordinary,cached)){f.e.kc=.01;f.e.vbmax=.2;}
        same(ordinary,cached);
        cached.e.setBridgeInputReuseEnabled(false);same(ordinary,cached);
        cached.e.setBridgeInputReuseEnabled(true);same(ordinary,cached);
    }
    @Test void signedZerosAndNonfiniteArgumentsRetainOrdinaryArithmetic() throws Exception {
        for(double value:new double[]{0.,-0.,Double.NaN,Double.POSITIVE_INFINITY,Double.MAX_VALUE}) {
            var ordinary=fixture(false);var cached=fixture(true);
            ordinary.e.kp=value;cached.e.kp=value;
            same(ordinary,cached);same(ordinary,cached);
            if(!Double.isFinite(value))assertEquals(0,cached.e.getBridgeInputReuseCount());
        }
        var ordinary=fixture(false);var cached=fixture(true);
        for(double zero:new double[]{0.,-0.,0.}) {
            long before=cached.e.getBridgeInputReuseCount();
            for(var f:List.of(ordinary,cached))f.bus.voltage=new Complex(.9,zero);
            same(ordinary,cached);assertEquals(before,cached.e.getBridgeInputReuseCount());
        }
    }
    @Test void customComplexArithmeticKeepsFreshOperandsAndLiveParameterOrder() throws Exception {
        var ordinary=fixture(false);var cached=fixture(true);
        class CustomVoltage extends Complex {
            final IEEE2005ST4BExciter e;Complex previous;int calls;
            CustomVoltage(IEEE2005ST4BExciter e){super(.95,.12);this.e=e;}
            @Override public Complex multiply(Complex factor) {
                assertNotSame(previous,factor);previous=factor;calls++;
                e.xl+=.01;e.ki+=.02;return super.multiply(factor);
            }
        }
        var a=new CustomVoltage(ordinary.e);var b=new CustomVoltage(cached.e);
        ordinary.bus.voltage=a;cached.bus.voltage=b;
        for(int i=0;i<10;i++)same(ordinary,cached);
        assertEquals(a.calls,b.calls);assertEquals(0,cached.e.getBridgeInputReuseCount());
    }
    @Test void getterSideEffectsRetainOriginalParameterSamplingBoundaries() throws Exception {
        var ordinary=fixture(false);var cached=fixture(true);
        for(var f:List.of(ordinary,cached)) {
            f.machine.beforeCurrent=()->{f.e.kp+=.03;f.e.angKp_deg+=.1;f.e.xl+=.001;f.e.ki+=.002;};
            f.machine.beforeField=()->{f.e.kc+=.01;f.e.vbmax-=.005;};
        }
        for(int i=0;i<40;i++)same(ordinary,cached);
    }
}
