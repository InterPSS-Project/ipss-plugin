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
 * Convenience loader for multi-file PSS/E data (RAW or RAWX + sequence + DYR, with
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
    private List<PsseCasePreparationProcessor.Entry> lastCasePreparationReport=List.of();
    private boolean autoDiscoverCasePreparationFiles=true;
    private boolean ignoreMissingHosts=true;
    private List<Path> runConfigurationFiles=List.of();
    private String dynamicRunConfigJson;
    private boolean requireMatchedRunSettings;
    private PsseIdvRunConfigConverter.Result lastRunConfigConversion;
    private List<String> lastRunConfigApplicationGaps=List.of();
    /** Run settings are separate from electrical preparation. Ordered scripts
     * are converted without executing binaries, snapshots or application code. */
    public PSSEMultiFileLoader setRunConfigurationFiles(Path... files) {
        runConfigurationFiles=List.copyOf(java.util.Arrays.asList(files));return this;
    }
    /** Explicit JSON overrides converted source settings, including false and zero. */
    public PSSEMultiFileLoader setDynamicRunConfigJson(String json) {
        org.interpss.plugin.dstab.config.DStabRunConfigRec.fromJson(json);dynamicRunConfigJson=json;return this;
    }
    public PSSEMultiFileLoader setRequireMatchedRunSettings(boolean required){requireMatchedRunSettings=required;return this;}
    public PsseIdvRunConfigConverter.Result getLastRunConfigConversion(){return lastRunConfigConversion;}
    public List<String> getLastRunConfigApplicationGaps(){return lastRunConfigApplicationGaps;}

    private List<PsseGovernorBsgProcessor.Entry> lastGovernorBlockingEntries=List.of();
    /** Registered policy only; retrieve the attached solver's report after initialization for outcomes. */
    public List<PsseGovernorBsgProcessor.Entry> getLastGovernorBlockingEntries(){return lastGovernorBlockingEntries;}

    /** Missing physical/dynamic hosts are skipped with source-located diagnostics by default. */
    public PSSEMultiFileLoader setIgnoreMissingHosts(boolean ignore) {ignoreMissingHosts=ignore;return this;}
    private java.util.function.Function<org.interpss.fadapter.psse.dyr.PsseDyrRecord,
            java.util.Map<Integer,String>> parameterOverrides=record->java.util.Map.of();

    /** Explicit, audited DYR corrections; source files and default import remain unchanged. */
    public PSSEMultiFileLoader setDynamicModelParameterOverrides(
            java.util.function.Function<org.interpss.fadapter.psse.dyr.PsseDyrRecord,
                    java.util.Map<Integer,String>> overrides) {
        parameterOverrides=java.util.Objects.requireNonNull(overrides,"overrides");return this;
    }

    /** Control neighboring-file discovery; explicitly supplied preparation files always apply. */
    public PSSEMultiFileLoader setAutoDiscoverCasePreparationFiles(boolean enabled) {
        autoDiscoverCasePreparationFiles=enabled;return this;
    }

    /** Applied data changes and explicitly deferred setup/run operations, in source order. */
    public List<PsseCasePreparationProcessor.Entry> getLastCasePreparationReport() {
        return lastCasePreparationReport;
    }

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

    private PSSEDirectParser createLfParser(com.interpss.core.aclf.BaseAclfNetwork<?, ?> net,
            AclfNetworkObjectFactory objectFactory) {
        return versionOverride == null
                ? new PSSEDirectParser(net, objectFactory)
                : new PSSEDirectParser(versionOverride, net, objectFactory);
    }

    private void parseLoadflow(String file, com.interpss.core.aclf.BaseAclfNetwork<?, ?> net,
            AclfNetworkObjectFactory objectFactory) throws InterpssException {
        // RAWX column names carry the layout; the RAW version override is only
        // needed for positional text records. Build directly into the target so
        // DYR attachment sees dynamic generators, loads, and transformer stars.
        if (file.toLowerCase(Locale.ROOT).endsWith(".rawx")) {
            new PSSEJsonDirectParser(net, objectFactory).parseInto(file);
        } else {
            createLfParser(net, objectFactory).parseInto(file);
        }
    }

    /**
     * Load a single LF file as AcscNetwork (for short-circuit analysis).
     */
    public AcscNetwork loadAcsc(String lfFile) throws InterpssException {
        AcscNetwork net = CoreObjectFactory.createAcscNetwork();
        net.setPositiveSeqDataOnly(true);
        parseLoadflow(lfFile, net, null);
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
     * @param files file paths: [0]=RAW or RAWX, followed by optional sequence, dynamic,
     *              and case-preparation IDV files. GNET and model-removal
     *              directives are always applied before DYR parsing.
     * @return SimuContext with DStabilityNetwork and DynamicSimuAlgorithm configured
     */
    public SimuContext loadDStab(String... files) throws InterpssException {
        var network=DStabObjectFactory.createDStabilityNetwork();
        // Imported dynamic cases use current-dependent active static load and
        // impedance-dependent reactive static load unless a run config overrides
        // either choice. The generic core's all-admittance flag must be false:
        // leaving it true would silently turn the active-current part into Z.
        // Apply defaults only to a network we create, never to a supplied network
        // whose caller may already have selected an explicit conversion policy.
        network.setStaticLoadModel(com.interpss.dstab.StaticLoadModel.CONST_I);
        network.setReactiveStaticLoadModel(com.interpss.dstab.StaticLoadModel.CONST_Z);
        network.setStaticLoadIncludedInYMatrix(false);
        return loadDStab(network, files);
    }

    /**
     * Load LF (+ optional sequence + dynamic) files into the supplied
     * DStab network implementation.
     *
     * @param dsNet network instance to populate
     * @param files array of file paths: [0]=LF, [1]=seq or dyn, [2]=dyn (optional)
     * @return SimuContext with the supplied network and DynamicSimuAlgorithm configured
     *         with {@link org.interpss.dstab.algo.DefaultDynamicSimulationSolver};
     *         callers can retrieve that solver without installing another instance
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
        lastGovernorBlockingEntries=List.of();
        lastCasePreparationReport=List.of();
        lastRunConfigConversion=null;lastRunConfigApplicationGaps=List.of();
        dsNet.setPositiveSeqDataOnly(true);
        parseLoadflow(files[0], dsNet, objectFactory);

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
            } else if (lowerCaseFile.endsWith(".idv") || lowerCaseFile.endsWith(".gnet")) {
                preparationFiles.add(Path.of(files[i]).toAbsolutePath().normalize());
            } else {
                modelFiles.add(files[i]);
            }
        }
        // Preparation is named after either the static case or the model case.
        // Their stems often differ; do not require a DYR-stem match to net the
        // explicitly associated static case. Full setup IDVs remain explicit.
        boolean discoverPreparationFiles=autoDiscoverCasePreparationFiles&&preparationFiles.isEmpty();
        if(discoverPreparationFiles) {
            discoverSiblingPreparation(files[0],"_gnet.idv").ifPresent(preparationFiles::add);
            discoverSiblingPreparation(files[0],".gnet").ifPresent(preparationFiles::add);
        }
        boolean discoverResponseFiles = responseFiles.isEmpty();
        for (String modelFile : modelFiles) {
            if (discoverResponseFiles)
                discoverSiblingIdv(modelFile, "_basegen.bsg").ifPresent(responseFiles::add);
            if(discoverPreparationFiles) {
                discoverSiblingIdv(modelFile, "_gnet.idv").ifPresent(preparationFiles::add);
                discoverSiblingIdv(modelFile, "_MODREMOVE.idv").ifPresent(preparationFiles::add);
            }
        }
        // Resolve the run once. Its registered governor-policy inputs belong in
        // the normal response-file pipeline even when the caller did not repeat
        // the BSG path among the import arguments. No IRF program is executed.
        if(!runConfigurationFiles.isEmpty()) {
            try {
                lastRunConfigConversion=PsseIdvRunConfigConverter.convert(runConfigurationFiles.toArray(Path[]::new));
                if(requireMatchedRunSettings)lastRunConfigConversion.requireFullyConverted();
                Path base=runConfigurationFiles.getFirst().toAbsolutePath().normalize().getParent();
                for(String relative:lastRunConfigConversion.governorPolicyFiles())responseFiles.add(base.resolve(relative).normalize());
            }catch(java.io.IOException|IllegalArgumentException failure){throw new InterpssException("Dynamic run configuration: "+failure.getMessage());}
        }
        var preparation=new PsseCasePreparationProcessor(dsNet,modelFiles.stream()
                .anyMatch(path->path.toLowerCase(Locale.ROOT).endsWith(".dyr")),objectFactory);
        try {
            for(Path file:preparationFiles)preparation.apply(file);
            preparation.finalizeElectricalData();
        }
        finally { lastCasePreparationReport=preparation.entries(); }
        var gnetRemovedGenerators=preparation.gnetRemoved();
        var modelRemovedGenerators=preparation.modelRemoved();
        var removalDirectives=preparation.removalDirectives();
        if(preparation.dynamicFiles().size()>1)throw new InterpssException("Multiple DYRE references are ambiguous; select one explicit DYR input");
        preparation.dynamicFiles().forEach(file->modelFiles.add(file.toString()));

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

        // The suffix is shared by unrelated contracts. Detect content and keep
        // renewable import-time response separate from post-init governor travel.
        Path renewablePolicy=null,governorPolicy=null;
        try {
            for(Path file:responseFiles) {
                if(PsseGovernorBsgProcessor.isGovernorFile(file)) {
                    if(governorPolicy!=null)throw new InterpssException("Specify one governor BSG policy");
                    governorPolicy=file;
                } else {
                    if(renewablePolicy!=null)throw new InterpssException("Specify one BASEGEN response file");
                    renewablePolicy=file;
                }
            }
            if(governorPolicy!=null)lastGovernorBlockingEntries=PsseGovernorBsgProcessor.read(governorPolicy);
        } catch(java.io.IOException failure){throw new InterpssException(failure.getMessage());}
        if(renewablePolicy!=null)lastResponseEntries=PsseBsgProcessor.apply(dsNet,renewablePolicy);

        // Missing generator models are netted from solved P/Q during default
        // preprocessing, before dynamic states and static loads are initialized.
        // Import/data checking must leave the power-flow generators in service.
        // Callers can disable this policy with setAllowGenWithoutMach(false).
        dsNet.setAllowGenWithoutMach(true);
        DynamicSimuAlgorithm dynAlgo = DStabObjectFactory.createDynamicSimuAlgorithm(dsNet);
        var solver=new org.interpss.dstab.algo.DefaultDynamicSimulationSolver(dynAlgo);
        solver.setGovernorBlockingPolicy(lastGovernorBlockingEntries);
        dynAlgo.setSolver(solver);
        if(!runConfigurationFiles.isEmpty()||dynamicRunConfigJson!=null) {
            try {
                var runConfig=new org.interpss.plugin.dstab.config.DStabRunConfigRec();
                if(!runConfigurationFiles.isEmpty()) {
                    runConfig=lastRunConfigConversion.config();
                }
                if(dynamicRunConfigJson!=null)runConfig=org.interpss.plugin.dstab.config.DStabRunConfigRec.overlayJson(runConfig,dynamicRunConfigJson);
                lastRunConfigApplicationGaps=runConfig.applyTo(dynAlgo,requireMatchedRunSettings);
                if(!lastRunConfigApplicationGaps.isEmpty())org.slf4j.LoggerFactory.getLogger(getClass()).warn(
                        "Imported run settings have unapplied policies: {}",lastRunConfigApplicationGaps);
            } catch(IllegalArgumentException e){throw new InterpssException("Dynamic run configuration: "+e.getMessage());}
        }
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
                .setIgnoreMissingHosts(ignoreMissingHosts)
                .setDynamicModelParameterOverrides(parameterOverrides)
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

    private static java.util.Optional<Path> discoverSiblingPreparation(String source,String suffix) {
        Path path=Path.of(source).toAbsolutePath().normalize();
        String name=path.getFileName().toString();int dot=name.lastIndexOf('.');
        Path sibling=path.resolveSibling((dot<0?name:name.substring(0,dot))+suffix);
        return Files.isRegularFile(sibling)?java.util.Optional.of(sibling):java.util.Optional.empty();
    }

}
