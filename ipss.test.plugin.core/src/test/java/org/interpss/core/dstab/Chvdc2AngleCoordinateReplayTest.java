package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interpss.dstab.hvdc.model.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/** Conditional recorded-input controller replay; this does not qualify the coupled electrical angle mapping. */
public class Chvdc2AngleCoordinateReplayTest {
    static Stream<Arguments> controls(){return Stream.of("zero","rectifier","inverter")
        .flatMap(end->Stream.of(false,true).map(staggered->Arguments.of(end,staggered)));}
    @ParameterizedTest @MethodSource("controls")
    void sourceAngleInitializationIsolatedFromElectricalBoundary(String end,boolean staggered)throws Exception {
        Chvdc2ResistanceReferenceTest.integrity();var json=new ObjectMapper();
        try(var z=new ZipFile(Chvdc2ResistanceReferenceTest.ARCHIVE.toFile())) {
            String prefix=end+"/flat/a/";
            var manifest=json.readTree(z.getInputStream(z.getEntry(prefix+"manifest.json")));
            var equipment=json.readTree(z.getInputStream(z.getEntry(prefix+"solved-equipment.json")));
            var rows=Tiocr1ResetReferenceTest.csv(z.getInputStream(z.getEntry(prefix+"native.csv")).readAllBytes());
            var initial=rows.getFirst();var i=equipment.path("9");
            double[] constants=json.convertValue(manifest.path("entered_constants"),double[].class);
            var p=new Chvdc2Parameters("1",Chvdc2Parameters.Dialect.CHVDC2U1,new int[]{0,0,1},constants);
            double reference=initial.get("VAR15")*constants[28],h=.0005,local=h/10;
            var boundary=new LccConverterBoundary(false,2,i.path("EBASE").asDouble()*1000,i.path("TR").asDouble(),
                i.path("TAP").asDouble(),end.equals("inverter")?.5:0,i.path("XC").asDouble(),1e8);
            double physicalAlpha=Math.toDegrees(boundary.firingAngleForDcVoltage(initial.get("V9"),initial.get("VAR8")*1000,initial.get("VAR7")));
            double sourceAlpha=initial.get("STATE7"),initialAngleError=physicalAlpha-sourceAlpha;
            double controllerAlpha=Math.toDegrees(boundary.controllerAngleForDcVoltage(
                    initial.get("V9"),initial.get("VAR8")*1000,initial.get("VAR7")));
            assertEquals(sourceAlpha,controllerAlpha,.002,"The prepared controller angle must use the source coordinate");
            double translated=boundary.electricalAngleForController(initial.get("V9"),initial.get("VAR7"),
                    Math.toRadians(controllerAlpha));
            assertEquals(physicalAlpha,Math.toDegrees(translated),1e-9);
            assertEquals(initial.get("VAR4"),Math.toDegrees(boundary.extinctionAtFiringAngle(
                    initial.get("V9"),initial.get("VAR7"),translated)),.002,
                    "The extinction margin must use the physical angle");
            if(end.equals("inverter"))assertTrue(Math.abs(initialAngleError)>.5,"Electrical initialization already differs before any stage");
            else assertEquals(sourceAlpha,physicalAlpha,.002);
            var order=p.prepareOrderControls(i.path("TR").asDouble(),i.path("TAP").asDouble());
            order.setInput(initial.get("VAR5"),initial.get("VAR7"),initial.get("VAR6"),initial.get("VAR8"),initial.get("V7"),initial.get("V9"),reference,0);
            order.initialize(sourceAlpha);double sourceCosine=order.cosineReference();
            assertEquals(initial.get("VAR16"),sourceCosine,2e-7,"Recorded controller angle reproduces independent cosine-reference initialization");
            order.initialize(physicalAlpha);double physicalCosine=order.cosineReference();
            if(end.equals("inverter"))assertTrue(Math.abs(physicalCosine-initial.get("VAR16"))>.006);
            var model=new Chvdc2ControlAssembly(p,i.path("TR").asDouble(),i.path("TAP").asDouble());
            bind(model,initial,reference);model.initialize(initial.get("STATE6"),sourceAlpha,initial.get("time_s"));
            double[] x=new double[15],f=new double[15],old=new double[15],maximum=new double[15],firstRate=new double[15];
            model.evaluateStaggered(0,firstRate,0);long sequence=0;boolean started=false;model.acceptBoundary(initial.get("time_s"),sequence++);
            var out=Files.createTempDirectory(Files.createDirectories(Path.of("target/chvdc2-angle-coordinate-replay")),end+"-"+staggered+"-");
            try(var writer=org.interpss.core.dstab.reference.BenchmarkOutputGuard.newWriter(out.resolve("comparison.csv"),2*1024*1024)) {
            writer.write("time_s");for(int k=0;k<15;k++)writer.write(",actual_"+k+",reference_"+k);writer.write("\n");
            for(int row=0;row<rows.size();row++) {
                var sample=rows.get(row);bind(model,sample,reference);
                if(row>0&&sample.get("time_s")>rows.get(row-1).get("time_s")) {
                    for(int sub=0;sub<10;sub++) {
                        if(staggered) {
                            model.readStaggeredState(x,0);model.evaluateStaggered(local,f,0);
                            if(!started)System.arraycopy(f,0,old,0,15);
                            for(int k=0;k<15;k++)x[k]+=local*(1.5*f[k]-.5*old[k]);
                            System.arraycopy(f,0,old,0,15);model.applyStaggeredState(x,0,local);started=true;
                        }else {model.step(local,0);model.step(local,1);}
                    }
                }
                model.acceptBoundary(sample.get("time_s"),sequence++);model.readPublishedStates(x,0);
                writer.write(Double.toString(sample.get("time_s")));
                for(int k=0;k<15;k++) {
                    maximum[k]=Math.max(maximum[k],Math.abs(x[k]-sample.get("STATE"+k)));
                    writer.write(","+x[k]+","+sample.get("STATE"+k));
                }
                writer.write("\n");
            }
            }
            json.writerWithDefaultPrettyPrinter().writeValue(out.resolve("diagnostic.json").toFile(),Map.of(
                "end",end,"staggered",staggered,"recorded_input_and_initial_angle_forcing",true,
                "physical_initial_angle_error_degrees",initialAngleError,"source_angle_cosine_reference",sourceCosine,
                "physical_angle_cosine_reference",physicalCosine,"first_controller_derivatives",firstRate,"maximum_state_errors",maximum));
            double[] limits={1e-5,1e-5,1e-5,1e-5,.002,.002,.002,.002,1e-5,1e-5,1e-5,1e-5,1e-5,1e-5,.002};
            for(int k=0;k<15;k++)assertTrue(maximum[k]<limits[k],"Conditional controller state "+k+" error "+maximum[k]);
        }
    }
    private static void bind(Chvdc2ControlAssembly m,Map<String,Double> r,double reference) {
        m.setInput(r.get("VAR5"),r.get("VAR7"),r.get("VAR6"),r.get("VAR8"),r.get("V7"),r.get("V9"),reference,r.get("VAR0"),r.get("VAR4"));
    }
}
