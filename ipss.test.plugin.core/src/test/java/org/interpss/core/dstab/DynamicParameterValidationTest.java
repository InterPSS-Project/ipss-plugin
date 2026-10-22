package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.algo.PsseStaggeredEulerSolver;
import org.interpss.dstab.control.exc.ieee.y1981.st1.IEEE1981ST1Exciter;
import org.interpss.dstab.control.exc.ieee.y1968.type2.Ieee1968Type2Exciter;
import org.interpss.dstab.control.exc.ieee.y2005.st4b.IEEE2005ST4BExciter;
import org.interpss.dstab.control.exc.ieee.y1968.type1.Ieee1968Type1Exciter;
import org.interpss.dstab.control.gov.ieee.steamTCDR.IeeeSteamTCDRGovernor;
import org.interpss.dstab.validation.DynamicParameterValidator;
import org.interpss.dstab.validation.DynamicParameterValidator.Severity;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.PsseDStabSolver;

/** Imported-parameter checks must not mutate the model or mistake local and network steps. */
public class DynamicParameterValidationTest {
    @Test void localStepControlsTheTimeConstantWarning() throws Exception {
        var f=load();f.exciter.getData().setTa(.001);
        f.solver.setSt1Substeps(1);
        assertTrue(f.solver.validateModelParameters().issues().stream().anyMatch(i->i.parameter().equals("Ta")&&i.severity()==Severity.WARNING));
        f.solver.setSt1Substeps(10);
        assertFalse(f.solver.validateModelParameters().issues().stream().anyMatch(i->i.parameter().equals("Ta")));
        f.solver.setSt1Substeps(f.machine.getId(),1);
        var issue=f.solver.validateModelParameters().issues().stream().filter(i->i.parameter().equals("Ta")).findFirst().orElseThrow();
        assertEquals(1./240,issue.effectiveStepSeconds());
        assertEquals(.001,f.exciter.getData().getTa());
        assertEquals(1./240,f.algorithm.getSimuStepSec());
    }

    @Test void ordinarySolverUsesNetworkStepAndMarginIsConfigurable() throws Exception {
        var f=load();f.exciter.getData().setTa(.005);
        var solver=new PsseDStabSolver(f.algorithm);
        assertTrue(solver.validateModelParameters().issues().stream().anyMatch(i->i.parameter().equals("Ta")));
        solver.setParameterTimeStepMultiple(1.);
        assertFalse(solver.validateModelParameters().issues().stream().anyMatch(i->i.parameter().equals("Ta")));
        for(double invalid:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->solver.setParameterTimeStepMultiple(invalid));
    }

    @Test void algebraicZeroIsAllowedButImproperLeadLagIsRejected() throws Exception {
        var f=load();var d=f.exciter.getData();
        d.setTa(0);d.setTr(0);d.setTc(0);d.setTb(0);d.setTc1(0);d.setTb1(0);d.setKf(0);d.setTf(0);
        assertEquals(0,f.solver.validateModelParameters().count(Severity.ERROR));
        d.setTc(.1);
        assertTrue(f.solver.validateModelParameters().issues().stream().anyMatch(i->i.parameter().equals("Tc/Tb")&&i.severity()==Severity.ERROR));
        assertEquals(.1,d.getTc());assertEquals(0,d.getTb());
    }

    @Test void feedbackRequiresItsDenominatorOnlyWhenEnabled() throws Exception {
        var f=load();var d=f.exciter.getData();d.setKf(0);d.setTf(0);
        assertFalse(f.solver.validateModelParameters().issues().stream().anyMatch(i->i.parameter().equals("Tf")));
        d.setKf(.1);
        assertTrue(f.solver.validateModelParameters().issues().stream().anyMatch(i->i.parameter().equals("Tf")&&i.severity()==Severity.ERROR));
    }

    @Test void invalidParametersBlockInitializationAndReportAllErrors() throws Exception {
        var f=load();f.exciter.getData().setTa(-.1);f.exciter.getData().setKa(Double.NaN);
        assertThrows(IllegalArgumentException.class,f.solver::initialization);
        var report=f.solver.getParameterValidationReport();
        assertTrue(report.count(Severity.ERROR)>=2);
        assertTrue(report.issues().stream().anyMatch(i->i.parameter().equals("Ta")));
        assertTrue(report.issues().stream().anyMatch(i->i.parameter().equals("Ka")));
    }

