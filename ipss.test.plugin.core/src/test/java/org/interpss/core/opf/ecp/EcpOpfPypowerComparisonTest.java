package org.interpss.core.opf.ecp;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.Stream;
import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.google.gson.JsonObject;
import com.interpss.core.algo.ecf.EcfNetworkModel;
import com.interpss.opf.ecp.*;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.matpower.MatpowerOpfDirectParser;

/** Actual imported-network GB AC-OPF solves compared with a frozen external
 * engine, with independent physical checks on every bus and both terminals. */
public class EcpOpfPypowerComparisonTest {
    @TempDir Path temporary;
    @BeforeAll static void initialize() {
        if(Boolean.getBoolean("ecp.exclude.jna"))assertThrows(ClassNotFoundException.class,()->Class.forName("com.sun.jna.Native"));
        IpssCorePlugin.init();
    }
    static Stream<Arguments> cases() {
        return Stream.of("case9","case14","case30").flatMap(name->Stream.of("I","S").flatMap(mode->Arrays.stream(EcpOpfConfig.Strategy.values()).map(strategy->Arguments.of(name,mode,strategy))));
    }
    @ParameterizedTest @MethodSource("cases")
    void optimizedDispatchMatchesReferenceEconomicsAndOriginalPhysicalConstraints(String name,String mode,EcpOpfConfig.Strategy strategy) throws Exception {
        verify(name,mode,strategy,EcpPowerFlowComparisonTest.reference());
    }
    EcpOpfResult verify(String name,String mode,EcpOpfConfig.Strategy strategy,JsonObject report) throws Exception {
        var input=report.getAsJsonObject("case_inputs").getAsJsonObject(name).getAsJsonObject("input");
        JsonObject reference=null;
        for(var item:report.getAsJsonArray("runs")) {
            var run=item.getAsJsonObject();if(name.equals(run.get("case").getAsString()) && mode.equals(run.get("flow_limit").getAsString()))reference=run;
        }
        assertNotNull(reference);var helper=new EcpPowerFlowComparisonTest();helper.temporary=temporary;
        var network=new MatpowerOpfDirectParser().parse(helper.caseFile(name,input).toString());
        var before=network.getBusList().stream().map(b->b.getVoltage()).toList();
        var config=new EcpOpfConfig(mode.equals("I")?EcpOpfConfig.ThermalLimit.CURRENT:EcpOpfConfig.ThermalLimit.APPARENT_POWER,
                EcpOpfConfig.BranchRatingUnit.PER_UNIT,5e-6,1e-4,1e-4,500,false).withStrategy(strategy)
                .withFormulation(EcpOpfConfig.Formulation.valueOf(System.getProperty("ecp.formulation","GENERATOR_ADMITTANCE")));
        if(strategy==EcpOpfConfig.Strategy.ECPACOPF || Boolean.getBoolean("ecp.initialize.all.strategies"))
            config=config.withInitialization(EcpOpfConfig.Initialization.valueOf(System.getProperty("ecp.initialization","FLAT")));
        if(strategy==EcpOpfConfig.Strategy.ECPACOPF && System.getProperty("ecp.step.control", "").equals("NON_DIVERGENT"))
            config=config.withNewtonStepControl(EcpOpfConfig.NewtonStepControl.nonDivergent());
        if(strategy==EcpOpfConfig.Strategy.ECPACOPF && System.getProperty("ecp.step.control", "").equals("POLYNOMIAL_LIMITING"))
            config=config.withNewtonStepControl(EcpOpfConfig.NewtonStepControl.polynomial());
        if(strategy==EcpOpfConfig.Strategy.ECPACOPF) {
            var limits=config.newtonStepControl();
            double voltage=System.getProperty("ecp.step.voltage", "").isEmpty()?limits.voltageLimit():Double.parseDouble(System.getProperty("ecp.step.voltage"));
            double adjoint=System.getProperty("ecp.step.adjoint", "").isEmpty()?limits.adjointLimit():Double.parseDouble(System.getProperty("ecp.step.adjoint"));
            double admittance=System.getProperty("ecp.step.admittance", "").isEmpty()?limits.admittanceFraction():Double.parseDouble(System.getProperty("ecp.step.admittance"));
            config=config.withNewtonStepControl(limits.withLimits(voltage,adjoint,admittance)
                    .withInitialComplementarity(Double.parseDouble(System.getProperty("ecp.initial.complementarity","1"))));
        }
        System.out.printf("ECP_CONFIG %s %s %s feasibility=%.3e optimality=%.3e variable=%.3e maxIterations=%d epsilon=%.3e saturation=%.3e txEnabled=%s txScale=%.3e%n",
                name,mode,strategy,config.feasibilityTolerance(),config.optimalityTolerance(),config.variableTolerance(),config.maximumIterations(),config.boundEpsilon(),config.boundaryMultiplier(),config.txStepping().enabled(),config.txStepping().seriesScale());
        var stepControl=config.newtonStepControl();
        System.out.printf("ECP_STEP_CONFIG %s %s %s mode=%s voltage=%.3e adjoint=%.3e admittanceFraction=%.3e boundaryFraction=%.3e minimumScale=%.3e backtracks=%d%n",
                name,mode,strategy,stepControl.mode(),stepControl.voltageLimit(),stepControl.adjointLimit(),stepControl.admittanceFraction(),stepControl.boundaryFraction(),stepControl.minimumScale(),stepControl.maximumBacktracks());
        System.out.printf("ECP_FORMULATION %s %s %s formulation=%s initialization=%s%n",name,mode,strategy,config.formulation(),config.initialization());
        String nativeFile=System.getProperty("ecp.native.library","");
        var sparse=nativeFile.isEmpty()?switch(EcpSparseSolverConfig.Backend.valueOf(System.getProperty("ecp.java.sparse.backend","JAVA_KLU"))) {
            case JAVA_KLU -> EcpSparseSolverConfig.javaKlu();
            case JAVA_CSPARSE -> Boolean.getBoolean("ecp.factor.reuse")?EcpSparseSolverConfig.javaCsparseReusing():EcpSparseSolverConfig.javaCsparse();
            case NATIVE_UMFPACK -> throw new IllegalArgumentException("Native diagnostic requires an explicit library file");
        }:EcpSparseSolverConfig.nativeUmfpack(nativeFile);
        var solveReport=new EcpOpfAlgorithm().solveDetailed(network,config,sparse);var result=solveReport.result();
        var linear=solveReport.linearStatistics();
        assertEquals(0,linear.activeMatrices());assertEquals(linear.handlesCreated(),linear.handlesFreed());
        if(sparse.backend()!=EcpSparseSolverConfig.Backend.JAVA_KLU)assertTrue(linear.triangularSolves()>0,"Requested alternative backend must actually solve");
        if(sparse.backend()==EcpSparseSolverConfig.Backend.JAVA_CSPARSE) {
            assertEquals(0,linear.handlesCreated());assertEquals(0,linear.handlesFreed());
        }
        System.out.printf("ECP_LINEAR %s %s %s %s%n",name,mode,strategy,linear);
        System.out.printf("ECP_OPF %s %s %s status=%s iterations=%d objective=%.9f equality=%.3e bound=%.3e dual=%.3e comp=%.3e PQ=%.3e variableChange=%.3e detail=%s%n",
                name,mode,strategy,result.status(),result.iterations(),result.objective(),result.maximumEqualityResidual(),result.maximumBoundViolation(),
                result.maximumStationarity(),result.maximumComplementarity(),result.maximumPowerMismatch(),result.maximumVariableChange(),result.detail());
        assertTrue(result.converged(),()->name+" "+mode+" "+strategy+" "+result.status()+": "+result.detail()+" eq="+result.maximumEqualityResidual()+" dual="+result.maximumStationarity()+" bound="+result.maximumBoundViolation());
        double referenceCost=reference.get("objective_per_hour").getAsDouble();
        assertEquals(referenceCost,result.objective(),Math.max(.01,1e-6*Math.abs(referenceCost)));
        assertTrue(result.maximumStationarity()<=config.optimalityTolerance());assertTrue(result.maximumComplementarity()<=config.optimalityTolerance());
        assertTrue(result.maximumEqualityResidual()<=config.feasibilityTolerance());assertTrue(result.maximumBoundViolation()<=config.feasibilityTolerance());
        assertTrue(result.maximumVariableChange()<=config.variableTolerance());assertTrue(result.iterations()<=config.maximumIterations());
        var model=EcfNetworkModel.compileForOptimization(network,false);var v=new Complex[model.busCount()];
        for(int i=0;i<v.length;i++)v[i]=result.voltages().get(model.bus(i).id());
        var currents=model.currents(v);var generation=new HashMap<String,Complex>();double recomputedCost=0;
        for(var bus:network.getBusList())for(var gen:bus.getContributeGenList())if(gen.isActive()) {
            var power=result.generation().get(new EcpOpfResult.GeneratorKey(bus.getId(),gen.getId()));
            assertNotNull(power);generation.merge(bus.getId(),power,Complex::add);
            assertTrue(power.getReal()>=gen.getPGenLimit().getMin()-config.feasibilityTolerance() && power.getReal()<=gen.getPGenLimit().getMax()+config.feasibilityTolerance());
            assertTrue(power.getImaginary()>=gen.getQGenLimit().getMin()-config.feasibilityTolerance() && power.getImaginary()<=gen.getQGenLimit().getMax()+config.feasibilityTolerance());
            var curve=gen.getIncCost().getQuadraticCurve();recomputedCost+=curve.getA()*power.getReal()*power.getReal()+curve.getB()*power.getReal()+curve.getC();
        }
        assertEquals(result.objective(),recomputedCost,1e-7);
        double maxDifference=0;
        for(int i=0;i<v.length;i++) {
            var bus=model.bus(i);var load=bus.load();
            var residual=v[i].multiply(currents[i].conjugate()).add(new Complex(load.p(),load.q())).subtract(generation.getOrDefault(bus.id(),Complex.ZERO));
            assertEquals(0,residual.getReal(),config.feasibilityTolerance(),bus.id()+" P");
            assertEquals(0,residual.getImaginary(),config.feasibilityTolerance(),bus.id()+" Q");
            var bounds=network.getBus(bus.id()).getVLimit();assertTrue(v[i].abs()>=bounds.getMin()-config.feasibilityTolerance() && v[i].abs()<=bounds.getMax()+config.feasibilityTolerance());
            var expected=reference.getAsJsonArray("bus").get(i).getAsJsonArray();
            double magnitude=expected.get(7).getAsDouble(),angle=Math.toRadians(expected.get(8).getAsDouble());
            maxDifference=Math.max(maxDifference,v[i].subtract(new Complex(magnitude*Math.cos(angle),magnitude*Math.sin(angle))).abs());
        }
        for(int i=0;i<model.branches().size();i++) {
            var branch=model.branches().get(i);double rating=input.getAsJsonArray("branch").get(i).getAsJsonArray().get(5).getAsDouble()/input.get("baseMVA").getAsDouble();
            if(rating==0)continue;
            for(boolean from:new boolean[]{true,false}) {
                var current=from?branch.fromCurrent(v[branch.from()],v[branch.to()]):branch.toCurrent(v[branch.from()],v[branch.to()]);
                var terminal=v[from?branch.from():branch.to()];double flow=mode.equals("I")?current.abs():terminal.multiply(current.conjugate()).abs();
                assertTrue(flow<=rating+config.feasibilityTolerance(),branch.id()+" terminal thermal constraint");
            }
        }
        assertEquals(before,network.getBusList().stream().map(b->b.getVoltage()).toList());assertFalse(network.isOpfSolved());
        System.out.printf("ECP_OPF_COMPARE %s %s %s costError=%.3e maxVoltageDifference=%.3e%n",name,mode,strategy,Math.abs(result.objective()-referenceCost),maxDifference);
        return result;
    }
}
