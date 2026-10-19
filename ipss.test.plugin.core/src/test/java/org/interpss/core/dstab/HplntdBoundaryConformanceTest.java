package org.interpss.core.dstab;

import java.io.StringReader;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.Test;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.*;
import org.interpss.dstab.renewable.*;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.*;
import com.interpss.core.aclf.*;
import com.interpss.dstab.*;
import org.interpss.numeric.datatype.Unit.UnitType;
import static org.junit.jupiter.api.Assertions.*;

/** Decoder/binding and physical measurement evidence, not attached dynamics.
 * The direct branch-flow oracle is independent network physics shared across
 * plant families; it does not certify hybrid controller switching or lifecycle. */
public class HplntdBoundaryConformanceTest extends CorePluginTestSetup {
    private static BaseDStabNetwork<?,?> network()throws Exception{return new PSSEMultiFileLoader().loadDStab("testData/adpter/psse/v33/renewable/regfma1_native.raw").getDStabilityNet();}
    private static HplntdData source(){return HplntdAggregationNativeReference.profiles().getFirst().plant();}
    private static HplntdData configured(int remote,int frequency,RepccData.Branch main,RepccData.Branch switching,int f,int comp,int mode){var d=source();return new HplntdData(remote,main,comp,mode,f,d.feedbackFlag(),d.feedforwardFlag(),0,switching,d.shunts(),frequency,d.reactiveFlag(),d.constants());}
    private static HplntdData configured(int from){return configured(3,3,new RepccData.Branch(from,Math.abs(from)==2?3:2,"1"),new RepccData.Branch(2,3,"1"),1,1,0);}
    private static Complex polar(double v,double angle){double a=Math.toRadians(angle);return new Complex(v*Math.cos(a),v*Math.sin(a));}
    private static List<Map<String,Double>> rows(String text){var lines=text.lines().toList();var names=lines.getFirst().split(",");var rows=new ArrayList<Map<String,Double>>();for(String line:lines.subList(1,lines.size())){var values=line.split(",");var row=new HashMap<String,Double>();for(int i=0;i<names.length;i++)row.put(names[i],Double.parseDouble(values[i]));rows.add(row);}return rows;}
    private static void equal(HplntdData a,HplntdData b){assertEquals(a.remoteBus(),b.remoteBus());assertEquals(a.measurementBranch(),b.measurementBranch());assertEquals(a.compensationFlag(),b.compensationFlag());assertEquals(a.referenceFlag(),b.referenceFlag());assertEquals(a.frequencyFlag(),b.frequencyFlag());assertEquals(a.feedbackFlag(),b.feedbackFlag());assertEquals(a.feedforwardFlag(),b.feedforwardFlag());assertEquals(a.switchingFlag(),b.switchingFlag());assertEquals(a.switchingBranch(),b.switchingBranch());assertEquals(a.shunts(),b.shunts());assertEquals(a.frequencyBus(),b.frequencyBus());assertEquals(a.reactiveFlag(),b.reactiveFlag());assertArrayEquals(a.constants(),b.constants(),0);}
    @Test void productionDecoderMatchesAllSeventeenIndependentContracts()throws Exception{
        var profiles=new LinkedHashMap<String,HplntdAggregationNativeReference.Profile>();for(var p:HplntdAggregationNativeReference.profiles())profiles.put(p.name(),p);for(var p:ReaxdLagNativeConformanceTest.reviewedProfiles())profiles.put(p.name(),p);for(var p:HplntdKernelNativeConformanceTest.reviewedProfiles())profiles.put(p.name(),p);assertEquals(17,profiles.size());
        for(var p:profiles.values()){
            var records=PsseDyrRecordReader.read(new StringReader(p.input()),p.name());var plan=HplntdNativeRecords.plan(records);assertEquals(Set.of(2),plan.plants().keySet());assertEquals(2,plan.downstream(2).size());equal(p.plant(),plan.plants().get(2).data());
            for(var link:plan.links()){assertEquals(HplntdBindingPlan.Status.LINKABLE,link.status());var expected=p.downstream(link.unit().source().busNumber());var actual=link.unit().data();assertEquals(expected.plantBus(),actual.plantBus());assertEquals(expected.plantModel(),actual.plantModel());assertArrayEquals(expected.relativeVariables(),actual.relativeVariables());assertArrayEquals(expected.constants(),actual.constants(),0);assertSame(plan.plants().get(2),link.plant());}
        }
        assertTrue(DynamicModelCatalog.find("HPLNTDU").isEmpty(),"A decoder is not a loadable runtime");assertTrue(DynamicModelCatalog.find("REAXDU").isEmpty());
    }
    @Test void directNativeBranchQueriesMatchBothEndsAtThreeLivePlantBases()throws Exception{
        double[] maximum=new double[5];int profiles=0;
        for(var p:RepccBoundaryNativeReference.profiles()){
            var net=network();var a=new HplntdNetworkBoundary(configured(2),net.getBus("Bus2"));var b=new HplntdNetworkBoundary(configured(3),net.getBus("Bus2"));var signed=new HplntdNetworkBoundary(configured(-2),net.getBus("Bus2"));
            var table=rows(p.body());assertEquals(p.samples(),table.size());var trace=new StringBuilder("time_s,plant_mva,P23_native,P23_interpss,Q23_native,Q23_interpss,P32_native,P32_interpss,Q32_native,Q32_interpss,Qswitch_native_Mvar,Qswitch_interpss_Mvar\n");
            for(var row:table){for(int bus=2;bus<=3;bus++)net.getBus("Bus"+bus).setVoltage(polar(row.get("V"+bus),row.get("A"+bus+"_DEG")));
                for(double base:new double[]{50,150,200}){
                    var f=a.measure(base);var r=b.measure(base);var negative=signed.measure(base).control();double[] expected={row.get("P23_MW")/base,row.get("Q23_MVAR")/base,row.get("P32_MW")/base,row.get("Q32_MVAR")/base,row.get("Q23_MVAR")};double[] actual={f.control().active(),f.control().reactive(),r.control().active(),r.control().reactive(),f.switchingReactiveMvar()};
                    trace.append(row.get("time_s")).append(',').append(base);for(int i=0;i<5;i++){maximum[i]=Math.max(maximum[i],Math.abs(expected[i]-actual[i])*(i<4?base/50:1./50));trace.append(',').append(expected[i]).append(',').append(actual[i]);}trace.append('\n');
                    assertEquals(-f.control().active(),negative.active(),1e-14);assertEquals(-f.control().reactive(),negative.reactive(),1e-14);assertTrue(f.control().branchCurrent().add(negative.branchCurrent()).abs()<1e-14);
                }
            }
            var out=Path.of("target/hplntd-boundary-comparisons",p.name()+".csv");Files.createDirectories(out.getParent());Files.writeString(out,trace);profiles++;
        }
        assertEquals(6,profiles);double[] bounds={2.64e-7,3.73e-6,2.64e-7,3.62e-6,3.73e-6};System.out.println("Hybrid physical branch normalized errors="+Arrays.toString(maximum));for(int i=0;i<5;i++)assertTrue(maximum[i]<bounds[i],"Physical branch channel "+i+"="+maximum[i]);
    }
    @Test void missingBranchUsesExactIgnoreTruthTableNotGeneratorFallback()throws Exception{
        var net=network();net.getBus("Bus2").setFreq(.98);
        for(int f=0;f<=1;f++)for(int comp=0;comp<=1;comp++)for(int mode=0;mode<=2;mode++){
            var d=configured(0,0,new RepccData.Branch(0,0,"0"),new RepccData.Branch(0,0,"0"),f,comp,mode);var boundary=new HplntdNetworkBoundary(d,net.getBus("Bus2"));boolean allowed=f==0&&comp==1&&mode==1;assertEquals(!allowed,boundary.ignored());assertFalse(boundary.mainBranchFound());assertFalse(boundary.switchingBranchFound());
            if(allowed){var m=boundary.measure(150);assertEquals(0,m.control().active());assertEquals(0,m.control().reactive());assertEquals(Complex.ZERO,m.control().branchCurrent());assertEquals(-.02,m.control().frequencyDeviation(),1e-14);assertEquals(0,m.switchingReactiveMvar());}
            else assertThrows(IllegalStateException.class,()->boundary.measure(150));
        }
    }
    @Test void zeroBusFallbacksAreExplicitAndUnresolvedSpecifiedBusesAreRejected()throws Exception{
        var net=network();var main=new RepccData.Branch(2,3,"1");var zero=new RepccData.Branch(0,0,"0");var b=new HplntdNetworkBoundary(configured(0,0,main,zero,1,1,0),net.getBus("Bus2"));assertEquals("Bus2",b.regulatedBusId());assertEquals("Bus2",b.frequencyBusId());net.getBus("Bus2").setFreq(.99);assertEquals(-.01,b.measure(150).control().frequencyDeviation(),1e-14);
        for(double bad:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY})assertThrows(IllegalArgumentException.class,()->b.measure(bad));
        assertThrows(IllegalArgumentException.class,()->new HplntdNetworkBoundary(configured(999,0,main,zero,1,1,0),net.getBus("Bus2")));assertThrows(IllegalArgumentException.class,()->new HplntdNetworkBoundary(configured(0,999,main,zero,1,1,0),net.getBus("Bus2")));
        net.setBaseKva(200000);assertThrows(IllegalStateException.class,()->b.measure(150));
    }
    @Test void transformerPortsAndFaultedVoltageDoNotUsePowerOverVoltage()throws Exception{
        var net=network();var branch=(AclfBranch)net.getBranch("Bus2","Bus3","1");branch.setBranchCode(AclfBranchCode.XFORMER);branch.setFromTurnRatio(1.1);branch.setToTurnRatio(1);net.getBus("Bus2").setVoltage(new Complex(.93,.07));net.getBus("Bus3").setVoltage(new Complex(1.02,-.02));
        Complex tapped=branch.powerFrom2To(UnitType.PU);branch.setBranchCode(AclfBranchCode.PS_XFORMER);branch.setFromPSXfrAngle(.13);assertTrue(branch.powerFrom2To(UnitType.PU).subtract(tapped).abs()>1e-3,"Activate the phase-shifting path, not only a stored unused angle");
        for(int from:new int[]{2,-2,3,-3}){var b=new HplntdNetworkBoundary(configured(from),net.getBus("Bus2"));Complex expected=(Math.abs(from)==2?branch.powerFrom2To(UnitType.PU):branch.powerTo2From(UnitType.PU)).multiply((from<0?-1:1)*100./150);var m=b.measure(150).control();assertEquals(expected.getReal(),m.active(),1e-12);assertEquals(expected.getImaginary(),m.reactive(),1e-12);}
        var b=new HplntdNetworkBoundary(configured(2),net.getBus("Bus2"));net.getBus("Bus2").setVoltage(Complex.ZERO);var m=b.measure(150).control();assertEquals(0,m.active(),0.);assertEquals(0,m.reactive(),0.);assertTrue(m.branchCurrent().abs()>0);branch.setStatus(false);assertEquals(Complex.ZERO,b.measure(150).control().branchCurrent());
    }
    @Test void physicalSwitchingUnitsAndNetworkMatrixRemainUnchangedByObservation()throws Exception{
        var net=network();net.getBus("Bus2").setVoltage(new Complex(.9,.1));net.getBus("Bus3").setVoltage(Complex.ONE);net.formYMatrix4DStab();net.setYMatrixDirty(false);int i=net.getBus("Bus2").getSortNumber();Complex y=net.getYMatrix().getA(i,i);var b=new HplntdNetworkBoundary(configured(2),net.getBus("Bus2"));var a=b.measure(50);var c=b.measure(150);assertTrue(Math.abs(a.switchingReactiveMvar())>1);assertEquals(a.switchingReactiveMvar(),c.switchingReactiveMvar(),0);assertEquals(a.control().reactive()/3,c.control().reactive(),1e-14);for(int n=0;n<10;n++)b.measure(200);assertEquals(y,net.getYMatrix().getA(i,i));assertFalse(net.isYMatrixDirty());
    }
}
