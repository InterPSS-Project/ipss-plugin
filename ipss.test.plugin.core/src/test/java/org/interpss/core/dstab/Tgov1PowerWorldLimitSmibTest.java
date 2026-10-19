package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.EmbeddedTrajectoryReference;
import org.interpss.core.dstab.reference.Tgov1LimitPowerWorldReference;
import org.interpss.core.dstab.reference.Tgov1LimitPowerWorldReference.Profile;
import org.interpss.dstab.control.gov.psse.tgov1.PsseTGov1SteamTurGovernor;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.RoundRotorMachine;
import com.interpss.dstab.mach.impl.RoundRotorMachineImpl;
import com.interpss.state.dstab.MachineDataState;
import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;
import com.interpss.dstab.controller.cml.field.ICMLStaticBlock.StaticBlockType;

/** Complete independent fixtures and explicit controller/coupled discrepancy localization. */
public class Tgov1PowerWorldLimitSmibTest extends CorePluginTestSetup {
    private static final String[] LABELS={"V1","V2","P_MW","Q_Mvar","relative_speed","relative_angle_deg","turbine","valve","Eqp","PsiDp","PsiQpp","Edp"};

    @Test void completeIndependentFixturesRetainBothEventSides() {
        for(var p:Tgov1LimitPowerWorldReference.profiles()) {
            var reference=p.reference();assertEquals(p.samples(),reference.samples().size());assertEquals(14,reference.fields().size());
            assertEquals(2,reference.samples().stream().filter(row->row.occurrence()==1).count());
            assertEquals(6,reference.fieldIndex("Generator","1 1","TSGovernorState:1"));
            assertEquals(7,reference.fieldIndex("Generator","1 1","TSGovernorState:2"));
            int limited=0;for(var row:reference.samples()) {
                for(double value:row.values())assertTrue(Double.isFinite(value));
                if(Math.abs(row.value(7)-(p.increment()>0?1.2:.2))<1e-7)limited++;
            }
            assertTrue(limited>50,"Independent valve limit must be sustained");
            assertTrue(Math.abs(reference.samples().getLast().value(7)-(p.increment()>0?1.2:.2))>.01,"Independent inward release");
            var before=reference.samples().getFirst().values();var copy=reference.samples().getFirst().values();copy[0]=99;
            assertArrayEquals(before,reference.samples().getFirst().values(),0);assertThrows(UnsupportedOperationException.class,()->reference.samples().clear());
        }
    }

    @Test void recordedSpeedReplayKeepsAllStatesAndEventRows(@TempDir Path input)throws Exception {
        for(var p:Tgov1LimitPowerWorldReference.profiles()) {
            var context=Tgov1LimitNativeConformanceTest.load(input.resolve(p.name()));
            var machine=context.getDStabilityNet().getMachine("Bus1-mach1");var gov=(PsseTGov1SteamTurGovernor)machine.getGovernor();
            var rows=p.reference().samples();var old=rows.getFirst();machine.setPm(old.value(6));machine.setSpeed(old.value(4));
            assertTrue(gov.initStates(machine.getDStabBus(),machine));double initial=gov.getRefPoint(),increment=0,previous=old.time();
            double[] maximum=new double[4],at=new double[4];
            var trace=new StringBuilder("time_s,ipss_valve,reference_valve,ipss_turbine,reference_turbine,ipss_storage,derived_storage,ipss_mechanical,derived_mechanical\n");
            for(var row:rows) {
                if(row.time()>previous) {
                    gov.setRefPoint(initial+increment);machine.setSpeed(old.value(4));assertTrue(gov.nextStep(p.step(),DynamicSimuMethod.MODIFIED_EULER,machine,0));
                    machine.setSpeed(row.value(4));assertTrue(gov.nextStep(p.step(),DynamicSimuMethod.MODIFIED_EULER,machine,1));
                } else if(row.occurrence()==1) {
                    if(Math.abs(row.time()-.05)<1e-10)increment=p.increment();
                    if(Math.abs(row.time()-.20)<1e-10)increment=0;
                    gov.setRefPoint(initial+increment);machine.setSpeed(row.value(4));
                }
                double[] actual={gov.getValvePosition(),gov.getTurbinePower(),gov.getReheaterStorage(),gov.getOutput(machine)};
                double[] expected={row.value(7),row.value(6),.3*row.value(6)-.1*row.value(7),row.value(6)-.5*(row.value(4)-1)};
                trace.append(row.time());
                for(int i=0;i<actual.length;i++) {
                    assertTrue(Double.isFinite(actual[i]));double error=Math.abs(actual[i]-expected[i]);
                    if(error>maximum[i]){maximum[i]=error;at[i]=row.time();}trace.append(',').append(actual[i]).append(',').append(expected[i]);
                }
                assertTrue(actual[0]>=.2-1e-12&&actual[0]<=1.2+1e-12);trace.append('\n');old=row;previous=row.time();
            }
            write(p.name()+"-replay",trace.toString());
            System.out.println("TGOV1 independent replay "+p.name()+" maxima="+Arrays.toString(maximum)+" at="+Arrays.toString(at));
            double[] bounds=switch(p.name()) {
                case "upper-coarse" -> new double[]{6e-7,5.7e-5,1.7e-5,5.7e-5};
                case "upper-fine" -> new double[]{.0042,.0014,4.3e-6,.0014};
                case "lower-coarse" -> new double[]{.0113,.0038,1.7e-5,.0038};
                case "lower-fine" -> new double[]{1e-7,1.42e-5,4.3e-6,1.42e-5};
                default -> throw new IllegalArgumentException("Unknown independent profile");
            };
            for(int i=0;i<maximum.length;i++)assertTrue(maximum[i]<bounds[i],p.name()+" replay metric "+i+" max="+maximum[i]+" at="+at[i]);
        }
    }

