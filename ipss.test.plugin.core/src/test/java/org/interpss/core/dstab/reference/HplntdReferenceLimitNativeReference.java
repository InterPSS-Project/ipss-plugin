package org.interpss.core.dstab.reference;

import java.util.*;
import org.interpss.core.dstab.reference.HplntdAggregationNativeReference.Profile;

/** Frozen direct-reference evidence; native voltage bypass is diagnostic only. */
public final class HplntdReferenceLimitNativeReference {
    private HplntdReferenceLimitNativeReference() {}
    public static List<Profile> profiles(){return List.of(
        new Profile("reference-limit-v-outside-up-coarse",0.0005,405,"63adcc611b7868b981cb575e8e7ef2e477cfac6f06c4e5f4bfd0df27f9b282ae","231ed9b6eefa009acac4c888730ac5317cc63307313c88cd0317632e668610a2"),
        new Profile("reference-limit-v-outside-up-fine",0.00025,805,"06bdad715eb0be99454ce23cf8de5d9b1a4c83670ecd981662beaa10cb60e8cc","231ed9b6eefa009acac4c888730ac5317cc63307313c88cd0317632e668610a2"),
        new Profile("reference-limit-v-outside-down-coarse",0.0005,405,"ea4af0a383b15fd913398e33b1c88d4fa84627f1c627688fa66da03751c95459","231ed9b6eefa009acac4c888730ac5317cc63307313c88cd0317632e668610a2"),
        new Profile("reference-limit-v-outside-down-fine",0.00025,805,"db61340f451fdb680f7343729612e4dc1235f2ebf06e3961e2b252ee14b2798a","231ed9b6eefa009acac4c888730ac5317cc63307313c88cd0317632e668610a2"),
        new Profile("reference-limit-v-inside-up-coarse",0.0005,405,"4fa72f3a1256fd2a498ba5f208194c8294cfd65357ac22a101856a66a456d052","aed788d7f8ae2eeae62b874c6d2975e2260886dc38ba460809e256b5438d122b"),
        new Profile("reference-limit-v-inside-up-fine",0.00025,805,"be18c77b4b92730bc3857205c48ec54829ff291cf28e4c97eeb5661e9db31765","aed788d7f8ae2eeae62b874c6d2975e2260886dc38ba460809e256b5438d122b"),
        new Profile("reference-limit-v-inside-down-coarse",0.0005,405,"b219760fea28bbea6c7e1f15de080861fdb0aa3b766f54544db8ade7ee01c88e","aed788d7f8ae2eeae62b874c6d2975e2260886dc38ba460809e256b5438d122b"),
        new Profile("reference-limit-v-inside-down-fine",0.00025,805,"86b6ebaa99cb4f6516280830ee1b4c35e77d7f0e958c3beb3774047aa375bc84","aed788d7f8ae2eeae62b874c6d2975e2260886dc38ba460809e256b5438d122b"),
        new Profile("reference-limit-v-inside-large-coarse",0.0005,405,"d43d716c5cb7ca1f47750ddf876cab99437c8f95862fc0d9ad4617eb2bfebb64","aed788d7f8ae2eeae62b874c6d2975e2260886dc38ba460809e256b5438d122b"),
        new Profile("reference-limit-v-inside-large-fine",0.00025,805,"40e5c1f7e8d7c5904214c311c2deac48c7a9270399666fb38a9590bfb347fa3e","aed788d7f8ae2eeae62b874c6d2975e2260886dc38ba460809e256b5438d122b"),
        new Profile("reference-limit-v-integral-narrow-coarse",0.0005,405,"43534c760b7c68e1316f77651457c0eb7b909d0b0806df49e637acc419ed43cc","ac8ef7eeeb4a652cd9551aaca5f1a34f1bd0a6d2061f03d8e7b5a20c41fad94c"),
        new Profile("reference-limit-v-integral-narrow-fine",0.00025,805,"fd6f8901c8bc2832f1179f1afb13114c680f8f52672c3897e5cb4dc1b8baad4c","ac8ef7eeeb4a652cd9551aaca5f1a34f1bd0a6d2061f03d8e7b5a20c41fad94c"),
        new Profile("reference-limit-q-upper-coarse",0.0005,405,"33d23cf9697dcde466f1ebc69fb4565137dd13bbf37f58519755f6d1bf2c9a46","1a6bd67dbd0c25557697d469a0e7a738536f0a88d68034592eb3bae16063d77c"),
        new Profile("reference-limit-q-upper-fine",0.00025,805,"b9383c4a26ea481ef3db55bbe55c597d023ad460a6993495cc5b4b282743c8d6","1a6bd67dbd0c25557697d469a0e7a738536f0a88d68034592eb3bae16063d77c"),
        new Profile("reference-limit-pf-lower-coarse",0.0005,405,"c5c44d4009825cc65a723cb672594b57194757ed34cf60f2dcb631df28d6c8bb","2ea747657fb9ef594dfb918de356507af9068bbf4c1d5ccaaa7fdad56b0a3a78"),
        new Profile("reference-limit-pf-lower-fine",0.00025,805,"26138fd00bf9d90908c644034ef1947e9011a3cedcb57cc4ec03248a01388235","2ea747657fb9ef594dfb918de356507af9068bbf4c1d5ccaaa7fdad56b0a3a78")
    );}
    public static List<Profile> acceptedProfiles(){return profiles().stream().filter(p->p.name().contains("q-upper")||p.name().contains("pf-lower")).toList();}
    public static double[] replayBounds(String name){return switch(name){
        case "reference-limit-pf-lower-coarse" -> new double[]{1e-12,2.5691e-07,5.6491e-08,2.0683e-07,4.6771e-08,4.7702e-08,8.1313e-08,1e-12,1e-12,1e-12,4.6771e-08,1e-12,2.1452e-07,8.1313e-08,2.1452e-07,8.1314e-08,2.1452e-07};
        case "reference-limit-pf-lower-fine" -> new double[]{1e-12,2.5691e-07,3.9601e-06,2.685e-06,4.6771e-08,4.7704e-08,8.1363e-08,1e-12,1e-12,1e-12,4.6771e-08,1e-12,2.6718e-06,8.1363e-08,2.6718e-06,8.1363e-08,2.6718e-06};
        case "reference-limit-q-upper-coarse" -> new double[]{1e-12,2.5691e-07,3.1006e-07,3.0341e-07,4.6771e-08,4.7702e-08,8.1313e-08,1e-12,1e-12,1e-12,4.6771e-08,2.5691e-07,3.3926e-07,8.1313e-08,3.3926e-07,8.1314e-08,3.7205e-07};
        case "reference-limit-q-upper-fine" -> new double[]{1e-12,2.5691e-07,3.2703e-07,2.4968e-07,4.6771e-08,4.7704e-08,8.1363e-08,1e-12,1e-12,1e-12,4.6771e-08,2.5691e-07,2.8646e-07,8.1363e-08,2.8646e-07,8.1363e-08,3.1924e-07};
        default -> throw new IllegalArgumentException("Not a Q/PF acceptance profile "+name);
    };}
    public static double[] coupledBounds(String name){return switch(name){
        case "reference-limit-pf-lower-coarse" -> new double[]{1.4635e-06,9.5788e-07,1.967e-07,1.2012e-06,7.245e-07,4.7841e-06,1.8818e-06,2.5896e-06,1e-12,5.0317e-06,0.00011208,0.00042713,1.7812e-06,3.2783e-08,1.7485e-06,9.5788e-07,1e-12,1e-12,1.7812e-06,1e-12,0.00043361,1.4818e-06,0.00069014,1.8818e-06,0.00034289};
        case "reference-limit-pf-lower-fine" -> new double[]{1.4635e-06,9.5788e-07,1.967e-07,1.2012e-06,7.245e-07,4.7841e-06,1.8818e-06,2.5896e-06,1e-12,4.9415e-06,5.8488e-05,0.00021763,1.7812e-06,3.2783e-08,1.7485e-06,9.5788e-07,1e-12,1e-12,1.7812e-06,1e-12,0.00021926,1.4818e-06,0.00035038,1.8818e-06,0.00017302};
        case "reference-limit-q-upper-coarse" -> new double[]{1.4635e-06,9.5788e-07,1.967e-07,1.2012e-06,7.245e-07,4.7841e-06,1.8818e-06,2.5896e-06,1e-12,5.0317e-06,0.00012402,0.00047728,1.7812e-06,3.2783e-08,1.7485e-06,9.5788e-07,1e-12,1e-12,1.7812e-06,0.013618,0.00039071,1.4818e-06,0.003455,1.8818e-06,0.0017254};
        case "reference-limit-q-upper-fine" -> new double[]{1.4635e-06,9.5788e-07,1.967e-07,1.2012e-06,7.245e-07,4.7841e-06,1.8818e-06,2.5896e-06,1e-12,4.9415e-06,3.4686e-05,0.00012351,1.7812e-06,3.2783e-08,1.7485e-06,9.5788e-07,1e-12,1e-12,1.7812e-06,0.0068112,0.000103683,1.4818e-06,0.0017316,1.8818e-06,0.00086362};
        default -> throw new IllegalArgumentException("Not a Q/PF acceptance profile "+name);
    };}
}
