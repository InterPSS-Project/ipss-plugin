package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.Tgov1LimitNativeReference;
import org.interpss.core.dstab.reference.Tgov1LimitNativeReference.Profile;
import org.interpss.dstab.control.gov.psse.tgov1.PsseTGov1SteamTurGovernor;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Active valve limits and recorded-input replay, distinct from complete coupled parity. */
public class Tgov1LimitNativeConformanceTest extends CorePluginTestSetup {
    static Stream<Profile> profiles(){return Tgov1LimitNativeReference.profiles().stream();}

    static com.interpss.simu.SimuContext load(Path input)throws Exception {
        return load(input,true);
    }

    static com.interpss.simu.SimuContext load(Path input,boolean initialize)throws Exception {
        Files.createDirectories(input);Files.writeString(input.resolve("network.raw"),Tgov1LimitNativeReference.network());
        Files.writeString(input.resolve("dynamic.dyr"),Tgov1LimitNativeReference.input());
        var context=new PSSEMultiFileLoader().loadDStab(input.resolve("network.raw").toString());
        var parser=new PSSEDStabDirectParser(new DStabNetworkBuilder(context.getDStabilityNet())).setStrictImport(true);
        parser.parseDynFile(input.resolve("dynamic.dyr").toString());assertTrue(parser.getLastImportReport().isStrictlyComplete());
        assertEquals(3,parser.getLastImportReport().totalRecordCount());
        var algorithm=context.getDynSimuAlgorithm();algorithm.getAclfAlgorithm().setTolerance(1e-10);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuOutputHandler(new StateMonitor());if(initialize)assertTrue(algorithm.initialization());return context;
    }

    @Test void nativeCardinalityAndFixedReferenceFingerprints() {
        var descriptor=DynamicModelCatalog.find("TGOV1").orElseThrow();assertEquals(7,descriptor.parameterCount());
        assertEquals(4,Tgov1LimitNativeReference.profiles().size());
        for(var p:Tgov1LimitNativeReference.profiles()) {
            var rows=HplntdSwitchingNativeSmibTest.rows(p.body());assertEquals(p.samples(),rows.size());
            assertTrue(rows.getFirst().get("time_s")<0);int duplicates=0,limited=0;double previous=Double.NEGATIVE_INFINITY;
            double reference=rows.getFirst().get("GOV_REFERENCE");
            for(var row:rows) {
                for(double value:row.values())assertTrue(Double.isFinite(value));double time=row.get("time_s");
                assertTrue(time>=previous);if(time==previous)duplicates++;previous=time;
                assertEquals(reference,row.get("GOV_REFERENCE"),0,"Stored model reference is not the external command increment");
                if(Math.abs(row.get("GOV_STATE_1")-(p.upper()?1.2:.2))<1e-7)limited++;
            }
            assertEquals(2,duplicates);assertTrue(limited>50,p.name()+" must sustain the selected native bound");
            assertTrue(Math.abs(rows.getLast().get("GOV_STATE_1")-(p.upper()?1.2:.2))>.01,"Release must leave the bound");
        }
    }

    @Test void nativeTurbineStorageCoordinateFollowsThePublishedTransferFunction() {
        for(var p:Tgov1LimitNativeReference.profiles()) {
            double maximum=0;
            for(var row:HplntdSwitchingNativeSmibTest.rows(p.body())) {
                double expected=(.1/.3)*row.get("GOV_STATE_1")+row.get("GOV_STATE_2")/.3-.5*row.get("SPEED");
                maximum=Math.max(maximum,Math.abs(expected-row.get("PMECH")));
            }
            System.out.println("TGOV1 native coordinate "+p.name()+" residual="+maximum);
            assertTrue(maximum<3e-7,p.name()+" physical output identity");
        }
    }

    @Test void replayEveryRawStateAndOutputRefinesWithoutChangingTheModel(@TempDir Path input)throws Exception {
        var errors=new LinkedHashMap<String,double[]>();
        for(var p:Tgov1LimitNativeReference.profiles())errors.put(p.name(),replay(p,input.resolve(p.name())));
        for(String direction:List.of("upper","lower")) {
            double[] coarse=errors.get(direction+"-coarse"),fine=errors.get(direction+"-fine");
            for(int i=0;i<coarse.length;i++)assertTrue(fine[i]<.65*coarse[i],direction+" metric "+i+" must refine");
        }
    }

