package org.interpss.fadapter.builder;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.Aclf3WBranch;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.aclf.BaseAclfNetwork;

/**
 * Creates the concrete network objects populated by {@link AclfNetworkBuilder}.
 */
public interface AclfNetworkObjectFactory {

    BaseAclfBus<?, ?> createBus(String busId, BaseAclfNetwork<?, ?> network)
            throws InterpssException;

    AclfBranch createBranch();

    Aclf3WBranch create3WBranch();

    AclfGen createGen(String genId);

    AclfLoad createLoad(String loadId);
}
