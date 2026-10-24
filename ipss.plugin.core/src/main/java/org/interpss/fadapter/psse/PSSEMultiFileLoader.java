package org.interpss.fadapter.psse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
 * Convenience loader for multi-file PSS/E data (RAW + sequence + DYR, with
 * optional IDV case-preparation files).
 *
 * <p>GE PSLF DYD is a different source format and is deliberately rejected by
 * this loader. A PSLF adapter must be invoked as a separate import workflow;
 * DYD and DYR are never combined as inputs to this reader.</p>
 *
 * Replaces the old ODM-based pipeline:
 *   PSSERawAdapter -> ODMAcsc/DStabParserMapper -> SimuContext
 * with direct parsers:
 *   PSSEDirectParser -> PSSEAcscDirectParser -> PSSEDStabDirectParser
 */
public class PSSEMultiFileLoader {
    /** Null means auto-detect REV from the LF RAW header. */
    private final Integer versionOverride;
    private org.interpss.fadapter.psse.dyr.DynamicModelImportReport lastDynamicImportReport =
            org.interpss.fadapter.psse.dyr.DynamicModelImportReport.empty();
    private java.util.Map<PsseGnetIdvProcessor.GeneratorKey, PsseBsgProcessor.Entry> lastResponseEntries = java.util.Map.of();

    public org.interpss.fadapter.psse.dyr.DynamicModelImportReport getLastDynamicImportReport() {
        return lastDynamicImportReport;
    }
    public java.util.Map<PsseGnetIdvProcessor.GeneratorKey, PsseBsgProcessor.Entry> getLastResponseEntries() {
        return lastResponseEntries;
    }

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
     * @param files file paths: [0]=LF, followed by optional sequence, dynamic,
     *              and case-preparation IDV files. GNET and model-removal
     *              directives are always applied before DYR parsing.
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

        lastDynamicImportReport = org.interpss.fadapter.psse.dyr.DynamicModelImportReport.empty();
        lastResponseEntries = java.util.Map.of();
        dsNet.setPositiveSeqDataOnly(true);
        createLfParser(dsNet, objectFactory).parseInto(files[0]);

        SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
        simuCtx.setDStabilityNet(dsNet);

        List<String> modelFiles = new ArrayList<>();
        Set<Path> preparationFiles = new LinkedHashSet<>();
        Set<Path> responseFiles = new LinkedHashSet<>();
        for (int i = 1; i < files.length; i++) {
            String lowerCaseFile = files[i].toLowerCase(Locale.ROOT);
            if (lowerCaseFile.endsWith(".dyd")) {
                throw new InterpssException("GE PSLF .dyd is not a PSS/E input: "
                        + files[i]);
            }
            if (lowerCaseFile.endsWith(".bsg")) {
                responseFiles.add(Path.of(files[i]).toAbsolutePath().normalize());
            } else if (lowerCaseFile.endsWith(".idv")) {
                preparationFiles.add(Path.of(files[i]).toAbsolutePath().normalize());
            } else {
                modelFiles.add(files[i]);
            }
        }
        boolean discoverResponseFiles = responseFiles.isEmpty();
        for (String modelFile : modelFiles) {
            if (discoverResponseFiles)
                discoverSiblingIdv(modelFile, "_basegen.bsg").ifPresent(responseFiles::add);
            discoverSiblingIdv(modelFile, "_gnet.idv").ifPresent(preparationFiles::add);
            discoverSiblingIdv(modelFile, "_MODREMOVE.idv").ifPresent(preparationFiles::add);
        }
        Set<PsseGnetIdvProcessor.GeneratorKey> gnetRemovedGenerators = new LinkedHashSet<>();
        Set<PsseGnetIdvProcessor.GeneratorKey> modelRemovedGenerators = new LinkedHashSet<>();
        Set<PsseModelRemoveIdvProcessor.Directive> removalDirectives = new LinkedHashSet<>();
        for (Path preparationFile : preparationFiles) {
            gnetRemovedGenerators.addAll(PsseGnetIdvProcessor.apply(dsNet,
                    preparationFile.toString())
                    .convertedGeneratorKeys());
            var removals = PsseModelRemoveIdvProcessor.apply(dsNet, preparationFile.toString());
            modelRemovedGenerators.addAll(removals.removedGeneratorKeys());
            removalDirectives.addAll(removals.directives());
        }

        if (modelFiles.size() == 1) {
            String modelFile = modelFiles.get(0);
            if (modelFile.toLowerCase(Locale.ROOT).endsWith(".dyr")) {
                parseDynamicModels(dsNet, modelFile, gnetRemovedGenerators,
                        modelRemovedGenerators, removalDirectives);
            } else {
                new PSSEAcscDirectParser(new AcscNetworkBuilder(dsNet)).parseSequenceFile(modelFile);
            }
        } else if (modelFiles.size() >= 2) {
            new PSSEAcscDirectParser(new AcscNetworkBuilder(dsNet)).parseSequenceFile(modelFiles.get(0));
            parseDynamicModels(dsNet, modelFiles.get(1), gnetRemovedGenerators,
                    modelRemovedGenerators, removalDirectives);
        }

        if (responseFiles.size() > 1)
            throw new InterpssException("Specify one BASEGEN response file; multiple policies are ambiguous");
        for (Path responseFile : responseFiles)
            lastResponseEntries = PsseBsgProcessor.apply(dsNet, responseFile);

        // Missing generator models are netted from solved P/Q during default
        // preprocessing, before dynamic states and static loads are initialized.
        // Import/data checking must leave the power-flow generators in service.
        // Callers can disable this policy with setAllowGenWithoutMach(false).
        dsNet.setAllowGenWithoutMach(true);
        DynamicSimuAlgorithm dynAlgo = DStabObjectFactory.createDynamicSimuAlgorithm(dsNet);
        dynAlgo.setSolver(new PsseDStabSolver(dynAlgo));
        simuCtx.setDynSimuAlgorithm(dynAlgo);
        return simuCtx;
    }

    private void parseDynamicModels(BaseDStabNetwork<?, ?> dsNet, String modelFile,
            Set<PsseGnetIdvProcessor.GeneratorKey> gnetRemovedGenerators,
            Set<PsseGnetIdvProcessor.GeneratorKey> modelRemovedGenerators,
            Set<PsseModelRemoveIdvProcessor.Directive> removalDirectives)
            throws InterpssException {
        DStabNetworkBuilder builder = new DStabNetworkBuilder(dsNet);
        var parser = new PSSEDStabDirectParser(builder)
                .setGnetRemovedGenerators(gnetRemovedGenerators)
                .setModelRemovedGenerators(modelRemovedGenerators)
                .setModelRemovalDirectives(removalDirectives);
        try { parser.parseDynFile(modelFile); }
        finally { lastDynamicImportReport = parser.getLastImportReport(); }
    }

    private static java.util.Optional<Path> discoverSiblingIdv(String modelFile, String suffix) {
        Path path = Path.of(modelFile).toAbsolutePath().normalize();
        String name = path.getFileName().toString();
        if (!name.toLowerCase(Locale.ROOT).endsWith(".dyr")) {
            return java.util.Optional.empty();
        }
        String stem = name.substring(0, name.length() - 4);
        Path sibling = path.resolveSibling(stem + suffix);
        return Files.isRegularFile(sibling)
                ? java.util.Optional.of(sibling)
                : java.util.Optional.empty();
    }

}
