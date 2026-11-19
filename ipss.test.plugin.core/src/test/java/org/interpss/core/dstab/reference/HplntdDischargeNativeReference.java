package org.interpss.core.dstab.reference;

import java.util.List;

/** Exact source-contract counterexamples, not accepted coupled fault trajectories.
 * Shared resource decoding preserves the original model and wrapper, not an alias. */
public final class HplntdDischargeNativeReference {
    private HplntdDischargeNativeReference() {}
    public static List<HplntdSwitchingNativeReference.Profile> profiles(){return List.of(
        new HplntdSwitchingNativeReference.Profile("discharge-zero-coarse",0.0005,1202,601,"13559b63dc78cbfe90068e286612961d3f0c27e87a4cec079b099f412540f5c2","ac4649cef96f2d57d711820d3c34d396e9ed463e7185c95fa0f6c39d338a83a6","4c47eed4f466946d794e803c77a2c87ee77460fdb4076df5a1078e6f1f317921"),
        new HplntdSwitchingNativeReference.Profile("discharge-zero-fine",0.00025,2402,1201,"0404083fc19a1e65dd5122648b3fc04e30a5529cd24efd96915131761f18cc08","ac4649cef96f2d57d711820d3c34d396e9ed463e7185c95fa0f6c39d338a83a6","cf1d1110ead9e7963b7749cd1244636b9c0bcc1b9ade1a720370e8a36c73cc18"),
        new HplntdSwitchingNativeReference.Profile("discharge-short-coarse",0.0005,1202,601,"13559b63dc78cbfe90068e286612961d3f0c27e87a4cec079b099f412540f5c2","c119639ba75aa86fc26c59382b2233c45ed59997c2d5733db05a91658ccb6df1","4c47eed4f466946d794e803c77a2c87ee77460fdb4076df5a1078e6f1f317921"),
        new HplntdSwitchingNativeReference.Profile("discharge-short-fine",0.00025,2402,1201,"0404083fc19a1e65dd5122648b3fc04e30a5529cd24efd96915131761f18cc08","c119639ba75aa86fc26c59382b2233c45ed59997c2d5733db05a91658ccb6df1","cf1d1110ead9e7963b7749cd1244636b9c0bcc1b9ade1a720370e8a36c73cc18"),
        new HplntdSwitchingNativeReference.Profile("discharge-long-coarse",0.0005,1202,601,"13559b63dc78cbfe90068e286612961d3f0c27e87a4cec079b099f412540f5c2","83411a069bd05b15972a53d407b99da05846dbb19c1b590ebf0f98898fe6c82a","4c47eed4f466946d794e803c77a2c87ee77460fdb4076df5a1078e6f1f317921"),
        new HplntdSwitchingNativeReference.Profile("discharge-long-fine",0.00025,2402,1201,"0404083fc19a1e65dd5122648b3fc04e30a5529cd24efd96915131761f18cc08","83411a069bd05b15972a53d407b99da05846dbb19c1b590ebf0f98898fe6c82a","cf1d1110ead9e7963b7749cd1244636b9c0bcc1b9ade1a720370e8a36c73cc18")
    );}
}
