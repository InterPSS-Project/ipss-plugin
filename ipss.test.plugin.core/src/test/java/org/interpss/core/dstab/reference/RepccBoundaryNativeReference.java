package org.interpss.core.dstab.reference;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import com.interpss.dstab.renewable.RepccData;

/** Direct physical branch-flow API snapshots, distinct from controller plot VARs. */
public final class RepccBoundaryNativeReference {
    private RepccBoundaryNativeReference() {}
    public record Profile(String name,String template,double step,int samples,String sha256) {
        public String body(){
            try(var stream=RepccBoundaryNativeReference.class.getResourceAsStream("/repcc-boundary-native/"+name+".csv.b64")){
                if(stream==null)throw new IllegalStateException("Missing REPCC boundary reference "+name);
                byte[] bytes=Base64.getMimeDecoder().decode(stream.readAllBytes());
                try(var gzip=new GZIPInputStream(new ByteArrayInputStream(bytes))){
                    String text=new String(gzip.readAllBytes(),StandardCharsets.UTF_8);
                    if(!WtgibffraNativeReference.digest(text).equals(sha256))throw new IllegalStateException("Changed REPCC branch reference "+name);
                    return text;
                }
            }catch(IOException e){throw new UncheckedIOException(e);}
        }
        public RepccData parameters(){return RepccShuntNativeReference.profiles().stream().filter(p->p.name().equals(template)).findFirst().orElseThrow().parameters();}
    }
    public static List<Profile> profiles(){return List.of(
        new Profile("negative-slow-coarse","negative-slow-ten-coarse",0.0005,601,"ce1ac12c6a8655474eedc0e71e8d966cbe87aff28e08f93da659dcdc88709c86"),
        new Profile("negative-slow-fine","negative-slow-ten-fine",0.00025,1201,"f0473e8f93dc47b13ca8a837ee016fb5b9eec7a853209ad8eedc356e58b562c7"),
        new Profile("positive-slow-coarse","positive-slow-ten-coarse",0.0005,601,"07947b0dc4b1e51e6b99c32d933b631dda81e730fad36dd6505359b5d72ddcc8"),
        new Profile("positive-slow-fine","positive-slow-ten-fine",0.00025,1201,"308f2dd27687cdd7fb3c9e03b039a61150a2ec5173617ae907281f12e38fc1b8"),
        new Profile("lockout-coarse","lockout-ten-coarse",0.0005,601,"754da62048600b39d57616c9267a6c4e095f55a40b4150797b4409e5a329a1c4"),
        new Profile("lockout-fine","lockout-ten-fine",0.00025,1201,"4e1a952d323408a7dd007d2b1d73335dbe69ebf1f4872410db80b2262db03b07")
    );}
}