    @Test void reversedLimitsAreVisibleWithoutSilentlyRewritingImportedValues() throws Exception {
        var f=load();var d=f.exciter.getData();d.setVimin(2);d.setVimax(-2);
        var report=f.solver.validateModelParameters();
        assertTrue(report.issues().stream().anyMatch(i->i.parameter().equals("Vi")&&i.severity()==Severity.WARNING));
        assertEquals(2,d.getVimin());assertEquals(-2,d.getVimax());
        assertThrows(UnsupportedOperationException.class,()->report.issues().clear());
        assertThrows(UnsupportedOperationException.class,()->report.checkedModels().clear());
    }

    @Test void inactiveAttachedControllersAreStillChecked() throws Exception {
        var f=load();f.exciter.setStatus(false);f.exciter.getData().setTa(-1);
        var issue=f.solver.validateModelParameters().issues().stream().filter(i->i.parameter().equals("Ta")).findFirst().orElseThrow();
        assertEquals(Severity.ERROR,issue.severity());assertFalse(issue.initiallyActive());
    }

    @Test void reactanceOrderUsesTheMachineEquationsNotTheReversedDocumentationInequality() throws Exception {
        var f=load();var machine=(com.interpss.dstab.mach.RoundRotorMachine)f.machine;
        assertEquals(0,f.solver.validateModelParameters().count(Severity.ERROR));
        machine.setXd11(machine.getXd1()+.1);
        assertTrue(f.solver.validateModelParameters().issues().stream().anyMatch(i->i.parameter().equals("Xd'' <= Xd'")&&i.severity()==Severity.ERROR));
    }

    @Test void unimplementedRulesAreReportedAsCoverageGaps() throws Exception {
        var f=load();f.machine.getControllerList().remove(f.exciter);
        f.machine.getControllerList().add(new Ieee1968Type2Exciter());
        var report=f.solver.validateModelParameters();
        assertEquals(1,report.uncoveredModels().get("Ieee1968Type2Exciter"));
        assertFalse(report.checkedModels().containsKey("Ieee1968Type2Exciter"));
    }

    @Test void st4bAbsentFeedbackLimitAllowsOnlyPositiveInfinity() throws Exception {
        var f=load();var c=new IEEE2005ST4BExciter();f.machine.getControllerList().add(c);
        assertEquals(0,f.solver.validateModelParameters().count(Severity.ERROR));
        assertEquals(1,f.solver.getParameterValidationReport().checkedModels().get(c.getClass().getSimpleName()));
        for(double value:new double[]{Double.NaN,Double.NEGATIVE_INFINITY}) {
            c.getData().setVgmax(value);
            assertTrue(f.solver.validateModelParameters().issues().stream().anyMatch(i->i.parameter().equals("Vgmax")&&i.severity()==Severity.ERROR));
        }
        c.getData().setVgmax(Double.POSITIVE_INFINITY);c.getData().setKpr(Double.POSITIVE_INFINITY);
        assertTrue(f.solver.validateModelParameters().issues().stream().anyMatch(i->i.parameter().equals("Kpr")&&i.severity()==Severity.ERROR));
    }

    @Test void st4bWarningsDescribeExistingCorrectionsWithoutApplyingThem() throws Exception {
        var f=load();var c=new IEEE2005ST4BExciter();f.machine.getControllerList().add(c);var d=c.getData();
        d.setKpr(0);d.setKir(0);d.setKpm(0);d.setKim(0);d.setTa(.001);d.setVmmin(2);d.setVmmax(1);
        var before=scalarValues(d);var report=f.solver.validateModelParameters();
        assertEquals(0,report.count(Severity.ERROR));
        for(String parameter:new String[]{"Kpr/Kir","Kpm/Kim","Ta","Vm"})
            assertTrue(report.issues().stream().anyMatch(i->i.model().equals(c.getClass().getSimpleName())&&i.parameter().equals(parameter)&&i.severity()==Severity.WARNING),parameter);
        assertEquals(before,scalarValues(d));
        var issue=report.issues().stream().filter(i->i.parameter().equals("Ta")&&i.model().equals(c.getClass().getSimpleName())).findFirst().orElseThrow();
        assertEquals(1./240,issue.effectiveStepSeconds()); // ST1's local substeps do not apply to ST4B.
    }

