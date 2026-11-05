package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.renewable.RegccModel;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.dstab.DStabGen;

/** Independent PI-equation discriminator and complete substep trajectory diagnostics. */
class RegccSubstepReferenceTest extends CorePluginTestSetup {
    private static byte[] decode(String name)throws Exception {
        try(var stream=RegccSubstepReferenceTest.class.getResourceAsStream("/regcc/substeps/"+name)) {
            assertNotNull(stream,name);return Base64.getMimeDecoder().decode(stream.readAllBytes());
        }
    }
    private static Path stage(Path directory,String profile)throws Exception {
        var folder=directory.resolve(profile);Files.createDirectories(folder);
        Files.write(folder.resolve("network.raw"),decode(profile+".raw.b64"));
        Files.write(folder.resolve("dynamic.dyr"),decode(profile+".dyr.b64"));
        try(var input=new GZIPInputStream(new ByteArrayInputStream(decode(profile+".csv.gz.b64")))) {
            Files.write(folder.resolve("native.csv"),input.readAllBytes());
        }
        return folder;
    }
    @Test void clippedOutputAndPlainIntegralMatchIndependentSmoothDerivatives(@TempDir Path directory)throws Exception {
        var folder=stage(directory,"high-gain");
        var context=new PSSEMultiFileLoader().loadDStab(folder.resolve("network.raw").toString(),folder.resolve("dynamic.dyr").toString());
        assertTrue(context.getDynSimuAlgorithm().getAclfAlgorithm().loadflow());
        context.getDynSimuAlgorithm().setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        context.getDynSimuAlgorithm().setSimuStepSec(1./240.);assertTrue(context.getDynSimuAlgorithm().initialization());
        var model=(RegccModel)((DStabGen)context.getDStabilityNet().getBus("Bus1").getContributeGen("1")).getDynamicGenDevice();
        var lines=Files.readAllLines(folder.resolve("native.csv"),StandardCharsets.UTF_8);
        var columns=Arrays.asList(lines.getFirst().split(","));var rows=new ArrayList<double[]>();
        for(String line:lines.subList(1,lines.size()))rows.add(Arrays.stream(line.split(",")).mapToDouble(Double::parseDouble).toArray());
        int time=columns.indexOf("time_s"),ed=columns.indexOf("STATE6"),iq=columns.indexOf("STATE0");
        double edSquare=0.,iqSquare=0.;int samples=0;
        for(int k=1;k<rows.size()-1;k++) {
            var a=rows.get(k-1);var b=rows.get(k);var c=rows.get(k+1);
            if(b[time]<=.052 || b[time]>=.098 || Math.min(b[time]-a[time],c[time]-b[time])<.0002)continue;
            double[] x=new double[9];for(int i=0;i<9;i++)x[i]=b[columns.indexOf("STATE"+i)];
            double angle=Math.toRadians(b[columns.indexOf("A1_DEG")]),voltage=b[columns.indexOf("V1")];
            var v=new Complex(voltage*Math.cos(angle),voltage*Math.sin(angle));
            var current=new Complex(b[columns.indexOf("VAR8")],-b[columns.indexOf("VAR9")])
                    .multiply(new Complex(Math.cos(x[8]),Math.sin(x[8])));
            var dx=model.evaluateDerivatives(x,v,current,b[columns.indexOf("VAR0")],b[columns.indexOf("VAR1")]);
            double errorEd=(c[ed]-a[ed])/(c[time]-a[time])-dx[6];
            double errorIq=(c[iq]-a[iq])/(c[time]-a[time])-dx[0];
            edSquare+=errorEd*errorEd;iqSquare+=errorIq*errorIq;samples++;
        }
        assertEquals(177,samples);assertTrue(Math.sqrt(edSquare/samples)<.011);
        assertTrue(Math.sqrt(iqSquare/samples)<.005);
    }
    @Test void allLocalCountsRetainFullEventSidedDiagnostics(@TempDir Path directory)throws Exception {
        for(String profile:List.of("fault","high-gain","minimum")) {
            Path input=stage(directory,profile);var expected=RegccSubstepStudy.reference(input.resolve("native.csv"));
            assertEquals(771,expected.size());
            for(int count:new int[]{1,2,4,8}) {
                var run=RegccSubstepStudy.run(input,directory.resolve(profile+"-n"+count+".csv"),count,.2);
                assertEquals(51,run.rows().size());assertEquals(4./240.,run.recoveryTime());
                assertEquals(profile.equals("minimum")?2./240.:.01,run.effectiveTe());
                var errors=RegccSubstepStudy.compare(run.rows(),expected);
                assertEquals(771,errors.get("reference_rows_compared"));
                var maximum=(double[])errors.get("maximum");
                for(double value:maximum)assertTrue(Double.isFinite(value));
                // Fixed commands leave these lags constant: the reference's finer grid cannot change them.
                assertTrue(maximum[3]<1e-6);assertTrue(maximum[4]<1e-6);
                System.out.println(profile+" local steps="+count+" diagnostic maxima="+Arrays.toString(maximum));
            }
        }
    }
}
