package org.interpss.dstab.renewable;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.DStabGen;

/** Physical host metadata needed to bind electrical-controller measurements. */
public interface RenewableConverterHost {
    BaseDStabBus<?,?> getDStabBus();
    DStabGen getParentGen();
}
