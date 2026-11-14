package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.ReeceLocalPiNativeReference;
import org.interpss.dstab.renewable.*;
import com.interpss.dstab.renewable.*;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.*;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Typed tests; native-import tests reuse the runner without manual attachment. */
public class ReeceTypedSmibIntegrationTest extends CorePluginTestSetup {
    private static final String BASE="testData/adpter/psse/v33/renewable/";
    @Test void flatRunAtBothSteps() throws Exception {for(double h:new double[]{.0005,.00025})run(h,"flat");}
    @Test void threeCycleFaultAtBothSteps() throws Exception {for(double h:new double[]{.0005,.00025})run(h,"fault");}
    @Test void tripAndRestartAtBothSteps() throws Exception {for(double h:new double[]{.0005,.00025})run(h,"trip");}
    private static void run(double h,String profile) throws Exception {
        run(h,profile,false);
    }
    static void run(double h,String profile,boolean nativeImport) throws Exception {
        run(h,profile,nativeImport,BASE+(nativeImport?"regca_reece_bus1062.dyr":"regca_reecd_paux_bus1062.dyr"));
    }
    static void run(double h,String profile,java.nio.file.Path nativeDyr) throws Exception {run(h,profile,true,nativeDyr.toString());}
    private static void run(double h,String profile,boolean nativeImport,String dyr) throws Exception {
        var context=new PSSEMultiFileLoader().loadDStab(BASE+"regca_reeca_repca_bus1062.raw",dyr);
        var net=context.getDStabilityNet();net.setAllowGenWithoutMach(true);var algo=context.getDynSimuAlgorithm();
        algo.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);assertTrue(algo.getAclfAlgorithm().loadflow());
        var gen=(DStabGen)net.getBus("Bus1").getContributeGen("1");var device=gen.getDynamicGenDevice();
        var electrical=device instanceof Regca1Model a?a.getActiveElectricalController():assertInstanceOf(Regcb1Model.class,device).getActiveElectricalController();
        var controller=nativeImport?assertInstanceOf(Reece1Model.class,electrical)
                :new Reece1Model(ReeceLocalPiNativeReference.profiles().getFirst().parameters());
        if(!nativeImport)assertInstanceOf(Regca1Model.class,device).setActiveElectricalController(controller);
        if(nativeImport&&java.nio.file.Files.readString(java.nio.file.Path.of(dyr)).contains("'REPCA1'"))assertNotNull(controller.getPlantController());
        assertEquals(ReeceLocalPiNativeReference.profiles().getFirst().parameters(),controller.getData());
        algo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algo.setSimuStepSec(h);algo.setTotalSimuTimeSec(.4);algo.setSimuOutputHandler(new StateMonitor());
        if(profile.equals("fault"))net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(device instanceof Regcb1Model?"Bus1":"Bus2",net,
                SimpleFaultCode.GROUND_3P,new Complex(0,device instanceof Regcb1Model?.01:.001),null,.1,.05),"reece-three-cycle");
        if(profile.equals("trip"))net.addDynamicEvent(DStabObjectFactory.createGeneratorTripEvent("Bus1","1",net,.2),"reece-trip");
        assertTrue(algo.initialization());double v0=net.getBus("Bus1").getVoltageMag(),minV=v0,maxDrift=0;boolean dip=false;Map<String,Double> stopped=null;
        var trace=new java.util.ArrayList<String>();trace.add("time_s,v1r,v1i,v2r,v2i,v3r,v3i,p,q,Vmeas,Pmeas,PIQ,PIV,Q_V,Pord,Vcomp,PIP,IP,IQ,V_FILTER,EQ,ED");
        try { for(int tick=0;tick<=(int)Math.round(.4/h);tick++) {
            if(device instanceof Regcb1Model b) {
                var row=new java.util.ArrayList<Double>();row.add(algo.getSimuTime());
                for(String id:new String[]{"Bus1","Bus2","Bus3"}){var voltage=net.getBus(id).getVoltage();row.add(voltage.getReal());row.add(voltage.getImaginary());}
                row.add(b.getP());row.add(b.getQ());row.addAll(controller.getNamedStates().values());for(double value:b.getStateSnapshot())row.add(value);
                trace.add(String.join(",",row.stream().map(Object::toString).toList()));
            }
            assertTrue(algo.solveDEqnStep(true));double v=net.getBus("Bus1").getVoltageMag();minV=Math.min(minV,v);maxDrift=Math.max(maxDrift,Math.abs(v-v0));dip|=controller.isVoltageDip();
            for(double state:controller.getNamedStates().values())assertTrue(Double.isFinite(state));
            assertTrue(Math.hypot(controller.getIpcmd(),controller.getIqcmd())<=1.3+1e-9);
            if(profile.equals("trip")&&tick*h>.21){if(stopped==null)stopped=controller.getNamedStates();else assertEquals(stopped,controller.getNamedStates());}
        }} finally {
            if(device instanceof Regcb1Model) {
                var output=java.nio.file.Path.of("target","reece-import-traces","native-regcb",profile+"-"+h+".csv");
                java.nio.file.Files.createDirectories(output.getParent());java.nio.file.Files.write(output,trace);
            }
        }
        if(profile.equals("flat"))assertTrue(maxDrift<1e-7,"flat voltage drift "+maxDrift);
        if(profile.equals("fault")){assertTrue(minV<v0-.1);assertTrue(dip);}
        if(profile.equals("trip")){assertNotNull(stopped);controller.initialize(.3,.1,1);assertEquals(.3,controller.getIpcmd(),1e-15);}
    }
}
