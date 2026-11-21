package org.interpss.core.dstab.reference;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import com.interpss.dstab.mach.model.Wt12a1bData;
import org.interpss.fadapter.psse.dyr.PsseDyrRecordReader;

/** Exact standard active-stall inputs; bounded replay is distinct from full coupled parity. */
public final class Wt12a1bNativeReference {
    private Wt12a1bNativeReference() {}
    private static String resource(String name,boolean zipped,String digest){
        try(var input=Wt12a1bNativeReference.class.getResourceAsStream("/wt12a1b-native/"+name+".b64")){
            if(input==null)throw new IllegalStateException("Missing active-stall resource: "+name);
            byte[] bytes=Base64.getMimeDecoder().decode(input.readAllBytes());
            if(zipped)try(var stream=new GZIPInputStream(new ByteArrayInputStream(bytes))){bytes=stream.readAllBytes();}
            String text=new String(bytes,StandardCharsets.UTF_8);
            if(!WtgibffraNativeReference.digest(text).equals(digest))throw new IllegalStateException("Changed active-stall resource: "+name);return text;
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    public record Profile(String name,double step,int samples,int statusSamples,String csvSha256,String inputSha256,String statusSha256){
        public String body(){return resource(name+".csv",true,csvSha256);}
        public String status(){return resource(name+".status",true,statusSha256);}
        public String input(){return resource(name+".dyr",false,inputSha256);}
        public String network(){return resource("network.raw",false,"ae85ebe7a4888b0a4345a15b8ad9257101a3ceece031b39d68203f74da8b257a");}
        public boolean acceptedReplay(){return !name.startsWith("direct-");}
        public Wt12a1bData data(){
            try{var r=PsseDyrRecordReader.read(new StringReader(input()),name).stream().filter(v->v.sourceModelName().equals("WT12A1B")).findFirst().orElseThrow();
                if(r.parameterCount()!=14)throw new IllegalStateException("Exact fourteen-CON active-stall input required");
                return new Wt12a1bData(0,r.doubleParameter(0),r.doubleParameter(1),r.doubleParameter(2),r.doubleParameter(3),r.doubleParameter(4),r.doubleParameter(5),r.doubleParameter(6),r.doubleParameter(7),r.doubleParameter(8),r.doubleParameter(9),r.doubleParameter(10),r.doubleParameter(11),r.doubleParameter(12),r.doubleParameter(13));
            }catch(IOException e){throw new UncheckedIOException(e);}
        }
        public double[] bounds(){return switch(name){
            case "flat-coarse" -> new double[]{1.317e-06,1.51e-05,5.723e-06};
            case "flat-fine" -> new double[]{2.671e-06,4.877e-05,5.539e-05};
            case "fault-coarse" -> new double[]{0.002909,0.0003277,0.0001675};
            case "fault-fine" -> new double[]{0.001489,0.0001658,8.547e-05};
            case "below-coarse" -> new double[]{0.002909,1.51e-05,5.723e-06};
            case "below-fine" -> new double[]{0.001489,4.877e-05,5.539e-05};
            case "rate-coarse" -> new double[]{0.002909,5.029e-05,2.719e-05};
            case "rate-fine" -> new double[]{0.001489,1.208e-05,3.441e-06};
            case "repeat-coarse" -> new double[]{0.002909,0.0004987,0.000315};
            case "repeat-fine" -> new double[]{0.001489,0.000251,0.000159};
            default -> throw new IllegalArgumentException("Zero-time callback diagnostic is not replay acceptance: "+name);
        };}
    }
    public static List<Profile> profiles(){return List.of(
        new Profile("flat-coarse",0.0005,4002,2001,"ada83b96cc88870422a42060a995b35fcb2b54a9860cf7e239c2b42f33fadbc4","f2f08dc3049b9ea32a9b4bb3f504ec042faddf154328b9388593b99b34534942","306a47d021fcf3aaedbb5e812e78073007d57411b59a166698b592890a5d483b"),
        new Profile("flat-fine",0.00025,8003,4001,"eb2351e43e39e9acad316c748d5fc0c540ba01d0327ae9380713d1a84bc47b79","f2f08dc3049b9ea32a9b4bb3f504ec042faddf154328b9388593b99b34534942","7609f8c9eab128da76f765cc27eac89a7a535cdf825a41f06b3181b2d8c7ec86"),
        new Profile("fault-coarse",0.0005,4002,2001,"e31f3437e53f1c9c792f376c5a7f642e485c551dd3f319790112b703fa840471","f2f08dc3049b9ea32a9b4bb3f504ec042faddf154328b9388593b99b34534942","59cca963de401a0c895d1ec045da9952b567fe3053f545605a9a3497ef104e2e"),
        new Profile("fault-fine",0.00025,8003,4001,"f3ff1d5ca717bc85bef2052cb5f4d104e3f2853e1ceba8e6bc379ee49ec8620f","f2f08dc3049b9ea32a9b4bb3f504ec042faddf154328b9388593b99b34534942","96ca4e51f6e64ef044902ba2739d2d79bbca516a6c131bc80b2d2a0e1df549f7"),
        new Profile("direct-coarse",0.0005,4002,2001,"c14a4f6382531d7f95024a09312ff24bd75ff2b36ee5d10014edc65d8f7edeb4","a42e01ffb99ff38922a2d19b09ae268e832ac9dcd8a66cd12fe61772fed5a668","5954e8cf2c3cfff3c3ca8cf2f6e7312be420c74191341f3a7c7ea737479079fb"),
        new Profile("direct-fine",0.00025,8003,4001,"410a6319057e3d9058528f96bd0c4a77a1efacacc967c8f06aadeaf7c23f5e58","a42e01ffb99ff38922a2d19b09ae268e832ac9dcd8a66cd12fe61772fed5a668","5cf56f84a6d9bb8a7a9d09a47de660bba1d1f2840cb7617675032d411a4fb8a5"),
        new Profile("below-coarse",0.0005,4002,2001,"7238317f7b76c9b27e5c3655426b81d26cb9144413812719206fe7f201c0f53e","104429b085078c815b7b80218f9ed9d4f0d93ee4bb6f6ec55b4ad5efa11c7e89","306a47d021fcf3aaedbb5e812e78073007d57411b59a166698b592890a5d483b"),
        new Profile("below-fine",0.00025,8003,4001,"099541346f9de3b3b7cb5d6631a03b6bf634ae92cc7b1e495b73ff0742a1e4bc","104429b085078c815b7b80218f9ed9d4f0d93ee4bb6f6ec55b4ad5efa11c7e89","7609f8c9eab128da76f765cc27eac89a7a535cdf825a41f06b3181b2d8c7ec86"),
        new Profile("rate-coarse",0.0005,4002,2001,"004e30dd414b66ba063ec29226505921884ff2c0c7b857560ddac6662e7d8889","049bac60a88588c14ce8fe4a7f5d589bc639b45b68246111d6083e1ff24439fc","59cca963de401a0c895d1ec045da9952b567fe3053f545605a9a3497ef104e2e"),
        new Profile("rate-fine",0.00025,8003,4001,"fc2bcb68bbdea8cd83d4a135561e733c3d87c2e6b3b57db4d63a7c0f224ee0b7","049bac60a88588c14ce8fe4a7f5d589bc639b45b68246111d6083e1ff24439fc","96ca4e51f6e64ef044902ba2739d2d79bbca516a6c131bc80b2d2a0e1df549f7"),
        new Profile("repeat-coarse",0.0005,4002,2001,"a0aa3d16abf19236b8658bc5273dd6fd34b2bf06cac03c8d6bbf95b6a01acfc7","f2f08dc3049b9ea32a9b4bb3f504ec042faddf154328b9388593b99b34534942","5b862452b93007bf919210713644c0591865db4fab953d5786649ac9cc05203e"),
        new Profile("repeat-fine",0.00025,8003,4001,"d4486649adb587ded67544935151b3a16f90a607d86455b0d28dc456709f20f0","f2f08dc3049b9ea32a9b4bb3f504ec042faddf154328b9388593b99b34534942","e7731186816bfbaf4a127ce92fcf47473f9df3c9b690b1a01b4d1d1622173f0b")
    );}
}
