package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

import org.apache.commons.math3.complex.Complex;
import org.eclipse.emf.ecore.InternalEObject;
import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.reference.EmbeddedZeroTimeBoundaryValues;
import org.interpss.dstab.control.gov.psse.gast2a.PsseGast2adGovernor;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.MachineController;
import com.interpss.dstab.mach.RoundRotorMachine;

/** Recorded native boundaries isolate controller equations from network/integrator differences. */
public class ZeroTimePsseBoundaryReplayTest {
    private static final double DT = .0005;
    private static final Path ROOT = Path.of("testData/adpter/psse/v33/zero-time");

    @ParameterizedTest
    @ValueSource(strings={"SCRX","ESST4B","AC1C","AC2C","AC3C","AC7B","REXSYS","GASTWDD","GAST2AD"})
    void nativeZeroTimeProfile(String model) throws Exception {
        IpssCorePlugin.init();
        Path dir=ROOT.resolve(model);
        var rows=EmbeddedZeroTimeBoundaryValues.lines(model).stream().skip(1)
                .map(line->Arrays.stream(line.split(",")).mapToDouble(Double::parseDouble).toArray()).toList();
        var context=new PSSEMultiFileLoader().loadDStab(dir.resolve("smib.raw").toString(),dir.resolve("smib.dyr").toString());
        Machine actual=context.getDStabilityNet().getMachine("Bus2-mach1");
        assertNotNull(actual);
        boolean gas=model.startsWith("GAST");
        MachineController controller=gas?actual.getGovernor():actual.getExciter();
        // EMF containment still belongs to the real imported machine. Only the recorded
        // external boundary signals are substituted; all controller code is production code.
        double[][] boundary={rows.get(0)};
        Machine replay=(Machine)Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{RoundRotorMachine.class,InternalEObject.class},(proxy,method,args)->{
            double[] r=boundary[0];
            return switch(method.getName()) {
                case "calculateIfd" -> r[7];
                case "getSpeed" -> 1+r[4];
                case "getPe" -> r[5];
                case "getPm" -> r[3];
                case "getEfd" -> r[2];
                case "getIxy" -> new Complex(r[5],-r[6]).divide(r[1]);
                default -> {
                    try { yield method.invoke(actual,args); }
                    catch(InvocationTargetException e) { throw e.getCause(); }
                }
            };
        });
        controller.setMachine(replay);
        actual.getDStabBus().setVoltage(new Complex(rows.get(0)[1],0));
        assertTrue(controller.initStates(actual.getDStabBus(),replay),model+" initialization");
        double initialRef=gas?rows.get(0)[9]:((Number)controller.getClass().getMethod("getRefPoint").invoke(controller)).doubleValue();
        var output=new ArrayList<String>();output.add("time_s,native,interpss,error");
        double maximum=0,maximumTime=0;
        for(int i=0;i<rows.size();i++) {
            double[] r=rows.get(i);
            boundary[0]=r;actual.getDStabBus().setVoltage(new Complex(r[1],0));
            double value=controller.getOutput(replay),expected=r[gas?3:2],error=Math.abs(value-expected);
            assertEquals(value,controller.getOutput(replay),1e-12,model+" repeated output query at "+r[0]);
            output.add(String.format(Locale.ROOT,"%.9g,%.12g,%.12g,%.12g",r[0],expected,value,error));
            if(error>maximum){maximum=error;maximumTime=r[0];}
            if(i+1==rows.size())break;
            // Native applies the event after recording t=.05. Keep that event side
            // for both Heun stages of the following step, including its discontinuity.
            double delta=i>=100?(gas?-.02:.01):0;
            double reference=initialRef+delta;
            if(controller instanceof PsseGast2adGovernor g)
                reference=rows.get(0)[3]+g.getData().getW()*delta; // speed reference -> power-reference API, equal 100-MVA bases
            controller.setRefPoint(reference);
            assertTrue(controller.nextStep(DT,DynamicSimuMethod.MODIFIED_EULER,replay,0));
            boundary[0]=rows.get(i+1);actual.getDStabBus().setVoltage(new Complex(boundary[0][1],0));
            assertTrue(controller.nextStep(DT,DynamicSimuMethod.MODIFIED_EULER,replay,1));
        }
        Path report=Path.of("target/zero-time-replay");Files.createDirectories(report);
        Files.write(report.resolve(model+".csv"),output);
        System.out.printf(Locale.ROOT,"ZERO-TIME %s max error %.9g at %.6f s%n",model,maximum,maximumTime);
        double tolerance=switch(model) {
            case "SCRX" -> 6e-4;
            case "ESST4B" -> 4e-6;
            case "AC1C", "AC2C" -> 3e-6;
            case "AC3C" -> 1e-4;
            case "AC7B" -> 6e-5;
            case "REXSYS" -> 5e-5;
            case "GASTWDD" -> 7e-5;
            case "GAST2AD" -> 2.5e-4;
            default -> throw new IllegalArgumentException(model);
        };
        assertTrue(maximum<tolerance,model+" maximum output error="+maximum+" at "+maximumTime);
    }
}
