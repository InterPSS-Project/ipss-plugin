package org.interpss.core.adapter.psse.raw.aclf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.psse.PSSEDirectParser;
import org.interpss.fadapter.psse.PSSEJsonDirectParser;
import org.interpss.fadapter.psse.export.PSSEJsonExporter;
import org.interpss.fadapter.psse.export.PSSERawExporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PsseTransformerMagnetizingShuntTest extends CorePluginTestSetup {
    @TempDir Path dir;

    @Test void rawAndRawxUseNativeTerminalLossAtBothTapPositions() throws Exception {
        for (boolean threeWinding : new boolean[] {false, true}) {
            Path input = dir.resolve("source-" + threeWinding + ".raw");
            Files.writeString(input, raw(threeWinding));
            AclfNetwork original = new PSSEDirectParser().parse(input.toString());
            verify(original, threeWinding);

            Path rawx = dir.resolve("round-trip-" + threeWinding + ".rawx");
            new PSSEJsonExporter(original).export(rawx);
            AclfNetwork importedRawx = new PSSEJsonDirectParser().parse(rawx.toString());
            verify(importedRawx, threeWinding);

            Path raw = dir.resolve("round-trip-" + threeWinding + ".raw");
            new PSSERawExporter(importedRawx, 35).export(raw);
            verify(new PSSEDirectParser().parse(raw.toString()), threeWinding);
        }
    }

    private static void verify(AclfNetwork net, boolean threeWinding) throws Exception {
        AclfBranch leg = threeWinding
                ? net.get3WXfr("Bus1", "Bus2", "Bus3").getFromAclfBranch()
                : net.getBranch("Bus1", "Bus2", "1");
        assertTrue(leg.isFromShuntOnBusSide());
        assertEquals(new Complex(.08, -.10), leg.getFromShuntY());
        var algorithm = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
        algorithm.getLfAdjAlgo().getVoltAdjConfig().setXfrTapControl(false);
        algorithm.setTolerance(1e-9);
        for (double tap : new double[] {.9, 1.1}) {
            leg.setFromTurnRatio(tap);
            assertTrue(algorithm.loadflow());
            double loadMw = threeWinding ? 45.0 : 30.0;
            double lossMw = net.getBus("Bus1").calNetGenResults().getReal() * 100.0 - loadMw;
            // Native 36.7.0 gives 8.65280 MW for both winding counts and both taps.
            assertEquals(8.65280, lossMw, 2e-5);
        }
    }

    private static String raw(boolean threeWinding) {
        String raw = """
                0,100.0,33,0,1,60
                MAGNETIZING SHUNT BASIS
                ZERO SERIES RESISTANCE
                1,'WINDING ONE',230,3,1,1,1,1.04,0,1.3,.7,1.3,.7
                2,'WINDING TWO',230,1,1,1,1,1.0,0,1.3,.7,1.3,.7
                """;
        if (threeWinding) raw += "3,'WINDING THREE',230,1,1,1,1,1.0,0,1.3,.7,1.3,.7\n";
        raw += "0 / END OF BUS DATA, BEGIN LOAD DATA\n2,'1',1,1,1,30,7,0,0,0,0,1,1,0\n";
        if (threeWinding) raw += "3,'1',1,1,1,15,4,0,0,0,0,1,1,0\n";
        raw += """
                0 / END OF LOAD DATA, BEGIN FIXED SHUNT DATA
                0 / END OF FIXED SHUNT DATA, BEGIN GENERATOR DATA
                1,'1',50,0,1000,-1000,1.04,0,100,0,.2,0,0,1,1,100,999,-999,1,1
                0 / END OF GENERATOR DATA, BEGIN BRANCH DATA
                0 / END OF BRANCH DATA, BEGIN TRANSFORMER DATA
                """;
        raw += "1,2," + (threeWinding ? "3" : "0") + ",'1',2,2,1,.08,-.10,1,'MAGTEST',1,1,1\n";
        raw += threeWinding ? "0,.12,100,0,.13,100,0,.11,100,1,0\n" : "0,.12,100\n";
        String winding = "230,230,0,1000,1000,1000,0,0,253,207,1.1,.9,21,0,0,0,0\n";
        raw += winding;
        raw += threeWinding ? winding + winding : "230,230\n";
        return raw + "0 / END OF TRANSFORMER DATA, BEGIN AREA DATA\nQ\n";
    }
}
