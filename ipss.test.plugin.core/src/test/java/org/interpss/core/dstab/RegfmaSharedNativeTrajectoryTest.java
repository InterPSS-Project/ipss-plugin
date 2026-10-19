package org.interpss.core.dstab;

/** The same fifteen archived reference profiles with shared staggered integration. */
public class RegfmaSharedNativeTrajectoryTest extends Regfma1NativeTrajectoryTest {
    @Override protected boolean useSharedIntegration() { return true; }
}
