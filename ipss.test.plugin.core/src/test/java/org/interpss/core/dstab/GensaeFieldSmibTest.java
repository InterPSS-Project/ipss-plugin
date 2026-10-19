package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.GensalNativeReference;
import org.interpss.core.dstab.reference.GensaeFieldReference;
import org.interpss.dstab.control.exc.ieee.y1981.st1.IEEE1981ST1Exciter;
import org.interpss.dstab.mach.GensaeMachine;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.StaticLoadModel;
import com.interpss.dstab.algo.*;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.*;

/** Field-current ports and composed-exciter evidence, not complete model acceptance. */
public class GensaeFieldSmibTest extends CorePluginTestSetup {
    private static final List<String> EXC_KEYS=List.of("Measured voltage","First lead-lag","Second lead-lag","Regulator output","Feedback");
    private record Fixture(DynamicSimuAlgorithm algorithm,GensaeMachine machine,Machine reference) {}
    private static Fixture load(String rawText,String input,double step,boolean fault,Path directory)throws Exception {
        Files.createDirectories(directory);Path raw=directory.resolve("network.raw"),dyr=directory.resolve("dynamic.dyr");
        Files.writeString(raw,rawText);Files.writeString(dyr,input);
        var context=new PSSEMultiFileLoader().loadDStab(raw.toString());var net=context.getDStabilityNet();
        net.setStaticLoadIncludedInYMatrix(false);net.setStaticLoadModel(StaticLoadModel.CONST_P);net.setReactiveStaticLoadModel(StaticLoadModel.CONST_P);
        var parser=new PSSEDStabDirectParser(new DStabNetworkBuilder(net)).setStrictImport(true);
        parser.parseDynFile(dyr.toString());assertTrue(parser.getLastImportReport().isStrictlyComplete());
        var algorithm=context.getDynSimuAlgorithm();algorithm.getAclfAlgorithm().setTolerance(1e-10);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(step);algorithm.setTotalSimuTimeSec(.5);algorithm.setSimuOutputHandler(new StateMonitor());
        if(fault)net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,
                SimpleFaultCode.GROUND_3P,new Complex(0,.2),null,.05,.05),"field-three-cycle");
        assertTrue(algorithm.initialization());
        return new Fixture(algorithm,assertInstanceOf(GensaeMachine.class,net.getMachine("Bus1-mach1")),net.getMachine("Bus2-mach1"));
    }
    private static LinkedHashMap<String,Double> snapshot(Fixture f,double time) {
        var m=f.machine();var values=new LinkedHashMap<String,Double>();
        var v=m.getDStabBus().getVoltage();var current=m.getIgen().subtract(v.multiply(m.getYgen()));var power=v.multiply(current.conjugate());
        values.put("time_s",time);values.put("EP_Q",m.getEq1());values.put("PSIQ_PP",-m.getPsiq11());values.put("PSIK_D",m.getPsikd());
        values.put("SPEED_DEV",m.getSpeed()-1);values.put("ANGLE",m.getAngle());
        IEEE1981ST1Exciter exc=m.getExciter()==null?null:assertInstanceOf(IEEE1981ST1Exciter.class,m.getExciter());
        if(exc!=null){var states=exc.getNativeStateSnapshot();String[] names={"EXC_VMEAS","EXC_LL1","EXC_LL2","EXC_VA","EXC_FEEDBACK"};for(int i=0;i<5;i++)values.put(names[i],states.get(EXC_KEYS.get(i)));}
        var ref=f.algorithm().getNetwork().getBus("Bus2").getVoltage();
        values.put("V_BUS1",v.abs());values.put("ANGLE_BUS1",Math.toDegrees(v.getArgument()));values.put("V_BUS2",ref.abs());values.put("ANGLE_BUS2",Math.toDegrees(ref.getArgument()));
        values.put("MACH_ANGLE",Math.toDegrees(m.getAngle()));values.put("P_PU",power.getReal());values.put("Q_PU",power.getImaginary());
        values.put("EFD",m.getEfd());values.put("PMECH",m.getPm());values.put("MACH_SPEED",m.getSpeed()-1);
        values.put("XAD_IFD",m.calculateIfd(MachineIfdBase.EXCITER));values.put("ECOMP",m.getCmlMachineVoltage());if(exc!=null)values.put("VREF",exc.getRefPoint());
        values.put("REF_ANGLE",Math.toDegrees(f.reference().getAngle()));values.put("REF_SPEED",f.reference().getSpeed()-1);return values;
    }
    private static void append(StringBuilder text,Map<String,Double> row) {
        boolean first=true;for(double value:row.values()){assertTrue(Double.isFinite(value));if(!first)text.append(',');text.append(value);first=false;}text.append('\n');
    }
    @Test void allProfilesImportAndProduceCompleteCoupledDiagnosticTraces(@TempDir Path dir)throws Exception {
        assertEquals(22,GensaeFieldReference.profiles().size());
        var errors=new LinkedHashMap<String,List<Double>>();
        for(var p:GensaeFieldReference.profiles()) {
            var f=load(p.network(),p.input(),p.step(),p.fault(),dir.resolve(p.name()));var initial=snapshot(f,0);
            assertEquals(p.closed(),f.machine().getExciter()!=null);var trace=new StringBuilder(String.join(",",initial.keySet())+"\n");
            var points=new ArrayList<Map<String,Double>>();
            int ticks=(int)Math.round(.5/p.step());for(int tick=0;tick<=ticks;tick++) {
                var row=snapshot(f,tick*p.step());points.add(row);append(trace,row);
                if(tick<ticks)assertTrue(f.algorithm().solveDEqnStep(true));
            }
            Path output=Path.of("target/gensae-field-coupled",p.name()+".csv");Files.createDirectories(output.getParent());Files.writeString(output,trace);
            errors.put(p.name(),compareAllStateRows(p,points));
        }
        for(var p:GensaeFieldReference.profiles())if(p.fault() && p.name().endsWith("-coarse")) {
            var coarse=errors.get(p.name());var fine=errors.get(p.name().replace("-coarse","-fine"));
            for(int j=0;j<coarse.size();j++)if(j!=4 && coarse.get(j)>(j==3?1e-7:1e-6))
                assertTrue(fine.get(j)<.7*coarse.get(j),p.name()+" state "+j+" must shrink under step halving");
        }
    }
    private static List<Double> compareAllStateRows(GensaeFieldReference.Profile p,List<Map<String,Double>> points) {
        var names=new ArrayList<>(List.of("EP_Q","PSIQ_PP","PSIK_D","SPEED_DEV","ANGLE"));
        if(p.closed())names.addAll(List.of("EXC_VMEAS","EXC_LL1","EXC_LL2","EXC_VA","EXC_FEEDBACK"));
        assertEquals(names.size(),p.stateBounds().size());var maximum=new ArrayList<>(Collections.nCopies(names.size(),0.));
        var rows=HplntdSwitchingNativeSmibTest.rows(p.body());assertEquals(p.samples(),rows.size());
        for(var expected:rows) {
            double position=Math.max(0,Math.min(points.size()-1,expected.get("time_s")/p.step()));
            int lower=(int)Math.floor(position),upper=Math.min(points.size()-1,lower+1);double fraction=position-lower;
            for(int j=0;j<names.size();j++) {
                String key=names.get(j);double actual=points.get(lower).get(key)+fraction*(points.get(upper).get(key)-points.get(lower).get(key));
                double error=Math.abs(actual-expected.get(key));maximum.set(j,Math.max(maximum.get(j),error));
                assertTrue(error<=p.stateBounds().get(j),p.name()+" "+key+" @"+expected.get("time_s")+" error="+error);
            }
        }
        return maximum;
    }
    @Test void referencesRetainExactSchemaFiniteHistoryAndEveryEventSide() {
        for(var p:GensaeFieldReference.profiles()) {
            var rows=HplntdSwitchingNativeSmibTest.rows(p.body());assertEquals(p.samples(),rows.size());
            assertEquals(p.closed()?26:20,rows.getFirst().size());assertTrue(rows.getFirst().get("time_s")<0);
            assertTrue(rows.stream().flatMap(r->r.values().stream()).allMatch(Double::isFinite));
            int duplicates=0;for(int i=1;i<rows.size();i++)if(rows.get(i).get("time_s").equals(rows.get(i-1).get("time_s")))duplicates++;
            // Interval boundaries are exported twice even in the no-fault
            // discriminator. Keep those pause rows as well as event sides.
            assertEquals(2,duplicates,p.name());
        }
    }
    @Test void instantaneousFieldCurrentReplaysEveryNativeRawRow(@TempDir Path dir)throws Exception {
        for(var p:GensaeFieldReference.profiles()) {
            var f=load(p.network(),p.input(),p.step(),false,dir.resolve(p.name()));var m=f.machine();
            var rows=HplntdSwitchingNativeSmibTest.rows(p.body());assertEquals(p.samples(),rows.size());
            double maximum=0,at=0;var trace=new StringBuilder("time_s,expected_ifd,actual_ifd\n");
            for(var expected:rows) {
                m.setEq1(expected.get("EP_Q"));m.setPsikd(expected.get("PSIK_D"));m.setPsiq11(-expected.get("PSIQ_PP"));
                m.setSpeed(1+expected.get("SPEED_DEV"));m.setAngle(expected.get("ANGLE"));
                double angle=Math.toRadians(expected.get("ANGLE_BUS1")),magnitude=expected.get("V_BUS1");
                m.getDStabBus().setVoltage(new Complex(magnitude*Math.cos(angle),magnitude*Math.sin(angle)));m.getIgen();
                double actual=m.calculateIfd(MachineIfdBase.EXCITER),error=Math.abs(actual-expected.get("XAD_IFD"));
                assertTrue(error<=p.fieldBound(),p.name()+" field current @"+expected.get("time_s")+" error="+error);
                if(error>maximum){maximum=error;at=expected.get("time_s");}
                trace.append(expected.get("time_s")).append(',').append(expected.get("XAD_IFD")).append(',').append(actual).append('\n');
            }
            Path output=Path.of("target/gensae-field-replay",p.name()+".csv");Files.createDirectories(output.getParent());Files.writeString(output,trace);
            System.out.println("GENSAE field replay "+p.name()+" maximum="+maximum+" at="+at);
        }
    }
    @Test void composedFieldVoltageGateReplaysRecordedRegulatorAndMachineBoundaries(@TempDir Path dir)throws Exception {
        for(var p:GensaeFieldReference.profiles())if(p.closed()) {
            var f=load(p.network(),p.input(),p.step(),false,dir.resolve(p.name()));var m=f.machine();
            var exc=assertInstanceOf(IEEE1981ST1Exciter.class,m.getExciter());var regulator=exc.getNamedStateBlocks().get("kaDelayBlock");
            var rows=HplntdSwitchingNativeSmibTest.rows(p.body());double maximum=0,at=0;int ceiling=0,lr=0;
            var trace=new StringBuilder("time_s,expected_efd,actual_efd,ifd,upper,lr_active,upper_active\n");
            for(var expected:rows) {
                m.setEq1(expected.get("EP_Q"));m.setPsikd(expected.get("PSIK_D"));m.setPsiq11(-expected.get("PSIQ_PP"));
                m.setSpeed(1+expected.get("SPEED_DEV"));m.setAngle(expected.get("ANGLE"));
                double angle=Math.toRadians(expected.get("ANGLE_BUS1")),magnitude=expected.get("V_BUS1");
                m.getDStabBus().setVoltage(new Complex(magnitude*Math.cos(angle),magnitude*Math.sin(angle)));m.getIgen();
                regulator.setStateX(expected.get("EXC_VA"));
                double actual=exc.getStaggeredFieldVoltage(),error=Math.abs(actual-expected.get("EFD"));
                double ifd=m.calculateIfd(MachineIfdBase.EXCITER),upper=exc.getFieldVoltageUpperLimit();
                boolean lrActive=exc.klr>0 && ifd>exc.ilr,upperActive=exc.getPostFieldCurrentLimiterSignal()>upper;
                if(lrActive)lr++;if(upperActive)ceiling++;
                if(error>maximum){maximum=error;at=expected.get("time_s");}
                trace.append(expected.get("time_s")).append(',').append(expected.get("EFD")).append(',').append(actual).append(',').append(ifd).append(',').append(upper)
                        .append(',').append(lrActive?1:0).append(',').append(upperActive?1:0).append('\n');
            }
            Path output=Path.of("target/gensae-field-gate-replay",p.name()+".csv");Files.createDirectories(output.getParent());Files.writeString(output,trace);
            System.out.println("GENSAE field gate "+p.name()+" maximum="+maximum+" at="+at+" lr="+lr+" ceiling="+ceiling);
            if(p.name().startsWith("esst1a-ceiling-"))assertTrue(ceiling>0,"Current-derated ceiling must actually activate");
            if(p.name().startsWith("esst1a-fault-") || p.name().startsWith("esst1a-compensated-"))assertTrue(lr>0,"Field-current limiter must actually activate");
        }
    }
    @Test void cachedFieldGateReplaysEveryNativeHistoryAndEventRow(@TempDir Path dir)throws Exception {
        for(var p:GensaeFieldReference.profiles())if(p.closed()) {
            var f=load(p.network(),p.input(),p.step(),false,dir.resolve(p.name()));var m=f.machine();
            var exc=assertInstanceOf(IEEE1981ST1Exciter.class,m.getExciter());
            var regulator=exc.getNamedStateBlocks().get("kaDelayBlock");
            var rows=HplntdSwitchingNativeSmibTest.rows(p.body());
            var previous=rows.getFirst();double maximum=0;
            var trace=new StringBuilder("time_s,expected_efd,actual_efd,native_history_equation\n");
            for(var expected:rows) {
                // Native EFD is a cached controller port: current regulator
                // storage with the preceding recorded machine/network boundary.
                // Repeated event-side rows are distinct and must not be removed.
                m.setEq1(previous.get("EP_Q"));m.setPsikd(previous.get("PSIK_D"));m.setPsiq11(-previous.get("PSIQ_PP"));
                m.setSpeed(1+previous.get("SPEED_DEV"));m.setAngle(previous.get("ANGLE"));
                double angle=Math.toRadians(previous.get("ANGLE_BUS1")),magnitude=previous.get("V_BUS1");
                m.getDStabBus().setVoltage(new Complex(magnitude*Math.cos(angle),magnitude*Math.sin(angle)));m.getIgen();
                regulator.setStateX(expected.get("EXC_VA"));
                double actual=exc.getStaggeredFieldVoltage();
                double field=previous.get("XAD_IFD");
                double equation=Math.max(magnitude*exc.vrmin,Math.min(magnitude*exc.vrmax-exc.kc*field,
                        expected.get("EXC_VA")-Math.max(0,exc.klr*(field-exc.ilr))));
                double error=Math.abs(actual-expected.get("EFD"));maximum=Math.max(maximum,error);
                assertEquals(expected.get("EFD"),equation,4e-7,p.name()+" native cached equation @"+expected.get("time_s"));
                assertEquals(expected.get("EFD"),actual,p.historyGateBound(),p.name()+" staged gate @"+expected.get("time_s"));
                trace.append(expected.get("time_s")).append(',').append(expected.get("EFD")).append(',').append(actual).append(',').append(equation).append('\n');
                previous=expected;
            }
            Path output=Path.of("target/gensae-field-history-gate",p.name()+".csv");Files.createDirectories(output.getParent());Files.writeString(output,trace);
            System.out.println("GENSAE history gate "+p.name()+" maximum="+maximum);
        }
    }
    @Test void initialStatesAndFieldPortsAreIndependentNativeChecks(@TempDir Path dir)throws Exception {
        for(var p:GensaeFieldReference.profiles()) {
            var f=load(p.network(),p.input(),p.step(),false,dir.resolve(p.name()));var expected=HplntdSwitchingNativeSmibTest.rows(p.body()).getFirst();
            for(var value:snapshot(f,0).entrySet())if(!value.getKey().equals("time_s")) {
                double bound=value.getKey().contains("ANGLE")?3e-5:3e-6;
                assertEquals(expected.get(value.getKey()),value.getValue(),bound,p.name()+" initial "+value.getKey());
            }
        }
    }
    @Test void composedExciterHasAnImmutableNonmutatingPublishedCoordinateSnapshot(@TempDir Path dir)throws Exception {
        var p=GensalNativeReference.profiles().stream().filter(v->v.name().equals("exponential-flat-coarse")).findFirst().orElseThrow();
        var f=load(p.network(),p.input()+"1 'ESST1A' '1' 1 1 .02 .8 -.8 .2 .8 .1 .4 80 .04 99 -99 7.3 -7.3 .2 .1 1 2 1.3 /\n",p.step(),false,dir);
        var m=f.machine();var exc=assertInstanceOf(IEEE1981ST1Exciter.class,m.getExciter());
        var raw=exc.getNamedStates();var observed=exc.getNativeStateSnapshot();
        assertEquals(List.of("Measured voltage","First lead-lag","Second lead-lag","Regulator output","Feedback"),new ArrayList<>(observed.keySet()));
        assertThrows(UnsupportedOperationException.class,()->observed.put("Feedback",99.));
        assertEquals(raw,exc.getNamedStates());
        assertEquals(.8*raw.get("filterBlock"),observed.get("First lead-lag"),1e-15);
        assertEquals(.4*raw.get("filterBlock1"),observed.get("Second lead-lag"),1e-15);
        assertEquals(m.getEfd()/80*(.8-.2),observed.get("First lead-lag"),1e-14);
        assertEquals(m.getEfd()/80*(.4-.1),observed.get("Second lead-lag"),1e-14);
        assertEquals(.1*m.getEfd(),observed.get("Feedback"),1e-14);
        assertEquals(m.getEfd(),m.calculateIfd(MachineIfdBase.EXCITER),1e-12);
        System.out.println("GENSAE ESST1A raw="+raw+" published="+observed);
    }
    @Test void nativeWashoutObservesThePreCeilingSignalWhenTheOutputIsClipped(@TempDir Path dir)throws Exception {
        var p=GensalNativeReference.profiles().stream().filter(v->v.name().equals("exponential-flat-coarse")).findFirst().orElseThrow();
        var f=load(p.network(),p.input()+"1 'ESST1A' '1' 1 1 .02 .8 -.8 .2 .8 .1 .4 80 .04 99 -99 1.6 -7.3 .2 .1 1 0 1.3 /\n",p.step(),false,dir);
        var exc=assertInstanceOf(IEEE1981ST1Exciter.class,f.machine().getExciter());var blocks=exc.getNamedStateBlocks();
        blocks.get("kaDelayBlock").setStateX(3.0);
        assertEquals(3.0,exc.getRegulatorOutput(),1e-14);
        assertTrue(exc.getStaggeredFieldVoltage()<1.5,"The output ceiling must be active");
        exc.uel=3;exc.setVuel(4);assertEquals(4,exc.rateFeedbackInputBlock.getY(),1e-14);
        exc.setVoel(2);assertEquals(2,exc.rateFeedbackInputBlock.getY(),1e-14);
        exc.uel=1;exc.clearVoel();
        double before=blocks.get("washoutBlock").getStateX();
        assertTrue(exc.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,f.machine(),0));
        double increment=blocks.get("washoutBlock").getStateX()-before;
        System.out.println("Feedback discriminator before="+before+" after="+exc.getNativeStateSnapshot()+" input="+exc.rateFeedbackInputBlock.getY());
        assertTrue(increment>1e-4,"Feedback must see the pre-ceiling regulator signal, not the clipped field output: "+increment);
    }
}
