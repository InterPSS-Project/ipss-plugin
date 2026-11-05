package org.interpss.core.dstab.dynLoad;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipFile;
import org.interpss.IpssCorePlugin;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Independent motor-allocation preparation control and no-motor trajectory. */
class CompositeMotorAllocationDiscriminatorTest {
    private static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/cmldblu1-motor-allocation-20261105/captures.zip");
    private static final String SHA="6937323b632cd0a22d90f310c20393faad5e6ba4e3ce831a04e05e91e2d6bbe6";
    private static final List<String> INACTIVE=List.of("VAR69","VAR70","VAR71","VAR72");

    private record Table(String[] header,List<String[]> rows) {
        int column(String name) { int i=Arrays.asList(header).indexOf(name);assertTrue(i>=0,name);return i; }
        double value(int row,String name) { return Double.parseDouble(rows.get(row)[column(name)]); }
    }

    private static ZipFile verifiedArchive() throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE));
        assertEquals(SHA,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(ARCHIVE))));
        return new ZipFile(ARCHIVE.toFile());
    }

    private static Table table(ZipFile zip,String path) throws Exception {
        try(var reader=new BufferedReader(new InputStreamReader(zip.getInputStream(zip.getEntry(path)),
                StandardCharsets.UTF_8))) {
            String[] header=reader.readLine().split(",",-1);
            List<String[]> rows=new ArrayList<>();
            for(String line;(line=reader.readLine())!=null;) {
                String[] fields=line.split(",",-1);
                assertEquals(header.length,fields.length,path+" row "+rows.size());
                rows.add(fields);
            }
            return new Table(header,rows);
        }
    }

    private static void exactActiveRepeat(Table a,Table b) {
        assertArrayEquals(a.header,b.header);
        assertEquals(1005,a.rows.size());assertEquals(a.rows.size(),b.rows.size());
        for(int row=0;row<a.rows.size();row++) for(int col=0;col<a.header.length;col++)
            if(!INACTIVE.contains(a.header[col]))
                assertEquals(a.rows.get(row)[col],b.rows.get(row)[col],a.header[col]+" row "+row);
    }

    @Test void motorAllocationBarelyChangesPreparedStaticAdmittance() throws Exception {
        try(var zip=verifiedArchive()) {
            var one=table(zip,"one_motor_removed/a/native.csv");
            var noMotor=table(zip,"all_motors_removed/a/native.csv");
            exactActiveRepeat(one,table(zip,"one_motor_removed/b/native.csv"));
            exactActiveRepeat(noMotor,table(zip,"all_motors_removed/b/native.csv"));
            for(var source:List.of(one,noMotor)) {
                assertEquals(source.value(102,"time_s"),source.value(103,"time_s"));
                assertEquals(0.,source.value(102,"VAR57"),1e-12);
                assertTrue(source.value(103,"VAR57")>.79);
                assertTrue(source.value(103,"VAR29")<.5);
            }
            assertEquals(.796466946602,one.value(103,"VAR57"),1e-9);
            assertEquals(.794167339802,noMotor.value(103,"VAR57"),1e-9);
        }
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void noMotorPortMatchesCompleteAllocationControl(boolean staggered,@TempDir Path staged) throws Exception {
        Table source;
        Path dynamic=staged.resolve("dynamic.dyr");
        try(var zip=verifiedArchive()) {
            source=table(zip,"all_motors_removed/a/native.csv");
            exactActiveRepeat(source,table(zip,"all_motors_removed/b/native.csv"));
            Files.write(dynamic,zip.getInputStream(zip.getEntry("all_motors_removed/a/dynamic.dyr")).readAllBytes());
        }
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        var actual=Cmldblu1SharedRecording.simulateStaticControl(dynamic,staggered,.0005,.04,0,.5);
        assertEquals(source.rows.size()-2,actual.frames.size());
        int duplicates=0;
        for(int row=3;row<source.rows.size();row++)
            if(source.value(row,"time_s")==source.value(row-1,"time_s"))duplicates++;
        assertEquals(2,duplicates);
        for(String[] pair:new String[][]{{"V1","V1"},{"V2","V2"},{"V3","V3"},
                {"VAR25","STATIC.P"},{"VAR26","STATIC.Q"},{"VAR29","Load.V"},
                {"VAR57","STATIC.LowVoltageG"},{"VAR58","STATIC.LowVoltageB"},
                {"ANGLE_DEG","ANGLE_DEG"}}) {
            double sum=0,peak=0,min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
            for(int row=0;row<actual.frames.size();row++) {
                double expected=source.value(row+2,pair[0]);
                var frame=actual.frames.get(row);
                assertEquals(source.value(row+2,"time_s"),frame.time(),5e-6);
                double observed=frame.values().get(pair[1]),error=observed-expected;
                sum+=error*error;peak=Math.max(peak,Math.abs(error));
                min=Math.min(min,expected);max=Math.max(max,expected);
            }
            double rmse=Math.sqrt(sum/actual.frames.size()),nrmse=rmse/(max-min);
            System.out.printf("CMLD_ALLOC_ZERO %s %s rmse=%.12g nrmse=%.12g peak=%.12g%n",
                    staggered,pair[1],rmse,nrmse,peak);
            if(pair[1].equals("ANGLE_DEG"))assertTrue(rmse<.01,pair[1]+" RMSE="+rmse);
            else if(pair[1].startsWith("STATIC.LowVoltage"))assertTrue(peak<5e-6,pair[1]+" peak="+peak);
            else assertTrue(nrmse<.001,pair[1]+" NRMSE="+nrmse);
        }
    }
}
