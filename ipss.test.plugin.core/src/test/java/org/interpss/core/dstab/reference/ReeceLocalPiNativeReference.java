package org.interpss.core.dstab.reference;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import com.interpss.dstab.renewable.Reece1Data;
/** Native 36.7 REECE local-PI recorded-input profiles only, not a complete model. */
public final class ReeceLocalPiNativeReference {
    private ReeceLocalPiNativeReference() {}
    public record Profile(String name,double step,int samples,int checkpoints,int postRow,
            double initialP,double initialQ,double initialV,double[] constants,String rawSha256,String inputsSha256,String statesSha256) {
        public Profile {constants=constants.clone();}
        @Override public double[] constants(){return constants.clone();}
        public Reece1Data parameters(){return Reece1Data.fromNativeParameters(new int[]{0,0,2,0,1,0,1,0},constants);}
        public String inputs(){return read(name+"-inputs",inputsSha256);}
        public String states(){return read(name+"-states",statesSha256);}
    }
    private static String read(String name,String expected) {
        try(var resource=ReeceLocalPiNativeReference.class.getResourceAsStream("/reece-local-pi/"+name+".b64")) {
            if(resource==null)throw new IllegalStateException("Missing REECE partial reference");
            try(var zip=new GZIPInputStream(new ByteArrayInputStream(Base64.getMimeDecoder().decode(resource.readAllBytes())))) {
                String body=new String(zip.readAllBytes(),StandardCharsets.UTF_8);
                if(!WtgibffraNativeReference.digest(body).equals(expected))throw new IllegalStateException("Changed REECE partial reference");
                return body;
            }
        }catch(IOException ex){throw new UncheckedIOException(ex);}
    }
    public static List<Profile> profiles(){return List.of(
new Profile("native-coarse",0.0005,2002,210,201,0.240000009537,0.0474445894361,1.00999999046,new double[]{0.7,1.3,0.02,-0.05,0.05,0,1,-1,0,0,0,0,0.02,1,-1,1.1,0.9,2,5,0,0,0,0.02,99,-99,1,0,1.3,0.02,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.2,2.0,0.0,2.0,10.0},"409bd2662bcb31137c77fc8d81ffeea1bac5f2d6bcb81ca8abe2cfd592d4ca0c","5410c10a472d4c4004887009855cbc86cd69221a3dd7ccaea189ae1df770fbfe","7751813bedc6c6de3f18359bdea123ce4106dbc3312bcd06a60814ac69f51996"),
new Profile("native-fine",0.00025,4003,210,401,0.240000009537,0.0474445894361,1.00999999046,new double[]{0.7,1.3,0.02,-0.05,0.05,0,1,-1,0,0,0,0,0.02,1,-1,1.1,0.9,2,5,0,0,0,0.02,99,-99,1,0,1.3,0.02,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.2,2.0,0.0,2.0,10.0},"fbea27fb3e22950d5bfaab658e5dd4df07a3bfea8737defe313b5bba1a15bcfd","6e422132eb014226b8b804e1876f97fc46c2cd19fc6805a5e85a551c96c06a40","ebaaa4be78bb9eb692becb15744ee2633bcc5217c22d8794fa5b2875a0a2bddc"));}
}
