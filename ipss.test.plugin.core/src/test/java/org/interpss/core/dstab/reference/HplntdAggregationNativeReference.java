package org.interpss.core.dstab.reference;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.interpss.dstab.renewable.*;
import com.interpss.dstab.renewable.*;
import org.interpss.fadapter.psse.dyr.*;

/** Frozen hybrid aggregation/interface references, not a controller-runtime claim. */
public final class HplntdAggregationNativeReference {
    private HplntdAggregationNativeReference() {}
    public record Profile(String name,double step,int samples,String csvSha256,String inputSha256) {
        private byte[] resource(String suffix)throws IOException {
            try(var stream=HplntdAggregationNativeReference.class.getResourceAsStream("/hplntd-foundation-native/"+name+"."+suffix+".b64")) {
                if(stream==null)throw new IllegalStateException("Missing hybrid reference "+name);
                return Base64.getMimeDecoder().decode(stream.readAllBytes());
            }
        }
        public String body() {
            try(var stream=new GZIPInputStream(new ByteArrayInputStream(resource("csv")))) {
                String text=new String(stream.readAllBytes(),StandardCharsets.UTF_8);
                if(!WtgibffraNativeReference.digest(text).equals(csvSha256))throw new IllegalStateException("Changed hybrid CSV");return text;
            }catch(IOException e){throw new UncheckedIOException(e);}
        }
        public String input() {
            try {
                String text=new String(resource("dyr"),StandardCharsets.UTF_8);
                if(!WtgibffraNativeReference.digest(text).equals(inputSha256))throw new IllegalStateException("Changed hybrid input");return text;
            }catch(IOException e){throw new UncheckedIOException(e);}
        }
        private List<PsseDyrRecord> records(){try{return PsseDyrRecordReader.read(new StringReader(input()),name);}catch(IOException e){throw new UncheckedIOException(e);}}
        private static void allocation(PsseDyrRecord r,int... expected) {
            for(int i=0;i<expected.length;i++)if(r.intParameter(i)!=expected[i])throw new IllegalStateException("Changed hybrid allocation");
        }
        public HplntdData plant() {
            var r=records().stream().filter(v->v.sourceModelName().equals("HPLNTDU")).findFirst().orElseThrow();
            if(!r.fields().get(1).equals("USRBUS")||r.parameterCount()!=102)throw new IllegalStateException("Exact bus wrapper required");
            allocation(r,504,0,35,61,13,45);int s=6;var slots=new ArrayList<RepccData.Shunt>();
            for(int i=0;i<10;i++)slots.add(new RepccData.Shunt(r.intParameter(s+13+2*i),r.parameter(s+14+2*i)));
            double[] c=new double[61];for(int i=0;i<61;i++)c[i]=r.doubleParameter(s+35+i);
            return new HplntdData(r.intParameter(s),new RepccData.Branch(r.intParameter(s+1),r.intParameter(s+2),r.parameter(s+3)),
                    r.intParameter(s+4),r.intParameter(s+5),r.intParameter(s+6),r.intParameter(s+7),r.intParameter(s+8),r.intParameter(s+9),
                    new RepccData.Branch(r.intParameter(s+10),r.intParameter(s+11),r.parameter(s+12)),slots,r.intParameter(s+33),r.intParameter(s+34),c);
        }
        public ReaxdData downstream(int bus) {
            var r=records().stream().filter(v->v.sourceModelName().equals("REAXDU")&&v.busNumber()==bus).findFirst().orElseThrow();
            if(!r.fields().get(1).equals("USRMDL")||r.parameterCount()!=23)throw new IllegalStateException("Exact downstream wrapper required");
            allocation(r,107,0,9,8,2,7);int[] indexes=new int[7];double[] c=new double[8];
            for(int i=0;i<7;i++)indexes[i]=r.intParameter(8+i);for(int i=0;i<8;i++)c[i]=r.doubleParameter(15+i);
            return new ReaxdData(r.intParameter(6),r.parameter(7),indexes,c);
        }
    }
    public static List<Profile> profiles(){return List.of(
        new Profile("flat-auto-coarse",0.0005,405,"e2d189e223fa829e46b0394dcde421d1c7b13546ceb0ba5634d68e9cb54f642e","aa957a46a6da32b2ea45ffac9f1fa4493dbcdcd6a4008671f97ff6d6528f02e1"),
        new Profile("flat-fixed-coarse",0.0005,405,"5711762d3942be3f8c21c3a8b41080f9245a4ca3871dc0c415695da702489ce9","8c4219ba3343f984b2f49375df4ab10f6ff1528c90c8394496dca7b03f4a0d87"),
        new Profile("commands-coarse",0.0005,405,"98bb73975c8334ddf7ced5afd1d0f8c6f33582386e4d680edfd9866a4784419e","aa957a46a6da32b2ea45ffac9f1fa4493dbcdcd6a4008671f97ff6d6528f02e1"),
        new Profile("commands-fine",0.00025,805,"53858850d295e86f932bc8776381bbdcb6f3a0c32be90287f049bbe5f752ad3a","aa957a46a6da32b2ea45ffac9f1fa4493dbcdcd6a4008671f97ff6d6528f02e1"),
        new Profile("trip-coarse",0.0005,405,"e72f8a17324f13855deb1f27d19a6125cc7bd6128948345a99ea7ad96fb755af","aa957a46a6da32b2ea45ffac9f1fa4493dbcdcd6a4008671f97ff6d6528f02e1"),
        new Profile("trip-fine",0.00025,805,"65419260faee799e3f9b32ffa7d25ca8872c2617f8b37e1263a60a680e1f3b96","aa957a46a6da32b2ea45ffac9f1fa4493dbcdcd6a4008671f97ff6d6528f02e1"),
        new Profile("fixed-continuous-coarse",0.0005,403,"6cf9d9073f3a33612b26cd3322b784f73dfaa61442c65d5c90a862413c9a0fa2","8c4219ba3343f984b2f49375df4ab10f6ff1528c90c8394496dca7b03f4a0d87")
    );}
}
