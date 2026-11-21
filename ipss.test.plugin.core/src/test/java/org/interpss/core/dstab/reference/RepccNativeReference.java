package org.interpss.core.dstab.reference;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import com.interpss.dstab.renewable.RepccData;
import org.interpss.fadapter.psse.dyr.PsseDyrRecordReader;

/** Cold-paired standard-native REPCC initialization profiles, not runtime acceptance. */
public final class RepccNativeReference {
    private RepccNativeReference() {}
    public record Profile(String name,double step,int samples,String csvSha256,String inputSha256) {
        private byte[] resource(String suffix) throws IOException {
            try(var stream=RepccNativeReference.class.getResourceAsStream("/repcc-foundation-native/"+name+"."+suffix+".b64")) {
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
        new Profile("branch-flat",0.0005,605,"e92c5191ad5f0cba53a9c57d03a8e74d54b6de9e95d2e36ca2cf1c5e0ad4debf","de1557af5d85f9fda00f1ce1b3bf7478ffde7eeba09c1e24aca9ee2162916f70"),
        new Profile("q-flat",0.0005,605,"549c4f8c664ea0cce33c377385c8c730e060f577b058cd6d606df00e53011f05","df9521b981de63be6050cf5d77f8cf21a8f5551942396b6c7bb8fcb691413626"),
        new Profile("pf-feasible-flat",0.0005,605,"71f635e4b384242beaa80b6470b57100204adc21253e8914946fa4590df67db5","9c12383f43491ab42d897c63e2a2aeb261b1a5f515de87b71201847de77581f2"),
        new Profile("leadlag-flat",0.0005,605,"2c13c2614c9be1e8489b2fe511bf22ec61dec89519076bf9245c18af24c87c5d","c46221dcb13f665dac5852631af3b3b71ced045f7de50815740ebb243c523039"),
        new Profile("droop-flat",0.0005,605,"84d008b06fd7278189ac8715c31f52ecbbe3b683b35482ec5145b4087867926c","4d6225e5999ce52c45451be6883addef3e9c932dc604678e3df7607c2246ee33"),
        new Profile("no-feedforward-flat",0.0005,605,"25cefd1b58c910453ec588916aacad1177bcb550b78f25824b86cfc779d23e2b","d1e55f0180714ae8857f16a1cfe52a04aac5b1ce625c664ae69d507882f89fe9"),
        new Profile("no-feedback-flat",0.0005,605,"2ceef0987867c4f96c66f4fceb83a18b2f6555970d5f306310049e7ad702c5ba","2d13506ade22a44c22e3014d3ad8bb5a2b89884257152f713013eebdbf9aaa1e"),
        new Profile("fallback-flat",0.0005,605,"475833150484b5a31e836ff6c6c922b2cbef64b5e5e72f7985cc81123d28ba51","2ab102445e0e9809013e9415175b744f313b1527e9110132fa6a5b741e636b70")
    );}
}
