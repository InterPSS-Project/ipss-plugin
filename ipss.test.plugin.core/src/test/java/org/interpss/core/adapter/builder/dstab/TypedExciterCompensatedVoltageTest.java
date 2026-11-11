package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import com.google.gson.Gson;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.psse.rexsys.*;
import org.interpss.dstab.control.exc.psse.esdc2a.*;
import org.interpss.dstab.control.exc.psse.esdc1a.*;
import org.interpss.dstab.control.exc.psse.dc1c.*;
import org.interpss.dstab.control.exc.psse.dc2c.*;
import org.interpss.dstab.control.exc.psse.dc3a.*;
import org.interpss.dstab.control.exc.psse.dc4b.*;
import org.interpss.dstab.control.exc.psse.dc4c.*;
import org.interpss.dstab.control.exc.psse.st6b.*;
import org.interpss.dstab.control.exc.psse.st6c.*;
import org.interpss.dstab.control.exc.psse.esst2a.*;
import org.interpss.dstab.control.exc.psse.esac4a.*;
import org.interpss.dstab.control.exc.psse.ieeet4.*;
import org.interpss.dstab.mach.IeeeVcData;
import org.interpss.dstab.mach.IeeeVoltageCompensatedMachine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.annotate.AnnotateExciter;
import com.interpss.dstab.mach.Machine;

/** Synthetic EC contracts; physical terminal supply and ceiling inputs stay separate. */
public class TypedExciterCompensatedVoltageTest extends CorePluginTestSetup {
    private static final double TOL=1e-9;
    private static final IeeeVcData LEFT=new IeeeVcData(.023,.034);
    private static final IeeeVcData RIGHT=new IeeeVcData(.017,-.029);
    enum Family { REXSYS, ESDC2A, ESDC1A, DC1C, DC2C, DC4B, DC4C, ST6C, ST6B, ESST2A, DC3A, ESAC4A, IEEET4 }
    private record Fixture(Family family,Machine machine,AnnotateExciter exciter,IeeeVoltageCompensatedMachine provider) {}

    @ParameterizedTest @EnumSource(Family.class)
    void compensatedInitializationUsesBothSignsAndPreservesEquilibrium(Family family)throws Exception {
        Fixture plain=fixture(family,.05,null);
        for(double xc:new double[]{.034,-.041}) {
            Fixture f=fixture(family,.05,new IeeeVcData(.023,xc));
            double ec=f.provider.getCmlMachineVoltage(),vt=f.machine.getDStabBus().getVoltageMag();
            assertTrue(Math.abs(ec-vt)>1e-4);
            assertEquals(ec-vt,f.exciter.getRefPoint()-plain.exciter.getRefPoint(),TOL);
            if(family!=Family.IEEET4)assertEquals(ec,sensed(f),TOL);
            assertArrayEquals(new double[storage(f,"state").length],rhs(f),TOL);
            assertEquals(1.2,f.exciter.getOutput(f.machine),TOL);
        }
    }

