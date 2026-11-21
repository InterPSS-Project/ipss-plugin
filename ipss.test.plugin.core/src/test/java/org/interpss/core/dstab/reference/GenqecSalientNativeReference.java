package org.interpss.core.dstab.reference;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Exact native generator inputs and complete raw histories, not complete-model parity. */
public final class GenqecSalientNativeReference {
    private GenqecSalientNativeReference() {}
    private static String resource(String name,boolean zipped,String digest){
        try(var stream=GenqecSalientNativeReference.class.getResourceAsStream("/genqec-salient-native/"+name+".b64")){
            if(stream==null)throw new IllegalStateException("Missing generator schema reference: "+name);
            byte[] bytes=Base64.getMimeDecoder().decode(stream.readAllBytes());
            if(zipped)try(var input=new GZIPInputStream(new ByteArrayInputStream(bytes))){bytes=input.readAllBytes();}
            String text=new String(bytes,StandardCharsets.UTF_8);
            if(!WtgibffraNativeReference.digest(text).equals(digest))throw new IllegalStateException("Changed generator schema reference: "+name);
            return text;
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    public record Profile(String name,double step,int samples,int selector,String csvSha256,String inputSha256){
        public double[] replayBounds(){return switch(name){
                case "genqec-0-coarse" -> new double[]{1.2866e-05,1e-14,0.0022381,0.0054462,0.00034388,0.016205,0.034019};
                case "genqec-0-fine" -> new double[]{6.1911e-06,1e-14,0.0011728,0.0031616,0.00020646,0.010074,0.019228};
                case "genqec-1-coarse" -> new double[]{1.2718e-05,1e-14,0.0022371,0.0054177,0.00021032,0.015913,0.033509};
                case "genqec-1-fine" -> new double[]{6.0639e-06,1e-14,0.0011717,0.0031393,0.0001581,0.0098715,0.018929};
                case "genqej-0-coarse" -> new double[]{1.0197e-05,1e-14,0.0022463,0.0054349,0.0011223,0.016324,0.034908};
                case "genqej-0-fine" -> new double[]{4.7322e-06,1e-14,0.0011782,0.0031685,0.00066879,0.010134,0.019736};
                case "genqej-1-coarse" -> new double[]{9.918e-06,1e-14,0.0022481,0.0053843,0.001772,0.015924,0.03506};
                case "genqej-1-fine" -> new double[]{4.6417e-06,1e-14,0.0011796,0.0031447,0.0010599,0.0099379,0.019886};
                default -> throw new IllegalArgumentException("Unknown salient-pole profile: "+name);
        };}
        public String body(){return resource(name+".csv",true,csvSha256);}
        public String input(){return resource(name+".dyr",false,inputSha256);}
        public String network(){return resource("network.raw",false,"986c6b3c8201ff0c4581a60e48af3438a14c692c12e4f8b966f4f2c710daa02b");}
    }
    public static List<Profile> profiles(){return List.of(
        new Profile("genqec-0-coarse",0.0005,605,0,"30f879e619954f1395cb464b81034be08896d68307d0c0a21088a05aadfa2f55","686172998f7f958a69402b4f03b5e8204262a6be404487ad27d8fdde90342117"),
        new Profile("genqec-0-fine",0.00025,1205,0,"efc693dd6d33079e11ba667dd3f5c1e3db83987ae77f2450100b7d2e487bb380","686172998f7f958a69402b4f03b5e8204262a6be404487ad27d8fdde90342117"),
        new Profile("genqec-1-coarse",0.0005,605,1,"4995f98b7bf848b87fd4943d8cb5857ec1ce192f8afdb3ae59839126889b4dd5","755ff76ca8c651f5c9584b29db4ce38499c29150bd26ce27b97a24336c75149e"),
        new Profile("genqec-1-fine",0.00025,1205,1,"7a66773729ae8232c74290dfa45a31db5fc9a7fb0cb4d415d8a42728e49ecb9b","755ff76ca8c651f5c9584b29db4ce38499c29150bd26ce27b97a24336c75149e"),
        new Profile("genqej-0-coarse",0.0005,605,0,"8ef352fe14579632d1c063318274ac733e370430736065b58efd04de4e8fad31","59f450ad3e862aec6b5af72d49175237f2a1a9936c9bf85b370918d8cd5d4004"),
        new Profile("genqej-0-fine",0.00025,1205,0,"7be05c3ae50ae67b1177b32ea7050976940c7fdd25663b45b675c630cb97571a","59f450ad3e862aec6b5af72d49175237f2a1a9936c9bf85b370918d8cd5d4004"),
        new Profile("genqej-1-coarse",0.0005,605,1,"24c37484fcc5ad75043638a67fe7ff7e57deda7cba274a605bbacf28d8cf3be1","a723c4b4da9b59afbd3f84f0afe7bae8d59b58266e2f62161d7a287c5abf36a1"),
        new Profile("genqej-1-fine",0.00025,1205,1,"8611047bdf1fa84c84bde19112964f6da9a9349564333ed5478f1c4a149177c8","a723c4b4da9b59afbd3f84f0afe7bae8d59b58266e2f62161d7a287c5abf36a1")
    );}
}