    @Test void type1RejectsZeroIntegratorAndEnabledFeedbackDenominators() throws Exception {
        var f=load();var c=new Ieee1968Type1Exciter();f.machine.getControllerList().add(c);var d=c.getData();
        d.setTa(0);d.setTr(0);d.setKf(0);d.setTf(0);
        assertEquals(0,f.solver.validateModelParameters().count(Severity.ERROR));
        d.setTe(0);d.setKf(.1);var before=scalarValues(d);
        var report=f.solver.validateModelParameters();
        for(String parameter:new String[]{"Te","Tf"})
            assertTrue(report.issues().stream().anyMatch(i->i.parameter().equals(parameter)&&i.severity()==Severity.ERROR));
        assertEquals(before,scalarValues(d));
        assertThrows(IllegalArgumentException.class,f.solver::initialization);
    }

    @Test void steamGovernorRejectsSingularInitializationAndImproperFilter() throws Exception {
        var f=load();var c=new IeeeSteamTCDRGovernor("gov","gov","test");f.machine.getControllerList().add(c);var d=c.getData();
        d.setT1(0);d.setT2(0);d.setTch(0);d.setTrh1(0);d.setTrh2(0);d.setTco(0);
        assertEquals(0,f.solver.validateModelParameters().count(Severity.ERROR));
        d.setT3(0);d.setT2(.1);d.setFvhp(0);d.setFhp(0);d.setFip(0);d.setFlp(0);
        var report=f.solver.validateModelParameters();
        for(String parameter:new String[]{"T3","T2/T1","shaft fraction sum"})
            assertTrue(report.issues().stream().anyMatch(i->i.parameter().equals(parameter)&&i.severity()==Severity.ERROR),parameter);
    }

    @Test void steamGovernorReportsFractionAndRateCorrectionsWithoutMutation() throws Exception {
        var f=load();var c=new IeeeSteamTCDRGovernor("gov","gov","test");f.machine.getControllerList().add(c);var d=c.getData();
        d.setFvhp(1);d.setFhp(1);d.setPup(-.1);d.setPdown(.1);d.setT3(.001);
        var before=scalarValues(d);var report=f.solver.validateModelParameters();
        assertEquals(0,report.count(Severity.ERROR));
        for(String parameter:new String[]{"shaft fraction sum","valve rate sign","T3"})
            assertTrue(report.issues().stream().anyMatch(i->i.parameter().equals(parameter)&&i.severity()==Severity.WARNING),parameter);
        assertEquals(before,scalarValues(d));
    }

    private static java.util.Map<String,Double> scalarValues(Object data) throws Exception {
        var values=new java.util.TreeMap<String,Double>();
        for(var getter:data.getClass().getMethods())
            if(getter.getName().startsWith("get")&&getter.getParameterCount()==0&&getter.getReturnType()==double.class)
                values.put(getter.getName(),(double)getter.invoke(data));
        return values;
    }

    private static Fixture load() throws Exception {
        IpssCorePlugin.init();String base="testData/adpter/psse/v33/SMIB/";
        var context=new PSSEMultiFileLoader().loadDStab(base+"SMIB_v33.raw",base+"SMIB_v33_genrou_exst1.dyr");
        var algorithm=context.getDynSimuAlgorithm();algorithm.setSimuStepSec(1./240);
        var machine=context.getDStabilityNet().getMachine("Bus1-mach1");
        var solver=new PsseStaggeredEulerSolver(algorithm);algorithm.setSolver(solver);
        return new Fixture(algorithm,solver,machine,(IEEE1981ST1Exciter)machine.getExciter());
    }
    private record Fixture(com.interpss.dstab.algo.DynamicSimuAlgorithm algorithm,
            PsseStaggeredEulerSolver solver,com.interpss.dstab.mach.Machine machine,IEEE1981ST1Exciter exciter) { }
}
