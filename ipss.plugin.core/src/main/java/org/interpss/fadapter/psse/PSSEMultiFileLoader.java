package org.interpss.fadapter.psse;

import org.interpss.fadapter.builder.AcscNetworkBuilder;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.builder.AclfNetworkObjectFactory;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.acsc.AcscNetwork;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.simu.SimuObjectFactory;

/**
 * Convenience loader for multi-file PSS/E data (LF + sequence + dynamic).
 * Replaces the old ODM-based pipeline:
 *   PSSERawAdapter -> ODMAcsc/DStabParserMapper -> SimuContext
 * with direct parsers:
 *   PSSEDirectParser -> PSSEAcscDirectParser -> PSSEDStabDirectParser
 */
public class PSSEMultiFileLoader {
    /** Null means auto-detect REV from the LF RAW header. */
    private final Integer versionOverride;

    /** Auto-detect PSS/E REV from the LF file header. */
    public PSSEMultiFileLoader() {
        this.versionOverride = null;
    }

    /** Force section layout to {@code version} (override header REV). */
    public PSSEMultiFileLoader(int version) {
        this.versionOverride = version;
    }

    private PSSEDirectParser createLfParser(com.interpss.core.aclf.BaseAclfNetwork<?, ?> net) {
        return versionOverride == null
                ? new PSSEDirectParser(net)
                : new PSSEDirectParser(versionOverride, net);
    }

    private PSSEDirectParser createLfParser(com.interpss.core.aclf.BaseAclfNetwork<?, ?> net,
            AclfNetworkObjectFactory objectFactory) {
        return versionOverride == null
                ? new PSSEDirectParser(net, objectFactory)
                : new PSSEDirectParser(versionOverride, net, objectFactory);
    }

    /**
     * Load a single LF file as AcscNetwork (for short-circuit analysis).
     */
    public AcscNetwork loadAcsc(String lfFile) throws InterpssException {
        AcscNetwork net = CoreObjectFactory.createAcscNetwork();
        net.setPositiveSeqDataOnly(true);
        createLfParser(net).parseInto(lfFile);
        return net;
    }

    /**
     * Load LF + sequence files as AcscNetwork.
     */
    public AcscNetwork loadAcsc(String lfFile, String seqFile) throws InterpssException {
        AcscNetwork net = loadAcsc(lfFile);
        AcscNetworkBuilder acscBuilder = new AcscNetworkBuilder(net);
        new PSSEAcscDirectParser(acscBuilder).parseSequenceFile(seqFile);
        return net;
    }

    /**
     * Load LF (+ optional sequence + dynamic) files as DStabilityNetwork
     * wrapped in a SimuContext for dynamic simulation.
     *
     * @param files array of file paths: [0]=LF, [1]=seq or dyn, [2]=dyn (optional)
     * @return SimuContext with DStabilityNetwork and DynamicSimuAlgorithm configured
     */
    public SimuContext loadDStab(String... files) throws InterpssException {
        return loadDStab(DStabObjectFactory.createDStabilityNetwork(), files);
    }

    /**
     * Load LF (+ optional sequence + dynamic) files into the supplied
     * DStab network implementation.
     *
     * @param dsNet network instance to populate
     * @param files array of file paths: [0]=LF, [1]=seq or dyn, [2]=dyn (optional)
     * @return SimuContext with the supplied network and DynamicSimuAlgorithm configured
     */
    public SimuContext loadDStab(BaseDStabNetwork<?, ?> dsNet, String... files) throws InterpssException {
        return loadDStab(dsNet, null, files);
    }

    /**
     * Load PSS/E files into the supplied network using the supplied concrete
     * bus, branch, generator, and load factory.
     */
    public SimuContext loadDStab(BaseDStabNetwork<?, ?> dsNet,
            AclfNetworkObjectFactory objectFactory, String... files) throws InterpssException {
        if (files == null || files.length == 0) {
            throw new InterpssException("At least one file (LF) is required");
        }

        dsNet.setPositiveSeqDataOnly(true);
        createLfParser(dsNet, objectFactory).parseInto(files[0]);

        SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
        simuCtx.setDStabilityNet(dsNet);

        if (files.length == 2) {
            if (files[1].endsWith(".dyr")) {
                new PSSEDStabDirectParser(new DStabNetworkBuilder(dsNet)).parseDynFile(files[1]);
            } else {
                new PSSEAcscDirectParser(new AcscNetworkBuilder(dsNet)).parseSequenceFile(files[1]);
            }
        } else if (files.length >= 3) {
            new PSSEAcscDirectParser(new AcscNetworkBuilder(dsNet)).parseSequenceFile(files[1]);
            new PSSEDStabDirectParser(new DStabNetworkBuilder(dsNet)).parseDynFile(files[2]);
        }

        DynamicSimuAlgorithm dynAlgo = DStabObjectFactory.createDynamicSimuAlgorithm(dsNet);
        simuCtx.setDynSimuAlgorithm(dynAlgo);
        return simuCtx;
    }
}
