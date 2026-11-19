package org.interpss.core.dstab.reference;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import com.interpss.dstab.renewable.Regfmc1Data;
/** Native selector/manual-reference and active-bound profiles, never fitted. */
public final class Regfmc1SelectorNativeReference {
    private Regfmc1SelectorNativeReference(){}
    public record Profile(String name,String mode,double step,int samples,String rawSha256,String inputSha256) {
        public String body() {
            try(var resource=Regfmc1SelectorNativeReference.class.getResourceAsStream("/regfmc-native-selector/"+name+".b64")) {
                if(resource==null)throw new IllegalStateException("Missing REGFMC1U selector reference");
                try(var zip=new GZIPInputStream(new ByteArrayInputStream(Base64.getMimeDecoder().decode(resource.readAllBytes())))) {
                    String body=new String(zip.readAllBytes(),StandardCharsets.UTF_8);
                    if(!WtgibffraNativeReference.digest(body).equals(rawSha256))throw new IllegalStateException("Changed REGFMC1U selector reference");return body;
                }
            }catch(IOException e){throw new UncheckedIOException(e);}
        }
        public Regfmc1Data parameters() {
            var base=Regfmc1NativeContractReference.parameters();double[] c=base.constants();
            if(!mode.equals("frequency-off")){c[11]=.7;c[32]=.35;}
            if(mode.equals("bounded")){int[] ix={3,4,15,16,17,18,19,20};double[] v={.002,-.002,.01,-.01,.005,-.005,.00005,-.00005};for(int i=0;i<ix.length;i++)c[ix[i]]=v[i];}
            return Regfmc1Data.nativePsse(base.sourceImpedance(),mode.equals("off")?new int[]{0,1,0}:mode.equals("frequency-off")?new int[]{0,0,1}:new int[]{1,1,1},c);
        }
    }
    public static List<Profile> profiles(){return List.of(
new Profile("off-coarse","off",0.0005,608,"01a29e8f44c2fec7931e6270c9f992adb1d7c1d54ceb071faf578500f70bc04e","96207d30e252329716320ec7a542ea19630708716185e9660e4ab31e391fb26a"),
new Profile("off-fine","off",0.00025,1208,"4fe0f15df5e06d950dbe551b69363685ef0befe64cfc0c5c0f84094410b9348b","96207d30e252329716320ec7a542ea19630708716185e9660e4ab31e391fb26a"),
new Profile("bounded-coarse","bounded",0.0005,608,"4612687252ed93c08b7b23c3466fdab47082a1c73a35a1e06d325937f0d0d45d","2a4fd2156515ca1ec4ecefad026f034ddf4ba11bb07de2b199ea8ded266154eb"),
new Profile("bounded-fine","bounded",0.00025,1208,"f3dca139e2fd47efc294adf1689435a15160f2bb6f53c1458c2cb5b48f4bba1b","2a4fd2156515ca1ec4ecefad026f034ddf4ba11bb07de2b199ea8ded266154eb"),
new Profile("frequency-off-coarse","frequency-off",0.0005,608,"c33f34b7ce66196baca1b188cbf02e36ac8b3dec637e68da72ead0f09594dbb0","bdb1e660b54a9a5704d8c0eec53fe78dd5a6348a421303dd1c86e5c66e37414f"),
new Profile("frequency-off-fine","frequency-off",0.00025,1208,"73f5c60b15d637a9d3eb410b05e0694283ee50dd23164b1cf30c421600d057b1","bdb1e660b54a9a5704d8c0eec53fe78dd5a6348a421303dd1c86e5c66e37414f"));}
}
