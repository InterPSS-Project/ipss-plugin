package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.WtgibffraNativeReference;
import org.interpss.core.dstab.reference.WtgibffraCoupledReference;
import org.interpss.dstab.renewable.*;
import com.interpss.dstab.renewable.*;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.*;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;

public class WtgibffraSmibIntegrationTest extends CorePluginTestSetup {
    private static final Path BASE=Path.of("testData/adpter/psse/v33/renewable");
    @Test void flatRunsStayAtEquilibriumAtBothSteps(@TempDir Path temporary) throws Exception {
        for(double h:new double[]{.0005,.00025})run(h,"flat",temporary);
    }
    @Test void physicalRemoteSpeedStepExercisesInertiaThroughNetworkAndElectricalController(@TempDir Path temporary) throws Exception {
        for(double h:new double[]{.0005,.00025})run(h,"speed",temporary);
    }
    @Test void threeCycleFaultDoesNotSpuriouslyTriggerFromRemoteInfiniteBusFrequency(@TempDir Path temporary) throws Exception {
        for(double h:new double[]{.0005,.00025})run(h,"fault",temporary);
    }
    @Test void generatorTripFreezesInertiaStatesAndReinitializationClearsResponse(@TempDir Path temporary) throws Exception {
        for(double h:new double[]{.0005,.00025})run(h,"trip",temporary);
    }
    @Test void governorReferenceStepProducesIndependentlyReproducibleUnderfrequency(@TempDir Path temporary) throws Exception {
        double[] coarse=run(.0005,"governor",temporary),fine=run(.00025,"governor",temporary);
        for(int channel:new int[]{28,30,34})assertTrue(fine[channel]<.7*coarse[channel],"Coupled channel "+channel+" must improve under step halving");
    }
    @Test void coupledReferenceBodiesHaveFrozenColdPairIdentities() {
        var bundle=new StringBuilder();
        for(var p:WtgibffraCoupledReference.profiles()) {
            p.body();bundle.append(p.rawSha256()).append('|').append(p.bodySha256()).append('\n');
            assertThrows(UnsupportedOperationException.class,()->p.transitions().clear());
        }
        assertEquals("9c8aad9c83002b6c07d8cb5a3699424892e1ade453eb3a36e33a6fa137413673",WtgibffraNativeReference.digest(bundle.toString()));
    }
    private static double[] run(double h,String kind,Path temporary) throws Exception {
        double[] constants=WtgibffraNativeReference.profiles().getFirst().constants();
        Path dyr=temporary.resolve("inertia-"+kind+"-"+h+".dyr");
        String dynamics=Files.readString(BASE.resolve("regca_reecd_paux_bus1062.dyr"));
        Path raw=BASE.resolve("regca_reeca_repca_bus1062.raw");
        if(kind.equals("governor")) {
            dynamics=dynamics.replace("99999.0","3.0")+"3 'TGOV1' 1 .05 .1 1.5 0 0 .1 0 /\n";
            raw=temporary.resolve("loaded.raw");Files.writeString(raw,Files.readString(BASE.resolve("regca_reeca_repca_bus1062.raw"))
                    .replace("0 / END OF LOAD DATA","2,'1',1,1,1,100.0,20.0,0,0,0,0,1,1,0\n0 / END OF LOAD DATA"));
        }
        Files.writeString(dyr,dynamics+
                "1 'WTGIBFFRA' 1 3 "+String.join(" ",Arrays.stream(constants).mapToObj(Double::toString).toList())+" /\n");
        var context=new PSSEMultiFileLoader().loadDStab(raw.toString(),dyr.toString());
        var net=context.getDStabilityNet();net.setAllowGenWithoutMach(true);var algorithm=context.getDynSimuAlgorithm();
        algorithm.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algorithm.setSimuStepSec(h);algorithm.setTotalSimuTimeSec(2.5);
        algorithm.setSimuOutputHandler(new StateMonitor());
        if(kind.equals("fault"))net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus2",net,
                SimpleFaultCode.GROUND_3P,new Complex(0,1),null,.05,.05),"inertia-three-cycle-fault");
        if(kind.equals("trip"))net.addDynamicEvent(DStabObjectFactory.createGeneratorTripEvent("Bus1","1",net,.2),"inertia-trip");
        if(kind.equals("governor"))for(var bus:net.getBusList())
            assertInstanceOf(com.interpss.dstab.controller.deqn.measure.BusFreqMeasurementImpl.class,bus.getBusFreqMeasureBlock()).setTf(.05);
        assertTrue(algorithm.initialization());var gen=(DStabGen)net.getBus("Bus1").getContributeGen("1");
        var converter=(Regca1Model)gen.getDynamicGenDevice();var electrical=(Reecd1Model)converter.getActiveElectricalController();
        var inertia=electrical.getInertialResponseController();assertNotNull(inertia);
        var remote=(Machine)((DStabGen)net.getBus("Bus3").getContributeGen("1")).getDynamicGenDevice();
        var modes=EnumSet.noneOf(WtgibffraModel.Mode.class);double[] held=null,heldVars=null;
        double initialVoltage=net.getBus("Bus1").getVoltageMag();double initialPower=converter.getNamedStates().get("Active Power");
        double maxP=initialPower,minP=initialPower,minVoltage=initialVoltage;var csv=new ArrayList<String>();
        csv.add("time_s,mode,rise,fall,recovery,fmeas,paux,ipcmd,v1,v2,v3,p,q,f3,iqcmd,"+
                String.join(",",java.util.stream.IntStream.range(0,17).mapToObj(i->"IB_VAR"+i).toList())+","+
                String.join(",",java.util.stream.IntStream.range(0,7).mapToObj(i->"EC_STATE"+i).toList())+",speed3,pm3,pe3");
        int lastTick=(int)Math.round(2.5/h)+1,changeTick=(int)Math.round(.05/h);
        for(int tick=0;tick<=lastTick;tick++) {
            if(tick>0)assertTrue(algorithm.solveDEqnStep(true),kind+" tick "+tick);
            if(tick==changeTick&&(kind.equals("speed")||kind.equals("trip")))remote.setSpeed(.98);
            if(tick==changeTick&&kind.equals("governor")) {
                var governor=assertInstanceOf(com.interpss.dstab.controller.gov.tgov1.PsseTGov1SteamTurGovernor.class,remote.getGovernor());
                governor.setRefPoint(governor.getRefPoint()-.01);
            }
            double[] s=inertia.getNativeStates();var named=converter.getNamedStates();modes.add(inertia.getMode());
            assertEquals(s[0],named.get("WTGIBFFRA.Rise Power Increment"));
            assertEquals(inertia.getAuxiliaryPower(),electrical.getAuxiliaryPower(),0);
            for(double value:s)assertTrue(Double.isFinite(value));
            double v1=net.getBus("Bus1").getVoltageMag(),p=named.get("Active Power");
            maxP=Math.max(maxP,p);minP=Math.min(minP,p);minVoltage=Math.min(minVoltage,v1);
            if(kind.equals("flat")){assertEquals(initialVoltage,v1,1e-8);assertEquals(initialPower,p,1e-8);assertEquals(WtgibffraModel.Mode.READY,inertia.getMode());}
            if(kind.equals("trip")&&algorithm.getSimuTime()>.21){
                if(held==null){held=s;heldVars=inertia.getVariableSnapshot();}
                else {assertArrayEquals(held,s,0);assertArrayEquals(heldVars,inertia.getVariableSnapshot(),0);}
            }
            csv.add(algorithm.getSimuTime()+","+inertia.getMode().code()+","+s[0]+","+s[1]+","+s[2]+","+s[3]+","+
                    inertia.getAuxiliaryPower()+","+electrical.getIpcmd()+","+v1+","+net.getBus("Bus2").getVoltageMag()+","+
                    net.getBus("Bus3").getVoltageMag()+","+p+","+named.get("Reactive Power")+","+net.getBus("Bus3").getFreq()+","+electrical.getIqcmd()+","+
                    String.join(",",Arrays.stream(inertia.getVariableSnapshot()).mapToObj(Double::toString).toList())+","+
                    String.join(",",electrical.getNamedStates().values().stream().map(Object::toString).toList())+","+remote.getSpeed()+","+remote.getPm()+","+remote.getPe());
        }
        Path output=Path.of("target/wtgibffra-import-smib");Files.createDirectories(output);Files.write(output.resolve(kind+"-"+h+".csv"),csv);
        if(kind.equals("speed")||kind.equals("governor")) {
            assertTrue(modes.containsAll(EnumSet.of(WtgibffraModel.Mode.RISE,WtgibffraModel.Mode.PEAK,WtgibffraModel.Mode.FALL,
                    WtgibffraModel.Mode.RECOVERY,WtgibffraModel.Mode.COOLDOWN)),modes.toString());
            assertTrue(maxP>initialPower+.01);assertTrue(minP<initialPower-.005);
        }
        if(kind.equals("fault")){assertTrue(minVoltage<initialVoltage-.02);assertEquals(Set.of(WtgibffraModel.Mode.READY),modes);}
        if(kind.equals("trip")) {
            assertNotNull(held);assertFalse(gen.isActive());assertFalse(converter.isActive());
            assertTrue(converter.initStates(net.getDStabBus("Bus1")));assertEquals(0,inertia.getAuxiliaryPower());
            assertEquals(WtgibffraModel.Mode.READY,inertia.getMode());
        }
        System.out.println("WTGIBFFRA coupled SMIB "+kind+" h="+h+" modes="+modes+" P-range="+minP+".."+maxP+" Vmin="+minVoltage);
        return kind.equals("governor")?compareNative(h,csv):new double[38];
    }

    private static double[] compareNative(double h,List<String> csv) {
        var profile=WtgibffraCoupledReference.profiles().stream().filter(p->p.step()==h).findFirst().orElseThrow();
        List<double[]> actual=csv.stream().skip(1).map(WtgibffraSmibIntegrationTest::numbers).toList();
        Map<String,Integer> ac=columns(csv.getFirst());List<String> lines=profile.body().lines().toList();Map<String,Integer> nc=columns(lines.getFirst());
        assertTrue(actual.size()>profile.lastTick());
        var transitions=new ArrayList<WtgibffraCoupledReference.Transition>();int mode=Integer.MIN_VALUE;
        for(int i=0;i<actual.size();i++)if((int)actual.get(i)[ac.get("mode")]!=mode) {
            mode=(int)actual.get(i)[ac.get("mode")];transitions.add(new WtgibffraCoupledReference.Transition(i,i*h,mode));
        }
        assertEquals(profile.transitions().stream().map(WtgibffraCoupledReference.Transition::mode).toList(),
                transitions.stream().map(WtgibffraCoupledReference.Transition::mode).toList());
        double lag=0;
        for(int i=0;i<transitions.size();i++) {
            double delta=Math.abs(transitions.get(i).rawTime()-profile.transitions().get(i).rawTime());
            assertTrue(delta<=(i+2)*h+.00015,"Coupled phase "+i+": "+delta);lag=Math.max(lag,delta);
        }
        final double window=lag+h+.0001;
        var ak=new ArrayList<String>(List.of("rise","fall","recovery","fmeas"));
        var nk=new ArrayList<String>(List.of("IB_STATE0","IB_STATE1","IB_STATE2","IB_STATE3"));
        for(int i=0;i<17;i++){ak.add("IB_VAR"+i);nk.add("IB_VAR"+i);}
        for(int i=0;i<7;i++){ak.add("EC_STATE"+i);nk.add("EC_STATE"+i);}
        ak.addAll(List.of("ipcmd","iqcmd","paux","v1","v2","v3","p","q","f3","speed3"));
        nk.addAll(List.of("EC_VAR13","EC_VAR14","EC_VAR6","V1","V2","V3","P_PU","Q_PU","FDEV3","SG_STATE0"));
        double[] raw=new double[38],scored=new double[38],at=new double[38];int posts=0,sustained=0;
        for(String line:lines.subList(1,lines.size())) {
            double[] expected=numbers(line);int tick=(int)expected[nc.get("tick")];double[] value=actual.get(tick);double time=tick*h;
            if(expected[nc.get("post")]==1)posts++;
            boolean callback=profile.transitions().stream().anyMatch(t->Math.abs(time-t.rawTime())<=window)
                    ||transitions.stream().anyMatch(t->Math.abs(time-t.rawTime())<=window);
            boolean same=(int)value[ac.get("mode")]==(int)expected[nc.get("IB_VAR16")];
            if(!callback&&same)sustained++;
            for(int c=0;c<38;c++) {
                double oracle=expected[nc.get(nk.get(c))];
                // REECD VAR6 is the separate external Paux slot. WPAUX's
                // published output is the sum of its three ramp STATEs.
                if(c==30)for(int i=0;i<3;i++)oracle+=expected[nc.get("IB_STATE"+i)];
                if(c==29)oracle=-oracle; // native positive-Q command vs negative REECD Iqcmd
                if(c==36||c==37)oracle+=1; // frequency/speed deviations -> absolute pu
                if(c==24||c==27)assertEquals(0,oracle,0,"Only inactive native storage may be excluded");
                double error=Math.abs(value[ac.get(ak.get(c))]-oracle);assertTrue(Double.isFinite(error));
                if(error>raw[c]){raw[c]=error;at[c]=expected[nc.get("time_s")];}
                // Only discontinuous inertia flags/ramps exclude callback
                // windows. Filter and all electrical/network channels do not.
                if(c==3||c>=21||same&&!callback)scored[c]=Math.max(scored[c],error);
            }
        }
        assertEquals(profile.checkpoints(),lines.size()-1);assertEquals(1,posts);assertTrue(sustained>400);
        double[] bounds=new double[38];Arrays.fill(bounds,1e-7);
        double floatBudget=profile.lastTick()*Math.ulp((float).036);
        double captured=lines.stream().skip(1).map(WtgibffraSmibIntegrationTest::numbers).mapToDouble(row->row[nc.get("IB_VAR1")]).max().orElseThrow();
        var band=WtgibffraNativeReference.profiles().getFirst().parameters().bands().getFirst();
        bounds[0]=band.boost()*captured/band.riseTime()*(lag+h)+floatBudget;
        bounds[1]=(band.boost()+band.deficit())*captured/band.fallTime()*(lag+h)+floatBudget;
        bounds[2]=band.deficit()*captured/1.2*(lag+h)+floatBudget;
        bounds[3]=bounds[4]=h==.0005?2.45e-5:3.4e-5;
        bounds[5]=bounds[16]=bounds[17]=3.8e-6;bounds[18]=bounds[19]=window;
        bounds[21]=3.6e-5;bounds[22]=bounds[23]=1e-12;bounds[25]=6e-6;
        // Native Pord is already 0.240001395345 in negative-time initialization
        // and remains constant. Retain that raw offset; do not fit initialP.
        bounds[26]=2e-6;
        bounds[28]=h==.0005?.0011:.00044;bounds[29]=6e-6;bounds[30]=h==.0005?.0009:.00035;
        bounds[31]=3.6e-5;bounds[32]=2.9e-5;bounds[33]=2.9e-6;bounds[34]=h==.0005?.0011:.00045;
        bounds[35]=h==.0005?5.5e-6:4.7e-6;bounds[36]=3.5e-5;
        for(int c=0;c<37;c++)if(c!=24&&c!=27)assertTrue(scored[c]<=bounds[c],profile.name()+" channel "+ak.get(c)+": "+scored[c]+" > "+bounds[c]);
        // PIV and Vcomp inactive storage coordinates, native callback resets,
        // and SG/governor pointwise conformance remain explicitly unaccepted.
        System.out.println("WTGIBFFRA coupled "+profile.name()+" raw-max="+Arrays.toString(raw)+" times="+Arrays.toString(at)+" scored="+Arrays.toString(scored));
        return scored;
    }
    private static Map<String,Integer> columns(String header) {
        String[] names=header.split(",");var result=new HashMap<String,Integer>();for(int i=0;i<names.length;i++)result.put(names[i],i);return result;
    }
    private static double[] numbers(String line){return Arrays.stream(line.split(",")).mapToDouble(Double::parseDouble).toArray();}
}