    @Test void projectedModifiedEulerCrossingExplainsThePhaseSensitiveLocalError() {
        for(boolean upper:new boolean[]{true,false}) {
            double start=upper?1.192:.2214,input=upper?2.5:-1.5,step=upper?.00025:.0005,bound=upper?1.2:.2;
            var block=new DelayControlBlock(StaticBlockType.NonWindup,1,.04,1.2,.2);assertTrue(block.initStateY0(start));
            double rate=(input-start)/.04;block.eulerStep1(input,step);assertEquals(bound,block.getY(),0);
            block.eulerStep2(input,step);assertEquals(start+.5*step*rate,block.getStateX(),1e-14,"Projected outward corrector rate is zero");
            assertNotEquals(bound,block.getY(),"A limit crossing can leave a one-step interior residual");
            block.eulerStep1(input,step);block.eulerStep2(input,step);assertEquals(bound,block.getY(),0);
            block.eulerStep1(input,step);block.eulerStep2(input,step);assertEquals(bound,block.getY(),0,"Sustained outward drive holds the limit");
            block.eulerStep1(.5,step);block.eulerStep2(.5,step);assertTrue(upper?block.getY()<bound:block.getY()>bound,"Inward drive releases immediately");
        }
    }

    @Test void coupledProfilesRetainEveryComparisonChannelWithoutClaimingParity(@TempDir Path input)throws Exception {
        coupled(input,false);
    }

    /** Published independent stator convention only; the native reader's realization is unchanged. */
    private static final class SpeedScaledRoundRotor extends RoundRotorMachineImpl {
        @Override protected double statorVoltageScale(){return getSpeed();}
        @Override protected double cal_dW_dt(double speed,double frequency) {
            return ((getPm()-getD()*.01*frequency*(speed-1))/speed-getPe()-getPe_NegSeq())/(2*getH());
        }
    }

    @Test void explicitPublishedSpeedScaledBoundaryLocalizesTheCoupledDifference(@TempDir Path input)throws Exception {
        coupled(input,true);
    }

    private static void coupled(Path input,boolean speedScaled)throws Exception {
        for(var p:Tgov1LimitPowerWorldReference.profiles()) {
            var context=Tgov1LimitNativeConformanceTest.load(input.resolve(p.name()),!speedScaled);var network=context.getDStabilityNet();
            if(speedScaled) {
                var original=network.getMachine("Bus1-mach1");var governor=(PsseTGov1SteamTurGovernor)original.getGovernor();var data=governor.getData();var parameters=new MachineDataState(original.getMachData());
                var adapter=new SpeedScaledRoundRotor();adapter.setId(original.getId());adapter.setName(original.getName());adapter.setMachType(original.getMachType());adapter.setMachData(parameters.create());
                assertEquals(0,adapter.getRa(),0);assertEquals(0,adapter.getD(),0);
                var copied=new MachineDataState(adapter.getMachData());
                for(var field:MachineDataState.class.getFields())if(field.getType()==double.class)assertEquals(field.getDouble(parameters),field.getDouble(copied),0,field.getName());
                adapter.setPoles(original.getPoles());network.addMachine(adapter,"Bus1","1");
                assertEquals(0,data.getTrate(),0);assertEquals(0,data.getDbH(),0);assertEquals(0,data.getDbL(),0);
                assertNotNull(new DStabNetworkBuilder(network).addGovTgov1("Bus1","1",data.getR(),data.getT1(),data.getvMax(),data.getvMin(),data.getT2(),data.getT3(),data.getDt()));
                assertSame(adapter,network.getMachine("Bus1-mach1"));assertTrue(context.getDynSimuAlgorithm().initialization());
            }
            var machine=(RoundRotorMachine)network.getMachine("Bus1-mach1");var infinite=network.getMachine("Bus2-mach1");
            var gov=(PsseTGov1SteamTurGovernor)machine.getGovernor();var algorithm=context.getDynSimuAlgorithm();algorithm.setSimuStepSec(p.step());
            double initial=gov.getRefPoint();List<double[]> actual=new ArrayList<>();int count=(int)Math.round(.5/p.step());
            for(int tick=0;tick<=count;tick++) {
                double time=tick*p.step();if(tick==Math.round(.05/p.step()))gov.setRefPoint(initial+p.increment());
                if(tick==Math.round(.20/p.step()))gov.setRefPoint(initial);
                var voltage=machine.getDStabBus().getVoltage();var current=machine.getIgen().subtract(voltage.multiply(machine.getYgen()));var power=voltage.multiply(current.conjugate());
                double[] row={time,network.getBus("Bus1").getVoltageMag(),network.getBus("Bus2").getVoltageMag(),power.getReal()*100,power.getImaginary()*100,
                        machine.getSpeed(),Math.toDegrees(machine.getAngle()),gov.getTurbinePower(),gov.getValvePosition(),
                        infinite.getSpeed(),Math.toDegrees(infinite.getAngle()),machine.getEq1(),machine.getPsikd(),machine.getPsikq(),machine.getEd1()};
                for(double value:row)assertTrue(Double.isFinite(value));actual.add(row);
                assertTrue(gov.getValvePosition()>=.2-1e-12&&gov.getValvePosition()<=1.2+1e-12);
                if(tick<count)assertTrue(algorithm.solveDEqnStep(true));
            }
            compare(p,actual,speedScaled);
        }
    }

