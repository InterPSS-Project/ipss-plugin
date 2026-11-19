package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.renewable.*;
import com.interpss.dstab.renewable.*;
import org.junit.jupiter.api.Test;
import com.interpss.dstab.*;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.device.NetworkIterationAware;

/** Committed voltage input is independent of trial count and derivative scheme. */
public class RegcaAcceptedVoltageMemoryTest {
    @Test void bothMethodsCommitOncePerPhysicalStepAndEvent() {
        for(var scheme:Regca1Model.IntegrationScheme.values()) {
            var f=fixture(scheme);var model=f.model();
            var cold=model.getAcceptedVoltageMemoryState();
            f.bus().setVoltage(Complex.NaN);model.prepareIntegrationStep(.1,.01);
            assertEquals(cold,model.getAcceptedVoltageMemoryState(),"Clock preparation never samples voltage");
            f.bus().setVoltage(new Complex(.7));
            solve(model,"INITIAL",.1,true);
            assertThrows(IllegalStateException.class,()->model.setNetworkVoltageMemoryPolicy(Regca1Model.NetworkVoltageMemoryPolicy.PER_NETWORK_SOLVE));
            assertThrows(IllegalStateException.class,()->model.setNetworkIterationHighVoltageEnabled(false));
            assertEquals(1.,model.getAcceptedVoltageMemoryState().savedVoltage(),0.);
            model.acceptNetworkEventInput(.1);
            assertEquals(.7,model.getAcceptedVoltageMemoryState().terminalVoltage(),0.);
            var event=model.getAcceptedVoltageMemoryState();
            solve(model,"INITIAL",.1,true);model.acceptNetworkEventInput(.1);
            assertEquals(event,model.getAcceptedVoltageMemoryState(),"Repeated event solve cannot commit again");
            assertTrue(model.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,0));
            var pending=model.getAcceptedVoltageMemoryState();
            assertThrows(IllegalStateException.class,()->model.setNetworkVoltageMemoryPolicy(Regca1Model.NetworkVoltageMemoryPolicy.PER_NETWORK_SOLVE));
            assertThrows(IllegalStateException.class,()->model.setNetworkIterationHighVoltageEnabled(false));
            for(String stage:new String[]{"PREDICTOR","CORRECTOR","DIRECT","DIRECT"}) {
                f.bus().setVoltage(new Complex(.66));solve(model,stage,.1,false);
                assertEquals(.76,model.getNetworkHighVoltageState().savedVoltage(),1.e-15);
                assertEquals(pending,model.getAcceptedVoltageMemoryState(),"Converged trial is not a physical commit");
            }
            assertTrue(model.afterStep(.01));
            assertEquals(.76,model.getAcceptedVoltageMemoryState().savedVoltage(),1.e-15);
            assertEquals(.66,model.getAcceptedVoltageMemoryState().terminalVoltage(),0.);
            assertEquals(.1,model.getAcceptedVoltageMemoryState().stepStartTime(),0.);
            var committed=model.getAcceptedVoltageMemoryState();model.afterStep(.01);
            assertEquals(committed,model.getAcceptedVoltageMemoryState());
            assertThrows(IllegalStateException.class,()->model.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,0));
        }
    }
    @Test void distinctSameTimeAcceptedEventReplacesTerminalWithoutAdvancingSavedMemory() {
        for(var scheme:Regca1Model.IntegrationScheme.values()) {
            var f=fixture(scheme);var model=f.model();model.prepareIntegrationStep(.1,.01);
            f.bus().setVoltage(new Complex(.7));solve(model,"INITIAL",.1,true);model.acceptNetworkEventInput(.1);
            double saved=model.getAcceptedVoltageMemoryState().savedVoltage();
            f.bus().setVoltage(new Complex(.6));solve(model,"INITIAL",.1,true);model.acceptNetworkEventInput(.1);
            var finalEvent=model.getAcceptedVoltageMemoryState();
            assertEquals(saved,finalEvent.savedVoltage(),0.,"One saved-memory advance for the physical timestamp");
            assertEquals(.6,finalEvent.terminalVoltage(),0.,"Next step starts from the final accepted event voltage");
            model.acceptNetworkEventInput(.1);assertEquals(finalEvent,model.getAcceptedVoltageMemoryState());
            // A completed repeated event is no longer a pending configuration boundary.
            model.setNetworkVoltageMemoryPolicy(Regca1Model.NetworkVoltageMemoryPolicy.ACCEPTED_BOUNDARY);
            model.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,0);
            solve(model,"CORRECTOR",.1,false);model.afterStep(.01);
            assertEquals(.68,model.getAcceptedVoltageMemoryState().savedVoltage(),1.e-15);
            model.setNetworkIterationHighVoltageEnabled(false);
        }
    }
    @Test void activeHighVoltageCorrectionIsTrialLocalAndRetainedOnlyOnSuccess() {
        for(var scheme:Regca1Model.IntegrationScheme.values()) {
            var f=fixture(scheme);var model=f.model();model.prepareIntegrationStep(.1,.01);
            var states=model.getIntegrationStates();var rates=model.getStateDerivativeSnapshot();
            f.bus().setVoltage(new Complex(1.5));model.beginNetworkSolution(context("INITIAL",.1,true));
            model.beforeNetworkIteration(0);
            assertEquals(1.4,model.getNetworkHighVoltageState().deceleratedVoltage(),1.e-15);
            assertEquals(.14,model.getNetworkHighVoltageState().correction(),1.e-15);
            f.bus().setVoltage(new Complex(1.4));model.afterNetworkConvergence();model.endNetworkSolution(true);
            assertEquals(.224,model.getNetworkHighVoltageState().correction(),1.e-15);
            assertEquals(.1-.224/1.32,model.getIq(),1.e-15,"Power correction is divided by decelerated voltage");
            model.acceptNetworkEventInput(.1);
            var accepted=model.getAcceptedVoltageMemoryState();var output=model.getNetworkHighVoltageState();
            for(int i=0;i<3;i++){model.getIq();model.getOutputObject();model.getStates(null);}
            assertEquals(accepted,model.getAcceptedVoltageMemoryState());assertEquals(output,model.getNetworkHighVoltageState());
            assertEquals(states,model.getIntegrationStates());assertEquals(rates,model.getStateDerivativeSnapshot());
            model.beginNetworkSolution(context("INITIAL",.1,true));
            assertEquals(0.,model.getNetworkHighVoltageState().correction(),0.,"Each trial starts with zero working correction");
            f.bus().setVoltage(new Complex(1.8));model.beforeNetworkIteration(0);model.endNetworkSolution(false);
            assertEquals(output,model.getNetworkHighVoltageState());assertEquals(accepted,model.getAcceptedVoltageMemoryState());
            assertThrows(IllegalStateException.class,()->model.acceptNetworkEventInput(.1));
            f.bus().setVoltage(new Complex(1.));solve(model,"INITIAL",.1,true);model.acceptNetworkEventInput(.1);
            assertEquals(0.,model.getNetworkHighVoltageState().correction(),0.,"Inactive correction does not retain the previous solve's increment");
            assertEquals(1.,model.getAcceptedVoltageMemoryState().savedVoltage(),0.);
            assertEquals(states,model.getIntegrationStates());assertEquals(rates,model.getStateDerivativeSnapshot());
            model.setNetworkIterationHighVoltageEnabled(false);
        }
    }
    @Test void acceptedWeightingAndActiveCorrectionStayDistinctForBothMethods() {
        for(var scheme:Regca1Model.IntegrationScheme.values())for(double acceleration:new double[]{.4,.8}) {
            var f=fixture(scheme,acceleration);var model=f.model();model.prepareIntegrationStep(.1,.01);
            f.bus().setVoltage(new Complex(1.6));solve(model,"INITIAL",.1,true);model.acceptNetworkEventInput(.1);
            assertEquals(1.,model.getAcceptedVoltageMemoryState().savedVoltage(),0.);
            model.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,0);
            double saved=(1.-acceleration)+acceleration*1.6;
            f.bus().setVoltage(new Complex(1.5));solve(model,"CORRECTOR",.1,false);
            double decelerated=(1.-acceleration)*saved+acceleration*1.5;
            assertEquals(saved,model.getNetworkHighVoltageState().savedVoltage(),1.e-15);
            assertEquals(decelerated,model.getNetworkHighVoltageState().deceleratedVoltage(),1.e-15);
            assertEquals(2.*.7*(decelerated-1.2),model.getNetworkHighVoltageState().correction(),1.e-15,
                    "Correction gain stays Khv; voltage-memory acceleration is a separate parameter");
            model.afterStep(.01);assertEquals(saved,model.getAcceptedVoltageMemoryState().savedVoltage(),1.e-15);
            assertEquals(1.5,model.getAcceptedVoltageMemoryState().terminalVoltage(),0.);
        }
    }
    @Test void rejectedNetworkTrialLeavesCommitUntouchedAndCanBeRetried() {
        for(var scheme:Regca1Model.IntegrationScheme.values()) {
            var f=fixture(scheme);var model=f.model();model.prepareIntegrationStep(-.01,.01);
            f.bus().setVoltage(new Complex(.7));solve(model,"INITIAL",-.01,true);model.acceptNetworkEventInput(-.01);
            model.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,0);
            var committed=model.getAcceptedVoltageMemoryState();var output=model.getNetworkHighVoltageState();
            model.beginNetworkSolution(context("PREDICTOR",-.01,false));
            f.bus().setVoltage(new Complex(.2));model.beforeNetworkIteration(0);model.endNetworkSolution(false);
            assertEquals(committed,model.getAcceptedVoltageMemoryState());assertEquals(output,model.getNetworkHighVoltageState());
            assertThrows(IllegalStateException.class,()->model.afterStep(.01));
            assertThrows(IllegalStateException.class,()->model.prepareIntegrationStep(0.,.01));
            model.prepareIntegrationStep(-.01,.01); // same-clock retry is valid and does not commit
            assertEquals(committed,model.getAcceptedVoltageMemoryState());
            f.bus().setVoltage(new Complex(.65));solve(model,"CORRECTOR",-.01,false);model.afterStep(.01);
            assertEquals(.76,model.getAcceptedVoltageMemoryState().savedVoltage(),1.e-15);
            assertEquals(.65,model.getAcceptedVoltageMemoryState().terminalVoltage(),0.);
            assertEquals(-.01,model.getAcceptedVoltageMemoryState().stepStartTime(),0.);
        }
    }
    @Test void failedEventInactiveDeviceAndReinitializationDoNotInventHistory() {
        var f=fixture(Regca1Model.IntegrationScheme.MODIFIED_EULER);var model=f.model();
        var cold=model.getAcceptedVoltageMemoryState();model.prepareIntegrationStep(0.,.01);
        model.beginNetworkSolution(context("INITIAL",0.,true));f.bus().setVoltage(new Complex(.7));
        model.beforeNetworkIteration(0);model.endNetworkSolution(false);
        assertThrows(IllegalStateException.class,()->model.acceptNetworkEventInput(0.));
        assertEquals(cold,model.getAcceptedVoltageMemoryState());
        model.setStatus(false);model.acceptNetworkEventInput(0.);assertEquals(cold,model.getAcceptedVoltageMemoryState());
        model.setStatus(true);f.bus().setVoltage(new Complex(.8));assertTrue(model.initStates(f.bus()));
        assertEquals(.8,model.getAcceptedVoltageMemoryState().savedVoltage(),0.);
        assertEquals(.8,model.getAcceptedVoltageMemoryState().terminalVoltage(),0.);
        assertFalse(model.getAcceptedVoltageMemoryState().positiveStepPending());
        assertTrue(Double.isNaN(model.getAcceptedVoltageMemoryState().eventTime()));
        assertThrows(IllegalArgumentException.class,()->model.prepareIntegrationStep(0.,Double.NaN));
        assertThrows(IllegalStateException.class,()->model.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,0));
    }
    @Test void invalidIntegrationRequestsAndFailedRepeatedEventsAreAtomic() {
        for(var scheme:Regca1Model.IntegrationScheme.values()) {
            var f=fixture(scheme);var model=f.model();model.prepareIntegrationStep(.1,.01);
            var before=model.getAcceptedVoltageMemoryState();
            assertThrows(IllegalArgumentException.class,()->model.nextStep(.01,null,0));
            for(int flag:new int[]{-1,2})assertThrows(IllegalArgumentException.class,()->model.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,flag));
            for(double dt:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-.01})assertThrows(IllegalArgumentException.class,()->model.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,0));
            assertEquals(before,model.getAcceptedVoltageMemoryState());
            f.bus().setVoltage(new Complex(.7));solve(model,"INITIAL",.1,true);model.acceptNetworkEventInput(.1);
            var accepted=model.getAcceptedVoltageMemoryState();var power=new java.util.HashMap<>(model.getStates(null));
            model.beginNetworkSolution(context("INITIAL",.1,true));f.bus().setVoltage(new Complex(.2));
            model.beforeNetworkIteration(0);model.endNetworkSolution(false);
            assertThrows(IllegalStateException.class,()->model.acceptNetworkEventInput(.1));
            assertEquals(accepted,model.getAcceptedVoltageMemoryState());assertEquals(power,model.getStates(null));
            f.bus().setVoltage(new Complex(.7));solve(model,"INITIAL",.1,true);model.acceptNetworkEventInput(.1);
            assertEquals(accepted,model.getAcceptedVoltageMemoryState());
        }
    }

    @Test void disablingAtAcceptedBoundaryClearsWorkingMemoryWithoutChangingStates() {
        var f=fixture(Regca1Model.IntegrationScheme.MODIFIED_EULER);var model=f.model();
        model.prepareIntegrationStep(0.,.01);model.nextStep(.01,DynamicSimuMethod.MODIFIED_EULER,0);
        f.bus().setVoltage(new Complex(.65));solve(model,"CORRECTOR",0.,false);model.afterStep(.01);
        var states=model.getIntegrationStates();var rates=model.getStateDerivativeSnapshot();
        model.setNetworkIterationHighVoltageEnabled(false);
        assertFalse(model.getNetworkHighVoltageState().outputAvailable());
        assertFalse(model.getAcceptedVoltageMemoryState().positiveStepPending());
        assertEquals(.65,model.getAcceptedVoltageMemoryState().savedVoltage(),0.);
        var disabled=model.getNetworkHighVoltageState();solve(model,"DIRECT",0.,false);
        assertEquals(disabled,model.getNetworkHighVoltageState());assertEquals(states,model.getIntegrationStates());assertEquals(rates,model.getStateDerivativeSnapshot());
        model.setNetworkVoltageMemoryPolicy(Regca1Model.NetworkVoltageMemoryPolicy.PER_NETWORK_SOLVE);
        assertEquals(states,model.getIntegrationStates());
        model.setNetworkIterationHighVoltageEnabled(true);
        model.setNetworkVoltageMemoryPolicy(Regca1Model.NetworkVoltageMemoryPolicy.ACCEPTED_BOUNDARY);
        assertTrue(model.initStates(f.bus()));model.prepareIntegrationStep(-.01,.01);
        assertFalse(model.getAcceptedVoltageMemoryState().positiveStepPending());
    }

    private static NetworkIterationAware.Context context(String stage,double start,boolean event) {
        return new NetworkIterationAware.Context(stage,start,stage.equals("INITIAL")?start:start+.01,50,1.e-4,event);
    }
    private static void solve(Regca1Model model,String stage,double start,boolean event) {
        model.beginNetworkSolution(context(stage,start,event));model.beforeNetworkIteration(0);
        model.afterNetworkConvergence();model.endNetworkSolution(true);
    }
    private record Fixture(DStabBus bus,Regca1Model model) { }
    private static Fixture fixture(Regca1Model.IntegrationScheme scheme) {return fixture(scheme,.8);}
    private static Fixture fixture(Regca1Model.IntegrationScheme scheme,double acceleration) {
        var network=DStabObjectFactory.createDStabilityNetwork();network.setBaseKva(100000);
        var bus=(DStabBus)DStabObjectFactory.createDStabBus("B",network).orElseThrow();bus.setVoltage(Complex.ONE);
        var gen=DStabObjectFactory.createDStabGen("1");gen.setGen(new Complex(.2,.1));gen.setMvaBase(100.);gen.setStatus(true);gen.setPosGenZ(Complex.ZERO);bus.getContributeGenList().add(gen);
        var model=new Regca1Model(gen,bus,"1",new Regca1Data(0,.02,10.,.9,.4,1.2,1.2,.8,.4,-1.3,.02,.7,999.,-999.,acceleration));
        model.setStatus(true);model.setActiveElectricalController(new ConverterCurrentCommands());
        model.setIntegrationScheme(scheme);model.setNetworkIterationHighVoltageEnabled(true);
        model.setNetworkVoltageMemoryPolicy(Regca1Model.NetworkVoltageMemoryPolicy.ACCEPTED_BOUNDARY);
        assertTrue(model.initStates(bus));return new Fixture(bus,model);
    }
}
