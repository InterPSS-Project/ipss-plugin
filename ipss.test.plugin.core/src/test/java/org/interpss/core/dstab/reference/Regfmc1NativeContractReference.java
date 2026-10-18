package org.interpss.core.dstab.reference;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.renewable.Regfmc1Data;
/** Exact native REGFMC1U contract references, not an implemented dynamic runtime. */
public final class Regfmc1NativeContractReference {
    private Regfmc1NativeContractReference(){}
    public record Profile(String name,double step,int samples,String rawSha256,String inputSha256) {
        public String body() {
            try(var resource=Regfmc1NativeContractReference.class.getResourceAsStream("/regfmc-native-contract/"+name+".b64")) {
                if(resource==null)throw new IllegalStateException("Missing native REGFMC1U reference");
                try(var zip=new GZIPInputStream(new ByteArrayInputStream(Base64.getMimeDecoder().decode(resource.readAllBytes())))) {
                    String body=new String(zip.readAllBytes(),StandardCharsets.UTF_8);
                    if(!WtgibffraNativeReference.digest(body).equals(rawSha256))throw new IllegalStateException("Changed native REGFMC1U reference");return body;
                }
            }catch(IOException e){throw new UncheckedIOException(e);}
        }
    }
    public static Regfmc1Data parameters(){return Regfmc1Data.nativePsse(new Complex(.015,.23),new int[]{1,0,1},
            new double[]{.4,.35,3,.2,-.2,.025,.35,.02,.01,.02,1.5,0,100,3,.05,1.35,-1.35,1.5,-1.5,.2,-.35,.025,.3,.8,-.8,.6,-.6,.9,0,2,.15,-.15,.9,1.2});}
    public static List<Profile> profiles(){return List.of(
new Profile("flat-coarse",0.0005,605,"ad5c1cfe8c5b523a51e5dd5579956f5a527396701e041f895acb4b2812ce4f8f","5ed4dbcdead719a5ac30c1e0e7e7592a292347e37941d69849f42f4f219944b0"),
new Profile("flat-fine",0.00025,1205,"7589765615d0b4060d602e389d89ce234ea16bbc95db8a5e0a75236a5b1e976e","5ed4dbcdead719a5ac30c1e0e7e7592a292347e37941d69849f42f4f219944b0"),
new Profile("fault-coarse",0.0005,605,"77bf897569a5275fa6e4074b47a9ff45ead2505b9e87c75f8d85570093a36c63","5ed4dbcdead719a5ac30c1e0e7e7592a292347e37941d69849f42f4f219944b0"),
new Profile("fault-fine",0.00025,1205,"f482550a7a659d5568269f02118ba830b74b26b86b0bbc5f0f3d12210af45b7e","5ed4dbcdead719a5ac30c1e0e7e7592a292347e37941d69849f42f4f219944b0"),
new Profile("limit-coarse",0.0005,605,"9f222557502a741b55e4a5f263805632a2e74c4dcfcbf2e730947330a36391b9","5ed4dbcdead719a5ac30c1e0e7e7592a292347e37941d69849f42f4f219944b0"),
new Profile("limit-fine",0.00025,1205,"c0f9c04558a72bbe7eecbbed701f70a5f8e04020d2aa3119620780b5d3d02a3b","5ed4dbcdead719a5ac30c1e0e7e7592a292347e37941d69849f42f4f219944b0"));}
}
