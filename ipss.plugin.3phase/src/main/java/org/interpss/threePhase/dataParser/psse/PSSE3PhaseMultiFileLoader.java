package org.interpss.threePhase.dataParser.psse;

import org.interpss.fadapter.builder.AclfNetworkObjectFactory;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.Aclf3WBranch;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.simu.SimuContext;

/**
 * PSS/E multi-file loader that preserves three-phase network object types.
 */
public class PSSE3PhaseMultiFileLoader extends PSSEMultiFileLoader {

    private static final AclfNetworkObjectFactory OBJECT_FACTORY =
            new AclfNetworkObjectFactory() {
                @Override
                public BaseAclfBus<?, ?> createBus(String busId,
                        BaseAclfNetwork<?, ?> network) {
                    return ThreePhaseObjectFactory.create3PDStabBus(
                            busId, (DStabNetwork3Phase) network);
                }

                @Override
                public AclfBranch createBranch() {
                    return ThreePhaseObjectFactory.create3PBranch();
                }

                @Override
                public Aclf3WBranch create3WBranch() {
                    return ThreePhaseObjectFactory.createBranch3W3Phase();
                }

                @Override
                public AclfGen createGen(String genId) {
                    return ThreePhaseObjectFactory.create3PGenerator(genId);
                }

                @Override
                public AclfLoad createLoad(String loadId) {
                    return ThreePhaseObjectFactory.create3PLoad(loadId);
                }
            };

    public PSSE3PhaseMultiFileLoader(int version) {
        super(version);
    }

    @Override
    public SimuContext loadDStab(String... files) throws InterpssException {
        DStabNetwork3Phase network = ThreePhaseObjectFactory.create3PhaseDStabNetwork();
        return super.loadDStab(network, OBJECT_FACTORY, files);
    }
}
