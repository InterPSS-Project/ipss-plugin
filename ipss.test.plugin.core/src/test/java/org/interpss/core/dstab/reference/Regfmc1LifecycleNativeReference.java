package org.interpss.core.dstab.reference;
import java.util.List;
/** Native trip profiles are separate from active-branch controller replay. */
public final class Regfmc1LifecycleNativeReference {
    private Regfmc1LifecycleNativeReference(){}
    public static List<Regfmc1NativeContractReference.Profile> profiles(){return List.of(
new Regfmc1NativeContractReference.Profile("trip-coarse",0.0005,606,"b8e110041e996e1ff63f64d52882bf75bb60efedb8c8b3b93ab7b77e90e4c370","5ed4dbcdead719a5ac30c1e0e7e7592a292347e37941d69849f42f4f219944b0"),
new Regfmc1NativeContractReference.Profile("trip-fine",0.00025,1206,"03f4bcb33b156742226043e094d518e9791507782dbd9762f6c3fcd0b484094c","5ed4dbcdead719a5ac30c1e0e7e7592a292347e37941d69849f42f4f219944b0"));}
}
