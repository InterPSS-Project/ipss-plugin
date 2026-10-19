package org.interpss.core.dstab.reference;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.interpss.dstab.renewable.HplntdData;

/** Frozen physical-Mvar switching evidence, not imported physical-network acceptance. */
public final class HplntdSwitchingNativeReference {
    private HplntdSwitchingNativeReference() {}
    public record Profile(String name,double step,int samples,int statusSamples,String csvSha256,String inputSha256,String statusSha256){
        private HplntdAggregationNativeReference.Profile plant(){return new HplntdAggregationNativeReference.Profile(name,step,samples,csvSha256,inputSha256);}
        public HplntdData parameters(){return plant().plant();}
        public String input(){return plant().input();}
        public String body(){return plant().body();}
        public boolean zeroDelay(){return name.contains("zero-delay");}
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
