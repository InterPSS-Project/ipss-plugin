package org.interpss.core.dstab.reference;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import com.interpss.dstab.controller.pss.ieee1992pss2a.Ieee1992PSS2AStabilizer;
import org.junit.jupiter.api.Test;
import com.interpss.dstab.controller.cml.annotate.AbstractChildAnnotateController;
import com.interpss.dstab.controller.cml.annotate.AbstractAnnotateController;
import com.interpss.dstab.controller.cml.wrapper.BaseFieldAnWrapper;
import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;
import com.interpss.dstab.controller.cml.wrapper.BlockFieldAnWrapper;
import com.interpss.dstab.controller.cml.wrapper.ControllerFieldAnWrapper;

/** Checks that audit observations detect a bad rate without changing the state. */
class Texas2kInitializationAuditTest {
    @Test void secondOrderStabilizerSlopesAreCompleteAndReadOnly() throws Exception {
        var controller = new Ieee1992PSS2AStabilizer();
        var lag = new com.interpss.dstab.controller.pss.ieeest.SecondOrderLagBlock(1., .1, .02);
        lag.initStateU0(.2); lag.setStateX(.1); lag.setRate(.3);
        var lagField = new BlockFieldAnWrapper<com.interpss.dstab.controller.pss.ieeest.SecondOrderLagBlock>("lag");
        lagField.setField(lag); installFixtureField(controller, lagField);
        var first = Texas2kInitializationAudit.controller(controller);
        assertEquals(.3, ((Map<?, ?>)first.get("rates")).get("lag.output"));
        assertEquals(3.5, (double)((Map<?, ?>)first.get("rates")).get("lag.rate"), 1.e-12);
        assertTrue(((java.util.List<?>)first.get("rate_coverage_gaps")).isEmpty());
        assertEquals(first, Texas2kInitializationAudit.controller(controller));
        assertEquals(.1, lag.getStateX()); assertEquals(.3, lag.getRate());
        var lead = new com.interpss.dstab.controller.pss.ieeest.SecondOrderLeadLagBlock(.1, .02, .3, .04);
        lead.initStateU0(.2); lead.setStateX(.1); lead.setSecondState(.15);
        var leadField = new BlockFieldAnWrapper<com.interpss.dstab.controller.pss.ieeest.SecondOrderLeadLagBlock>("lead");
        leadField.setField(lead); installFixtureField(controller, leadField);
        var second = Texas2kInitializationAudit.controller(controller);
        assertEquals(2., (double)((Map<?, ?>)second.get("rates")).get("lead.first"), 1.e-12);
        assertEquals(.1, ((Map<?, ?>)second.get("rates")).get("lead.second"));
        assertEquals(second, Texas2kInitializationAudit.controller(controller));
        assertEquals(.1, lead.getFirstState()); assertEquals(.15, lead.getSecondState());
    }

