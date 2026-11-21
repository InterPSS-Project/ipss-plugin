package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.RepccShuntNativeReference;
import org.interpss.dstab.renewable.*;
import com.interpss.dstab.renewable.*;
import org.interpss.fadapter.psse.*;
import com.interpss.dstab.algo.dynamic.*;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import com.interpss.dstab.*;
import com.interpss.dstab.algo.*;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.common.DStabSimuException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.simu.SimuContext;
import java.util.regex.Pattern;
import org.apache.commons.math3.complex.Complex;

/** Independent fast-band comparison and explicit slow-band clock discriminator. */
public class RepccSwitchingNativeSmibTest extends CorePluginTestSetup {
    private static List<Map<String,Double>> rows(String csv) {
        var lines=csv.lines().toList();var headers=lines.getFirst().split(",");var result=new ArrayList<Map<String,Double>>();
        for(String line:lines.subList(1,lines.size())) {
            var fields=line.split(",");assertEquals(headers.length,fields.length);var row=new LinkedHashMap<String,Double>();
            for(int i=0;i<fields.length;i++){double v=Double.parseDouble(fields[i]);assertTrue(Double.isFinite(v));row.put(headers[i],v);}result.add(row);
        }return result;
    }
    private static Regca1Model converter(DynamicSimuAlgorithm algo) {
        return (Regca1Model)((DStabGen)algo.getNetwork().getBus("Bus1").getContributeGen("1")).getDynamicGenDevice();
    }
    private static double[] snapshot(DynamicSimuAlgorithm algo) {
        var c=converter(algo);double[] result=new double[18];
        for(int i=0;i<3;i++)result[i]=algo.getNetwork().getBus("Bus"+(i+1)).getVoltageMag();
        result[3]=result[0]*c.getIp()/2;result[4]=result[0]*c.getIq()/2;
        System.arraycopy(c.getActiveElectricalController().getRepccController().getKernel().states(),0,result,5,13);return result;
    }
    private static final class ObservedSolver extends PsseDStabSolver {
        final DynamicSimuAlgorithm algo;final double step;final Map<Integer,double[]> start=new HashMap<>();
        ObservedSolver(DynamicSimuAlgorithm algo,double step){super(algo);this.algo=algo;this.step=step;}
        @Override public void diffEqnIntegrationStep(double time,double dt,DynamicSimuMethod method,int flag)throws DStabSimuException {
            if(flag==0&&dt>0)start.put((int)Math.round(time/step),snapshot(algo));
            super.diffEqnIntegrationStep(time,dt,method,flag);
        }
    }
    private static RepccShuntController schedule(RepccShuntNativeReference.Profile p,Map<String,Double> initial) {
        var devices=new ArrayList<RepccShuntController.Device>();
        for(var key:p.parameters().shunts())devices.add(new RepccShuntController.Device(key,initial.get(key.id()+"_B_NOM")/100,initial.get(key.id()+"_STATUS")==1));
        var result=new RepccShuntController(p.parameters());result.initialize(devices,true);return result;
    }
    private static SimuContext load(RepccShuntNativeReference.Profile p,Path dir,String input)throws Exception {
        var initial=rows(p.status()).getFirst();StringBuilder fixed=new StringBuilder();
        for(var key:p.parameters().shunts())fixed.append(key.bus()).append(",'"+key.id()+"',")
                .append(initial.get(key.id()+"_STATUS").intValue()).append(',').append(initial.get(key.id()+"_G_NOM"))
                .append(',').append(initial.get(key.id()+"_B_NOM")).append('\n');
        String raw=Files.readString(Path.of("testData/adpter/psse/v33/renewable/regfma1_native.raw"));
        Path rawFile=dir.resolve(p.name()+".raw"),dyrFile=dir.resolve(p.name()+".dyr");
        Files.writeString(rawFile,raw.replace("0 / END OF FIXED SHUNT DATA",fixed+"0 / END OF FIXED SHUNT DATA"));Files.writeString(dyrFile,input);
        var ctx=new PSSEMultiFileLoader().loadDStab(rawFile.toString());var net=ctx.getDStabilityNet();var algo=ctx.getDynSimuAlgorithm();
        var parser=new PSSEDStabDirectParser(new DStabNetworkBuilder(net)).setStrictImport(true);parser.parseDynFile(dyrFile.toString());assertTrue(parser.getLastImportReport().isStrictlyComplete());
        net.setAllowGenWithoutMach(true);algo.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);algo.getAclfAlgorithm().setTolerance(1e-10);assertTrue(algo.getAclfAlgorithm().loadflow());
        algo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algo.setSimuStepSec(p.step());algo.setTotalSimuTimeSec(.3);algo.setSimuOutputHandler(new StateMonitor());
        return ctx;
    }
    private static String changed(String input,Map<Integer,String> changes) {
        return input.lines().map(line->{
            if(!line.contains("'REPCC'"))return line;
            var matcher=Pattern.compile("'(?:[^']|'')*'|[^\\s,]+").matcher(line);var fields=new ArrayList<String>();
            while(matcher.find())fields.add(matcher.group());changes.forEach((index,value)->fields.set(index+3,value));
            return String.join(" ",fields);
        }).reduce("",(a,b)->a+b+"\n");
    }
    @Test void zeroDelayOriginActionsFollowCompensationAndPreserveHybridCurrent(@TempDir Path dir)throws Exception {
        var p=RepccShuntNativeReference.profiles().getFirst();
        var baseline=load(p,dir,changed(p.input(),Map.of(9,"0")));assertTrue(baseline.getDynSimuAlgorithm().initialization());
        var net0=baseline.getDStabilityNet();int index=net0.getBus("Bus2").getSortNumber();var y0=net0.getYMatrix().getA(index,index);
        var currents=new Hashtable<>(net0.getCustomBusCurrInjHashtable());
        double[] states=converter(baseline.getDynSimuAlgorithm()).getActiveElectricalController().getRepccController().getKernel().states();
        var edits=new HashMap<Integer,String>();
        for(var parameter:List.of(RepccData.Parameter.TDELAY1,RepccData.Parameter.TDELAY2,RepccData.Parameter.TMSSBRK))edits.put(35+parameter.ordinal(),"0");
        for(boolean hybrid:new boolean[]{false,true}) {
            var ctx=load(p,dir,changed(p.input(),edits));var net=ctx.getDStabilityNet();var algo=ctx.getDynSimuAlgorithm();
            var supplied=new Hashtable<>(currents);if(hybrid)net.setCustomBusCurrInjHashtable(supplied);
            assertTrue(algo.initialization());var plant=converter(algo).getActiveElectricalController().getRepccController();
            assertFalse(net.getBus("Bus2").getCompensator("C1").isActive(),"Zero-delay origin opens the first capacitor");
            assertTrue(net.getYMatrix().getA(index,index).subtract(y0.subtract(new Complex(0,.01))).abs()<1e-12);
            assertEquals(currents,net.getCustomBusCurrInjHashtable(),"The physical action is not absorbed into compensation");
            if(hybrid)assertSame(supplied,net.getCustomBusCurrInjHashtable());
            assertArrayEquals(states,plant.getKernel().states(),1e-14);assertEquals(0,plant.getKernel().time());
            assertTrue(plant.acceptedEndpoint().isEmpty());assertTrue(algo.solveDEqnStep(true));
            for(double value:snapshot(algo))assertTrue(Double.isFinite(value));
        }
        for(boolean generatorOff:new boolean[]{true,false}) {
            var inactive=load(p,dir,changed(p.input(),edits));var device=converter(inactive.getDynSimuAlgorithm());
            if(generatorOff)device.getParentGen().setStatus(false);else device.setStatus(false);
            assertEquals(device.getExtendedDeviceId(),device.getStates(null).get(com.interpss.dstab.common.DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID));
            assertTrue(inactive.getDynSimuAlgorithm().initialization());
            assertTrue(inactive.getDStabilityNet().getBus("Bus2").getCompensator("C1").isActive(),"Inactive owners/devices cannot dispatch at startup");
        }
    }
    private static double[] limits(String name) {
        return switch(name) {
            case "negative-fast-ten-coarse" -> new double[]{1.087e-5,5.626e-6,5.094e-7,5.431e-6,1.315e-6,1e-12,1.333e-4,7.514e-6,2.172e-5,1.183e-5,4.962e-8,5.639e-6,1.175e-6,1e-12,2.603e-7,4.984e-7,3.058e-6,1.999e-5};
            case "negative-fast-ten-fine" -> new double[]{1.087e-5,5.623e-6,5.086e-7,2.659e-6,1.313e-6,1e-12,6.686e-5,3.904e-6,7.705e-6,5.772e-6,3.337e-8,3.305e-6,2.487e-6,1e-12,2.624e-7,4.984e-7,3.058e-6,7.278e-6};
            case "positive-fast-ten-coarse" -> new double[]{2.447e-5,1.284e-5,1.19e-6,6.612e-6,8.939e-5,1e-12,2.069e-4,1.797e-4,3.208e-4,1.439e-5,1.298e-7,5.66e-6,1.18e-6,1e-12,2.617e-7,4.984e-7,3.058e-6,3.194e-4};
            case "positive-fast-ten-fine" -> new double[]{1.219e-5,6.372e-6,6.205e-7,3.478e-6,4.418e-5,1e-12,1.024e-4,9.233e-5,1.637e-4,7.396e-6,8.131e-8,2.53e-6,3.78e-6,1e-12,2.639e-7,4.984e-7,3.058e-6,1.634e-4};
            default -> throw new IllegalArgumentException("No accepted continuous bounds for "+name);
        };
    }
    @Test void fastBandStatusesAndEveryRawContinuousSampleMatchAtBothSteps(@TempDir Path dir)throws Exception {
        assertEquals(0,compareProfiles(true,dir));
    }
    @Test void slowBandRetainsClockPrecisionDiscrepancyWithoutTimingOffsets(@TempDir Path dir)throws Exception {
        assertTrue(compareProfiles(false,dir)>0,"The unresolved clock discriminator must remain visible");
    }
    private int compareProfiles(boolean fast,Path dir)throws Exception {
        int totalMismatch=0,profiles=0;
        for(var p:RepccShuntNativeReference.profiles()) {
            if(p.lockoutDiscrepancy()||p.name().contains("-fast-")!=fast)continue;
            var status=rows(p.status());var initial=status.getFirst();var parameters=p.parameters();
            var ctx=load(p,dir,p.input());var net=ctx.getDStabilityNet();var algo=ctx.getDynSimuAlgorithm();
            var solver=new ObservedSolver(algo,p.step());algo.setSolver(solver);assertTrue(algo.initialization());
            var plant=converter(algo).getActiveElectricalController().getRepccController();
            var origin=rows(p.body()).stream().filter(r->r.get("time_s")==0).findFirst().orElseThrow();
            assertEquals(0,plant.getKernel().time());
            assertEquals(origin.get("PLANT_VAR14"),plant.getNamedStates().get("switching.slowStart"));
            assertEquals(origin.get("PLANT_VAR15"),plant.getNamedStates().get("switching.fastStart"));
            assertTrue(plant.acceptedEndpoint().isEmpty(),"Repeated origin sampling is idempotent");
            int ticks=(int)Math.round(.3/p.step());var actual=new ArrayList<double[]>();var clocks=new ArrayList<Double>();
            var actualStatuses=new ArrayList<boolean[]>();int mismatches=0;double firstMismatch=-1;
            StringBuilder statusTrace=new StringBuilder("native_time_s,ipss_time_s");
            for(var key:parameters.shunts())statusTrace.append(",IPSS_").append(key.id()).append(",NATIVE_").append(key.id());statusTrace.append('\n');
            for(int tick=0;tick<=ticks;tick++) {
                assertEquals(ticks+1,status.size());var expected=status.get(tick);actual.add(snapshot(algo));clocks.add(plant.getKernel().time());
                boolean[] statuses=new boolean[10];int slot=0;
                assertEquals(tick==0?0:expected.get("time_s"),tick*p.step(),3e-6);
                statusTrace.append(expected.get("time_s")).append(',').append(tick*p.step());
                for(var key:parameters.shunts()) {
                    double value=net.getBus("Bus"+key.bus()).getCompensator(key.id()).isActive()?1:0;
                    statuses[slot++]=value==1;
                    double reference=expected.get(key.id()+"_STATUS");statusTrace.append(',').append(value).append(',').append(reference);
                    if(value!=reference){mismatches++;if(firstMismatch<0)firstMismatch=tick*p.step();}
                }
                actualStatuses.add(statuses);statusTrace.append('\n');if(tick<ticks)assertTrue(algo.solveDEqnStep(true));
            }
            Path out=Path.of("target/repcc-switching-coupled",p.name()+"-statuses.csv");Files.createDirectories(out.getParent());Files.writeString(out,statusTrace.toString());
            String[] channels=new String[18];System.arraycopy(new String[]{"V1","V2","V3","P_PU","Q_PU"},0,channels,0,5);for(int i=0;i<13;i++)channels[5+i]="PLANT_STATE"+i;
            double[] max=new double[18],at=new double[18];int duplicates=0,history=0;Double previous=null;
            StringBuilder paired=new StringBuilder("native_time_s,ipss_time_s,side");for(String channel:channels)paired.append(",IPSS_").append(channel).append(",NATIVE_").append(channel);paired.append('\n');
            var nativeClock=schedule(p,initial);var solverClock=schedule(p,initial);
            for(var row:rows(p.body())) {
                double time=row.get("time_s");int tick=Math.max(0,(int)Math.round(time/p.step()));boolean duplicate=previous!=null&&time==previous;previous=time;
                double[] values=duplicate?solver.start.get(tick):actual.get(tick);assertNotNull(values);if(time<0)history++;if(duplicate)duplicates++;
                paired.append(time).append(',').append(tick*p.step()).append(',').append(time<0?-1:duplicate?1:0);
                for(int i=0;i<18;i++){assertTrue(Double.isFinite(values[i]));double reference=row.get(channels[i]),error=Math.abs(values[i]-reference);if(error>max[i]){max[i]=error;at[i]=time;}paired.append(',').append(values[i]).append(',').append(reference);}paired.append('\n');
                if(!fast&&time>=0) {
                    // Replay the identical recorded Q twice, changing only the
                    // clock. Do not quantize production time or shift samples.
                    nativeClock.advance(time,row.get("PLANT_VAR13")/50);
                    solverClock.advance(clocks.get(tick),row.get("PLANT_VAR13")/50);
                    for(int i=0;i<10;i++) {
                        var key=parameters.shunts().get(i);
                        assertEquals(status.get(tick).get(key.id()+"_STATUS")==1,nativeClock.slots().get(i).inService(),p.name()+" native clock slot "+i+" tick "+tick);
                        assertEquals(actualStatuses.get(tick)[i],solverClock.slots().get(i).inService(),p.name()+" solver clock slot "+i+" tick "+tick);
                    }
                }
            }
            assertEquals(2,history);assertEquals(ticks-1,duplicates);
            Files.writeString(out.resolveSibling(p.name()+"-paired.csv"),paired.toString());
            System.out.println("REPCC switching coupled "+p.name()+" mismatches="+mismatches+" first="+firstMismatch+" errors="+Arrays.toString(max)+" times="+Arrays.toString(at));
            if(fast)for(int i=0;i<18;i++)assertTrue(max[i]<limits(p.name())[i],p.name()+" "+channels[i]+" error="+max[i]+" at="+at[i]);
            totalMismatch+=mismatches;profiles++;
        }
        assertEquals(4,profiles);return totalMismatch;
    }
}
