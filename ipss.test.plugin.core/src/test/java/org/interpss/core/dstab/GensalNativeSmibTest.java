package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.*;
import org.interpss.core.dstab.reference.GensalNativeReference;
import com.interpss.dstab.mach.model.GensaeMachine;
import com.interpss.dstab.mach.ieeevc.IeeeVcSalientPoleMachine;
import com.interpss.dstab.mach.ieeevc.IeeeVcData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.dstab.StaticLoadModel;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.algo.*;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.SalientPoleMachine;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import com.interpss.state.dstab.MachineState;

/** Native salient-pole contract tests; complete acceptance is tracked separately. */
public class GensalNativeSmibTest extends CorePluginTestSetup {
    private record Fixture(DynamicSimuAlgorithm algorithm,Machine machine,Machine reference) {}
    private static final String HEADER="time_s,EP_Q,PSIK_D,PSIQ_PP,SPEED_DEV,ANGLE,V_BUS1,ANGLE_BUS1,V_BUS2,ANGLE_BUS2,MACH_ANGLE,P_PU,Q_PU,EFD,PMECH,MACH_SPEED,REF_ANGLE,REF_SPEED\n";
    private static Fixture load(String rawText,String input,double step,Path directory)throws Exception {
        return load(rawText,input,step,directory,false);
    }
    private static Fixture load(String rawText,String input,double step,Path directory,boolean fault)throws Exception {
        Files.createDirectories(directory);Path raw=directory.resolve("network.raw"),dyr=directory.resolve("dynamic.dyr");
        Files.writeString(raw,rawText);Files.writeString(dyr,input);
        var context=new PSSEMultiFileLoader().loadDStab(raw.toString());var net=context.getDStabilityNet();
        net.setStaticLoadIncludedInYMatrix(false);net.setStaticLoadModel(StaticLoadModel.CONST_P);
        net.setReactiveStaticLoadModel(StaticLoadModel.CONST_P);
        var parser=new PSSEDStabDirectParser(new DStabNetworkBuilder(net)).setStrictImport(true);
        parser.parseDynFile(dyr.toString());assertTrue(parser.getLastImportReport().isStrictlyComplete());
        var algorithm=context.getDynSimuAlgorithm();algorithm.getAclfAlgorithm().setTolerance(1e-10);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(step);algorithm.setTotalSimuTimeSec(.5);algorithm.setSimuOutputHandler(new StateMonitor());
        if(fault)net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,
                SimpleFaultCode.GROUND_3P,new Complex(0,.2),null,.05,.05),"salient-three-cycle");
        assertTrue(algorithm.initialization());return new Fixture(algorithm,net.getMachine("Bus1-mach1"),net.getMachine("Bus2-mach1"));
    }
    private static Fixture load(GensalNativeReference.Profile p,Path dir)throws Exception {
        return load(p.network(),p.input(),p.step(),dir,p.fault());
    }
    private static double[] snapshot(Fixture f,double time) {
        var m=f.machine();var s=assertInstanceOf(SalientPoleMachine.class,m);var v=m.getDStabBus().getVoltage();
        var power=v.multiply(m.getIgen().subtract(v.multiply(m.getYgen())).conjugate());
        var ref=f.algorithm().getNetwork().getBus("Bus2").getVoltage();
        return new double[]{time,s.getEq1(),s.getPsikd(),-s.getPsiq11(),m.getSpeed()-1,m.getAngle(),v.abs(),
                Math.toDegrees(v.getArgument()),ref.abs(),Math.toDegrees(ref.getArgument()),Math.toDegrees(m.getAngle()),
                power.getReal(),power.getImaginary(),m.getEfd(),m.getPm(),m.getSpeed()-1,
                Math.toDegrees(f.reference().getAngle()),f.reference().getSpeed()-1};
    }
    @Test void everyImportedProfileRunsCoupledAndRetainsUnforcedNamedStates(@TempDir Path dir)throws Exception {
        Map<String,double[]> errors=new LinkedHashMap<>();
        for(var p:GensalNativeReference.profiles()) {
            var f=load(p,dir.resolve(p.name()));var m=f.machine();
            if(p.name().startsWith("exponential-"))assertInstanceOf(GensaeMachine.class,m);
            else assertInstanceOf(IeeeVcSalientPoleMachine.class,m);
            var states=assertInstanceOf(ICMLStateProvider.class,m).getNamedStates();
            assertEquals(p.name().startsWith("exponential-")?List.of("E'q","Psi''q","Psi'd","Speed deviation","Angle"):
                    List.of("E'q","Psi'd","Psi''q","Speed deviation","Angle"),new ArrayList<>(states.keySet()));
            assertThrows(UnsupportedOperationException.class,()->states.put("Angle",99.));
            var trace=new StringBuilder(HEADER);int ticks=(int)Math.round(.5/p.step());var points=new ArrayList<double[]>();
            for(int tick=0;tick<=ticks;tick++) {
                var row=snapshot(f,tick*p.step());points.add(row);
                for(int i=0;i<row.length;i++){assertTrue(Double.isFinite(row[i]),p.name()+" column "+i);if(i>0)trace.append(',');trace.append(row[i]);}
                trace.append('\n');if(tick<ticks)assertTrue(f.algorithm().solveDEqnStep(true));
            }
            Path output=Path.of("target/gensal-native-coupled",p.name()+".csv");
            Files.createDirectories(output.getParent());Files.writeString(output,trace);
            assertEquals(0,states.get("Speed deviation"));
            errors.put(p.name(),compareEveryNativeStateRow(p,points));
        }
        for(var p:GensalNativeReference.profiles())if(p.fault() && p.name().endsWith("-coarse")) {
            double[] coarse=errors.get(p.name()),fine=errors.get(p.name().replace("-coarse","-fine"));
            for(int j=0;j<4;j++)if(coarse[j]>(j==3?1e-7:1e-5))
                assertTrue(fine[j]<=.65*coarse[j],p.name()+" state "+j+" must shrink under step halving");
        }
    }
    private static double[] compareEveryNativeStateRow(GensalNativeReference.Profile p,List<double[]> points) {
        String[] names={"EP_Q","PSIK_D","PSIQ_PP","SPEED_DEV","ANGLE"};double[] maximum=new double[5],at=new double[5];
        assertEquals(5,p.stateBounds().length);
        for(var expected:HplntdSwitchingNativeSmibTest.rows(p.body())) {
            double time=expected.get("time_s"),position=Math.max(0,Math.min(points.size()-1,time/p.step()));
            int lower=(int)Math.floor(position),upper=Math.min(points.size()-1,lower+1);double fraction=position-lower;
            for(int j=0;j<5;j++) {
                double actual=points.get(lower)[j+1]+fraction*(points.get(upper)[j+1]-points.get(lower)[j+1]);
                double error=Math.abs(expected.get(names[j])-actual);
                if(error>maximum[j]){maximum[j]=error;at[j]=time;}
                assertTrue(error<=p.stateBounds()[j],p.name()+" "+names[j]+" raw row t="+time+" error="+error);
            }
        }
        System.out.println("GENSAL native states "+p.name()+" maxima="+Arrays.toString(maximum)+" at="+Arrays.toString(at));
        return maximum;
    }
    @Test void nativeInitialCoordinatesAndPhasorsMatchWithoutStateForcing(@TempDir Path dir)throws Exception {
        for(var p:GensalNativeReference.profiles()) {
            var f=load(p,dir.resolve(p.name()));double[] actual=snapshot(f,0);
            var expected=HplntdSwitchingNativeSmibTest.rows(p.body()).getFirst();String[] names=HEADER.strip().split(",");
            assertEquals(17,p.initialBounds().length);
            for(int i=1;i<actual.length;i++) {
                assertEquals(expected.get(names[i]),actual[i],p.initialBounds()[i-1],p.name()+" initial "+names[i]);
            }
            if(f.machine() instanceof GensaeMachine m)assertArrayEquals(new double[3],m.getElectricalDerivatives(),1e-10);
        }
    }
    @Test void rawHistoriesHaveExactFiveStateAllocationAndBothPhasorBoundaries() {
        assertEquals(32,GensalNativeReference.profiles().size());
        for(var p:GensalNativeReference.profiles()) {
            var rows=HplntdSwitchingNativeSmibTest.rows(p.body());assertEquals(p.samples(),rows.size());
            assertEquals(18,rows.getFirst().size());assertTrue(rows.getFirst().get("time_s")<0);
            assertTrue(rows.stream().flatMap(r->r.values().stream()).allMatch(Double::isFinite));
            assertEquals(0,rows.getFirst().get("SPEED_DEV"));
        }
    }
    @Test void zeroSaturationRetainsTheCoreElectricalAndMechanicalStageConvention(@TempDir Path dir)throws Exception {
        String raw=Files.readString(Path.of("testData/adpter/psse/v33/SMIB/SMIB_v33.raw"));
        String input="1 'GENSAL' '1' 6 .033 .078 6.4 0 .8958 .8645 .1189 .089 .0521 0 0 /\n2 'GENCLS' '1' 99999 0 /\n";
        var core=load(raw,input,.0005,dir.resolve("core"),true);
        var exponential=load(raw,input.replace("GENSAL","GENSAE"),.0005,dir.resolve("exponential"),true);
        for(int tick=0;tick<=1000;tick++) {
            var a=snapshot(core,tick*.0005);var b=snapshot(exponential,tick*.0005);
            assertArrayEquals(a,b,1e-11,"Zero-saturation shared limit at tick "+tick);
            if(tick<1000){assertTrue(core.algorithm().solveDEqnStep(true));assertTrue(exponential.algorithm().solveDEqnStep(true));}
        }
    }
    @Test void exactNativeCardinalityRejectsMissingAndExtraConstantsForBothIdentifiers(@TempDir Path dir)throws Exception {
        String raw=Files.readString(Path.of("testData/adpter/psse/v33/SMIB/SMIB_v33.raw"));
        String constants="6 .033 .078 6.4 0 .8958 .8645 .1189 .089 .0521 .1 .4";
        for(String model:List.of("GENSAL","GENSAE"))for(boolean extra:List.of(false,true)) {
            String input="1 '"+model+"' '1' "+(extra?constants+" 1":constants.substring(0,constants.lastIndexOf(' ')))+" /\n2 'GENCLS' '1' 99999 0 /\n";
            assertThrows(com.interpss.common.exp.InterpssException.class,()->load(raw,input,.0005,dir.resolve(model+extra)));
        }
    }
    @Test void predictorAndCorrectorUseDistinctExplicitDerivativesAndRejectInvalidStages(@TempDir Path dir)throws Exception {
        var p=GensalNativeReference.profiles().stream().filter(v->v.name().equals("exponential-flat-coarse")).findFirst().orElseThrow();
        var f=load(p,dir);var m=assertInstanceOf(GensaeMachine.class,f.machine());var net=f.algorithm().getNetwork();
        m.getDStabBus().setVoltage(m.getDStabBus().getVoltage().multiply(.9));m.getIgen();
        double dt=.0005;double[] old={m.getEq1(),-m.getPsiq11(),m.getPsikd()},first=m.getElectricalDerivatives();
        assertTrue(m.nextStepElectrical(dt,DynamicSimuMethod.MODIFIED_EULER,net,0));
        assertArrayEquals(new double[]{old[0]+dt*first[0],old[1]+dt*first[1],old[2]+dt*first[2]},
                new double[]{m.getEq1(),-m.getPsiq11(),m.getPsikd()},1e-14);
        m.getIgen();double[] second=m.getElectricalDerivatives();
        assertFalse(Arrays.equals(first,second));
        assertTrue(m.nextStepElectrical(dt,DynamicSimuMethod.MODIFIED_EULER,net,1));
        assertArrayEquals(new double[]{old[0]+.5*dt*(first[0]+second[0]),old[1]+.5*dt*(first[1]+second[1]),old[2]+.5*dt*(first[2]+second[2])},
                new double[]{m.getEq1(),-m.getPsiq11(),m.getPsikd()},1e-14);
        var before=m.getNamedStates();
        assertThrows(IllegalArgumentException.class,()->m.nextStepElectrical(Double.NaN,DynamicSimuMethod.MODIFIED_EULER,net,0));
        assertThrows(IllegalArgumentException.class,()->m.nextStepElectrical(-dt,DynamicSimuMethod.MODIFIED_EULER,net,0));
        assertThrows(UnsupportedOperationException.class,()->m.nextStepElectrical(dt,DynamicSimuMethod.MODIFIED_EULER,net,7));
        assertEquals(before,m.getNamedStates());
    }
    @Test void reinitializationUsesTheNewSolvedOperatingPointNotOldAdapterMemory(@TempDir Path dir)throws Exception {
        var p=GensalNativeReference.profiles().stream().filter(v->v.name().equals("exponential-flat-coarse")).findFirst().orElseThrow();
        var f=load(p,dir.resolve("existing"));var m=assertInstanceOf(GensaeMachine.class,f.machine());
        m.getParentGen().setGen(new Complex(.60001,m.getParentGen().getGen().getImaginary()));
        assertTrue(f.algorithm().getAclfAlgorithm().loadflow());assertTrue(m.initStates(m.getDStabBus()));
        var fresh=load(p.network().replace("1,'1',50.001,","1,'1',60.001,"),p.input(),p.step(),dir.resolve("fresh"));
        var expected=assertInstanceOf(GensaeMachine.class,fresh.machine()).getNamedStates();
        for(var entry:expected.entrySet())assertEquals(entry.getValue(),m.getNamedStates().get(entry.getKey()),1e-10,entry.getKey());
        assertArrayEquals(new double[3],m.getElectricalDerivatives(),1e-10);
    }
    @Test void unsupportedCheckpointAndUninitializedDerivativePathsAreExplicit(@TempDir Path dir)throws Exception {
        assertThrows(IllegalStateException.class,()->new GensaeMachine().getElectricalDerivatives());
        var p=GensalNativeReference.profiles().stream().filter(v->v.name().equals("exponential-flat-coarse")).findFirst().orElseThrow();
        var f=load(p,dir);assertFalse(MachineState.supports(f.machine()),"Do not silently reconstruct GENSAE as a core d-axis-only machine");
    }
    @Test void nativeCompensationRecordUsesTheSharedAlgebraicBoundary(@TempDir Path dir)throws Exception {
        var p=GensalNativeReference.profiles().stream().filter(v->v.name().equals("exponential-flat-coarse")).findFirst().orElseThrow();
        var f=load(p.network(),p.input()+"1 'IEEEVC' '1' .01 .02 /\n",p.step(),dir);
        var m=assertInstanceOf(GensaeMachine.class,f.machine());assertEquals(new IeeeVcData(.01,.02),m.getIeeeVcData());
        var voltage=m.getDStabBus().getVoltage();var current=m.getIgen().subtract(voltage.multiply(m.getYgen()));
        double expected=voltage.add(new Complex(.01,.02).multiply(m.getZMultiFactor()).multiply(current)).abs()/m.getVMultiFactor();
        assertEquals(expected,m.getCmlMachineVoltage(),1e-14);assertTrue(Math.abs(expected-voltage.abs())>1e-4);
    }
    @Test void exponentialBothAxisIdentifierCannotInitializeLikeDaxisOnlyQuadratic(@TempDir Path dir)throws Exception {
        String raw=Files.readString(Path.of("testData/adpter/psse/v33/SMIB/SMIB_v33.raw"));
        String quadratic="1 'GENSAL' '1' 6 .033 .078 6.4 0 .8958 .8645 .1189 .089 .0521 .1 .4 /\n2 'GENCLS' '1' 99999 0 /\n";
        var a=load(raw,quadratic,.0005,dir.resolve("quadratic"));
        var b=load(raw,quadratic.replace("GENSAL","GENSAE"),.0005,dir.resolve("exponential"));
        assertTrue(Math.abs(a.machine().getAngle()-b.machine().getAngle())>.001,
                "Native both-axis saturation produces a distinct equilibrium rotor angle");
        assertTrue(Math.abs(a.machine().getEfd()-b.machine().getEfd())>.01,
                "Native d-axis-only and both-axis saturation cannot have identical field initialization");
    }
}
