package org.interpss.core.dstab.reference;

import java.util.*;
import org.interpss.core.dstab.reference.HplntdAggregationNativeReference.Profile;

/** Immutable disabled-control references; not zero-gain or full-model acceptance. */
public final class HplntdSelectorNativeReference {
    private HplntdSelectorNativeReference() {}
    public static List<Profile> profiles(){return List.of(
        new Profile("selector-p-disabled-coarse",0.0005,405,"9beee7754959206ae177d5b4a4d62b7e42ea1c296a12043b1a506aae8b9793d4","554aab847a50a027cea10a151b14c6d080f0652c956ea1340677b9917b53651e"),
        new Profile("selector-p-disabled-fine",0.00025,805,"af843f6bfbbc61dd572aef770d526d95375c04d6d5a5f8f11cadcc74fe6bfcd6","554aab847a50a027cea10a151b14c6d080f0652c956ea1340677b9917b53651e"),
        new Profile("selector-q-disabled-coarse",0.0005,405,"48e1cf7894338135edd077b193f8455077c5dfe338320527aca09dbe8ee1e030","575ff9ea043e36f8c369b59261e839402e37cd5fa0115887c3ea2e01630e1995"),
        new Profile("selector-q-disabled-fine",0.00025,805,"f2eed04390803f5e9b389f08e7338659fbb9ceb63b5c9cf961c30f0405019042","575ff9ea043e36f8c369b59261e839402e37cd5fa0115887c3ea2e01630e1995"),
        new Profile("selector-both-disabled-coarse",0.0005,405,"facc507622df8b445279a244cd415983cd8a368257f4958a3e23e0d07d92f093","2a568180a40ba51be681afb3428cc66d0d54602dbbf1beab9840baee0f4df130"),
        new Profile("selector-both-disabled-fine",0.00025,805,"2f6ade7117c4b27f895d9668662700ce0a300a4fe7dc6a86853a44f7f6add0c0","2a568180a40ba51be681afb3428cc66d0d54602dbbf1beab9840baee0f4df130"),
        new Profile("selector-p-disabled-vcomp-coarse",0.0005,405,"2f950857c9153b325d94ccbb72d37e8baae9eb11906eb42aaa01bc457dcf83e2","6cf5b69f29b54e86b613c5a22835ab1df5da12ead8d7cf2e0ce8587dbe571bf7"),
        new Profile("selector-p-disabled-vcomp-fine",0.00025,805,"d8f051c37312f42c00ff8171f5a36112e8b6a86cfd63ca17d37fabe614e194a0","6cf5b69f29b54e86b613c5a22835ab1df5da12ead8d7cf2e0ce8587dbe571bf7"),
        new Profile("selector-p-disabled-vdroop-coarse",0.0005,405,"59918e3432b3ba2c012b8ea44eef934b048c0900f098e18f296e31f49d50369b","15d72b79fc57a38fd4c83ddec0f099ac05907b4ea7c1d2040371f205a0a923e2"),
        new Profile("selector-p-disabled-vdroop-fine",0.00025,805,"dcb4ffe36101ac04bbb2a15f7705c11ebfbfa6086ad0cdf5cb497cc37eccab6c","15d72b79fc57a38fd4c83ddec0f099ac05907b4ea7c1d2040371f205a0a923e2"),
        new Profile("selector-p-disabled-pf-coarse",0.0005,405,"21aabea44ec51c0d79142a0f5153e9cdac336864804f8b51cc433a36c282a810","e3ed71873b6232e74293a3bc8ab64d6de6857af9e9f828661353da8927675d4f"),
        new Profile("selector-p-disabled-pf-fine",0.00025,805,"506457926a4d78607250550e2cf13dc3de5a058c17da2ac3149e5bb2c4140900","e3ed71873b6232e74293a3bc8ab64d6de6857af9e9f828661353da8927675d4f"),
        new Profile("selector-q-disabled-pf-coarse",0.0005,405,"48e1cf7894338135edd077b193f8455077c5dfe338320527aca09dbe8ee1e030","d4b9d16cd885d15a9e0be9e058948659bfd756ef7b784ce9c8904da42ad624c3"),
        new Profile("selector-q-disabled-pf-fine",0.00025,805,"f2eed04390803f5e9b389f08e7338659fbb9ceb63b5c9cf961c30f0405019042","d4b9d16cd885d15a9e0be9e058948659bfd756ef7b784ce9c8904da42ad624c3"),
        new Profile("selector-both-disabled-pf-coarse",0.0005,405,"facc507622df8b445279a244cd415983cd8a368257f4958a3e23e0d07d92f093","8497005e6b3c4b6b3a5bccbcc77997a4a26a19a2909fa5f98d49fe77a508350a"),
        new Profile("selector-both-disabled-pf-fine",0.00025,805,"44dc7f7bca7cd116fdb54974f0415f63512241f169f28e30246f488923ebc7b2","8497005e6b3c4b6b3a5bccbcc77997a4a26a19a2909fa5f98d49fe77a508350a")
    );}
    public static double[] replayBounds(String name){return switch(name){
        case "selector-both-disabled-coarse" -> new double[]{1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12};
        case "selector-both-disabled-fine" -> new double[]{1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12};
        case "selector-both-disabled-pf-coarse" -> new double[]{1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12};
        case "selector-both-disabled-pf-fine" -> new double[]{1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12};
        case "selector-p-disabled-coarse" -> new double[]{1e-12,2.5691e-07,7.3558e-08,2.1475e-07,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,2.5691e-07,2.2142e-07,1e-12,2.2142e-07,1e-12,2.2142e-07};
        case "selector-p-disabled-fine" -> new double[]{1e-12,2.5691e-07,2.3575e-06,1.8163e-06,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,2.5691e-07,1.818e-06,1e-12,1.818e-06,1e-12,1.818e-06};
        case "selector-p-disabled-pf-coarse" -> new double[]{1e-12,2.5691e-07,2.0919e-07,3.6303e-07,4.6771e-08,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,3.6192e-07,1e-12,3.6192e-07,1e-12,3.6192e-07};
        case "selector-p-disabled-pf-fine" -> new double[]{1e-12,2.5691e-07,2.0906e-07,3.6289e-07,4.6771e-08,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,3.6234e-07,1e-12,3.6234e-07,1e-12,3.6234e-07};
        case "selector-p-disabled-vcomp-coarse" -> new double[]{1.4248e-07,1e-12,1.4266e-07,2.4757e-07,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,2.4681e-07,1e-12,2.4681e-07,1e-12,2.4681e-07};
        case "selector-p-disabled-vcomp-fine" -> new double[]{1.4248e-07,1e-12,1.4257e-07,2.4747e-07,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,2.471e-07,1e-12,2.471e-07,1e-12,2.471e-07};
        case "selector-p-disabled-vdroop-coarse" -> new double[]{1e-12,1e-12,3.3756e-08,5.858e-08,1e-12,1e-12,1e-12,1e-12,5.0921e-09,1e-12,1e-12,1e-12,5.8402e-08,1e-12,5.8402e-08,1e-12,5.8402e-08};
        case "selector-p-disabled-vdroop-fine" -> new double[]{1e-12,1e-12,3.3735e-08,5.8558e-08,1e-12,1e-12,1e-12,1e-12,5.0921e-09,1e-12,1e-12,1e-12,5.8469e-08,1e-12,5.8469e-08,1e-12,5.8468e-08};
        case "selector-q-disabled-coarse" -> new double[]{1e-12,1e-12,1e-12,1e-12,1.7094e-06,5.2519e-08,1.7751e-06,4.6642e-07,1e-12,4.1335e-10,4.6771e-08,1e-12,1e-12,1.7751e-06,1e-12,1.7751e-06,1e-12};
        case "selector-q-disabled-fine" -> new double[]{1e-12,1e-12,1e-12,1e-12,5.2351e-07,4.7164e-08,2.1919e-07,8.8506e-07,1e-12,2.6578e-10,4.6771e-08,1e-12,1e-12,2.1919e-07,1e-12,2.1919e-07,1e-12};
        case "selector-q-disabled-pf-coarse" -> new double[]{1e-12,1e-12,1e-12,1e-12,1.7094e-06,5.2519e-08,1.7751e-06,4.6642e-07,1e-12,4.1335e-10,4.6771e-08,1e-12,1e-12,1.7751e-06,1e-12,1.7751e-06,1e-12};
        case "selector-q-disabled-pf-fine" -> new double[]{1e-12,1e-12,1e-12,1e-12,5.2351e-07,4.7164e-08,2.1919e-07,8.8506e-07,1e-12,2.6578e-10,4.6771e-08,1e-12,1e-12,2.1919e-07,1e-12,2.1919e-07,1e-12};
        default -> throw new IllegalArgumentException("Unknown selector profile "+name);
    };}
    public static double[] coupledBounds(String name){return switch(name){
        case "selector-both-disabled-coarse" -> new double[]{1.4635e-06,9.5788e-07,1.967e-07,1.2012e-06,7.245e-07,4.7841e-06,1.8818e-06,2.5896e-06,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,9.5788e-07,1e-12,1e-12,1e-12,1e-12,1e-12,1.4818e-06,9.5354e-06,1.8818e-06,2.5896e-06};
        case "selector-both-disabled-fine" -> new double[]{1.4635e-06,9.5788e-07,1.967e-07,1.2012e-06,7.245e-07,4.7841e-06,1.8818e-06,2.5896e-06,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,9.5788e-07,1e-12,1e-12,1e-12,1e-12,1e-12,1.4818e-06,9.5354e-06,1.8818e-06,2.5896e-06};
        case "selector-both-disabled-pf-coarse" -> new double[]{1.4635e-06,9.5788e-07,1.967e-07,1.2012e-06,7.245e-07,4.7841e-06,1.8818e-06,2.5896e-06,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,9.5788e-07,1e-12,1e-12,1e-12,1e-12,1e-12,1.4818e-06,9.5354e-06,1.8818e-06,2.5896e-06};
        case "selector-both-disabled-pf-fine" -> new double[]{1.4635e-06,9.5788e-07,1.967e-07,1.2012e-06,7.245e-07,4.7841e-06,1.8818e-06,2.5896e-06,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,1e-12,9.5788e-07,1e-12,1e-12,1e-12,1e-12,1e-12,1.4818e-06,9.5354e-06,1.8818e-06,2.5896e-06};
        case "selector-p-disabled-coarse" -> new double[]{1.4635e-06,9.5788e-07,1.967e-07,1.2012e-06,7.245e-07,4.7841e-06,1.8818e-06,2.5896e-06,1e-12,5.0317e-06,1.8655e-05,5.9905e-05,1e-12,1e-12,1e-12,9.5788e-07,1e-12,1e-12,1e-12,0.0024014,4.6052e-05,1.4818e-06,9.7168e-05,1.8818e-06,4.6406e-05};
        case "selector-p-disabled-fine" -> new double[]{1.4635e-06,9.5788e-07,1.967e-07,1.2012e-06,7.245e-07,4.7841e-06,1.8818e-06,2.5896e-06,1e-12,4.9415e-06,1.178e-05,3.2405e-05,1e-12,1e-12,1e-12,9.5788e-07,1e-12,1e-12,1e-12,0.0024014,2.5505e-05,1.4818e-06,5.3807e-05,1.8818e-06,2.4725e-05};
        case "selector-p-disabled-pf-coarse" -> new double[]{1.4635e-06,9.5788e-07,1.967e-07,1.2012e-06,7.245e-07,4.7841e-06,1.8818e-06,2.5896e-06,1e-12,5.0317e-06,4.9049e-06,4.9049e-06,1.7812e-06,1e-12,1e-12,9.5788e-07,1e-12,1e-12,1e-12,1e-12,4.9049e-06,1.4818e-06,9.5354e-06,1.8818e-06,2.5896e-06};
        case "selector-p-disabled-pf-fine" -> new double[]{1.4635e-06,9.5788e-07,1.967e-07,1.2012e-06,7.245e-07,4.7841e-06,1.8818e-06,2.5896e-06,1e-12,4.9415e-06,4.9049e-06,4.9049e-06,1.7812e-06,1e-12,1e-12,9.5788e-07,1e-12,1e-12,1e-12,1e-12,4.9049e-06,1.4818e-06,9.5354e-06,1.8818e-06,2.5896e-06};
        case "selector-p-disabled-vcomp-coarse" -> new double[]{1.4635e-06,9.5788e-07,1.967e-07,1.2012e-06,7.245e-07,4.7841e-06,1.8818e-06,2.5896e-06,5.614e-07,1e-12,4.9049e-06,4.9049e-06,1e-12,1e-12,1e-12,9.5788e-07,1e-12,1e-12,1e-12,1e-12,4.9049e-06,1.4818e-06,9.5354e-06,1.8818e-06,2.5896e-06};
        case "selector-p-disabled-vcomp-fine" -> new double[]{1.4635e-06,9.5788e-07,1.967e-07,1.2012e-06,7.245e-07,4.7841e-06,1.8818e-06,2.5896e-06,5.614e-07,1e-12,4.9049e-06,4.9049e-06,1e-12,1e-12,1e-12,9.5788e-07,1e-12,1e-12,1e-12,1e-12,4.9049e-06,1.4818e-06,9.5354e-06,1.8818e-06,2.5896e-06};
        case "selector-p-disabled-vdroop-coarse" -> new double[]{1.4635e-06,9.5788e-07,1.967e-07,1.2012e-06,7.245e-07,4.7841e-06,1.8818e-06,2.5896e-06,1e-12,1e-12,4.9049e-06,4.9049e-06,1e-12,1e-12,1e-12,9.5788e-07,1.01565e-07,1e-12,1e-12,1e-12,4.9049e-06,1.4818e-06,9.5354e-06,1.8818e-06,2.5896e-06};
        case "selector-p-disabled-vdroop-fine" -> new double[]{1.4635e-06,9.5788e-07,1.967e-07,1.2012e-06,7.245e-07,4.7841e-06,1.8818e-06,2.5896e-06,1e-12,1e-12,4.9049e-06,4.9049e-06,1e-12,1e-12,1e-12,9.5788e-07,9.9004e-08,1e-12,1e-12,1e-12,4.9049e-06,1.4818e-06,9.5354e-06,1.8818e-06,2.5896e-06};
        case "selector-q-disabled-coarse" -> new double[]{2.664e-05,2.3874e-05,2.6703e-06,3.3182e-05,0.00012208,4.7841e-06,0.00049497,2.5896e-06,1e-12,1e-12,1e-12,1e-12,0.00064149,5.6476e-06,0.0003536,2.1205e-05,1e-12,1.492e-06,0.0060175,1e-12,1e-12,0.00018371,9.5354e-06,0.00036633,2.5896e-06};
        case "selector-q-disabled-fine" -> new double[]{1.2689e-05,1.1478e-05,1.2785e-06,1.5993e-05,6.098e-05,4.7841e-06,0.00024747,2.5896e-06,1e-12,1e-12,1e-12,1e-12,0.00032059,2.8208e-06,0.00017785,1.01122e-05,1e-12,7.4716e-07,0.0060776,1e-12,1e-12,9.3251e-05,9.5354e-06,0.00018506,2.5896e-06};
        case "selector-q-disabled-pf-coarse" -> new double[]{2.664e-05,2.3874e-05,2.6703e-06,3.3182e-05,0.00012208,4.7841e-06,0.00049497,2.5896e-06,1e-12,1e-12,1e-12,1e-12,0.00064149,5.6476e-06,0.0003536,2.1205e-05,1e-12,1.492e-06,0.0060175,1e-12,1e-12,0.00018371,9.5354e-06,0.00036633,2.5896e-06};
        case "selector-q-disabled-pf-fine" -> new double[]{1.2689e-05,1.1478e-05,1.2785e-06,1.5993e-05,6.098e-05,4.7841e-06,0.00024747,2.5896e-06,1e-12,1e-12,1e-12,1e-12,0.00032059,2.8208e-06,0.00017785,1.01122e-05,1e-12,7.4716e-07,0.0060776,1e-12,1e-12,9.3251e-05,9.5354e-06,0.00018506,2.5896e-06};
        default -> throw new IllegalArgumentException("Unknown selector profile "+name);
    };}
}
