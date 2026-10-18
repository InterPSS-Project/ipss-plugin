package org.interpss.core.dstab.reference;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.renewable.Regfmb1Data;
/** Exact native REGFMB1U contract references, not an implemented dynamic runtime. */
public final class Regfmb1NativeContractReference {
    private Regfmb1NativeContractReference(){}
    public record Profile(String name,double step,int samples,String rawSha256,String inputSha256) {
        public String body() {
            try(var resource=Regfmb1NativeContractReference.class.getResourceAsStream("/regfmb-native-contract/"+name+".b64")) {
                if(resource==null)throw new IllegalStateException("Missing native REGFMB1U reference");
                try(var zip=new GZIPInputStream(new ByteArrayInputStream(Base64.getMimeDecoder().decode(resource.readAllBytes())))) {
                    String body=new String(zip.readAllBytes(),StandardCharsets.UTF_8);
                    if(!WtgibffraNativeReference.digest(body).equals(rawSha256))throw new IllegalStateException("Changed native REGFMB1U reference");return body;
                }
            }catch(IOException e){throw new UncheckedIOException(e);}
        }
    }
    public static Regfmb1Data parameters(){return Regfmb1Data.nativePsse(new Complex(.015,.23),new int[]{0,0,0,0,1,0},
            new double[]{.05,0,5.86,.01,.1,-.1,.265,2.65,.1,-.1,.02,3,.001,100,50,1.2,.9,2,2,.02,.02,.02,.02,1,.05});}
    public static List<Profile> profiles(){return List.of(
new Profile("flat-coarse",0.0005,605,"b81eeb8fa14bc875c185a6c95803c6015de9f9a90dd8fde36efcd43114bebd05","4202df0d64bd26c9146336674ab459c220911c540801646b74b765c20b6837f4"),
new Profile("flat-fine",0.00025,1205,"0f0576b9633f317caa1a1f5ab1a0a6d28d66bff00ae9488087d9f0c0213f2d69","4202df0d64bd26c9146336674ab459c220911c540801646b74b765c20b6837f4"),
new Profile("fault-coarse",0.0005,605,"ae81d7bde874d513ad30e36f48764a067f8aa42966ac09e6f35536950d094e12","4202df0d64bd26c9146336674ab459c220911c540801646b74b765c20b6837f4"),
new Profile("fault-fine",0.00025,1205,"6d768040b25750b07ad774f0da839a690071d57d86111456d7649198febf8c2e","4202df0d64bd26c9146336674ab459c220911c540801646b74b765c20b6837f4"));}
}
