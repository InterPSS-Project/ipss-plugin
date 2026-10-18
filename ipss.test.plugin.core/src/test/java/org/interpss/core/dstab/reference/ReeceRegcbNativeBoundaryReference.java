package org.interpss.core.dstab.reference;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
/** Converged cold-paired native REGCB/REECE generator-bus fault, boundary evidence only. */
public final class ReeceRegcbNativeBoundaryReference {
    private ReeceRegcbNativeBoundaryReference(){}
    public record Profile(String name,double step,int samples,String rawSha256,String repeatRawSha256,String inputSha256) {
        public String body() {
            try(var resource=ReeceRegcbNativeBoundaryReference.class.getResourceAsStream("/reece-regcb-blocking/"+name+".b64")) {
                if(resource==null)throw new IllegalStateException("Missing native REGCB blocking reference");
                try(var zip=new GZIPInputStream(new ByteArrayInputStream(Base64.getMimeDecoder().decode(resource.readAllBytes())))) {
                    String body=new String(zip.readAllBytes(),StandardCharsets.UTF_8);
                    if(!WtgibffraNativeReference.digest(body).equals(rawSha256))throw new IllegalStateException("Changed native REGCB blocking reference");return body;
                }
            }catch(IOException e){throw new UncheckedIOException(e);}
        }
    }
    public static List<Profile> profiles(){return List.of(
new Profile("coarse",0.0005,805,"046a59de0040c29eee23f384b71b0a749563f1798e3baa9da94d563ee4a17a31","046a59de0040c29eee23f384b71b0a749563f1798e3baa9da94d563ee4a17a31","b8c82e74011ea7553c52dfe8472108c1403a90e57c70dad8d810edc7239e36fc"),
new Profile("fine",0.00025,1605,"0a20ab5d7129f9b8b8894aba7a2fe05f88fb95de83f2d68a99a8c9bc6b18738a","0a2c8240d57cad4d484dab67d3879e357ae3101a8d8b5798066b176eabb1a8e1","b8c82e74011ea7553c52dfe8472108c1403a90e57c70dad8d810edc7239e36fc"));}
}
