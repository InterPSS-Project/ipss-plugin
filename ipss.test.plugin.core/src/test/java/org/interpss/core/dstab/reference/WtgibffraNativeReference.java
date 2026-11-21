package org.interpss.core.dstab.reference;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import com.interpss.dstab.renewable.WtgibffraData;

/** Native 36.7 kernel-only profiles. Full input tokens are losslessly encoded;
 * checkpoints preserve native float time and all four STATEs/seventeen VARs.
 * Negative-time initialization rows remain in the sealed raw artifact.
 * Not native automatic-recovery, DYR attachment or coupled acceptance. */
public final class WtgibffraNativeReference {
    private WtgibffraNativeReference() {}
    public record Transition(int row,double rawTime,int mode) {}
    public record Profile(String name,double step,int samples,int checkpoints,
            double[] constants,String rawSha256,String inputsSha256,String checkpointsSha256,List<Transition> transitions) {
        public Profile { constants=constants.clone();transitions=List.copyOf(transitions); }
        @Override public double[] constants(){return constants.clone();}
        public WtgibffraData parameters(){return WtgibffraData.fromNativeParameters(3,constants);}
        public String inputs(){return read(name+"-inputs",inputsSha256);}
        public String states(){return read(name+"-checkpoints",checkpointsSha256);}
    }
    private static String read(String name,String expected){
        try(var resource=WtgibffraNativeReference.class.getResourceAsStream("/wtgibffra-kernel/"+name+".b64")){
            if(resource==null)throw new IllegalStateException("Missing reference "+name);
            byte[] packed=Base64.getMimeDecoder().decode(resource.readAllBytes());
            try(var zip=new GZIPInputStream(new ByteArrayInputStream(packed))){
                String body=new String(zip.readAllBytes(),StandardCharsets.UTF_8);
                if(!digest(body).equals(expected))throw new IllegalStateException("Reference digest mismatch: "+name);
                return body;
            }
        }catch(IOException ex){throw new UncheckedIOException(ex);}
    }
    public static String digest(String text){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException ex){throw new AssertionError(ex);}
    }
    public static List<Profile> profiles(){return List.of(
new Profile("long-coarse",0.0005,5002,554,new double[]{0.001,0.1,0.2,0.3,0.4,0.5,0.6,0.1,0.1,0.1,0.1,0.1,0.1,0.05,0.05,0.05,0.05,0.05,0.05,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,1.2,1.2,1.2,1.2,1.2,1.2,0.02,0.2},"db98adbbcd06aecf6bb33e190aef6a6afb5bb275827227492dadbe55321e6858","cbdf036c50c8e505da515257f22cf480c9f9fcc3ad28bac8b101673f0414a00c","f77780b29645042bb20694f40f3ba5e132a76cf86ba3732049a9bbe7a63aa387",List.of(new Transition(0,0.0,-1),new Transition(121,0.0605000331998,1),new Transition(321,0.160499706864,2),new Transition(522,0.260998427868,3),new Transition(722,0.360997140408,4),new Transition(3122,1.560978055,-2),new Transition(3523,1.76146352291,-1))),
new Profile("long-fine",0.00025,10001,553,new double[]{0.001,0.1,0.2,0.3,0.4,0.5,0.6,0.1,0.1,0.1,0.1,0.1,0.1,0.05,0.05,0.05,0.05,0.05,0.05,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,1.2,1.2,1.2,1.2,1.2,1.2,0.02,0.2},"c92739f086b4b40b87a775fb856a7ba5c3460408a565726c4b3d4a5f8add172d","b436899b777fbf4e39bbd2c5ef8777f8b8a6fe5e2412663dd0863d3635992f51","88d2979a57169dc2e59c237cb8b9f0d26b9ddbb7f00443ac0f7bfee1dec3945c",List.of(new Transition(0,0.0,-1),new Transition(240,0.06000007689,1),new Transition(640,0.159998834133,2),new Transition(1041,0.260248154402,3),new Transition(1441,0.360252827406,4),new Transition(6241,1.56018257141,-2),new Transition(7042,1.76041805744,-1))),
new Profile("zero-band-dip",0.0005,5002,506,new double[]{0.001,0.1,0.2,0.3,0.4,0.5,0.6,0.1,0.1,0.1,0.1,0.1,0.1,0.05,0.0,0.05,0.05,0.05,0.05,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,1.2,1.2,1.2,1.2,1.2,1.2,0.02,0.2},"37c69050143f6df7a2a281999105287d46f62c836a74b6174c3802d0f9306ecc","c22c9cca907c0674f0a68808f3c8ecf2596e0785b3c47dd5a899c5dafd5ad60a","dfcd6edce0217d7aab14ae86b66fb3d697727dde3584b686ad2b96b20cc88541",List.of(new Transition(0,0.0,-1))),
new Profile("zero-peak",0.0005,5002,563,new double[]{0.001,0.1,0.2,0.3,0.4,0.5,0.6,0.1,0.1,0.1,0.1,0.1,0.1,0.05,0.05,0.05,0.05,0.05,0.05,0.1,0.1,0.1,0.1,0.1,0.1,0.0,0.0,0.0,0.0,0.0,0.0,0.1,0.1,0.1,0.1,0.1,0.1,1.2,1.2,1.2,1.2,1.2,1.2,0.02,0.2},"966ad4bbf243a476e44505f9a1e6a15eac3f181de13e688d80a6bebea5f111db","8f41576bbec8451c6cf796d0432ecd84b083b73f7814e2262d59189c9a3e9180","b599cda138c2dd59c3cbcc0750713f4da3c088632adc613a19b2e76d13e15b5f",List.of(new Transition(0,0.0,-1),new Transition(121,0.0605000331998,1),new Transition(321,0.160499706864,3),new Transition(521,0.260498434305,4),new Transition(2921,1.46048533916,-2),new Transition(3322,1.66097080708,-1),new Transition(3323,1.66147077084,1),new Transition(3523,1.76146352291,3),new Transition(3723,1.86145627499,4))),
new Profile("persistent",0.0005,5002,581,new double[]{0.001,0.1,0.2,0.3,0.4,0.5,0.6,0.1,0.1,0.1,0.1,0.1,0.1,0.05,0.05,0.05,0.05,0.05,0.05,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,1.2,1.2,1.2,1.2,1.2,1.2,0.02,0.2},"3d4654a5577c9a158651932290cf7cd3c605b01116cc5f987ffcb2d731eac05d","729cbba1218a789269efbe7f1be21b6f92c451879c9536aae9777f72b93282e9","0f1e8972957a5adf615b0b5f8f1f4353ae517e9292fe921cfcd5b6ffd1cab7ea",List.of(new Transition(0,0.0,-1),new Transition(121,0.0605000331998,1),new Transition(321,0.160499706864,2),new Transition(522,0.260998427868,3),new Transition(722,0.360997140408,4),new Transition(3122,1.560978055,-2),new Transition(3523,1.76146352291,-1),new Transition(3524,1.76196348667,1),new Transition(3724,1.86195623875,2),new Transition(3925,1.96244895458,3),new Transition(4125,2.06244182587,4))),
new Profile("below",0.0005,5002,506,new double[]{0.001,0.3,0.4,0.5,0.6,0.7,0.8,0.1,0.1,0.1,0.1,0.1,0.1,0.05,0.05,0.05,0.05,0.05,0.05,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.0,0.0,0.0,0.0,0.0,0.0,0.02,0.2},"37c69050143f6df7a2a281999105287d46f62c836a74b6174c3802d0f9306ecc","c22c9cca907c0674f0a68808f3c8ecf2596e0785b3c47dd5a899c5dafd5ad60a","dfcd6edce0217d7aab14ae86b66fb3d697727dde3584b686ad2b96b20cc88541",List.of(new Transition(0,0.0,-1))),
new Profile("invalid-band",0.0005,5002,506,new double[]{0.001,0.1,0.2,0.3,0.4,0.5,0.6,0.1,0.0,0.1,0.1,0.1,0.1,0.05,0.05,0.05,0.05,0.05,0.05,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.0,0.0,0.0,0.0,0.0,0.0,0.02,0.2},"37c69050143f6df7a2a281999105287d46f62c836a74b6174c3802d0f9306ecc","c22c9cca907c0674f0a68808f3c8ecf2596e0785b3c47dd5a899c5dafd5ad60a","dfcd6edce0217d7aab14ae86b66fb3d697727dde3584b686ad2b96b20cc88541",List.of(new Transition(0,0.0,-1))));}
}