    private static double[] replay(Profile p,Path input)throws Exception {
        var context=load(input);var machine=context.getDStabilityNet().getMachine("Bus1-mach1");
        var governor=(PsseTGov1SteamTurGovernor)machine.getGovernor();
        var rows=HplntdSwitchingNativeSmibTest.rows(p.body());var old=rows.getFirst();
        // This is an explicitly forced mechanical boundary, not a predicted machine initialization.
        machine.setPm(old.get("PMECH"));machine.setSpeed(1+old.get("SPEED"));
        assertTrue(governor.initStates(machine.getDStabBus(),machine));
        double increment=0,previous=old.get("time_s");double[] maximum=new double[3],at=new double[3];
        var trace=new StringBuilder("time_s,ipss_valve,native_valve,ipss_storage,native_storage,ipss_pm,native_pm\n");
        for(var row:rows) {
            double time=row.get("time_s");
            if(time>previous) {
                governor.setRefPoint(old.get("GOV_REFERENCE")+increment);machine.setSpeed(1+old.get("SPEED"));
                assertTrue(governor.nextStep(p.step(),DynamicSimuMethod.MODIFIED_EULER,machine,0));
                machine.setSpeed(1+row.get("SPEED"));assertTrue(governor.nextStep(p.step(),DynamicSimuMethod.MODIFIED_EULER,machine,1));
            } else if(time==previous) {
                if(Math.abs(time-p.apply())<1e-10)increment=p.increment();
                if(Math.abs(time-p.release())<1e-10)increment=0;
                governor.setRefPoint(row.get("GOV_REFERENCE")+increment);machine.setSpeed(1+row.get("SPEED"));
            }
            double[] actual={governor.getValvePosition(),governor.getReheaterStorage(),governor.getOutput(machine)};
            double[] expected={row.get("GOV_STATE_1"),row.get("GOV_STATE_2"),row.get("PMECH")};
            assertTrue(actual[0]>=.2-1e-12&&actual[0]<=1.2+1e-12,"Physical valve bounds");
            trace.append(time);
            for(int i=0;i<actual.length;i++) {
                assertTrue(Double.isFinite(actual[i]));double error=Math.abs(actual[i]-expected[i]);
                if(error>maximum[i]){maximum[i]=error;at[i]=time;}trace.append(',').append(actual[i]).append(',').append(expected[i]);
            }
            var states=governor.getNamedStates();double storage=governor.getReheaterStorage();
            assertEquals(storage,governor.getReheaterStorage(),0);assertEquals(states,governor.getNamedStates());
            assertEquals(.3*states.get("t2t3FilterBlock"),storage,1e-15);trace.append('\n');old=row;previous=time;
        }
        Path output=Path.of("target/tgov1-limit-replay",p.name()+".csv");Files.createDirectories(output.getParent());Files.writeString(output,trace);
        System.out.println("TGOV1 replay "+p.name()+" maxima="+Arrays.toString(maximum)+" at="+Arrays.toString(at));
        double[] bounds=switch(p.name()) {
            case "upper-coarse" -> new double[]{.0065,7.6e-6,.0022};
            case "upper-fine" -> new double[]{.0042,1.95e-6,.0014};
            case "lower-coarse" -> new double[]{.0113,7.7e-6,.0038};
            case "lower-fine" -> new double[]{.0033,1.97e-6,.00111};
            default -> throw new IllegalArgumentException("Unknown TGOV1 limit profile");
        };
        for(int i=0;i<maximum.length;i++)assertTrue(maximum[i]<bounds[i],p.name()+" metric "+i+" error="+maximum[i]);
        return maximum;
    }

    @ParameterizedTest @MethodSource("profiles")
    void strictImportedProfileEntersAndReleasesTheValveBound(Profile p,@TempDir Path input)throws Exception {
        var context=load(input);var machine=context.getDStabilityNet().getMachine("Bus1-mach1");
        var governor=(PsseTGov1SteamTurGovernor)machine.getGovernor();var algorithm=context.getDynSimuAlgorithm();
        algorithm.setSimuStepSec(p.step());double initialReference=governor.getRefPoint();int active=0;
        var trace=new StringBuilder("time_s,voltage,speed,valve,storage,pm\n");
        int count=(int)Math.round(.5/p.step());
        for(int tick=0;tick<=count;tick++) {
            double time=tick*p.step();if(tick==Math.round(.05/p.step()))governor.setRefPoint(initialReference+p.increment());
            if(tick==Math.round(.20/p.step()))governor.setRefPoint(initialReference);
            double valve=governor.getValvePosition();assertTrue(valve>=.2-1e-12&&valve<=1.2+1e-12);
            if(time>.08&&time<.20&&Math.abs(valve-(p.upper()?1.2:.2))<1e-7)active++;
            for(double value:governor.getNamedStates().values())assertTrue(Double.isFinite(value));
            assertTrue(Double.isFinite(machine.getSpeed()));assertTrue(Double.isFinite(governor.getOutput(machine)));
            trace.append(time).append(',').append(machine.getDStabBus().getVoltageMag()).append(',').append(machine.getSpeed()).append(',').append(valve).append(',').append(governor.getReheaterStorage()).append(',').append(governor.getOutput(machine)).append('\n');
            if(tick<count)assertTrue(algorithm.solveDEqnStep(true));
        }
        assertTrue(active>50,p.name()+" must enter the bound");assertTrue(Math.abs(governor.getValvePosition()-(p.upper()?1.2:.2))>.01,"Inward release");
        Path output=Path.of("target/tgov1-limit-coupled",p.name()+".csv");Files.createDirectories(output.getParent());Files.writeString(output,trace);
    }
}
