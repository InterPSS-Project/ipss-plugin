package com.interpss.dstab.controller.gov.hyg3;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.validation.DynamicParameterValidator;
import org.interpss.dstab.validation.DynamicParameterValidator.Severity;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.interpss.dstab.controller.gov.hyg3.PsseHyg3Governor;
import com.interpss.dstab.controller.gov.hyg3.PsseHyg3GovernorData;

/** Non-mutating HYG3 validation includes prepared zero time and feedback pole screening. */
public class Hyg3ParameterValidationTest {
    private static final double H=1./240;
    @BeforeAll static void setup(){IpssCorePlugin.init();}
    private static com.interpss.dstab.BaseDStabNetwork<?,?> load(int mode)throws Exception{
        var root=Path.of("testData/adpter/psse/v33/HspModels");
        return new PSSEMultiFileLoader().loadDStab(root.resolve("network.raw").toString(),
                root.resolve("hyg3u1-"+mode+".dyr").toString()).getDStabilityNet();
    }
    private static PsseHyg3Governor governor(com.interpss.dstab.BaseDStabNetwork<?,?> net){
        return (PsseHyg3Governor)net.getMachine("Bus1-mach1").getGovernor();
    }
    private static DynamicParameterValidator.Report validate(com.interpss.dstab.BaseDStabNetwork<?,?> net){
        return DynamicParameterValidator.validate(net,H,2.,machine->128);
    }
    @Test void importedZeroUsesOuterStepAndDoesNotInitializeOrRewriteData()throws Exception{
        for(int mode:new int[]{0,1}){
            var net=load(mode);var governor=governor(net);var d=governor.getData();d.setTf(0.);
            var states=governor.getIntegrationStates();var history=governor.getIntegrationHistories();
            var report=validate(net);assertEquals(0,report.count(Severity.ERROR));
            assertEquals(1,report.checkedModels().get("PsseHyg3Governor"));
            assertFalse(report.uncoveredModels().containsKey("PsseHyg3Governor"));
            var prepared=report.issues().stream().filter(i->i.parameter().equals("Tf preparation")).findFirst().orElseThrow();
            assertEquals(4*H,prepared.value(),0.);assertEquals(H,prepared.effectiveStepSeconds(),0.);
            assertEquals(0.,d.getTf(),0.);assertEquals(states,governor.getIntegrationStates());assertEquals(history,governor.getIntegrationHistories());
            d.setZeroDerivativeFilterConvention(PsseHyg3GovernorData.ZeroDerivativeFilterConvention.BYPASS);
            assertFalse(validate(net).issues().stream().anyMatch(i->i.parameter().equals("Tf preparation")));
        }
    }
    @Test void shortPositiveTimeWarnsWithoutFlooringAndShrinkingOuterStepClearsTheWarning()throws Exception{
        var net=load(0);var d=governor(net).getData();d.setTf(H/2);
        var issue=validate(net).issues().stream().filter(i->i.parameter().equals("Tf")).findFirst().orElseThrow();
        assertEquals(Severity.WARNING,issue.severity());assertEquals(H/2,issue.value(),0.);
        assertEquals(H,issue.effectiveStepSeconds(),0.);assertEquals(H/2,d.getTf(),0.);
        var fine=DynamicParameterValidator.validate(net,H/8,2.,machine->1);
        assertFalse(fine.issues().stream().anyMatch(i->i.parameter().equals("Tf")));
    }
    @Test void pidFeedbackCanWarnWhenTheIndividualLagScreensPass()throws Exception{
        var net=load(1);var d=governor(net).getData();d.setTd(.01);d.setTf(.01);
        var report=validate(net);
        assertFalse(report.issues().stream().anyMatch(i->i.severity()==Severity.WARNING&&(i.parameter().equals("Td")||i.parameter().equals("Tf"))));
        var issue=report.issues().stream().filter(i->i.parameter().equals("PID derivative/input screening time")).findFirst().orElseThrow();
        // The 2x2 frozen-integral Jacobian is [-205,100;100,-100].
        double expected=2./(305.+Math.sqrt(105.*105.+40000.));
        assertEquals(expected,issue.value(),1e-15);assertEquals(Severity.WARNING,issue.severity());
        assertEquals(.01,d.getTd(),0.);assertEquals(.01,d.getTf(),0.);
        d.setControlFlag(0);assertFalse(validate(net).issues().stream().anyMatch(i->i.parameter().equals("PID derivative/input screening time")));
    }
    @Test void invalidSelectorsAndCurvesAreErrorsAndInactiveModelsRemainChecked()throws Exception{
        var net=load(0);var g=governor(net);g.setStatus(false);g.getData().setControlFlag(7);
        var report=validate(net);assertThrows(IllegalArgumentException.class,report::requireNoErrors);
        var issue=report.issues().stream().filter(i->i.parameter().equals("model constraints")).findFirst().orElseThrow();
        assertFalse(issue.initiallyActive());assertEquals(7,g.getData().getControlFlag());
        g.getData().setControlFlag(0);g.getData().setGv(2,Double.NaN);
        assertThrows(IllegalArgumentException.class,()->validate(net).requireNoErrors());
    }
    @Test void zeroWaterTimeIsAnExplicitUnqualifiedExtensionAndNegativeTimeIsAnError()throws Exception{
        var net=load(0);var d=governor(net).getData();d.setTw(0.);
        var report=validate(net);assertEquals(0,report.count(Severity.ERROR));
        assertTrue(report.issues().stream().anyMatch(i->i.parameter().equals("Tw")&&i.severity()==Severity.WARNING));
        d.setTw(-.1);assertThrows(IllegalArgumentException.class,()->validate(net).requireNoErrors());
        assertEquals(-.1,d.getTw(),0.);
    }
    @Test void singularBypassedPidFeedbackFailsBeforeStateInitialization()throws Exception{
        for(double tf:new double[]{0.,.1}){
            var net=load(1);var g=governor(net);var d=g.getData();var m=g.getMachine();
            d.setTd(0.);d.setTf(tf);d.setK1(0.);d.setRgate(.05);d.setK2(-20.);
            var report=validate(net);
            assertTrue(report.issues().stream().anyMatch(i->i.parameter().equals("PID algebraic feedback denominator")&&i.severity()==Severity.ERROR));
            g.configureIntegrationStep(H);m.setPe(.4);m.setPm(.4);m.setSpeed(1.);
            assertFalse(g.initStates(m.getDStabBus(),m));
            d.setK2(-19.);assertFalse(validate(net).issues().stream().anyMatch(i->i.parameter().equals("PID algebraic feedback denominator")));
            assertTrue(g.initStates(m.getDStabBus(),m));
            d.setRgate(1e308);d.setK2(1e308);
            assertThrows(IllegalArgumentException.class,()->validate(net).requireNoErrors());
            assertFalse(g.initStates(m.getDStabBus(),m));
        }
    }
    @Test void bothSolverInitializersRejectSingularPidFeedbackThroughTheSharedReport()throws Exception{
        for(boolean staggered:new boolean[]{false,true}){
            var root=Path.of("testData/adpter/psse/v33/HspModels");
            var context=new PSSEMultiFileLoader().loadDStab(root.resolve("network.raw").toString(),root.resolve("hyg3u1-1.dyr").toString());
            var d=governor(context.getDStabilityNet()).getData();d.setTd(0.);d.setK1(0.);d.setK2(-20.);d.setRgate(.05);
            var algorithm=context.getDynSimuAlgorithm();algorithm.setSimuStepSec(H);
            org.interpss.fadapter.psse.PsseDStabSolver solver=staggered
                    ?new org.interpss.dstab.algo.StaggeredEulerSolver(algorithm)
                    :new org.interpss.dstab.algo.DefaultDynamicSimulationSolver(algorithm);
            assertThrows(IllegalArgumentException.class,solver::initialization);
            assertTrue(solver.getParameterValidationReport().issues().stream()
                    .anyMatch(i->i.parameter().equals("PID algebraic feedback denominator")&&i.severity()==Severity.ERROR));
        }
    }
}
