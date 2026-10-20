package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import org.interpss.dstab.control.oel.psse.oel3c.Oel3cData;
import org.interpss.dstab.control.oel.psse.oel3c.Oel3cKernel;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Exact native recorded-measurement playback, not network/measurement-route acceptance. */
public class Oel3cRecordedInputConformanceTest {
    private static final String BASE="/oel3c-recorded-input-reference/";
    private record Profile(String name,double step,int samples,String sha,int selector) { }
    private static byte[] resource(String name)throws IOException{
        try(var input=Oel3cRecordedInputConformanceTest.class.getResourceAsStream(BASE+name)){
            if(input==null)throw new IOException("Missing recorded-input fixture "+name);
            return input.readAllBytes();
        }
    }
    static Stream<Profile> profiles()throws IOException{
        return new String(resource("profiles.tsv"),StandardCharsets.UTF_8).lines().skip(1).map(line->{
            String[] fields=line.split("\t");assertEquals(5,fields.length);
            return new Profile(fields[0],Double.parseDouble(fields[1]),Integer.parseInt(fields[2]),fields[3],Integer.parseInt(fields[4]));
        });
    }
    @ParameterizedTest @MethodSource("profiles")
    void publishedKernelReplaysEveryRawMeasurementWithoutFittedStatesOrReference(Profile profile)throws Exception{
        byte[] body;
        try(var input=new GZIPInputStream(new ByteArrayInputStream(Base64.getMimeDecoder().decode(resource(profile.name()+".csv.gz.b64"))))){
            body=input.readAllBytes();
        }
        assertEquals(profile.sha(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body)));
        List<String> lines=new String(body,StandardCharsets.UTF_8).lines().toList();
        assertEquals(profile.samples(),lines.size()-1);List<String> names=Arrays.asList(lines.getFirst().split(","));
        String[] keys={"OEL_INPUT","OEL_INTEGRAL","OEL_REFERENCE","OEL_ERROR","OEL_GAINED_ERROR","OEL_PROPORTIONAL"};
        int[] columns=Arrays.stream(keys).mapToInt(key->{int index=names.indexOf(key);assertTrue(index>=0,key);return index;}).toArray();
        var data=new Oel3cData(profile.selector(),1,.5,.3333,0,.1,.1,1,0,-1,0,-1);
        var kernel=new Oel3cKernel(data);double[] previous=null,squares=new double[keys.length],maximum=new double[keys.length],peakTimes=new double[keys.length];
        int history=0,eventPosts=0,steps=0;double lastTime=Double.NEGATIVE_INFINITY;boolean initialized=false;
        for(String line:lines.subList(1,lines.size())){
            double[] row=Arrays.stream(line.split(",")).mapToDouble(Double::parseDouble).toArray();
            assertEquals(names.size(),row.length);for(double value:row)assertTrue(Double.isFinite(value));
            double time=row[0],raw=row[columns[0]]/data.scale();assertTrue(time>=lastTime);lastTime=time;
            if(!initialized){kernel.initialize(raw);assertEquals(0,row[columns[1]],0);initialized=true;}
            if(time<0)history++;
            else if(previous!=null&&time==previous[0])eventPosts++;
            else if(time>0){
                assertNotNull(previous);assertTrue(time-previous[0]<profile.step()*1.02);
                assertTrue(kernel.step(profile.step(),0,previous[columns[0]]/data.scale()));
                assertTrue(kernel.step(profile.step(),1,raw));steps++;
            }
            var signals=kernel.signals(raw);
            double[] values={signals.scaledInput(),kernel.state().integral(),signals.reference(),signals.error(),signals.gainedError(),signals.proportional()};
            assertEquals(.5,row[columns[2]],0,"Entered reference must not be replaced by initialized measurement");
            for(int i=0;i<keys.length;i++){
                double error=Math.abs(values[i]-row[columns[i]]);squares[i]+=error*error;
                if(error>maximum[i]){maximum[i]=error;peakTimes[i]=time;}
            }
            if(time>=0)previous=row;
        }
        assertEquals(2,history);assertEquals(2,eventPosts);assertEquals(Math.round(.5/profile.step()),steps);
        double[] rmses=Arrays.stream(squares).map(value->Math.sqrt(value/profile.samples())).toArray();
        System.out.println("OEL3C recorded-input "+profile.name()+" RMSE="+Arrays.toString(rmses)+" maxima="+Arrays.toString(maximum)+" peakTimes="+Arrays.toString(peakTimes));
        for(int i=0;i<keys.length;i++)assertTrue(rmses[i]<.001,keys[i]+" RMSE="+rmses[i]);
    }
}
