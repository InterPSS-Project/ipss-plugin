package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.WtgwgoaNativeReference;
import org.interpss.core.dstab.reference.WtgwgoaNetworkCoarse;
import org.interpss.core.dstab.reference.WtgwgoaNetworkFine;
import org.interpss.dstab.renewable.*;
import com.interpss.dstab.renewable.*;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.*;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Coupled solver/lifecycle checks; raw native discontinuities remain diagnostics. */
public class WtgwgoaSmibIntegrationTest extends CorePluginTestSetup {
    @Test void flatRunsRetainEquilibriumAtBothSteps(@TempDir Path temporary) throws Exception {
        for(double h:new double[]{.0005,.00025})run(h,false,false,false,temporary);
    }
    @Test void threeCycleFaultActivatesBothRampsAndReturnsToBypass(@TempDir Path temporary) throws Exception {
        for(double h:new double[]{.0005,.00025})run(h,true,false,false,temporary);
    }
    @Test void noPlantUsesFixedInitialReference(@TempDir Path temporary) throws Exception {
        for(double h:new double[]{.0005,.00025})run(h,true,false,true,temporary);
    }
    @Test void generatorTripFreezesWeakGridStatesAndTimer(@TempDir Path temporary) throws Exception {
        for(double h:new double[]{.0005,.00025})run(h,true,true,false,temporary);
    }
    private static void run(double h,boolean fault,boolean trip,boolean noPlant,Path temporary) throws Exception {
        Path base=Path.of("testData/adpter/psse/v33/renewable");
        Path dyr=base.resolve("regca_reeca_wtgwgoa_bus1062.dyr");
        if(noPlant){Path filtered=temporary.resolve("no-plant.dyr");Files.writeString(filtered,
                Files.readString(dyr).lines().filter(s->!s.contains("REPCA1")).reduce("",(a,b)->a+b+"\n"));dyr=filtered;}
        var context=new PSSEMultiFileLoader().loadDStab(base.resolve("regca_reeca_repca_bus1062.raw").toString(),dyr.toString());
        var net=context.getDStabilityNet();net.setAllowGenWithoutMach(true);
        var algorithm=context.getDynSimuAlgorithm();
        algorithm.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algorithm.setSimuStepSec(h);
        algorithm.setTotalSimuTimeSec(1.2);algorithm.setSimuOutputHandler(new StateMonitor());
        if(fault)net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus2",net,
                SimpleFaultCode.GROUND_3P,new Complex(0,1),null,.05,.05),"WGO-fault");
        if(trip)net.addDynamicEvent(DStabObjectFactory.createGeneratorTripEvent("Bus1","1",net,.2),"WGO-trip");
        assertTrue(algorithm.initialization());
        var gen=(DStabGen)net.getBus("Bus1").getContributeGen("1");var converter=(Regca1Model)gen.getDynamicGenDevice();
        var control=converter.getReeca1Controller();var weak=control.getWeakGridController();assertNotNull(weak);
        assertEquals(weak.getNativeStates()[0],converter.getNamedStates().get("WTGWGOA.Terminal Voltage Filter"));
        double[] initial=weak.getNativeStates(),held=null,heldVars=null;
        double initialVoltage=net.getBus("Bus1").getVoltageMag();var modes=EnumSet.noneOf(WtgwgoaModel.Mode.class);
        var observed=new ArrayList<double[]>();var csv=new ArrayList<String>();
        csv.add("time_s,vfilter,ramp1,ramp2,captured,timer,stored,pref,plant_pref,v1,v2,v3,p,q");
        int lastTick=(int)Math.round(1.2/h);
        if(fault&&!trip&&!noPlant){var reference=WtgwgoaNativeReference.profiles().stream()
                .filter(r->r.name().equals(h==.0005?"fault-coarse":"fault-fine")).findFirst().orElseThrow();
            lastTick=reference.tick(reference.samples()-1);}
        for(int tick=0;tick<=lastTick;tick++){
            if(tick>0)assertTrue(algorithm.solveDEqnStep(true),"WGO tick "+tick);
            double[] s=weak.getNativeStates(),vars=weak.getNativeVars();modes.add(weak.getMode());
            for(double value:s)assertTrue(Double.isFinite(value));for(double value:vars)assertTrue(Double.isFinite(value));
            double v1=net.getBus("Bus1").getVoltageMag();
            if(!fault){assertArrayEquals(initial,s,1e-9);assertEquals(initialVoltage,v1,1e-9);}
            if(trip && algorithm.getSimuTime()>=.21){if(held==null){held=s;heldVars=vars;}
                else{assertArrayEquals(held,s,0);assertArrayEquals(heldVars,vars,0);}}
            if(noPlant)assertEquals(weak.getNativeVars()[0],control.getPlantPowerReference(.24),1e-8);
            var row=new double[]{s[0],s[1],s[2],vars[0],vars[1],vars[2],weak.getReference(),control.getPlantPowerReference(.24),
                    v1,net.getBus("Bus2").getVoltageMag(),net.getBus("Bus3").getVoltageMag(),
                    converter.getNamedStates().get("Active Power"),converter.getNamedStates().get("Reactive Power")};
            observed.add(row);csv.add(algorithm.getSimuTime()+","+String.join(",",Arrays.stream(row).mapToObj(Double::toString).toList()));
        }
        if(fault&&!trip){assertTrue(modes.containsAll(EnumSet.allOf(WtgwgoaModel.Mode.class)));assertEquals(WtgwgoaModel.Mode.BYPASS,weak.getMode());}
        if(trip){assertNotNull(held);assertFalse(gen.isActive());assertFalse(converter.isActive());}
        Path output=Path.of("target","wtgwgoa-smib");Files.createDirectories(output);
        Files.write(output.resolve((trip?"trip":fault?noPlant?"no-plant":"fault":"flat")+"-"+h+".csv"),csv);
        if(fault&&!trip&&!noPlant){nativeDiagnostic(h,observed);assertNativeNetworkEnvelope(h,observed);}
    }
    private static void nativeDiagnostic(double h,List<double[]> actual){
        var p=WtgwgoaNativeReference.profiles().stream().filter(r->r.name().equals(h==.0005?"fault-coarse":"fault-fine")).findFirst().orElseThrow();p.verify();
        var inputs=p.inputs().lines().map(line->Arrays.stream(line.split(",")).mapToDouble(Double::parseDouble).toArray()).toList();
        double[] maximum=new double[8],at=new double[8];double sampledVoltageMaximum=0;int posts=0;
        for(String line:p.states().lines().toList()){
            String[] fields=line.split(",");int row=Integer.parseInt(fields[0]);double[] observed=actual.get(p.tick(row));
            if(p.isPost(row))posts++;
            else sampledVoltageMaximum=Math.max(sampledVoltageMaximum,Math.abs(observed[8]-inputs.get(row)[0]));
            for(int c=0;c<8;c++){double expected=c<6?Double.parseDouble(fields[c+2]):inputs.get(row)[c-6];
                double value=c<6?observed[c]:c==6?observed[8]:observed[7];double error=Math.abs(value-expected);
                if(error>maximum[c]){maximum[c]=error;at[c]=Double.parseDouble(fields[1]);}}
        }
        assertEquals(2,posts,"Retain both instantaneous network-post observations as unaccepted");
        assertTrue(maximum[0]<=(h==.0005?4.4e-4:2.3e-4),"Coupled filtered-voltage STATE");
        assertTrue(maximum[7]<=4.3e-4,"Coupled live plant-reference channel");
        assertTrue(sampledVoltageMaximum<=5e-5,"Regular sampled V1 boundary");
        System.out.println("WTGWGOA coupled diagnostic h="+h+" raw STATE/VAR,V1,plant maxima="+Arrays.toString(maximum)+" at="+Arrays.toString(at)
                +"; regular sampled V1 max="+sampledVoltageMaximum+"; callback/handoff and instantaneous network-post parity remain unaccepted");
    }
    private static void assertNativeNetworkEnvelope(double h,List<double[]> actual){
        String body=h==.0005?WtgwgoaNetworkCoarse.body():WtgwgoaNetworkFine.body();
        assertEquals(h==.0005?"2221f814cea1cf75a33af10447b43c1641df709a032e55f48a670c38ddb368d5"
                :"3dd83f179053ea975cc646f4b44cd2dd711e454b63a3c47106326f643d233500",WtgwgoaNativeReference.digest(body));
        double[] maximum=new double[5],at=new double[5],postMaximum=new double[5];int posts=0;
        for(String line:body.lines().toList()){
            double[] row=Arrays.stream(line.split(",")).mapToDouble(Double::parseDouble).toArray();
            double[] observed=actual.get((int)row[1]);boolean post=row[3]==1;if(post)posts++;
            for(int c=0;c<5;c++){double error=Math.abs(observed[c+8]-row[c+4]);
                if(post)postMaximum[c]=Math.max(postMaximum[c],error);
                else if(error>maximum[c]){maximum[c]=error;at[c]=row[2];}}
        }
        assertEquals(2,posts);assertEquals(245,body.lines().count());
        double[] bounds={5e-5,2.2e-5,4.8e-6,h==.0005?1.8e-3:9e-4,2.5e-6};
        for(int c=0;c<5;c++)assertTrue(maximum[c]<=bounds[c],"Native sampled network channel "+c+": "+maximum[c]);
        System.out.println("WTGWGOA native network h="+h+" sampled V1,V2,V3,P,Q max="+Arrays.toString(maximum)+" at="+Arrays.toString(at)
                +"; retained unaccepted instantaneous network-post max="+Arrays.toString(postMaximum));
    }
}
