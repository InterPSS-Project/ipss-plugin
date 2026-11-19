package org.interpss.core.dstab.reference;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Full cold-paired valve-limit histories; forced-input replay is not coupled acceptance. */
public final class Tgov1LimitNativeReference {
    private Tgov1LimitNativeReference() {}
    private static String resource(String name,boolean zipped,String expected) {
        try(var stream=Tgov1LimitNativeReference.class.getResourceAsStream(name)) {
            if(stream==null)throw new IllegalStateException("Missing TGOV1 reference: "+name);
            byte[] bytes=Base64.getMimeDecoder().decode(stream.readAllBytes());
            if(zipped)try(var input=new GZIPInputStream(new ByteArrayInputStream(bytes))){bytes=input.readAllBytes();}
            String body=new String(bytes,StandardCharsets.UTF_8);
            if(!WtgibffraNativeReference.digest(body.replace("\r\n","\n").replace('\r','\n')).equals(expected))throw new IllegalStateException("Changed TGOV1 reference: "+name);
            return body;
        }catch(IOException exception){throw new UncheckedIOException(exception);}
    }
    public record Profile(String name,double step,int samples,double apply,double release,String csvSha256) {
        public boolean upper(){return name.startsWith("upper-");}
        public double increment(){return upper()?.1:-.1;}
        public String body(){return resource("/tgov1-limit-native/"+name+".csv.b64",true,csvSha256);}
    }
    public static List<Profile> profiles(){return List.of(
        new Profile("upper-coarse",0.0005,1005,0.050000011920928955,0.19999919831752777,"5fe875f67b2d0b8862940a1a6be264f91805f3e21ab480a0f25b93222abf80fd"),
        new Profile("upper-fine",0.00025,2005,0.05000005662441254,0.19999831914901733,"5bcde478a270a08dcf21b671461444e67aad7d8b239f591b74b3f8e2069147b2"),
        new Profile("lower-coarse",0.0005,1005,0.050000011920928955,0.19999919831752777,"02d839141641e8fed02750e3c0d575d8c51796c05d571915e4bbefa20723288d"),
        new Profile("lower-fine",0.00025,2005,0.05000005662441254,0.19999831914901733,"cdd35f1f70b8d93eed13adc81d0b18ceecc2c1bdaa0940de52dd5734180c51f3")
    );}
    public static String input(){return resource("/tgov1-limit-native/dynamic.dyr.b64",false,"5ee9b74bbaae06876bda2b032935cf221b34256435874718f7cac395d8be029f");}
    public static String network(){return resource("/exciter-input-assets/986c6b3c8201ff0c4581a60e48af3438a14c692c12e4f8b966f4f2c710daa02b.b64",false,"986c6b3c8201ff0c4581a60e48af3438a14c692c12e4f8b966f4f2c710daa02b");}
}