    private static void compare(Profile p,List<double[]> actual,boolean speedScaled)throws Exception {
        var reference=p.reference();var first=reference.samples().getFirst();double nativeAngle=first.value(5)-first.value(9);
        double localAngle=actual.getFirst()[6]-actual.getFirst()[10];double[] maximum=new double[12],at=new double[12];
        int[] fields={0,1,2,3,4,5,6,7,10,11,12,13};var trace=new StringBuilder("time_s,occurrence");
        for(String label:LABELS)trace.append(",ipss_").append(label).append(",reference_").append(label);trace.append('\n');
        for(var expected:reference.samples()) {
            double[] local=interpolate(actual,expected.time());trace.append(expected.time()).append(',').append(expected.occurrence());
            for(int i=0;i<fields.length;i++) {
                int field=fields[i];double value=local[field+1],target=expected.value(field);
                if(i==4){value-=local[9];target-=expected.value(8);}
                if(i==5){value-=local[10]+localAngle;target-=expected.value(9)+nativeAngle;}
                double error=Math.abs(value-target);if(error>maximum[i]){maximum[i]=error;at[i]=expected.time();}
                trace.append(',').append(value).append(',').append(target);
            }
            trace.append('\n');
        }
        String kind=speedScaled?"speed-scaled":"native";write(p.name()+"-coupled-"+kind,trace.toString());
        System.out.println("TGOV1 independent coupled "+(speedScaled?"MATCHED-CONVENTION ":"DIAGNOSTIC ")+p.name()+" "+kind+" labels="+Arrays.toString(LABELS)+" maxima="+Arrays.toString(maximum)+" at="+Arrays.toString(at));
        if(speedScaled) {
            double turbine=switch(p.name()){case "upper-coarse"->5.7e-5;case "upper-fine"->.0014;case "lower-coarse"->.0038;case "lower-fine"->1.42e-5;default->throw new IllegalArgumentException();};
            double valve=switch(p.name()){case "upper-coarse"->6e-7;case "upper-fine"->.0042;case "lower-coarse"->.0113;case "lower-fine"->1e-7;default->throw new IllegalArgumentException();};
            double[] bounds={6e-7,1.3e-7,.0014,.00055,1.5e-7,.00028,turbine,valve,1.6e-6,1.7e-6,3.5e-6,2.7e-6};
            for(int i=0;i<maximum.length;i++)assertTrue(maximum[i]<bounds[i],p.name()+" "+LABELS[i]+" max="+maximum[i]+" at="+at[i]);
        } else {
            assertTrue(maximum[0]>1e-4&&maximum[3]>.1,"Preserve the known unmatched-convention discrepancy, not a parity claim");
        }
    }

    private static double[] interpolate(List<double[]> rows,double time) {
        for(int i=0;i<rows.size();i++) {
            var lower=rows.get(i);if(Math.abs(lower[0]-time)<1e-10)return lower;
            if(i+1<rows.size()&&rows.get(i+1)[0]>time) {
                var upper=rows.get(i+1);double fraction=(time-lower[0])/(upper[0]-lower[0]);double[] result=new double[lower.length];
                for(int j=0;j<result.length;j++)result[j]=lower[j]+fraction*(upper[j]-lower[j]);return result;
            }
        }
        throw new IllegalArgumentException("Comparison time outside actual trajectory");
    }

    private static void write(String name,String body)throws Exception {
        Path path=Path.of("target/tgov1-independent-limits",name+".csv");Files.createDirectories(path.getParent());Files.writeString(path,body);
    }
}
