package org.interpss.core.dstab.reference;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.interpss.dstab.renewable.HplntdData;

/** Frozen physical-Mvar switching inputs, raw outputs and scoped coupled bounds. */
public final class HplntdSwitchingNativeReference {
    private HplntdSwitchingNativeReference() {}
    public record Profile(String name,double step,int samples,int statusSamples,String csvSha256,String inputSha256,String statusSha256){
        private HplntdAggregationNativeReference.Profile plant(){return new HplntdAggregationNativeReference.Profile(name,step,samples,csvSha256,inputSha256);}
        public HplntdData parameters(){return plant().plant();}
        public String input(){return plant().input();}
        public String body(){return plant().body();}
        public boolean zeroDelay(){return name.contains("zero-delay");}
        public boolean coupledAccepted(){return name.endsWith("coarse")&&name.contains("-fast-")||name.contains("disabled")||name.contains("invalid-thresholds")||name.contains("missing-branch");}
        public double[] coupledBounds(){return switch(name){
            case "switching-positive-fast-coarse" -> new double[]{6.476e-06,4.756e-06,5.935e-07,7.11e-06,9.842e-07,5.309e-06,2.308e-06,3.438e-06,1e-12,9.628e-06,5.277e-06,5.554e-06,2.218e-06,1.56e-09,1.939e-06,1.673e-06,1e-12,6.685e-08,1.814e-06,5.225e-06,5.508e-06,1.856e-06,9.88e-06,2.118e-06,2.984e-06,7.424e-08};
            case "switching-negative-fast-coarse" -> new double[]{3.419e-06,2.749e-06,2.074e-07,4.533e-06,1.656e-06,5.07e-06,3.632e-06,2.85e-06,1e-12,5.461e-06,5.646e-06,6.353e-06,2.218e-06,1.04e-09,2.045e-06,1.281e-06,1e-12,6.432e-08,1.814e-06,5.188e-06,6.352e-06,1.539e-06,1.425e-05,2.212e-06,4.894e-06,7.098e-08};
            case "switching-disabled-coarse" -> new double[]{1.49e-06,1.008e-06,2.003e-07,1.223e-06,7.71e-07,4.94e-06,1.983e-06,2.984e-06,1e-12,5.317e-06,5.277e-06,5.277e-06,1.814e-06,1e-12,1.814e-06,8.741e-07,1e-12,3.617e-10,1.814e-06,5.225e-06,5.277e-06,1.542e-06,9.88e-06,1.983e-06,2.984e-06,6.64e-10};
            case "switching-disabled-fine" -> new double[]{1.49e-06,1.008e-06,2.003e-07,1.223e-06,7.71e-07,4.94e-06,1.983e-06,2.984e-06,1e-12,5.225e-06,5.277e-06,5.277e-06,1.814e-06,1e-12,1.814e-06,8.741e-07,1e-12,3.611e-10,1.814e-06,5.225e-06,5.277e-06,1.542e-06,9.88e-06,1.983e-06,2.984e-06,6.64e-10};
            case "switching-invalid-thresholds-coarse" -> new double[]{1.49e-06,1.008e-06,2.003e-07,1.223e-06,7.71e-07,4.94e-06,1.983e-06,2.984e-06,1e-12,5.317e-06,5.277e-06,5.277e-06,1.814e-06,1e-12,1.814e-06,8.741e-07,1e-12,3.617e-10,1.814e-06,5.225e-06,5.277e-06,1.542e-06,9.88e-06,1.983e-06,2.984e-06,6.64e-10};
            case "switching-invalid-thresholds-fine" -> new double[]{1.49e-06,1.008e-06,2.003e-07,1.223e-06,7.71e-07,4.94e-06,1.983e-06,2.984e-06,1e-12,5.225e-06,5.277e-06,5.277e-06,1.814e-06,1e-12,1.814e-06,8.741e-07,1e-12,3.611e-10,1.814e-06,5.225e-06,5.277e-06,1.542e-06,9.88e-06,1.983e-06,2.984e-06,6.64e-10};
            case "switching-missing-branch-coarse" -> new double[]{1.49e-06,1.008e-06,2.003e-07,1.223e-06,7.71e-07,4.94e-06,1.983e-06,2.984e-06,1e-12,5.317e-06,5.277e-06,5.277e-06,1.814e-06,1e-12,1.814e-06,8.741e-07,1e-12,3.617e-10,1.814e-06,5.225e-06,5.277e-06,1.542e-06,9.88e-06,1.983e-06,2.984e-06,6.64e-10};
            case "switching-missing-branch-fine" -> new double[]{1.49e-06,1.008e-06,2.003e-07,1.223e-06,7.71e-07,4.94e-06,1.983e-06,2.984e-06,1e-12,5.225e-06,5.277e-06,5.277e-06,1.814e-06,1e-12,1.814e-06,8.741e-07,1e-12,3.611e-10,1.814e-06,5.225e-06,5.277e-06,1.542e-06,9.88e-06,1.983e-06,2.984e-06,6.64e-10};
            default -> throw new IllegalArgumentException("Unaccepted coupled switching profile: "+name);
        };}
        /** Exact frozen network input; raw history and line endings are preserved. */
        public String network(){
            boolean negative=name.contains("negative");String file="/hplntd-foundation-native/switching-"+(negative?"negative":"positive")+".raw.b64";
            try(var stream=HplntdSwitchingNativeReference.class.getResourceAsStream(file)){
                if(stream==null)throw new IllegalStateException("Missing hybrid switching network");
                String text=new String(Base64.getMimeDecoder().decode(stream.readAllBytes()),StandardCharsets.UTF_8);
                if(!WtgibffraNativeReference.digest(text).equals(negative?"556b4b9c6e0ebe979729e8d8029df5db8e6b20f496b2b1ddfdacfff5787dbc24":"dc251adc48a2942de91ee74a118ac296afa7858f3227b431e1d9b8726948b0c1"))throw new IllegalStateException("Changed hybrid switching network");return text;
            }catch(IOException e){throw new UncheckedIOException(e);}
        }
        public String status(){
            try(var resource=HplntdSwitchingNativeReference.class.getResourceAsStream("/hplntd-foundation-native/"+name+".status.b64")){
                if(resource==null)throw new IllegalStateException("Missing hybrid switch status reference");
                try(var stream=new GZIPInputStream(new ByteArrayInputStream(Base64.getMimeDecoder().decode(resource.readAllBytes())))){
                    String text=new String(stream.readAllBytes(),StandardCharsets.UTF_8);
                    if(!WtgibffraNativeReference.digest(text).equals(statusSha256))throw new IllegalStateException("Changed hybrid switch status reference");return text;
                }
            }catch(IOException e){throw new UncheckedIOException(e);}
        }
    }
    public static List<Profile> profiles(){return List.of(
        new Profile("switching-positive-fast-coarse",0.0005,402,201,"67d8a8e587642a93707b5af86e62420d0f25966ad413f9e816d9cebfd5948436","7e7cd75f3cc40973a83fe21aa61b6ac1110039940f1eedebd4a26a4bb7a2d450","56cc8f54fa30a201812fd1f9343ddecf5cac9a572d6a68271558459fce985d6a"),
        new Profile("switching-positive-fast-fine",0.00025,802,401,"14f8c35273bca8ae8ebb8a9b630f52e1640ab75d6ad5d2f958ca4274c7e7355c","7e7cd75f3cc40973a83fe21aa61b6ac1110039940f1eedebd4a26a4bb7a2d450","2cc081461e397218b143608ba634ca211f747ddf650919dfc31f408ac13765a5"),
        new Profile("switching-negative-fast-coarse",0.0005,402,201,"7f8224a567a7b257f92de69a2584489fe608c683b677378cbb54246b0bd7f163","304eeb19fbf356050f8096bb7d740ecd3b03de1e4b79d74070d387497bbf1826","789b3d35c51b7a99adcae8cf295ab5203ef4a569082bda8e9ba016bf69ddc7fb"),
        new Profile("switching-negative-fast-fine",0.00025,802,401,"316ea52621515c901112480546f87ab35b3d8498fcbfb28c6618c4c0e07903e8","304eeb19fbf356050f8096bb7d740ecd3b03de1e4b79d74070d387497bbf1826","7ca6468d8a67f63f30db306e0e5eb514538e608929bb435518b93badb37b112b"),
        new Profile("switching-positive-slow-coarse",0.0005,402,201,"d734cc9e8886c0bcb35a24ac852f50f5113a1b149d6f26287d74a24760cca53f","72203aa4a6d5d7c29d77f7f1fae360a566eba82d38ce565858918c59e8f0c638","516e300c670767ac7c1d8118a6a096ecc89f86779ffa74320403684da26e8141"),
        new Profile("switching-positive-slow-fine",0.00025,802,401,"5a5f86370a5d1fe90bb4679ac93027c07d77040e62cc21a26ca77898c34eecb0","72203aa4a6d5d7c29d77f7f1fae360a566eba82d38ce565858918c59e8f0c638","6cbd5d2c4620258a7e5d5cb5d5a938cf5da652f1bbe1cde7a1c933211da5a8c7"),
        new Profile("switching-negative-slow-coarse",0.0005,402,201,"082032dc1d37071aa1878c0db556d889249f0d60cc339f0b12f76c7ef06d13b1","d30694fddd9d9ae1621b35d6f82967984fa02767b7cc9310a0012d7afa749f96","d42a4794f5fc3318bff512a545a9b8c5263a6bb501961da4d15c858450cda3a2"),
        new Profile("switching-negative-slow-fine",0.00025,802,401,"fa9de5352452295235b48c532dd2c83f32a38c04a1f73084ffa155f168342d15","d30694fddd9d9ae1621b35d6f82967984fa02767b7cc9310a0012d7afa749f96","7e00810de921775d994fa3b588fe1f514b52f71d57d150382b193c35fc7ef3d4"),
        new Profile("switching-disabled-coarse",0.0005,402,201,"14074987d6a7352adc972adac0f01704d3b4043c582b3b112a3cac5238730dcf","5949fcb98a0da0d18517b87a02b08f887db5200de76b895f19a03d1ca1594a9b","abddf6b70fefa9aa9c34d867aadd4456647a64a145333b5cdf2ede4e8fbe232c"),
        new Profile("switching-disabled-fine",0.00025,802,401,"6285acf7e25b2fdd0123d5606a7abcb395e484cccecb6cb183c224487754dd6c","5949fcb98a0da0d18517b87a02b08f887db5200de76b895f19a03d1ca1594a9b","35266bc7a7bfe5d54090eee61fb3339292b6f94e41e6ed1ba27e29c32b57d032"),
        new Profile("switching-invalid-thresholds-coarse",0.0005,402,201,"14074987d6a7352adc972adac0f01704d3b4043c582b3b112a3cac5238730dcf","3f0c0885f34480d015e4475e3c78ba93713525094dbbae82e097ffbedcc3717f","abddf6b70fefa9aa9c34d867aadd4456647a64a145333b5cdf2ede4e8fbe232c"),
        new Profile("switching-invalid-thresholds-fine",0.00025,802,401,"6285acf7e25b2fdd0123d5606a7abcb395e484cccecb6cb183c224487754dd6c","3f0c0885f34480d015e4475e3c78ba93713525094dbbae82e097ffbedcc3717f","35266bc7a7bfe5d54090eee61fb3339292b6f94e41e6ed1ba27e29c32b57d032"),
        new Profile("switching-missing-branch-coarse",0.0005,402,201,"14074987d6a7352adc972adac0f01704d3b4043c582b3b112a3cac5238730dcf","94833d9fa6266d664a96c91e0d24b0d71929f95589d92542a722079c337a18e8","abddf6b70fefa9aa9c34d867aadd4456647a64a145333b5cdf2ede4e8fbe232c"),
        new Profile("switching-missing-branch-fine",0.00025,802,401,"6285acf7e25b2fdd0123d5606a7abcb395e484cccecb6cb183c224487754dd6c","94833d9fa6266d664a96c91e0d24b0d71929f95589d92542a722079c337a18e8","35266bc7a7bfe5d54090eee61fb3339292b6f94e41e6ed1ba27e29c32b57d032"),
        new Profile("switching-zero-delay-coarse",0.0005,403,201,"293f4abb465616188d2ed9ee27530798c403f98cde5106508c5a69ef6bc0e822","351ff25c6991870e4c874ec020b7eafa24871a26723184fc93d5effeea561898","3ac61dd0575cdfbf7f596400078563daeb30a445aa23b5bcbd4d110b2a2f943a"),
        new Profile("switching-zero-delay-fine",0.00025,803,401,"e83f4ab0e763614d956fbf30dfa60bfcefc56d9dea978cbb62332d4cf8e1d88a","351ff25c6991870e4c874ec020b7eafa24871a26723184fc93d5effeea561898","ef04e4474f2305750b517124d7c89b872f1c49deeffd3fe788e35dc671211dd0")
    );}
}