    @Test void gridFormingStatesAndRatesAreIncludedWithoutAdvancingTheDevice(
        @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        org.interpss.IpssCorePlugin.init();
        var ref=Regfma1NativeReferenceCheckpoints.get("flat00");
        var dyr=directory.resolve("grid-forming.dyr");
        java.nio.file.Files.writeString(dyr,"1 'REGFMA1' '1' 0 0 "
            +String.join(" ",ref.constants().stream().map(Object::toString).toList())
            +" /\n3 'GENCLS' '1' 9999999 0 /\n");
        var context=new org.interpss.fadapter.psse.PSSEMultiFileLoader().loadDStab(
            "testData/adpter/psse/v33/renewable/regfma1_native.raw",dyr.toString());
        var net=context.getDStabilityNet();var algorithm=context.getDynSimuAlgorithm();
        net.setAllowGenWithoutMach(true);
        algorithm.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSolver(new org.interpss.dstab.algo.PsseStaggeredEulerSolver(algorithm));
        algorithm.setSimuMethod(com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(.0005);
        algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        assertTrue(algorithm.initialization());
        var model=(org.interpss.dstab.renewable.Regfma1Model)
            ((com.interpss.dstab.DStabGen)net.getBus("Bus1").getContributeGen("1")).getDynamicGenDevice();
        var before=model.getNamedStates();var rates=model.getStateDerivativeSnapshot();
        var file=directory.resolve("initial-states.json");
        Texas2kInitializationAudit.write(net,file);
        var rows=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(file)).getAsJsonArray();
        var row=rows.get(0).getAsJsonObject();
        assertEquals("REGFMA1",row.get("type").getAsString());
        var audit=row.getAsJsonObject("converter_audit");
        assertEquals(9,audit.getAsJsonObject("integration_states").size());
        assertEquals(9,audit.getAsJsonObject("rates").size());
        assertEquals(0,audit.getAsJsonArray("rate_coverage_gaps").size());
        assertEquals(before,model.getNamedStates());
        assertEquals(rates,model.getStateDerivativeSnapshot());
    }
    @Test void customStateAccessReportsMissingEvaluationAndDetectsNonzeroRate() {
        var model=new org.interpss.dstab.renewable.Wtdta1Model(
                new org.interpss.dstab.renewable.Wtdta1Data(4,0,.5,1,0));
        model.initialize(.8,1);
        var unobserved=Texas2kInitializationAudit.controller(model);
        assertFalse(((java.util.List<?>)unobserved.get("rate_coverage_gaps")).isEmpty());
        model.stepTwoStep(.001,.9,.8);
        var report=Texas2kInitializationAudit.controller(model);
        assertTrue(((java.util.List<?>)report.get("rate_coverage_gaps")).isEmpty());
        assertEquals(.025,((Map<String,Double>)report.get("rates")).get("Turbine Speed"),1.e-12);
        assertEquals(report,Texas2kInitializationAudit.controller(model));
    }
    private static void installFixtureField(AbstractAnnotateController controller,BaseFieldAnWrapper<?> field) throws Exception {
        var registry=AbstractAnnotateController.class.getDeclaredField("fieldWrapperList");
        registry.setAccessible(true);registry.set(controller,new java.util.ArrayList<>(java.util.List.of(field)));
        controller.refreshNamedStateBlocks();
    }
    @Test void readsNestedRatesWithoutAdvancingBlocks() throws Exception {
        var parent=new Ieee1992PSS2AStabilizer();
        var child=(AbstractChildAnnotateController)parent.getFieldObject(parent.getField("customBlock1"));
        var block=new DelayControlBlock(1.,.1);
        block.restoreIntegrationState(.4,.2,2.);
        var field=new BlockFieldAnWrapper<DelayControlBlock>("probe");field.setField(block);
        installFixtureField(child,field);
        var childField=new ControllerFieldAnWrapper("child");childField.setField(child);
        installFixtureField(parent,childField);
        var first=Texas2kInitializationAudit.controller(parent);
        assertEquals(.2,((Map<?,?>)first.get("states")).get("child.probe"));
        assertEquals(2.,((Map<?,?>)first.get("rates")).get("child.probe"));
        assertEquals(.4,block.getU(),0.);assertEquals(.2,block.getStateX(),0.);assertEquals(2.,block.getDX_dt(),0.);
        assertEquals(first,Texas2kInitializationAudit.controller(parent));
        block.restoreIntegrationState(.2,.2,0.);
        assertEquals(0.,((Map<?,?>)Texas2kInitializationAudit.controller(parent).get("rates")).get("child.probe"));
    }

    @Test void rejectsNonfiniteRetainedDerivative() throws Exception {
        var controller=new Ieee1992PSS2AStabilizer();
        var block=new DelayControlBlock(1.,.1);block.restoreIntegrationState(.2,.2,Double.NaN);
        var field=new BlockFieldAnWrapper<DelayControlBlock>("probe");field.setField(block);
        installFixtureField(controller,field);
        assertThrows(IllegalStateException.class,()->Texas2kInitializationAudit.controller(controller));
        assertTrue(Double.isNaN(block.getDX_dt()));
    }

    @Test void reportsUninitializedControllerCoverage() {
        var report=Texas2kInitializationAudit.controller(new Ieee1992PSS2AStabilizer());
        assertTrue(((java.util.List<?>)report.get("rate_coverage_gaps")).contains("CML fields not initialized"));
    }

    @Test void inspectsEveryCompoundFilterSection() throws Exception {
        var controller=new Ieee1992PSS2AStabilizer();
        var compound=new com.interpss.dstab.controller.cml.field.block.FilterNthOrderBlock(.1,.2,3,2);
        assertTrue(compound.initStateY0(.5));
        var blocks=compound.getIntegrationBlocks();assertEquals(6,blocks.size());
        blocks.get(3).restoreIntegrationState(.4,.2,8.);
        var field=new BlockFieldAnWrapper<com.interpss.dstab.controller.cml.field.block.FilterNthOrderBlock>("ramp");
        field.setField(compound);installFixtureField(controller,field);
        var report=Texas2kInitializationAudit.controller(controller);
        assertEquals(6,((Map<?,?>)report.get("rates")).size());
        assertEquals(8.,((Map<?,?>)report.get("rates")).get("ramp.section3"));
        assertEquals(.2,blocks.get(3).getStateX(),0.);
    }
}
