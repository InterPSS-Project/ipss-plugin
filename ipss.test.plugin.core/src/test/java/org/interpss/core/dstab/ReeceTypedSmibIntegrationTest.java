package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.ReeceLocalPiNativeReference;
import org.interpss.dstab.renewable.*;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.*;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Typed attachment only: this test does not imply native DYR import support. */
public class ReeceTypedSmibIntegrationTest extends CorePluginTestSetup {
    private static final String BASE="testData/adpter/psse/v33/renewable/";
    @Test void flatRunAtBothSteps() throws Exception {for(double h:new double[]{.0005,.00025})run(h,"flat");}
    @Test void threeCycleFaultAtBothSteps() throws Exception {for(double h:new double[]{.0005,.00025})run(h,"fault");}
    @Test void tripAndRestartAtBothSteps() throws Exception {for(double h:new double[]{.0005,.00025})run(h,"trip");}
    private static void run(double h,String profile) throws Exception {
        var context=new PSSEMultiFileLoader().loadDStab(BASE+"regca_reeca_repca_bus1062.raw",BASE+"regca_reecd_paux_bus1062.dyr");
        var net=context.getDStabilityNet();net.setAllowGenWithoutMach(true);var algo=context.getDynSimuAlgorithm();
        algo.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);assertTrue(algo.getAclfAlgorithm().loadflow());
        var gen=(DStabGen)net.getBus("Bus1").getContributeGen("1");var converter=assertInstanceOf(Regca1Model.class,gen.getDynamicGenDevice());
        var controller=new Reece1Model(ReeceLocalPiNativeReference.profiles().getFirst().parameters());converter.setActiveElectricalController(controller);
        algo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algo.setSimuStepSec(h);algo.setTotalSimuTimeSec(.4);algo.setSimuOutputHandler(new StateMonitor());
        if(profile.equals("fault"))net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus2",net,SimpleFaultCode.GROUND_3P,new Complex(0,.001),null,.1,.05),"reece-three-cycle");
        if(profile.equals("trip"))net.addDynamicEvent(DStabObjectFactory.createGeneratorTripEvent("Bus1","1",net,.2),"reece-trip");
        assertTrue(algo.initialization());double v0=net.getBus("Bus1").getVoltageMag(),minV=v0,maxDrift=0;boolean dip=false;Map<String,Double> stopped=null;
        for(int tick=0;tick<=(int)Math.round(.4/h);tick++) {
            assertTrue(algo.solveDEqnStep(true));double v=net.getBus("Bus1").getVoltageMag();minV=Math.min(minV,v);maxDrift=Math.max(maxDrift,Math.abs(v-v0));dip|=controller.isVoltageDip();
            for(double state:controller.getNamedStates().values())assertTrue(Double.isFinite(state));
            assertTrue(Math.hypot(controller.getIpcmd(),controller.getIqcmd())<=1.3+1e-9);
            if(profile.equals("trip")&&tick*h>.21){if(stopped==null)stopped=controller.getNamedStates();else assertEquals(stopped,controller.getNamedStates());}
        }
        if(profile.equals("flat"))assertTrue(maxDrift<1e-7,"flat voltage drift "+maxDrift);
        if(profile.equals("fault")){assertTrue(minV<v0-.1);assertTrue(dip);}
        if(profile.equals("trip")){assertNotNull(stopped);controller.initialize(.3,.1,1);assertEquals(.3,controller.getIpcmd(),1e-15);}
    }
}
