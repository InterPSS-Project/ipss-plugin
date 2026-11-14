package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.nio.file.*;
import java.util.*;
import org.interpss.dstab.renewable.*;
import com.interpss.dstab.renewable.*;
import org.interpss.core.dstab.reference.RepcaNativeVerification;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.IpssCorePlugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** Exact selector and unsorted-curve compatibility, without rewriting source data. */
public class WttqaCompatibilityTest {
    private static final Path ROOT=Path.of("testData/private/wttqa-compatibility");
    @BeforeAll static void setup(){IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");}
    private static Wttqa1Data data(int flag,double p1,double s1,double p2,double s2,double p3,double s3,double p4,double s4) {
        return new Wttqa1Data(flag,.6,3,.066667,30,1.2,.08,p1,s1,p2,s2,p3,s3,p4,s4,0);
    }
    @Test void sourceCurveOrderIsPreservedAndZeroWidthIntervalsAreSkipped() {
        var equal=data(1,.4,.58,.4,.73,.82,.87,.97,1);
        var model=new Wttqa1Model(equal);assertEquals(.58,model.speedForPower(.3999));
        assertEquals(.73,model.speedForPower(.4));assertEquals(.4,model.getData().p1());assertEquals(.4,model.getData().p2());
        model=new Wttqa1Model(data(1,.504,.58,.4,.74,.76,.869,.905,1));
        assertEquals(.58,model.speedForPower(.45));assertEquals(.74+(.6-.4)*(.869-.74)/(.76-.4),model.speedForPower(.6));
        model=new Wttqa1Model(data(1,.15,.58,.4,.66,.95,.861,.86,1));
        assertEquals(1,model.speedForPower(.9));assertEquals(1,model.speedForPower(1));
    }
    @Test void selectorTwoMatchesPowerControlThroughBothIntegrationStages() {
        var one=new Wttqa1Model(data(1,.2,.58,.4,.72,.78,.866,.98,1));
        var two=new Wttqa1Model(data(2,.2,.58,.4,.72,.78,.866,.98,1));
        one.initialize(.24,.608);two.initialize(.24,.608);
        for(int i=0;i<100;i++)for(int flag=0;flag<2;flag++) {
            double p=.24+.01*Math.sin(i*.03),speed=.608+.001*Math.cos(i*.03);
            one.step(.0005,p,speed,.25,false,flag);two.step(.0005,p,speed,.25,false,flag);
            assertEquals(one.getNamedStates(),two.getNamedStates());assertEquals(one.getPref(),two.getPref());
        }
        assertEquals(2,two.getData().tFlag());
    }
    @Test void malformedConstantsAreRejectedRatherThanDefaulted(@TempDir Path directory)throws Exception {
        Path fixture=Path.of("testData/adpter/psse/v33/renewable");
        String original=Files.readString(fixture.resolve("type3_wind_bus1062.dyr"));
        for(String value:List.of("bad","NaN","Infinity")) {
            String changed=original.replace("'1' 1 1.1 0.5 0.04 60 1.002", "'1' 1 "+value+" 0.5 0.04 60 1.002");
            assertNotEquals(original,changed);Path file=directory.resolve(value+".dyr");Files.writeString(file,changed);
            var loader=new PSSEMultiFileLoader();loader.loadDStab(fixture.resolve("regca_reeca_repca_bus1062.raw").toString(),file.toString());
            assertTrue(loader.getLastDynamicImportReport().failures().stream().anyMatch(e->e.canonicalModelName().equals("WTTQA1")));
        }
    }
    @Test void nativeOperatingPointCurveOutputsAreRetainedAcrossColdRuns()throws Exception {
        assumeTrue(Files.isDirectory(ROOT),"Private compatibility references unavailable");int profiles=0;
        for(Path directory:Files.list(ROOT).sorted().toList()) {
            String name=directory.getFileName().toString();
            if(!name.endsWith("-coarse-a")||name.contains("evaluation")||name.contains("selector"))continue;
            var manifest=com.google.gson.JsonParser.parseString(Files.readString(directory.resolve("manifest.json"))).getAsJsonObject();
            var constants=manifest.getAsJsonArray("initialized_constants");double[] c=new double[15];for(int i=0;i<15;i++)c[i]=constants.get(i).getAsDouble();
            int flag=manifest.get("initialized_selector").getAsInt();
            var model=new Wttqa1Model(new Wttqa1Data(flag,c[0],c[1],c[2],c[3],c[4],c[5],c[6],c[7],c[8],c[9],c[10],c[11],c[12],c[13],c[14]));
            var first=RepcaNativeVerification.rows(Files.readString(directory.resolve("native.csv"))).getFirst();
            double actual=model.speedForPower(first.get("TRQ_STATE0")),expected=first.get("TRQ_VAR0");
            System.out.println("WTTQA_CURVE "+name+" input="+first.get("TRQ_STATE0")+" error="+Math.abs(actual-expected));
            assertEquals(expected,actual,1e-6,name);
            Path repeat=directory.resolveSibling(name.substring(0,name.length()-1)+"b");
            assertEquals(-1,Files.mismatch(directory.resolve("native.csv"),repeat.resolve("native.csv")),name);profiles++;
        }
        assertEquals(20,profiles);
        for(String grid:List.of("coarse","fine"))for(String repeat:List.of("a","b")) {
            Path one=ROOT.resolve("wttqa-native-selector1-0p24-"+grid+"-"+repeat).resolve("native.csv");
            Path two=ROOT.resolve("wttqa-native-selector2-0p24-"+grid+"-"+repeat).resolve("native.csv");
            assertEquals(-1,Files.mismatch(one,two),grid+" "+repeat);
        }
    }
}
