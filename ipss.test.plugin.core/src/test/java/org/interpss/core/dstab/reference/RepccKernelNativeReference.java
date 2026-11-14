package org.interpss.core.dstab.reference;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import com.interpss.dstab.renewable.RepccData;
import org.interpss.fadapter.psse.dyr.PsseDyrRecordReader;

/** Cold-paired REPCC dynamic discriminators. Recorded-input replay, not network attachment acceptance. */
public final class RepccKernelNativeReference {
    private RepccKernelNativeReference() {}
    public record Profile(String name,double step,int samples,String csvSha256,String inputSha256) {
        private byte[] resource(String suffix) throws IOException {
            try(var stream=RepccKernelNativeReference.class.getResourceAsStream("/repcc-kernel-native/"+name+"."+suffix+".b64")) {
                if(stream==null)throw new IllegalStateException("Missing plant reference "+name);
                return Base64.getMimeDecoder().decode(stream.readAllBytes());
            }
        }
        public String body() {
            try(var stream=new GZIPInputStream(new ByteArrayInputStream(resource("csv")))) {
                String text=new String(stream.readAllBytes(),StandardCharsets.UTF_8);
                if(!WtgibffraNativeReference.digest(text).equals(csvSha256))throw new IllegalStateException("Changed plant CSV");
                return text;
            } catch(IOException e){throw new UncheckedIOException(e);}
        }
        public String input() {
            try {
                String text=new String(resource("dyr"),StandardCharsets.UTF_8);
                if(!WtgibffraNativeReference.digest(text).equals(inputSha256))throw new IllegalStateException("Changed plant DYR");
                return text;
            } catch(IOException e){throw new UncheckedIOException(e);}
        }
        public RepccData parameters() {
            try {
                var r=PsseDyrRecordReader.read(new StringReader(input()),name).stream()
                        .filter(row->row.sourceModelName().equals("REPCC")).findFirst().orElseThrow();
                var shunts=new ArrayList<RepccData.Shunt>();
                for(int i=0;i<10;i++)shunts.add(new RepccData.Shunt(r.intParameter(13+2*i),r.parameter(14+2*i)));
                double[] c=new double[57];for(int i=0;i<57;i++)c[i]=r.doubleParameter(35+i);
                return new RepccData(r.intParameter(0),new RepccData.Branch(r.intParameter(1),r.intParameter(2),r.parameter(3)),
                        r.intParameter(4),r.intParameter(5),r.intParameter(6),r.intParameter(7),r.intParameter(8),r.intParameter(9),
                        new RepccData.Branch(r.intParameter(10),r.intParameter(11),r.parameter(12)),shunts,r.intParameter(33),r.intParameter(34),c);
            } catch(IOException e){throw new UncheckedIOException(e);}
        }
    }
    public static List<Profile> profiles(){return List.of(
        new Profile("reference-coarse",0.0005,608,"3f11446ba127880c8ef667998fddb8527dc09383966c7b591459c2ce01366319","7ae20f6ae697871adf9c24ae17273f7f5936a8fbbed4d83ea47db8e3cd47369b"),
        new Profile("reference-fine",0.00025,1208,"a7a6784ca04b7ace20f9e845d9fc33b3d1dee110ca2ada51db723bb150f44b98","7ae20f6ae697871adf9c24ae17273f7f5936a8fbbed4d83ea47db8e3cd47369b"),
        new Profile("q-reference-coarse",0.0005,608,"8a1a30638a9a66bfbd8b745783b2f3ec0aa37df54a41c97035e15786ee4946da","ccf15ecdfb384b8522f0869a00551c715d19bd5cd32c5a793e0ea06511b5d2ab"),
        new Profile("q-reference-fine",0.00025,1208,"afd9dcb87d9b6df7eecea1768b7200222970ec19f6ec7b2786c4355a50f84d6c","ccf15ecdfb384b8522f0869a00551c715d19bd5cd32c5a793e0ea06511b5d2ab"),
        new Profile("pf-reference-coarse",0.0005,608,"9e73268fa0afc0d2129f32cddb81c566562f8601af9c6daa2dd5188c0b0ae115","f7417e015531aa58055d1e27160f227d99f652f799d200d2a2c93fcacfd2eb65"),
        new Profile("pf-reference-fine",0.00025,1208,"d1ac985cac734e76a66dbc424c11c03ea772b8a16e7cdffa4cb02504009f695a","f7417e015531aa58055d1e27160f227d99f652f799d200d2a2c93fcacfd2eb65"),
        new Profile("limits-coarse",0.0005,608,"4472f29048761829a3b798f34d1ccc7fa0a72d91d1b87a6f29563a0ccdc222a9","9a0a922259e50eb3b91223ae4e0aa12017fb250beb00266a597532b8d530488e"),
        new Profile("limits-fine",0.00025,1208,"072dd68044df5ab9a9779c15c82dd5c03764756b1b31c9ffedbbca08eb5680ab","9a0a922259e50eb3b91223ae4e0aa12017fb250beb00266a597532b8d530488e"),
        new Profile("voltage-coarse",0.0005,608,"a1d3c6416c186673356697f6973732df6d6cdfb80b47736e93eb5959b9715f8b","ae9418e0b7466fb7e34430442e6b4f0560021c9133eed917237c00f078b7a72b"),
        new Profile("voltage-fine",0.00025,1208,"3970e099cb83a13fb8c82ccb72743c98164327d0e1d941b1547a0d57192fd269","ae9418e0b7466fb7e34430442e6b4f0560021c9133eed917237c00f078b7a72b"),
        new Profile("freeze-coarse",0.0005,608,"868bbd38f3af79ce80430a80f91ea053039dfa45cbf949cea4920e132e4cff39","671205d1516362740df6fc34f8cf69003937d6a907ea53072ad0f8f02f3c679d"),
        new Profile("freeze-fine",0.00025,1208,"2536acd69648026360ce61d887bbdff77c01b0bdd78ebc2ebc978c25f426487e","671205d1516362740df6fc34f8cf69003937d6a907ea53072ad0f8f02f3c679d"),
        new Profile("frequency-coarse",0.0005,608,"257715e158660316d46faaf515a600c6e692092967991ba14a9a34a918903d03","69f348302ff83054bb24711969e99f69659bc042b687a75e8ce0c1c52b7796d5"),
        new Profile("frequency-fine",0.00025,1208,"ca1e991f607cd338a04f87392a51cad392e0ba957cb2ce036dd7095202c4f708","69f348302ff83054bb24711969e99f69659bc042b687a75e8ce0c1c52b7796d5")
    );}
}
