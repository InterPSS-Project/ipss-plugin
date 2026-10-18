package org.interpss.core.dstab.reference;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.interpss.dstab.renewable.Regfmb1Data;
/** Cold-paired native selector/PLL discriminators, not complete coupled acceptance. */
public final class Regfmb1SelectorNativeReference {
    private Regfmb1SelectorNativeReference(){}
    public record Profile(String name,double step,int samples,boolean excitedFreeze,int[] icons,String rawSha256,String inputSha256) {
        public Profile {icons=icons.clone();}
        @Override public int[] icons(){return icons.clone();}
        public Regfmb1Data parameters(){var base=Regfmb1NativeContractReference.parameters();return Regfmb1Data.nativePsse(base.sourceImpedance(),icons,base.constants());}
        public String body() {
            try(var resource=Regfmb1SelectorNativeReference.class.getResourceAsStream("/regfmb-native-selectors/"+name+".b64")) {
                if(resource==null)throw new IllegalStateException("Missing native REGFMB1U selector reference");
                try(var zip=new GZIPInputStream(new ByteArrayInputStream(Base64.getMimeDecoder().decode(resource.readAllBytes())))) {
                    String body=new String(zip.readAllBytes(),StandardCharsets.UTF_8);
                    if(!WtgibffraNativeReference.digest(body).equals(rawSha256))throw new IllegalStateException("Changed native REGFMB1U selector reference");return body;
                }
            }catch(IOException e){throw new UncheckedIOException(e);}
        }
    }
    public static List<Profile> profiles(){return List.of(
new Profile("current-voltage-flat-coarse",0.0005,605,false,new int[]{0,1,1,0,1,0},"faae992c15b0c99263dc076b573e0704ece6730df9ecf22138eba0a0ded29863","548bd0f8a99bbcee28bcffa54f13bb83d2537eea8839a32047966398b2c2deb0"),
new Profile("current-voltage-fault-coarse",0.0005,605,false,new int[]{0,1,1,0,1,0},"46b15872b17ef329b71e9ecb9b54e576d466fc2dc36bd4566ef1f149c3abb995","548bd0f8a99bbcee28bcffa54f13bb83d2537eea8839a32047966398b2c2deb0"),
new Profile("current-voltage-fault-fine",0.00025,1205,false,new int[]{0,1,1,0,1,0},"afdcb0683fe342fbca9da47eb396b97be44c821efc5137fee28c458c30736273","548bd0f8a99bbcee28bcffa54f13bb83d2537eea8839a32047966398b2c2deb0"),
new Profile("power-voltage-flat-coarse",0.0005,605,false,new int[]{0,0,1,0,1,0},"a1b96cfe1bcc47b30625fbd07f5a6cf09e3ca750d281116c960813dff53a32c3","bb4a92b1384d6d659aa656eea965c28b952b016a7379bee1331b8e2a1e0d3b5c"),
new Profile("power-voltage-fault-coarse",0.0005,605,false,new int[]{0,0,1,0,1,0},"6bb750313c154533e3df30a9378db5e6cc569f689ff1cc3100b443501a0a50d5","bb4a92b1384d6d659aa656eea965c28b952b016a7379bee1331b8e2a1e0d3b5c"),
new Profile("power-voltage-fault-fine",0.00025,1205,false,new int[]{0,0,1,0,1,0},"fbc2b87733511486cd4d9cf39b49e88114d980768a68404c51b745e8c891618c","bb4a92b1384d6d659aa656eea965c28b952b016a7379bee1331b8e2a1e0d3b5c"),
new Profile("storage-flat-coarse",0.0005,605,false,new int[]{1,0,0,1,1,1},"ba3fcf4fd381d4fac17c1b50f700d4e1acea87dbcd19e0be8d7d39f3a40bb85f","6f841440432f5fa5fe77c954334480ab1e9421bb5f61cf0dfdaf4782e43a3d08"),
new Profile("storage-fault-coarse",0.0005,605,false,new int[]{1,0,0,1,1,1},"c63dee5c830f2f98b0c2a889a2727239c48880aff9e2de30f9ef255950a405db","6f841440432f5fa5fe77c954334480ab1e9421bb5f61cf0dfdaf4782e43a3d08"),
new Profile("storage-fault-fine",0.00025,1205,false,new int[]{1,0,0,1,1,1},"2fb4ad5a002ba199e6d5476e47dd72ce7224b842cefc8fb1d3d9d51d6c34d3cb","6f841440432f5fa5fe77c954334480ab1e9421bb5f61cf0dfdaf4782e43a3d08"),
new Profile("freeze-excited-fault-coarse",0.0005,606,true,new int[]{0,0,0,0,1,0},"3b6374eab113c0ded6d2edcf18205dcc94387a41d7e5042b7db06be144c09c32","4202df0d64bd26c9146336674ab459c220911c540801646b74b765c20b6837f4"),
new Profile("freeze-excited-fault-fine",0.00025,1206,true,new int[]{0,0,0,0,1,0},"b6dbbaf1ec08cf2679f38123188757444361d3ecdd480029fc4d5b41170338e4","4202df0d64bd26c9146336674ab459c220911c540801646b74b765c20b6837f4"));}
}
