package org.interpss.core.dstab.reference;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Cold-paired exact REPCC coupled flat/fault references; raw event sides retained. */
public final class RepccRuntimeNativeReference {
    private RepccRuntimeNativeReference() {}
    public record Profile(String name,double step,int samples,String csvSha256,String inputSha256){
        private byte[] resource(String suffix)throws IOException{
            try(var stream=RepccRuntimeNativeReference.class.getResourceAsStream("/repcc-runtime-native/"+name+"."+suffix+".b64")){
                if(stream==null)throw new IllegalStateException("Missing REPCC runtime reference "+name);
                return Base64.getMimeDecoder().decode(stream.readAllBytes());
            }
        }
        public String body(){
            try(var stream=new GZIPInputStream(new ByteArrayInputStream(resource("csv")))){
                String text=new String(stream.readAllBytes(),StandardCharsets.UTF_8);
                if(!WtgibffraNativeReference.digest(text).equals(csvSha256))throw new IllegalStateException("Changed REPCC trace");return text;
            }catch(IOException e){throw new UncheckedIOException(e);}
        }
        public String input(){
            try{
                String text=new String(resource("dyr"),StandardCharsets.UTF_8);
                if(!WtgibffraNativeReference.digest(text).equals(inputSha256))throw new IllegalStateException("Changed REPCC input");return text;
            }catch(IOException e){throw new UncheckedIOException(e);}
        }
    }
    public static List<Profile> profiles(){return List.of(
        new Profile("flat-coarse",0.0005,605,"f5dcf1eddfa89550ffd1feb2004849880486582bba04fa13b54a80ccacbe7572","de1557af5d85f9fda00f1ce1b3bf7478ffde7eeba09c1e24aca9ee2162916f70"),
        new Profile("flat-fine",0.00025,1205,"970937bc440e077b9514a4515fa14e9dc89dd74bad40d003b4c75807efe7700d","de1557af5d85f9fda00f1ce1b3bf7478ffde7eeba09c1e24aca9ee2162916f70"),
        new Profile("fault-coarse",0.0005,605,"af647b9e3541934ed3591364b4ac214d2d28bce109684a16a13c7d716c4bb47b","de1557af5d85f9fda00f1ce1b3bf7478ffde7eeba09c1e24aca9ee2162916f70"),
        new Profile("fault-fine",0.00025,1205,"7e37ae21e6b2ad4d9228fd0251212e7576818efb40aae9519d02e71bca724106","de1557af5d85f9fda00f1ce1b3bf7478ffde7eeba09c1e24aca9ee2162916f70")
    );}
}
