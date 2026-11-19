package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.*;
import org.interpss.dstab.renewable.*;
import com.interpss.dstab.renewable.*;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.dstab.*;
import org.junit.jupiter.api.Test;

/** Recorded-voltage replay and physical Y switching, not coupled plant acceptance. */
public class RepccNetworkBoundaryConformanceTest extends CorePluginTestSetup {
    private static BaseDStabNetwork<?,?> network() throws Exception {
        return new PSSEMultiFileLoader().loadDStab("testData/adpter/psse/v33/renewable/regfma1_native.raw").getDStabilityNet();
    }
    private static DStabGen generator(BaseDStabNetwork<?,?> net){return (DStabGen)net.getBus("Bus1").getContributeGen("1");}
    private static RepccNetworkBoundary boundary(BaseDStabNetwork<?,?> net,RepccData data){return new RepccNetworkBoundary(data,generator(net),net.getBus("Bus1"));}
    private static RepccData configured(RepccData d,int remote,RepccData.Branch main,int freq,int enabled,List<RepccData.Shunt> slots){
        return new RepccData(remote,main,d.compensationFlag(),d.referenceFlag(),d.frequencyFlag(),d.feedbackFlag(),d.feedforwardFlag(),enabled,
                d.switchingBranch(),slots,freq,d.reactiveFlag(),d.constants());
    }
    private static List<Map<String,Double>> rows(String csv){
        var lines=csv.lines().toList();var headers=lines.getFirst().split(",");var result=new ArrayList<Map<String,Double>>();
        for(String line:lines.subList(1,lines.size())){var fields=line.split(",");assertEquals(headers.length,fields.length);var row=new HashMap<String,Double>();
            for(int i=0;i<fields.length;i++)row.put(headers[i],Double.parseDouble(fields[i]));result.add(row);}return result;
    }
    private static Complex polar(double magnitude,double degrees){double a=Math.toRadians(degrees);return new Complex(Math.cos(a),Math.sin(a)).multiply(magnitude);}
    private static void voltage(BaseDStabNetwork<?,?> net,Map<String,Double> row){
        for(int bus=1;bus<=3;bus++)net.getBus("Bus"+bus).setVoltage(polar(row.get("V"+bus),row.get("A"+bus+"_DEG")));
    }
    @Test void directPhysicalApiQueriesMatchBothBranchEndsAndSwitchingMeasurement() throws Exception {
        int profiles=0;double[] maximum=new double[5];
        for(var profile:RepccBoundaryNativeReference.profiles()){
            var net=network();var data=profile.parameters();var forward=boundary(net,data);
            var reverse=boundary(net,configured(data,3,new RepccData.Branch(3,2,"1"),3,0,data.shunts()));
            var table=rows(profile.body());assertEquals(profile.samples(),table.size());
            for(var row:table){
                for(int bus=2;bus<=3;bus++)net.getBus("Bus"+bus).setVoltage(polar(row.get("V"+bus),row.get("A"+bus+"_DEG")));
                var local=new RepccNetworkBoundary.Local(Complex.ZERO,Complex.ZERO);var a=forward.measure(local);var b=reverse.measure(local).control();
                double[] errors={Math.abs(a.control().active()-row.get("P23_MW")/50),Math.abs(a.control().reactive()-row.get("Q23_MVAR")/50),
                        Math.abs(b.active()-row.get("P32_MW")/50),Math.abs(b.reactive()-row.get("Q32_MVAR")/50),
                        Math.abs(a.switchingReactive()-row.get(profile.name().startsWith("positive")?"Q23_MVAR":"Q32_MVAR")/50)};
                for(int i=0;i<errors.length;i++)maximum[i]=Math.max(maximum[i],errors[i]);
            }profiles++;
        }
        assertEquals(6,profiles);System.out.println("REPCC direct physical API P23/Q23/P32/Q32/Qmss maxima="+Arrays.toString(maximum));
        double[] bounds={2.64e-7,3.73e-6,2.64e-7,3.62e-6,3.73e-6};
        for(int i=0;i<maximum.length;i++)assertTrue(maximum[i]<bounds[i],"Direct physical channel "+i+" "+maximum[i]);
    }
    @Test void nativeMainBranchAndDeferredSwitchingPlotAreNotConfused() throws Exception {
        int count=0;double[] maximum=new double[3];
        for(var profile:RepccShuntNativeReference.profiles()){
            var net=network();var b=boundary(net,profile.parameters());assertFalse(b.isMainBranchFallback());assertTrue(b.isSwitchingBranchFound());
            double localMaximum=0,at=0,observed=0,expected=0;
            for(var row:rows(profile.body())){
                voltage(net,row);var measurement=b.measure(new RepccNetworkBoundary.Local(Complex.ZERO,Complex.ZERO));
                double[] error={Math.abs(measurement.control().active()-row.get("PLANT_VAR4")/50),
                        Math.abs(measurement.control().reactive()-row.get("PLANT_VAR5")/50),
                        Math.abs(measurement.switchingReactive()-row.get("PLANT_VAR13")/50)};
                for(int i=0;i<3;i++)maximum[i]=Math.max(maximum[i],error[i]);
                if(error[2]>localMaximum){localMaximum=error[2];at=row.get("time_s");observed=measurement.switchingReactive();expected=row.get("PLANT_VAR13")/50;}
                assertEquals(row.get("V3"),measurement.control().regulatedVoltage().abs(),1e-14);
            }System.out.println("REPCC MSS measurement "+profile.name()+" maximum="+localMaximum+" at="+at+" actual="+observed+" expected="+expected);count++;
        }
        assertEquals(10,count);System.out.println("REPCC physical branch replay P/Q/Qmss errors="+Arrays.toString(maximum));
        assertTrue(maximum[0]<2.8e-7);assertTrue(maximum[1]<3.8e-6);
        assertTrue(maximum[2]>1.8,"The switching plot variable is not a simultaneous physical branch-flow sample");
    }
    @Test void missingBranchUsesExplicitMachineCurrentAndIndependentVoltageFrequencyFallbacks() throws Exception {
        var net=network();var d=RepccNativeReference.profiles().getFirst().parameters();
        net.getBus("Bus1").setVoltage(Complex.ZERO);net.getBus("Bus3").setFreq(.98);
        var local=new RepccNetworkBoundary.Local(new Complex(.4,.2),new Complex(.31,-.18));
        for(int remote:new int[]{0,999}){
            var b=boundary(net,configured(d,remote,new RepccData.Branch(1,3,"9"),3,0,d.shunts()));
            var measured=b.measure(local).control();assertTrue(b.isMainBranchFallback());assertEquals("Bus1",b.regulatedBusId());
            assertEquals("Bus3",b.frequencyBusId());assertEquals(local.current(),measured.branchCurrent());
            assertEquals(.4,measured.active());assertEquals(.2,measured.reactive());assertEquals(-.02,measured.frequencyDeviation(),1e-14);
            assertEquals(0,measured.regulatedVoltage().abs());
        }
        for(int freq:new int[]{0,999}){
            var b=boundary(net,configured(d,3,d.measurementBranch(),freq,0,d.shunts()));assertEquals("Bus3",b.frequencyBusId());
        }
    }
    @Test void signedReceivingEndAndOutOfServiceBranchUseCorrectCurrentWithoutVoltageDivision() throws Exception {
        var net=network();var d=RepccNativeReference.profiles().getFirst().parameters();var branch=(AclfBranch)net.getBranch("Bus2","Bus3","1");
        net.getBus("Bus2").setVoltage(new Complex(.9,.1));net.getBus("Bus3").setVoltage(new Complex(1.02,0));
        var local=new RepccNetworkBoundary.Local(Complex.ZERO,Complex.ZERO);
        for(int from:new int[]{2,-2,3,-3}){
            var b=boundary(net,configured(d,3,new RepccData.Branch(from,Math.abs(from)==2?3:2,"1"),3,0,d.shunts()));
            Complex current=(Math.abs(from)==2?net.getBus("Bus2").getVoltage().subtract(net.getBus("Bus3").getVoltage())
                    :net.getBus("Bus3").getVoltage().subtract(net.getBus("Bus2").getVoltage())).divide(branch.getZ()).multiply(from<0?-2:2);
            assertTrue(current.subtract(b.measure(local).control().branchCurrent()).abs()<1e-13);
        }
        var b=boundary(net,configured(d,3,new RepccData.Branch(2,3,"1"),3,0,d.shunts()));
        net.getBus("Bus2").setVoltage(Complex.ZERO);
        assertTrue(b.measure(local).control().branchCurrent().abs()>1);assertEquals(0,b.measure(local).control().active());
        branch.setStatus(false);assertEquals(Complex.ZERO,b.measure(local).control().branchCurrent());assertEquals(0,b.measure(local).control().reactive());
    }
    @Test void physicalSwitchChangesFullAdmittanceOnceAndSurvivesNetworkRebuild() throws Exception {
        var net=network();var profile=RepccShuntNativeReference.profiles().getFirst();var key=new RepccData.Shunt(2,"C1");
        var slots=new ArrayList<>(Collections.nCopies(10,new RepccData.Shunt(0,"0")));slots.set(0,key);
        var data=configured(profile.parameters(),3,profile.parameters().measurementBranch(),3,1,slots);
        Complex nominal=new Complex(.003,.01);var shunt=new AclfNetworkBuilder(net).addFixedShunt("Bus2","C1",true,.003,.01,null);
        net.getBus("Bus2").setVoltage(new Complex(1.05,0));net.getBus("Bus3").setVoltage(Complex.ONE);
        net.formYMatrix4DStab();int index=net.getBus("Bus2").getSortNumber();Complex initial=net.getYMatrix().getA(index,index),g=net.getBus("Bus2").getShuntY();
        var b=boundary(net,data);b.initializeShunts(Map.of(key,nominal));assertTrue(b.measure(new RepccNetworkBoundary.Local(Complex.ZERO,Complex.ZERO)).switchingReactive()<-.3);
        b.advanceShunts(0);b.advanceShunts(.0031);var actions=b.advanceShunts(.0072);assertEquals(1,actions.size());assertFalse(shunt.isActive());
        assertTrue(net.getYMatrix().getA(index,index).subtract(initial.subtract(nominal)).abs()<1e-12);
        assertEquals(g.subtract(new Complex(.003,0)),net.getBus("Bus2").getShuntY());assertTrue(net.isYMatrixDirty());
        assertTrue(b.advanceShunts(.0072).isEmpty());assertTrue(b.advanceShunts(.008).isEmpty());
        net.formYMatrix4DStab();assertTrue(net.getYMatrix().getA(index,index).subtract(initial.subtract(nominal)).abs()<1e-12,
                "Rebuild Y="+net.getYMatrix().getA(index,index)+" expected="+initial.subtract(nominal));
        b.initializeShunts(Map.of(key,nominal));assertFalse(b.shuntController().slots().getFirst().inService());
    }
    @Test void replayedVoltagesProduceAllPhysicalSwitchStatusesAndPersistentYChanges() throws Exception {
        int profiles=0;
        for(var profile:RepccShuntNativeReference.profiles()){
            if(profile.lockoutDiscrepancy())continue;
            var net=network();var data=profile.parameters();var status=rows(profile.status());var initial=status.getFirst();
            var topology=new AclfNetworkBuilder(net);var nominal=new HashMap<RepccData.Shunt,Complex>();
            for(var key:data.shunts()){
                double g=initial.get(key.id()+"_G_NOM")/100,b=initial.get(key.id()+"_B_NOM")/100;
                topology.addFixedShunt("Bus"+key.bus(),key.id(),initial.get(key.id()+"_STATUS")==1,g,b,null);nominal.put(key,new Complex(g,b));
            }
            var samples=new HashMap<Integer,Map<String,Double>>();
            for(var row:rows(profile.body()))if(row.get("time_s")>=0)samples.put((int)Math.round(row.get("time_s")/profile.step()),row);
            voltage(net,samples.get(0));net.formYMatrix4DStab();int index=net.getBus("Bus2").getSortNumber();Complex initialY=net.getYMatrix().getA(index,index);
            var boundary=boundary(net,data);boundary.initializeShunts(nominal);int events=0;
            assertTrue(status.getFirst().get("time_s")<0,"Initial API snapshot is retained integration history");
            boundary.advanceShunts(0);
            for(int tick=0;tick<status.size();tick++){
                var expected=status.get(tick);voltage(net,samples.get(tick));
                var actions=tick==0?List.<RepccShuntController.Action>of():boundary.advanceShunts(expected.get("time_s"));events+=actions.size();
                Complex delta=Complex.ZERO;
                for(var key:data.shunts()){
                    var device=net.getBus("Bus"+key.bus()).getCompensator(key.id());
                    assertEquals(expected.get(key.id()+"_STATUS")==1,device.isActive(),profile.name()+" tick="+tick+" "+key);
                    delta=delta.add(nominal.get(key).multiply(expected.get(key.id()+"_STATUS")-initial.get(key.id()+"_STATUS")));
                }
                assertTrue(net.getYMatrix().getA(index,index).subtract(initialY.add(delta)).abs()<1e-12);
                if(!actions.isEmpty()){
                    net.formYMatrix4DStab();assertTrue(net.getYMatrix().getA(index,index).subtract(initialY.add(delta)).abs()<1e-12);
                    assertTrue(boundary.advanceShunts(expected.get("time_s")).isEmpty());
                }
            }
            assertEquals(5,events,profile.name());profiles++;
        }
        assertEquals(8,profiles);
    }
    @Test void missingNominalDataDuplicateDevicesAndExternalStatusChangesFailBeforeMutation() throws Exception {
        var net=network();var original=RepccShuntNativeReference.profiles().getFirst().parameters();var key=original.shunts().get(0);
        var slots=new ArrayList<>(Collections.nCopies(10,new RepccData.Shunt(0,"0")));slots.set(0,key);var d=configured(original,3,original.measurementBranch(),3,1,slots);
        var shunt=new AclfNetworkBuilder(net).addFixedShunt("Bus2",key.id(),false,0,-.01,null);var b=boundary(net,d);
        assertThrows(IllegalArgumentException.class,()->b.initializeShunts(Map.of()));assertFalse(shunt.isActive());
        assertThrows(IllegalArgumentException.class,()->b.initializeShunts(Map.of(key,new Complex(0,.01))));
        b.initializeShunts(Map.of(key,new Complex(0,-.01)));
        assertThrows(IllegalStateException.class,()->b.advanceShunts(0));net.formYMatrix4DStab();shunt.setStatus(true);
        assertThrows(IllegalStateException.class,()->b.advanceShunts(0));assertEquals(0,b.shuntController().time());
        assertThrows(IllegalArgumentException.class,()->new RepccNetworkBoundary(d,generator(network()),net.getBus("Bus1")));
    }
    @Test void disabledSwitchingNeedsNeitherNominalShuntsNorDynamicMatrix() throws Exception {
        var net=network();var d=RepccShuntNativeReference.profiles().getFirst().parameters();
        var b=boundary(net,configured(d,3,d.measurementBranch(),3,0,d.shunts()));b.initializeShunts(Map.of());assertFalse(b.shuntController().isEnabled());
        assertTrue(b.advanceShunts(1).isEmpty());assertEquals(1,b.shuntController().time());
        generator(net).setMvaBase(0);assertThrows(IllegalArgumentException.class,()->boundary(net,d));
    }
}