    @ParameterizedTest @EnumSource(value=Family.class,names="IEEET4",mode=EnumSource.Mode.EXCLUDE)
    void transducerSamplesLiveCompensationAtBothStages(Family family)throws Exception {
        Fixture f=fixture(family,.05,LEFT);double origin=sensed(f),physical=f.machine.getDStabBus().getVoltageMag();
        f.provider.setIeeeVcData(RIGHT);double u0=f.provider.getCmlMachineVoltage(),d0=(u0-origin)/.05,h=.001;
        int sensor=sensorIndex(f);
        assertEquals(d0,rhs(f)[sensor],TOL);
        assertTrue(f.exciter.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,f.machine,0));
        double predictor=origin+h*d0;assertEquals(predictor,sensed(f),TOL);
        f.provider.setIeeeVcData(new IeeeVcData(-.012,.019));double u1=f.provider.getCmlMachineVoltage();
        assertTrue(f.exciter.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,f.machine,1));
        assertEquals(origin+.5*h*(d0+(u1-predictor)/.05),sensed(f),TOL);
        assertEquals(physical,f.machine.getDStabBus().getVoltageMag(),0.0);
    }

    @ParameterizedTest @EnumSource(Family.class)
    void bypassOrDirectErrorUsesProviderWithoutMutatingStorage(Family family)throws Exception {
        Fixture f=fixture(family,0,LEFT);double[] state=storage(f,"state").clone();
        double[] before=rhs(f);f.provider.setIeeeVcData(RIGHT);double ec=f.provider.getCmlMachineVoltage();
        if(family!=Family.IEEET4)assertEquals(ec,sensed(f),TOL);
        else {
            Ieeet4Exciter e=(Ieeet4Exciter)f.exciter;
            assertEquals(e.rateCommand(e.getRefPoint(),ec)/e.trh,rhs(f)[index(f,"VRH")],TOL);
        }
        assertTrue(maxDifference(before,rhs(f))>1e-5,"Direct voltage error must reach its downstream equation: "+family);
        assertArrayEquals(state,storage(f,"state"),0.0);
    }

    @ParameterizedTest @EnumSource(Family.class)
    void absentAndZeroCompensationRetainIdenticalValidInputTrajectories(Family family)throws Exception {
        Fixture plain=fixture(family,.05,null),zero=fixture(family,.05,new IeeeVcData(0,0));
        assertEquals(plain.exciter.getRefPoint(),zero.exciter.getRefPoint(),0.0);
        assertArrayEquals(storage(plain,"state"),storage(zero,"state"),0.0);
        plain.exciter.setRefPoint(plain.exciter.getRefPoint()+.01);zero.exciter.setRefPoint(zero.exciter.getRefPoint()+.01);
        for(int stage:new int[]{0,1}) {
            assertTrue(plain.exciter.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,plain.machine,stage));
            assertTrue(zero.exciter.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,zero.machine,stage));
            assertArrayEquals(storage(plain,"state"),storage(zero,"state"),0.0);
            assertArrayEquals(storage(plain,"trial"),storage(zero,"trial"),0.0);
            assertEquals(plain.exciter.getOutput(plain.machine),zero.exciter.getOutput(zero.machine),0.0);
        }
    }

    @ParameterizedTest @EnumSource(Family.class)
    void zeroTimeDoesNotChangePendingHistoryOrEnteredData(Family family)throws Exception {
        Fixture f=fixture(family,.05,LEFT);String entered=dataJson(f);
        f.provider.setIeeeVcData(RIGHT);
        assertTrue(f.exciter.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,f.machine,0));
        double[] state=storage(f,"state").clone(),trial=storage(f,"trial").clone(),history=storage(f,"oldDerivative").clone();
        f.provider.setIeeeVcData(new IeeeVcData(.013,.021));
        for(int flag:new int[]{0,1})assertTrue(f.exciter.nextStep(0,DynamicSimuMethod.MODIFIED_EULER,f.machine,flag));
        assertArrayEquals(state,storage(f,"state"),0.0);assertArrayEquals(trial,storage(f,"trial"),0.0);
        assertArrayEquals(history,storage(f,"oldDerivative"),0.0);assertEquals(entered,dataJson(f));
        assertTrue(f.exciter.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,f.machine,1));
        assertEquals(entered,dataJson(f));
    }

    @Test void dc4bPhysicalAmplifierGainIsIndependentOfCompensatedSensor()throws Exception {
        Fixture f=fixture(Family.DC4B,.05,LEFT);Dc4bExciter e=(Dc4bExciter)f.exciter;
        double vt=f.machine.getDStabBus().getVoltageMag(),vr=e.getRegulatorOutput();
        f.provider.setIeeeVcData(RIGHT);assertEquals(0,rhs(f)[index(f,"VR")],TOL);
        f.machine.getDStabBus().setVoltage(new Complex(.98,0));
        assertEquals((vr*.98/vt-vr)/e.ta,rhs(f)[index(f,"VR")],TOL);
    }

    @Test void dcVoltageDependentLimitsKeepPhysicalTerminalMagnitude()throws Exception {
        for(Family family:new Family[]{Family.ESDC2A,Family.DC2C,Family.ESDC1A,Family.DC1C}) {
            Fixture f=fixture(family,.05,LEFT);Esdc2aExciter e=(Esdc2aExciter)f.exciter;
            f.provider.setIeeeVcData(RIGHT);double scale=(family==Family.ESDC2A||family==Family.DC2C)?1.04:1;
            assertEquals(scale*e.vrmaxVt,e.getDynamicRegulatorUpperLimit(),TOL);
            assertEquals(scale*e.vrminVt,e.getDynamicRegulatorLowerLimit(),TOL);
            f.machine.getDStabBus().setVoltage(new Complex(.96,0));
            scale=(family==Family.ESDC2A||family==Family.DC2C)?.96:1;
            assertEquals(scale*e.vrmaxVt,e.getDynamicRegulatorUpperLimit(),TOL);
            assertEquals(scale*e.vrminVt,e.getDynamicRegulatorLowerLimit(),TOL);
        }
    }

    @Test void rexsysFlimfKeepsPhysicalTerminalVoltage()throws Exception {
        Fixture f=fixture(Family.REXSYS,.05,LEFT);RexsysExciter e=(RexsysExciter)f.exciter;
        e.flimf=1;e.ta=0;e.tp=0;e.vimax=1e6;e.setRefPoint(e.getRefPoint()+1000);
        f.provider.setIeeeVcData(RIGHT);
        assertEquals(1.04*e.vrmax,e.getRegulatorOutput(),TOL);
        assertEquals(1.04*e.vfmax,e.getBridgeOutput(),TOL);
        f.machine.getDStabBus().setVoltage(new Complex(.97,0));
        assertEquals(.97*e.vrmax,e.getRegulatorOutput(),TOL);
        assertEquals(.97*e.vfmax,e.getBridgeOutput(),TOL);
    }

    @Test void st6bVrmultKeepsPhysicalSupply()throws Exception {
        Fixture f=fixture(Family.ST6B,.05,LEFT);St6bExciter e=(St6bExciter)f.exciter;
        double vt=f.machine.getDStabBus().getVoltageMag(),efd=storage(f,"state")[index(f,"EFD")];
        f.provider.setIeeeVcData(RIGHT);assertEquals(0,rhs(f)[index(f,"EFD")],TOL);
        f.machine.getDStabBus().setVoltage(new Complex(.98,0));
        assertEquals((efd*.98/vt-efd)/e.ts,rhs(f)[index(f,"EFD")],TOL);
    }

    @Test void compoundPotentialAndBridgePathsKeepPhysicalPhasors()throws Exception {
        for(Family family:new Family[]{Family.DC4C,Family.ST6C,Family.ESST2A}) {
            Fixture f=fixture(family,.05,LEFT);double source=potential(f),bridge=bridge(f);
            assertEquals(1.04,source,TOL);
            f.provider.setIeeeVcData(RIGHT);assertEquals(source,potential(f),0.0);assertEquals(bridge,bridge(f),0.0);
            f.machine.getDStabBus().setVoltage(new Complex(.98,0));
            assertEquals(.98,potential(f),TOL);assertTrue(Math.abs(bridge(f)-bridge)>1e-3);
        }
    }

    private static Fixture fixture(Family family,double tr,IeeeVcData vc)throws Exception {
        var b=DStabBuilderTestFixture.createWithMachine();Machine m=b.getDStabNetwork().getMachine("Bus1-mach1");
        assertTrue(m.initStates(m.getDStabBus()));m.setEfd(1.2);
        var provider=assertInstanceOf(IeeeVoltageCompensatedMachine.class,m);provider.setIeeeVcData(vc);
        AnnotateExciter e=switch(family) {
            case REXSYS -> new RexsysExciter("exc",rexsys(tr),m);
            case ESDC2A -> new Esdc2aExciter("exc",dc(tr,false),m);
            case ESDC1A -> new Esdc1aExciter("exc",dc(tr,false),m);
            case DC1C -> new Dc1cExciter("exc",(Dc1cData)dc(tr,true),m);
            case DC2C -> new Dc2cExciter("exc",(Dc1cData)dc(tr,true),m);
            case DC4B -> new Dc4bExciter("exc","DC4B",dc4b(tr),m);
            case DC4C -> new Dc4cExciter("exc",dc4c(tr),m);
            case ST6C -> new St6cExciter("exc",st6c(tr),m);
            case ST6B -> new St6bExciter("exc","ST6B",st6b(tr),m);
            case ESST2A -> new Esst2aExciter("exc",esst2a(tr),m);
            case DC3A -> new Dc3aExciter("exc","DC3A",dc3a(tr),m);
            case ESAC4A -> new Esac4aExciter("exc",esac4a(tr),m);
            case IEEET4 -> new Ieeet4Exciter("exc","IEEET4",ieeet4(),m);
        };
        assertTrue(e.initStates(m.getDStabBus(),m),family.toString());return new Fixture(family,m,e,provider);
    }
    // Ordinary valid synthetic profiles based on the registered family fixtures.
    private static RexsysData rexsys(double tr){RexsysData d=new RexsysData();d.setTr(tr);d.setKvp(1);d.setKvi(2);d.setVimax(99);
        d.setTa(.1);d.setVrmax(99);d.setVrmin(-99);d.setKip(1);d.setKii(3);d.setTp(.2);d.setVfmax(99);d.setVfmin(-99);d.setKe(1);d.setTe(.3);return d;}
    private static Esdc2aData dc(double tr,boolean revision){Esdc2aData d=revision?new Dc1cData():new Esdc2aData();
        d.setTr(tr);d.setKa(20);d.setTa(.05);d.setTb(.2);d.setTc(.05);d.setVrmax(5);d.setVrmin(-5);d.setKe(1);d.setTe(.4);d.setKf(.1);d.setTf(.3);d.setSwitchValue(1);return d;}
    private static Dc4bData dc4b(double tr){Dc4bData d=new Dc4bData();d.setTr(tr);d.setKp(3);d.setKi(4);d.setKd(.5);d.setTd(.1);
        d.setVrmax(100);d.setVrmin(-100);d.setKa(2);d.setTa(.2);d.setKe(1);d.setTe(.4);d.setKf(.2);d.setTf(.3);return d;}
    private static Dc4cData dc4c(double tr){Dc4cData d=new Dc4cData();d.setSw1(1);d.setTr(tr);d.setKpr(3);d.setKir(4);d.setKdr(.5);d.setTdr(.1);
        d.setVrmax(100);d.setVrmin(-100);d.setKa(2);d.setTa(.2);d.setKe(1);d.setTe(.4);d.setKf(.2);d.setTf(.3);d.setKp(1);d.setVbmax(10);return d;}
    private static St6bData st6b(double tr){St6bData d=new St6bData();d.setVrmult(1);d.setTr(tr);d.setKpa(3);d.setKia(4);d.setKda(.5);d.setTda(.1);
        d.setVamax(10);d.setVamin(-10);d.setKff(.4);d.setKm(.6);d.setKcl(1);d.setKlr(1);d.setIlr(100);d.setVrmax(5);d.setVrmin(-5);d.setKg(.2);d.setTg(.3);d.setTs(.2);return d;}
    private static St6cData st6c(double tr){St6cData d=new St6cData();d.setOel(1);d.setUel(1);d.setSw1(1);d.setTr(tr);d.setKpa(3);d.setKia(4);d.setKda(.5);d.setTda(.1);
        d.setVamax(99);d.setVamin(-99);d.setKff(.4);d.setKm(.6);d.setKci(1);d.setKlr(1);d.setIlr(100);d.setVrmax(99);d.setVrmin(-99);d.setKg(.2);d.setTg(.3);d.setVmmax(99);d.setVmmin(-99);d.setTa(.2);d.setKp(1);d.setVbmax(99);return d;}
    private static Esst2aData esst2a(double tr){Esst2aData d=new Esst2aData();d.setTr(tr);d.setKa(5);d.setTa(.2);d.setVrmax(10);d.setVrmin(-10);d.setKe(1);d.setTe(.3);
        d.setKf(.1);d.setTf(.4);d.setKp(1);d.setKc(.2);d.setEfdmax(5);return d;}
    private static Dc3aData dc3a(double tr){Dc3aData d=new Dc3aData();d.setTr(tr);d.setKv(.005);d.setVrmax(2);d.setTrh(1);d.setTe(.3);d.setKe(1);return d;}
    private static Esac4aData esac4a(double tr){Esac4aData d=new Esac4aData();d.setTr(tr);d.setVimax(5);d.setVimin(-5);d.setTc(.2);d.setTb(.4);d.setKa(2);d.setTa(.3);d.setVrmax(5);d.setVrmin(-5);d.setKc(.1);return d;}
    private static Ieeet4Data ieeet4(){Ieeet4Data d=new Ieeet4Data();d.setKr(2);d.setTrh(.1);d.setKv(.2);d.setVrmax(5);d.setVrmin(-5);d.setTe(.3);d.setKe(1);return d;}

    private static double sensed(Fixture f)throws Exception{return (double)f.exciter.getClass().getMethod("getSensedVoltage").invoke(f.exciter);}
    private static Field field(Class<?> type,String name)throws Exception {
        for(Class<?> c=type;c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException absent){}
        throw new NoSuchFieldException(name);
    }
    private static double[] storage(Fixture f,String name)throws Exception{return (double[])field(f.exciter.getClass(),name).get(f.exciter);}
    private static int index(Fixture f,String name)throws Exception{return field(f.exciter.getClass(),name).getInt(null);}
    private static int sensorIndex(Fixture f)throws Exception{return index(f,f.exciter instanceof Esdc2aExciter?"SENSED":"VSENSE");}
    private static double[] rhs(Fixture f)throws Exception {
        Class<?> c=f.exciter.getClass();Method method=null;
        while(c!=null)try{method=c.getDeclaredMethod("derivatives",double[].class,double[].class,Machine.class);break;}catch(NoSuchMethodException absent){c=c.getSuperclass();}
        assertNotNull(method);method.setAccessible(true);double[] d=new double[storage(f,"state").length];method.invoke(f.exciter,storage(f,"state"),d,f.machine);return d;
    }
    private static double maxDifference(double[] a,double[] b){double max=0;for(int i=0;i<a.length;i++)max=Math.max(max,Math.abs(a[i]-b[i]));return max;}
    private static String dataJson(Fixture f)throws Exception{return new Gson().toJson(f.exciter.getClass().getMethod("getData").invoke(f.exciter));}
    private static double potential(Fixture f){return switch(f.family){case DC4C->((Dc4cExciter)f.exciter).getCompoundSource();case ST6C->((St6cExciter)f.exciter).getPotentialSource();case ESST2A->((Esst2aExciter)f.exciter).getCompoundSourceVoltage();default->throw new IllegalArgumentException();};}
    private static double bridge(Fixture f){return switch(f.family){case DC4C->((Dc4cExciter)f.exciter).getAvailableSupply();case ST6C->((St6cExciter)f.exciter).getAvailableBridge();case ESST2A->((Esst2aExciter)f.exciter).getBridgeVoltage();default->throw new IllegalArgumentException();};}

    static void checkSensing(java.util.function.BiFunction<
            org.interpss.fadapter.builder.DStabNetworkBuilder, Double, AnnotateExciter> attach)
            throws Exception {
        for (double time : new double[]{0.0, .05}) {
            var builder = DStabBuilderTestFixture.createWithMachine();
            Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
            assertTrue(machine.initStates(machine.getDStabBus()));
            machine.setEfd(1.2);
            var provider = assertInstanceOf(IeeeVoltageCompensatedMachine.class, machine);
            AnnotateExciter exciter = attach.apply(builder, time);
            Method sensor = exciter.getClass().getMethod("getSensedVoltage");
            assertTrue(exciter.initStates(machine.getDStabBus(), machine));
            double plainReference = exciter.getRefPoint();
            double terminal = machine.getDStabBus().getVoltageMag();

            provider.setIeeeVcData(LEFT);
            double initial = provider.getCmlMachineVoltage();
            assertTrue(Math.abs(initial - terminal) > 1e-4);
            assertTrue(exciter.initStates(machine.getDStabBus(), machine));
            assertEquals(initial - terminal, exciter.getRefPoint() - plainReference, TOL);
            assertEquals(initial, (double) sensor.invoke(exciter), TOL);
            assertEquals(1.2, exciter.getOutput(machine), TOL);

            provider.setIeeeVcData(RIGHT);
            double left = provider.getCmlMachineVoltage(), h = .001;
            assertTrue(exciter.nextStep(h, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            double predicted = time == 0 ? left : initial + h * (left - initial) / time;
            assertEquals(predicted, (double) sensor.invoke(exciter), TOL);

            provider.setIeeeVcData(new IeeeVcData(-.012, .019));
            double right = provider.getCmlMachineVoltage();
            assertTrue(exciter.nextStep(h, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
            double corrected = time == 0 ? right
                    : initial + .5 * h * ((left - initial) + (right - predicted)) / time;
            assertEquals(corrected, (double) sensor.invoke(exciter), TOL);
            assertEquals(terminal, machine.getDStabBus().getVoltageMag(), 0.0);
        }
    }
}
