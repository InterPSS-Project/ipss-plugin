package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.interpss.core.dstab.reference.WtgibffraNativeReference;
import org.interpss.core.dstab.reference.ReeceRegcbNativeBoundaryReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Native standard-name import and full-solver checks, not complete trajectory parity. */
public class ReeceNativeImportSmibIntegrationTest {
    @Test void nativeBlockedCurrentIsZeroDespiteRetainedConverterStates() {
        for(var profile:ReeceRegcbNativeBoundaryReference.profiles()) {
            var lines=profile.body().lines().toList();
            var headers=java.util.Arrays.asList(lines.getFirst().split(","));
            assertEquals(profile.samples(),lines.size()-1);
            int blocked=0,retained=0;
            for(String line:lines.subList(1,lines.size())) {
                String[] values=line.split(",");
                java.util.function.ToDoubleFunction<String> value=name->Double.parseDouble(values[headers.indexOf(name)]);
                // All four native current bounds zero identifies this profile's hardware block.
                if(value.applyAsDouble("EC_VAR9")==0 && value.applyAsDouble("EC_VAR10")==0
                        && value.applyAsDouble("EC_VAR11")==0 && value.applyAsDouble("EC_VAR12")==0) {
                    blocked++;
                    for(String name:new String[]{"REGC_VAR6","REGC_VAR7"})
                        assertEquals(0,value.applyAsDouble(name),0,profile.name()+":"+name);
                    // Native single-precision network cancellation leaves P <= 2.27e-9,
                    // Q <= 9.54e-7; current outputs themselves are exactly zero.
                    assertEquals(0,value.applyAsDouble("P_PU"),3e-9,profile.name()+":P_PU");
                    assertEquals(0,value.applyAsDouble("Q_PU"),1e-6,profile.name()+":Q_PU");
                    if(Math.abs(value.applyAsDouble("REGC_STATE0"))>.01
                            && Math.abs(value.applyAsDouble("REGC_STATE4"))>.01)retained++;
                }
            }
            assertTrue(blocked>0,profile.name());assertTrue(retained>0,profile.name());
        }
    }
    @Test void nativeFlatRunAtBothSteps() throws Exception {for(double h:new double[]{.0005,.00025})ReeceTypedSmibIntegrationTest.run(h,"flat",true);}
    @Test void nativeThreeCycleFaultAtBothSteps() throws Exception {for(double h:new double[]{.0005,.00025})ReeceTypedSmibIntegrationTest.run(h,"fault",true);}
    @Test void nativeTripAndRestartAtBothSteps() throws Exception {for(double h:new double[]{.0005,.00025})ReeceTypedSmibIntegrationTest.run(h,"trip",true);}
    @Test void publicFixtureRetainsEveryNativeByteApartFromDeclaredLfNormalization() throws Exception {
        String dyr=Files.readString(Path.of("testData/adpter/psse/v33/renewable/regca_reece_bus1062.dyr")).replace("\r\n","\n");
        assertEquals("8784f51ac2601b13ea58563c6b0bbdefc40035603db68f7af88e2687212fdb2c",WtgibffraNativeReference.digest(dyr));
        assertEquals("d600f2a124d6a6ca4932423c70d702e4d5fb944dcc1d109255c68b1d2368690f",WtgibffraNativeReference.digest(dyr.replace("\n","\r\n")));
    }
    @Test void behindImpedanceHostRunsFlatFaultAndTripAtBothSteps(@TempDir Path dir) throws Exception {
        String nativeDyr=Files.readString(Path.of("testData/adpter/psse/v33/renewable/regca_reece_bus1062.dyr"));
        Path file=dir.resolve("regcb-reece.dyr");Files.writeString(file,"1 'REGCB1' 1 0 1 .02 .01 10 -10 10 .01 1.2 /\n"+
                nativeDyr.substring(nativeDyr.indexOf('\n')+1));
        for(double h:new double[]{.0005,.00025})for(String profile:new String[]{"flat","fault","trip"})ReeceTypedSmibIntegrationTest.run(h,profile,file);
    }
    @Test void deferredPlantControllerPreservesFlatEquilibriumAtBothSteps(@TempDir Path dir) throws Exception {
        String base="testData/adpter/psse/v33/renewable/";
        String plant=Files.readString(Path.of(base+"regca_reeca_repca_bus1062.dyr")).lines().filter(line->line.contains("'REPCA1'")).findFirst().orElseThrow();
        Path file=dir.resolve("plant-reece.dyr");Files.writeString(file,plant+"\n"+Files.readString(Path.of(base+"regca_reece_bus1062.dyr")));
        for(double h:new double[]{.0005,.00025})ReeceTypedSmibIntegrationTest.run(h,"flat",file);
    }
}
