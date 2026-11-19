package org.interpss.core.adapter.psse.raw;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.Hashtable;
import org.apache.commons.math3.complex.Complex;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.sparse.impl.klu.JKLUSparseEqnComplexImpl;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.impl.DStabilityNetworkImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.interpss.plugin.dstab.config.*;
import org.interpss.fadapter.psse.*;
import com.interpss.dstab.algo.dynamic.*;
import org.interpss.dstab.dynLoad.impl.Cmldznu2Model;

/** Ordered script conversion, explicit overrides and physical initialization admission. */
@SuppressWarnings("deprecation")
public class DynamicRunConfigTest {
    @TempDir Path temp;
    private static final Path FIXTURE=Path.of("testData/adpter/psse/v33/HspModels");
    @BeforeAll static void setup(){org.interpss.IpssCorePlugin.init();org.interpss.IpssCorePlugin.configureSparseSolver("java-klu");}
    private Path file(String name,String text)throws Exception {Path p=temp.resolve(name);Files.createDirectories(p.getParent());Files.writeString(p,text);return p;}
    @Test void fluxStatorFrequencyIsOptInAndIndependentOfSourceNetfrq() throws Exception {
        var fixture = Path.of("testData/adpter/psse/v33/SMIB");
        var idv = file("flux-frequency.idv", "BAT_SET_NETFRQ,1\n");
        for (String family : new String[]{"genrou", "genroe", "gensal"}) {
            var dyr = fixture.resolve("SMIB_v33_" + (family.equals("genroe") ? "genrou" : family) + ".dyr");
            if (family.equals("genroe")) {
                dyr = file("genroe.dyr", Files.readString(dyr).replace("'GENROU'", "'GENROE'"));
            }
            for (String json : new String[]{
                    "{}",
                    "{\"fluxMachineNetworkFrequencyDependenceEnabled\":true}",
                    "{\"fluxMachineNetworkFrequencyDependenceEnabled\":false}",
                    "{\"networkFrequencyDependenceEnabled\":false,\"fluxMachineNetworkFrequencyDependenceEnabled\":true}"
            }) {
                boolean enabled = Boolean.TRUE.equals(
                        DStabRunConfigRec.fromJson(json).fluxMachineNetworkFrequencyDependenceEnabled);
                var loader = new PSSEMultiFileLoader().setRunConfigurationFiles(idv)
                        .setRequireMatchedRunSettings(true).setDynamicRunConfigJson(json);
                var context = loader.loadDStab(fixture.resolve("SMIB_v33_wt1g1.raw").toString(), dyr.toString());
                var algorithm = context.getDynSimuAlgorithm();
                assertTrue(algorithm.getAclfAlgorithm().loadflow(), family);
                algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
                assertTrue(algorithm.initialization(), family);
                var machine = algorithm.getNetwork().getMachine("Bus1-mach1");
                var bus = machine.getDStabBus();
                var voltage = bus.getVoltage();
                var y = machine.getYgen();
                var z = y.reciprocal();
                bus.setFreq(1.0);
                var nominal = (Complex) machine.getOutputObject();
                var emf = voltage.add(nominal.subtract(voltage.multiply(y)).multiply(z));
                double ratio = 1.04;
                bus.setFreq(ratio);
                var actual = (Complex) machine.getOutputObject();
                var expected = enabled ? emf.multiply(ratio).subtract(voltage)
                        .divide(new Complex(z.getReal(), ratio * z.getImaginary())).add(voltage.multiply(y))
                        : nominal;
                assertEquals(0.0, actual.subtract(expected).abs(), 1e-10, family);
                assertEquals(y, machine.getYgen(), family);
                if (enabled) {
                    var off = DStabRunConfigRec.fromJson("{\"fluxMachineNetworkFrequencyDependenceEnabled\":false}");
                    assertTrue(off.applyTo(algorithm, true).isEmpty());
                    assertTrue(algorithm.initialization(), family);
                    bus.setFreq(1.0);
                    var resetNominal = (Complex) machine.getOutputObject();
                    bus.setFreq(ratio);
                    assertEquals(resetNominal, machine.getOutputObject(), family);
                }
            }
        }
    }

    @Test void regca1RunConfigSelectsAcceptedVoltageMemoryAndPreservesOmittedPolicy() throws Exception {
        var fixture = Path.of("testData/adpter/psse/v33/renewable");
        var raw = fixture.resolve("regca_reeca_repca_bus1062.raw").toString();
        var dyr = fixture.resolve("regca_reeca_repca_bus1062.dyr").toString();
        var enabled = DStabRunConfigRec.fromJson("{\"regca1HighVoltageNetworkIterationEnabled\":true}");
        var disabled = DStabRunConfigRec.overlayJson(enabled, "{\"regca1HighVoltageNetworkIterationEnabled\":false}");
        assertFalse(disabled.regca1HighVoltageNetworkIterationEnabled);
        for (var config : new DStabRunConfigRec[]{new DStabRunConfigRec(), enabled, disabled}) {
            var context = new PSSEMultiFileLoader().setDynamicRunConfigJson(config.toJson()).loadDStab(raw, dyr);
            var algorithm = context.getDynSimuAlgorithm();
            var generator = (com.interpss.dstab.DStabGen) context.getDStabilityNet().getDStabBus("Bus1").getContributeGen("1");
            var converter = assertInstanceOf(com.interpss.dstab.renewable.Regca1Model.class,
                    generator.getDynamicGenDevice());
            // An omitted run setting preserves an explicit component setting.
            converter.setNetworkIterationHighVoltageEnabled(config != enabled);
            assertTrue(algorithm.getAclfAlgorithm().loadflow());
            algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
            assertTrue(algorithm.initialization());
            assertEquals(config != disabled, converter.getNetworkHighVoltageState().enabled());
            if (config == enabled) {
                assertEquals(com.interpss.dstab.renewable.Regca1Model.NetworkVoltageMemoryPolicy.ACCEPTED_BOUNDARY,
                        converter.getNetworkVoltageMemoryPolicy());
            }
        }
    }

    @Test void cutoffEqualityZeroQAndSignedPowerAreIndependent() {
        var t=new LoadModelThresholds(5,1.61,.97);
        assertTrue(t.violations(5,5/1.61,.97).isEmpty());
        assertTrue(t.violations(5,0,1).isEmpty());
        assertTrue(t.violations(-5,0,1).contains("MW_BELOW_THRESHOLD"));
        assertEquals(3,t.violations(4,4,.96).size());
        assertThrows(IllegalArgumentException.class,()->new LoadModelThresholds(Double.NaN,1,1));
    }
    @Test void sourceThenExplicitJsonPreservesFalseAndOtherFields() {
        var c=DStabRunConfigRec.fromJson("{\"schemaVersion\":1,\"stepSec\":0.0033333,\"sourceImpedanceReconciliationEnabled\":true}");
        var n=DStabRunConfigRec.overlayJson(c,"{\"sourceImpedanceReconciliationEnabled\":false}");
        assertFalse(n.sourceImpedanceReconciliationEnabled);assertEquals(c.stepSec,n.stepSec);
        assertTrue(c.sourceImpedanceReconciliationEnabled);
        assertThrows(IllegalArgumentException.class,()->DStabRunConfigRec.fromJson("{\"schemaVersion\":2}"));
        assertThrows(IllegalArgumentException.class,()->DStabRunConfigRec.fromJson("{\"typo\":1}"));
        assertThrows(IllegalArgumentException.class,()->DStabRunConfigRec.overlayJson(c,"{\"stepSec\":null}"));
    }
    @Test void partialThresholdOverrideKeepsOtherImportedBoundsAndRejectsTypos() {
        var base=DStabRunConfigRec.fromJson("{\"loadModelThresholds\":{\"minimumMw\":5,\"minimumAbsPOverQ\":1.61,\"minimumVoltagePu\":0.97}}");
        var changed=DStabRunConfigRec.overlayJson(base,"{\"loadModelThresholds\":{\"minimumMw\":0}}");
        assertEquals(0,changed.loadModelThresholds.minimumMw());assertEquals(1.61,changed.loadModelThresholds.minimumAbsPOverQ());assertEquals(.97,changed.loadModelThresholds.minimumVoltagePu());
        assertEquals(5,base.loadModelThresholds.minimumMw());
        assertThrows(IllegalArgumentException.class,()->DStabRunConfigRec.fromJson("{\"sourceImpedanceReconciliationEnabled\":\"true\"}"));
        assertThrows(IllegalArgumentException.class,()->DStabRunConfigRec.fromJson("{\"loadModelThresholds\":{\"minimumMw\":5}}"));
        assertThrows(IllegalArgumentException.class,()->DStabRunConfigRec.overlayJson(base,"{\"loadModelThresholds\":{\"minimumMWW\":5}}"));
    }
    @Test void quotedIncludesAndSlashCommentsRetainOrderedOverrides()throws Exception {
        file("sub dir/options.idv","BAT_SET_LOAD_MODEL_THRESH, 5, 1.61, .97 / source comment\nBAT_SET_ZSORCE_RECONCILE_FLAG,1\n");
        var result=PsseIdvRunConfigConverter.convert(file("run.idv","IDEV 'sub dir/options.idv'\nBAT_SET_ZSORCE_RECONCILE_FLAG,0\nBAT_RUN,1,10,0,5,0\n"));
        assertTrue(result.fullyConverted());assertFalse(result.config().sourceImpedanceReconciliationEnabled);
        assertEquals(5,result.config().loadModelThresholds.minimumMw());assertEquals(10,result.config().totalTimeSec);
        assertEquals(5,result.runSegments().get(0).outputEverySteps());assertEquals(2,result.sourceFiles().size());
    }
    @Test void exactEventSidesAndSourceAdmittanceStayInOrder()throws Exception {
        var r=PsseIdvRunConfigConverter.convert(file("event.idv","RUN TO 1 SECONDS, PRINT = 0, PLOT = 1\nAPPLY FAULT BUS 2 ADMITTANCE 0 -1D7 MVA\nRUN TO 1.06667 SECONDS, PRINT=0, PLOT=1\nCLEAR FAULT BUS 2\nTRIP TIE FROM BUS 2 TO BUS 3 CIRCUIT 1\nRUN TO 10 SECONDS, PRINT=0, PLOT=5\n"));
        assertTrue(r.fullyConverted());assertEquals(3,r.events().size());
        assertEquals(-1e7,r.events().get(0).susceptanceMva());assertEquals(1.06667,r.events().get(1).timeSec());
        assertEquals(r.events().get(1).timeSec(),r.events().get(2).timeSec());assertEquals("clear",r.events().get(1).kind());
        assertEquals(5,r.runSegments().get(2).outputEverySteps());
    }
    @Test void unsupportedCommandsAndIncludeCyclesCannotDisappear()throws Exception {
        var r=PsseIdvRunConfigConverter.convert(file("unknown.idv","BAT_NEW_UNIMPLEMENTED,1\n"));
        assertFalse(r.fullyConverted());assertThrows(IllegalArgumentException.class,r::requireFullyConverted);
        Path cycle=file("cycle.idv","IDEV 'cycle.idv'\n");assertThrows(IllegalArgumentException.class,()->PsseIdvRunConfigConverter.convert(cycle));
        assertThrows(IllegalArgumentException.class,()->PsseIdvRunConfigConverter.convert(file("bad.idv","BAT_SET_ZSORCE_RECONCILE_FLAG,2\n")));
    }
    @Test void documentedParameterDialogueDoesNotInventAnEquivalentAcceleration()throws Exception {
        var r=PsseIdvRunConfigConverter.convert(file("setup.idv","ALTR\n6\nY\n60,.4\nY\n,,.0033333\nN\nN\n0\n0\n"));
        assertTrue(r.fullyConverted());assertEquals(60,r.config().frequencyHz);assertEquals(.0033333,r.config().stepSec);
        assertEquals(.4,r.config().sourceNetworkAccelerationFactor);assertNull(r.config().networkRelaxationFactor);
    }
    private com.interpss.simu.SimuContext load(String model,String json)throws Exception {
        var c=new PSSEMultiFileLoader().setAutoDiscoverCasePreparationFiles(false).setDynamicRunConfigJson(json)
                .loadDStab(FIXTURE.resolve("load-network.raw").toString(),FIXTURE.resolve(model+".dyr").toString());
        assertTrue(c.getDynSimuAlgorithm().getAclfAlgorithm().loadflow());
        c.getDynSimuAlgorithm().setSimuStepSec(.0005);c.getDynSimuAlgorithm().setTotalSimuTimeSec(.01);
        c.getDynSimuAlgorithm().setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());return c;
    }
    @Test void subsystemScreeningRetainsSourceStaticLoadWithoutFeederOrMotor()throws Exception {
        var c=load("cmldznu2","{\"loadModelThresholds\":{\"minimumMw\":50,\"minimumAbsPOverQ\":0,\"minimumVoltagePu\":0}}");
        var net=c.getDStabilityNet();var bus=net.getDStabBus("Bus2");var source=bus.getContributeLoad("1");var cp=source.getLoadCP();var ci=source.getLoadCI();var cz=source.getLoadCZ();int n=net.getNoBus();
        assertTrue(c.getDynSimuAlgorithm().initialization());assertEquals(n,net.getNoBus());
        assertSame(source,bus.getContributeLoad("1"));assertTrue(source.isActive());assertEquals(cp,source.getLoadCP());assertEquals(ci,source.getLoadCI());assertEquals(cz,source.getLoadCZ());
        var report=((PsseDStabSolver)c.getDynSimuAlgorithm().getSolver()).getLoadModelScreeningReport();
        assertEquals(1,report.size());assertTrue(report.get(0).bypassed());assertEquals(40,report.get(0).pMw(),1e-8);
        assertEquals(com.interpss.dstab.StaticLoadModel.CONST_I,net.getStaticLoadModel());
        assertEquals(com.interpss.dstab.StaticLoadModel.CONST_Z,net.getReactiveStaticLoadModel());
        assertFalse(net.isStaticLoadIncludedInYMatrix());
        assertVoltageResponse(bus,net,1,2);
        assertTrue(c.getDynSimuAlgorithm().performSimulation());
    }
    /** Verify terminal power from the actual injected current, not only enum labels. */
    private void assertVoltageResponse(com.interpss.dstab.BaseDStabBus<?,?> bus,
            com.interpss.dstab.BaseDStabNetwork<?,?> net,int pExponent,int qExponent)throws Exception {
        var voltage=bus.getVoltage();double v0=bus.getInitVoltMag();var initial=bus.calStaticLoad();
        for(double ratio:new double[]{.8,1.1}) {
            bus.setVoltage(v0*ratio,voltage.getArgument());
            var power=bus.calStaticLoad();var current=bus.injCurDynamic(net.getStaticLoadModel(),net.getStaticLoadSwitchVolt(),net.getStaticLoadSwitchDeadZone());
            var fromCurrent=bus.getVoltage().multiply(current.conjugate()).negate();
            assertEquals(initial.getReal()*Math.pow(ratio,pExponent),power.getReal(),1e-10);
            assertEquals(initial.getImaginary()*Math.pow(ratio,qExponent),power.getImaginary(),1e-10);
            assertEquals(0,power.subtract(fromCurrent).abs(),1e-10);
        }
        bus.setVoltage(voltage);
    }
    private Path machinesOnly()throws Exception {
        String source=Files.readString(FIXTURE.resolve("cmldznu2.dyr"));var rows=new java.util.ArrayList<String>();
        for(String record:source.split("/"))if(!record.isBlank()&&!record.contains("USRLOD"))rows.add(record.strip()+" /\n");
        return file("machines-only.dyr",String.join("",rows));
    }
    @Test void ordinaryUnassignedStaticLoadUsesCurrentPAndImpedanceQ()throws Exception {
        var c=new PSSEMultiFileLoader().setAutoDiscoverCasePreparationFiles(false).loadDStab(FIXTURE.resolve("load-network.raw").toString(),machinesOnly().toString());
        var a=c.getDynSimuAlgorithm();assertTrue(a.getAclfAlgorithm().loadflow());a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        assertTrue(a.initialization());assertVoltageResponse(c.getDStabilityNet().getDStabBus("Bus2"),c.getDStabilityNet(),1,2);
    }
    @Test void jsonStaticConversionOverridesDefaultsAndRoundTrips()throws Exception {
        String json="{\"activeStaticLoadModel\":\"CONST_P\",\"reactiveStaticLoadModel\":\"CONST_I\"}";
        var config=DStabRunConfigRec.fromJson(DStabRunConfigRec.fromJson(json).toJson());assertEquals(com.interpss.dstab.StaticLoadModel.CONST_P,config.activeStaticLoadModel);
        var c=new PSSEMultiFileLoader().setAutoDiscoverCasePreparationFiles(false).setDynamicRunConfigJson(json)
                .loadDStab(FIXTURE.resolve("load-network.raw").toString(),machinesOnly().toString());
        var a=c.getDynSimuAlgorithm();assertTrue(a.getAclfAlgorithm().loadflow());a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());assertTrue(a.initialization());
        assertVoltageResponse(c.getDStabilityNet().getDStabBus("Bus2"),c.getDStabilityNet(),0,1);
        assertThrows(IllegalArgumentException.class,()->DStabRunConfigRec.fromJson("{\"activeStaticLoadModel\":\"FIXED\"}"));
    }
    @Test void callerSuppliedStaticConversionIsNotOverwrittenByImporter()throws Exception {
        var network=com.interpss.dstab.DStabObjectFactory.createDStabilityNetwork();
        network.setStaticLoadModel(com.interpss.dstab.StaticLoadModel.CONST_P);network.setReactiveStaticLoadModel(com.interpss.dstab.StaticLoadModel.CONST_I);network.setStaticLoadIncludedInYMatrix(false);
        var c=new PSSEMultiFileLoader().setAutoDiscoverCasePreparationFiles(false).loadDStab(network,FIXTURE.resolve("load-network.raw").toString(),machinesOnly().toString());
        assertSame(network,c.getDStabilityNet());assertEquals(com.interpss.dstab.StaticLoadModel.CONST_P,network.getStaticLoadModel());assertEquals(com.interpss.dstab.StaticLoadModel.CONST_I,network.getReactiveStaticLoadModel());
    }
    private String characteristic(String name)throws Exception {
        return Files.readString(FIXTURE.resolve(name+".dyr")).split("/",3)[2];
    }
    private com.interpss.simu.SimuContext priorityCase(String records,String json)throws Exception {
        var loader=new PSSEMultiFileLoader().setAutoDiscoverCasePreparationFiles(false).setDynamicRunConfigJson(json);
        var context=loader.loadDStab(FIXTURE.resolve("load-network.raw").toString(),
                file("priority.dyr",Files.readString(machinesOnly())+records).toString());
        assertTrue(loader.getLastDynamicImportReport().isStrictlyComplete(),loader.getLastDynamicImportReport().failureSummary());
        var algorithm=context.getDynSimuAlgorithm();assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());assertTrue(algorithm.initialization());
        return context;
    }
    private static final String LOCAL_IEEL="2 'IEELBL' '1' 1 0 0 1 0 0 0 0 0 0 0 0 0 0 /\n";
    private static final String AREA_IEEL="1 'IEELAR' '*' 1 0 0 1 0 0 0 0 2 0 0 2 0 0 /\n";
    @Test void specificCompositeWinsOverRegionalInBothOrders()throws Exception {
        String local=characteristic("cmldblu1"),regional=characteristic("cmldznu2");
        for(String records:new String[]{local+regional,regional+local}) {
            var c=priorityCase(records,"{}");
            var owner=(Cmldznu2Model)c.getDStabilityNet().getDStabBus("Bus2").getInfoOnlyDynModel();
            assertInstanceOf(org.interpss.dstab.dynLoad.impl.Cmldblu1Model.class,owner);assertEquals(1,owner.getSourceModels().size());
            var report=((PsseDStabSolver)c.getDynSimuAlgorithm().getSolver()).getLoadModelAssignmentReport();
            assertEquals(1,report.size());assertEquals("CMLDBLU1",report.get(0).appliedModel());
        }
    }
    @Test void specificAlgebraicLoadWinsOverZoneAndAreaInEveryPosition()throws Exception {
        String zone=characteristic("cmldznu2");
        for(String records:new String[]{LOCAL_IEEL+zone+AREA_IEEL,AREA_IEEL+zone+LOCAL_IEEL,zone+LOCAL_IEEL+AREA_IEEL}) {
            var c=priorityCase(records,"{}");var bus=c.getDStabilityNet().getDStabBus("Bus2");
            assertNull(bus.getInfoOnlyDynModel());assertEquals(1,bus.getDynLoadModelList().size());
            assertEquals("IEELBL",((PsseDStabSolver)c.getDynSimuAlgorithm().getSolver()).getLoadModelAssignmentReport().get(0).appliedModel());
        }
    }
    @Test void screenedZoneUsesStaticAndDoesNotReactivateLowerPriorityArea()throws Exception {
        String zone=characteristic("cmldznu2");
        for(String records:new String[]{zone+AREA_IEEL,AREA_IEEL+zone}) {
            var c=priorityCase(records,"{\"loadModelThresholds\":{\"minimumMw\":50,\"minimumAbsPOverQ\":0,\"minimumVoltagePu\":0}}");
            var bus=c.getDStabilityNet().getDStabBus("Bus2");assertTrue(bus.getDynLoadModelList().isEmpty());
            assertVoltageResponse(bus,c.getDStabilityNet(),1,2);
            var row=((PsseDStabSolver)c.getDynSimuAlgorithm().getSolver()).getLoadModelAssignmentReport().get(0);
            assertEquals("SCREENED_STATIC",row.status());assertEquals("CONST_I/CONST_Z",row.appliedModel());assertEquals(java.util.List.of("MW_BELOW_THRESHOLD"),row.reasons());
        }
    }
    @Test void ordinaryStaticFallbackIsIncludedInAssignmentAudit()throws Exception {
        var c=priorityCase("","{}");var report=((PsseDStabSolver)c.getDynSimuAlgorithm().getSolver()).getLoadModelAssignmentReport();
        assertEquals(1,report.size());assertEquals("STATIC_DEFAULT",report.get(0).status());assertEquals("NONE",report.get(0).candidateModel());
        assertEquals("CONST_I/CONST_Z",report.get(0).appliedModel());
    }
    @Test void regionalDefaultFillsOnlyUnassignedSourceOnSharedBus()throws Exception {
        String raw=Files.readString(FIXTURE.resolve("load-network.raw")).replace(
                "2,'1',1,1,1,40.0,10.0,0,0,0,0,1,1,0",
                "2,'1',1,1,1,40.0,10.0,0,0,0,0,1,1,0\n2,'2',1,1,1,20.0,5.0,0,0,0,0,1,1,0\n2,'3',0,1,1,10.0,2.5,0,0,0,0,1,1,0");
        var loader=new PSSEMultiFileLoader().setAutoDiscoverCasePreparationFiles(false);
        var c=loader.loadDStab(file("multiple.raw",raw).toString(),file("multiple.dyr",
                Files.readString(machinesOnly())+characteristic("cmldznu2")+characteristic("cmldblu1")).toString());
        assertTrue(loader.getLastDynamicImportReport().isStrictlyComplete(),loader.getLastDynamicImportReport().failureSummary());
        var a=c.getDynSimuAlgorithm();assertTrue(a.getAclfAlgorithm().loadflow());a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());assertTrue(a.initialization());
        var report=((PsseDStabSolver)a.getSolver()).getLoadModelAssignmentReport();assertEquals(3,report.size());
        assertEquals("CMLDBLU1",report.stream().filter(r->r.loadId().equals("1")).findFirst().orElseThrow().appliedModel());
        assertEquals("CMLDZNU2",report.stream().filter(r->r.loadId().equals("2")).findFirst().orElseThrow().appliedModel());
        assertEquals("INACTIVE",report.stream().filter(r->r.loadId().equals("3")).findFirst().orElseThrow().status());
        var owner=(Cmldznu2Model)c.getDStabilityNet().getDStabBus("Bus2").getInfoOnlyDynModel();assertEquals(2,owner.getSourceModels().size());
    }
    @Test void areaDefaultAndSpecificAlgebraicLoadDoNotConsumeCompositeThresholds()throws Exception {
        for(String record:new String[]{AREA_IEEL,LOCAL_IEEL}) {
            var c=priorityCase(record,"{\"loadModelThresholds\":{\"minimumMw\":1000000,\"minimumAbsPOverQ\":0,\"minimumVoltagePu\":0}}");
            var row=((PsseDStabSolver)c.getDynSimuAlgorithm().getSolver()).getLoadModelAssignmentReport().get(0);
            assertEquals("DYNAMIC",row.status());assertTrue(row.appliedModel().startsWith("IEEL"));
        }
    }
    @Test void duplicateZoneOwnerIsRejectedBeforeInstallingAnotherComposite()throws Exception {
        String zone=characteristic("cmldznu2");var loader=new PSSEMultiFileLoader().setAutoDiscoverCasePreparationFiles(false);
        var c=loader.loadDStab(FIXTURE.resolve("load-network.raw").toString(),file("duplicate.dyr",Files.readString(machinesOnly())+zone+zone).toString());
        assertFalse(loader.getLastDynamicImportReport().isStrictlyComplete());
        assertTrue(loader.getLastDynamicImportReport().failures().stream().anyMatch(e->e.message().contains("Duplicate overlapping zone")));
        var owner=(Cmldznu2Model)c.getDStabilityNet().getDStabBus("Bus2").getInfoOnlyDynModel();assertEquals(1,owner.getSourceModels().size());
    }
    @Test void explicitLegacyBusAssignmentIgnoresSubsystemThresholds()throws Exception {
        var c=load("cmldblu1","{\"loadModelThresholds\":{\"minimumMw\":1000000,\"minimumAbsPOverQ\":1000000,\"minimumVoltagePu\":2}}");
        assertTrue(c.getDynSimuAlgorithm().initialization());
        var r=((PsseDStabSolver)c.getDynSimuAlgorithm().getSolver()).getLoadModelScreeningReport();assertEquals(1,r.size());
        assertFalse(r.get(0).thresholdsConsulted());assertFalse(r.get(0).bypassed());
        var model=(Cmldznu2Model)c.getDStabilityNet().getDStabBus("Bus2").getInfoOnlyDynModel();assertNotNull(model.getLoadBus());
    }
    @Test void allThreeSubsystemThresholdBranchesHaveAnAuditedStaticFallback()throws Exception {
        for(String thresholds:new String[]{"0,1000000,0","0,0,2"}) {
            String[] a=thresholds.split(",");var c=load("cmldznu2","{\"loadModelThresholds\":{\"minimumMw\":"+a[0]+",\"minimumAbsPOverQ\":"+a[1]+",\"minimumVoltagePu\":"+a[2]+"}}");
            assertTrue(c.getDynSimuAlgorithm().initialization());var model=(Cmldznu2Model)c.getDStabilityNet().getDStabBus("Bus2").getInfoOnlyDynModel();
            assertTrue(model.getInitializationScreen().bypassed());assertNull(model.getLoadBus());assertNotNull(c.getDStabilityNet().getDStabBus("Bus2").getContributeLoad("1"));
        }
    }
    @Test void explicitReconciliationUpdatesOnlyTheSourceImpedanceAndIsReported()throws Exception {
        for(boolean enabled:new boolean[]{false,true}) {
            var c=load("cmldblu1","{\"sourceImpedanceReconciliationEnabled\":"+enabled+"}");
            var g=(com.interpss.dstab.DStabGen)c.getDStabilityNet().getDStabBus("Bus1").getContributeGen("1");g.setSourceZ(new org.apache.commons.math3.complex.Complex(0,.29));g.setPosGenZ(g.getSourceZ());
            double pg=g.getGen().getReal();assertTrue(c.getDynSimuAlgorithm().initialization());
            assertEquals(enabled?.089:.29,g.getSourceZ().getImaginary(),1e-10);assertEquals(pg,g.getGen().getReal(),1e-10);
            var report=((PsseDStabSolver)c.getDynSimuAlgorithm().getSolver()).getSourceImpedanceReconciliationReport();
            var row=report.stream().filter(v->v.bus().equals("Bus1")).findFirst().orElseThrow();assertEquals(enabled,row.changed());assertEquals(.29,row.originalReactance(),1e-12);
        }
    }
    @Test void loadRelayResolvesScreenedAssignmentAsItsRemainingStaticSource()throws Exception {
        var c=load("cmldznu2","{\"loadModelThresholds\":{\"minimumMw\":50,\"minimumAbsPOverQ\":0,\"minimumVoltagePu\":0}}");
        var bus=c.getDStabilityNet().getDStabBus("Bus2");
        var stage=new com.interpss.dstab.relay.model.LoadSheddingStage(61,0,0,.25);
        var off=new com.interpss.dstab.relay.model.LoadSheddingStage(0,0,0,0);
        var relay=new org.interpss.dstab.relay.Lds3blRelayModel(bus,"1",
                new com.interpss.dstab.relay.model.StagedLoadSheddingRelayData(java.util.List.of(stage,off,off,off,off),false),null,"",0);
        assertTrue(c.getDynSimuAlgorithm().initialization());bus.setFreq(1.);
        assertTrue(relay.afterStep(.001));assertEquals(.25,relay.getShedFraction(),1e-12);
        assertNotNull(bus.getContributeLoad("1"));
    }
    @Test void governorPolicyReferenceIsRegisteredWithoutExecutingItsProgram()throws Exception {
        Path policy=file("policy.bsg","1,'Synthetic',230.,'1',0\n");Path run=file("governor.idv","EXEC Gov_blk_R345.IRF\npolicy.bsg\n1\n");
        var loader=new PSSEMultiFileLoader().setAutoDiscoverCasePreparationFiles(false).setRunConfigurationFiles(run);
        loader.loadDStab(FIXTURE.resolve("load-network.raw").toString(),FIXTURE.resolve("cmldblu1.dyr").toString(),policy.toString());
        assertEquals(1,loader.getLastGovernorBlockingEntries().size());
        var implicit=new PSSEMultiFileLoader().setAutoDiscoverCasePreparationFiles(false).setRunConfigurationFiles(run);
        implicit.loadDStab(FIXTURE.resolve("load-network.raw").toString(),FIXTURE.resolve("cmldblu1.dyr").toString());
        assertEquals(1,implicit.getLastGovernorBlockingEntries().size());
    }
    @Test void nettingBlockIsOneSourceLocatedElectricalPreparationOperation()throws Exception {
        var r=PsseIdvRunConfigConverter.convert(file("gnet.idv","GNET\n101\n102\n0\n"));assertTrue(r.fullyConverted());
        assertEquals(1,r.commands().size());assertEquals(PsseIdvRunConfigConverter.Disposition.PREPARATION,r.commands().get(0).disposition());
    }
    @Test void importedNetfrqAndJsonOverrideApplyToBranchesAndFixedShunts() throws Exception {
        var network = (DStabilityNetworkImpl) DStabObjectFactory.createDStabilityNetwork();
        var from = DStabObjectFactory.createDStabBus("from", network).orElseThrow();
        var to = DStabObjectFactory.createDStabBus("to", network).orElseThrow();
        from.setSortNumber(0);
        to.setSortNumber(1);
        network.setBusNumberArranged(true);
        network.setPolarCoordinate(false);
        network.setStaticLoadIncludedInYMatrix(true);
        var branch = DStabObjectFactory.createDStabBranch("from", "to", network);
        branch.setBranchCode(AclfBranchCode.LINE);
        branch.setZ(new Complex(.02, .2));
        var fixedShunt = new Complex(0., .12);
        from.setShuntY(fixedShunt);
        var ground = new Complex(.8, -.2);
        var y = new JKLUSparseEqnComplexImpl(2);
        y.setA(ground.add(branch.yff()).add(fixedShunt), 0, 0);
        y.setA(branch.yft(), 0, 1);
        y.setA(branch.ytf(), 1, 0);
        y.setA(ground.add(branch.ytt()), 1, 1);
        network.setYMatrix(y);
        network.setYMatrixDirty(true);
        var injection = new Hashtable<String, Complex>();
        network.setCustomBusCurrInjHashtable(injection);
        var algorithm = DStabObjectFactory.createDynamicSimuAlgorithm(network);
        var solver = new PsseDStabSolver(algorithm);
        algorithm.setSolver(solver);
        solver.setNetworkVoltageTolerance(1e-10);
        from.setVoltage(Complex.ONE);
        to.setVoltage(Complex.ONE);
        from.setFreq(1.05);
        to.setFreq(.99);
        var imported = PsseIdvRunConfigConverter.convert(file("frequency.idv", "BAT_SET_NETFRQ,1\n")).config();
        assertTrue(imported.applyTo(algorithm, true).isEmpty());
        assertTrue(DStabRunConfigRec.fromJson("{}").applyTo(algorithm, true).isEmpty());
        var expectedFrom = new Complex(1.02, .04);
        var expectedTo = new Complex(.97, -.03);
        var series = expectedFrom.subtract(expectedTo).divide(new Complex(.02, .2 * 1.02));
        injection.put("from", series.add(expectedFrom.multiply(ground.add(fixedShunt.multiply(1.05)))));
        injection.put("to", series.negate().add(expectedTo.multiply(ground)));
        assertTrue(solver.networkSolutionStep());
        assertEquals(0., from.getVoltage().subtract(expectedFrom).abs(), 1e-8);
        assertEquals(0., to.getVoltage().subtract(expectedTo).abs(), 1e-8);

        var disabled = DStabRunConfigRec.overlayJson(imported, "{\"networkFrequencyDependenceEnabled\":false}");
        assertTrue(disabled.applyTo(algorithm, true).isEmpty());
        series = expectedFrom.subtract(expectedTo).divide(branch.getAdjustedZ());
        injection.put("from", series.add(expectedFrom.multiply(ground.add(fixedShunt))));
        injection.put("to", series.negate().add(expectedTo.multiply(ground)));
        assertTrue(solver.networkSolutionStep());
        assertEquals(0., from.getVoltage().subtract(expectedFrom).abs(), 1e-8);
        assertEquals(0., to.getVoltage().subtract(expectedTo).abs(), 1e-8);
    }
    @Test void importedLineEndShuntsUseLocalFrequencyIndependentlyOfCharging() throws Exception {
        Path rawx = file("line-shunts.rawx", """
                {"network":{
                  "caseid":{"fields":["sbase","basfrq"],"data":[100.0,60.0]},
                  "bus":{"fields":["ibus","name","baskv","ide","vm","va"],
                         "data":[[1,"FROM",230.0,1,1.0,0.0],[2,"TO",230.0,1,1.0,0.0]]},
                  "acline":{"fields":["ibus","jbus","ckt","rpu","xpu","bpu","gi","bi","gj","bj","stat"],
                            "data":[[1,2,"1",0.015,0.22,0.08,0.12,-0.04,0.06,0.05,1]]}
                }}
                """);
        var loader = new PSSEMultiFileLoader()
                .setRunConfigurationFiles(file("line-shunts.idv", "BAT_SET_NETFRQ,1\n"))
                .setRequireMatchedRunSettings(true);
        var context = loader.loadDStab(rawx.toString());
        assertTrue(loader.getLastRunConfigApplicationGaps().isEmpty());

        var network = context.getDStabilityNet();
        var branch = network.getBranch("Bus1", "Bus2", "1");
        assertEquals(new Complex(0., .04), branch.getHShuntY());
        assertEquals(new Complex(.12, -.04), branch.getFromShuntY());
        assertEquals(new Complex(.06, .05), branch.getToShuntY());
        var from = network.getDStabBus("Bus1");
        var to = network.getDStabBus("Bus2");
        from.setFreq(1.05);
        to.setFreq(.97);
        network.setPolarCoordinate(false);
        network.formYMatrix4DStab();

        var injection = new Hashtable<String, Complex>();
        network.setCustomBusCurrInjHashtable(injection);
        var algorithm = context.getDynSimuAlgorithm();
        var solver = (org.interpss.dstab.algo.DefaultDynamicSimulationSolver) algorithm.getSolver();
        solver.setNetworkVoltageTolerance(1.e-10);

        var expectedFrom = new Complex(1.02, .04);
        var expectedTo = new Complex(.97, -.03);
        var voltageDrop = expectedFrom.subtract(expectedTo);
        // Series reactance uses mean frequency. The from-end reactor cancels
        // charging at unity frequency, but the two terms scale oppositely.
        var series = voltageDrop.divide(new Complex(.015, .22 * 1.01));
        var fromShunt = new Complex(.12, .04 * 1.05 - .04 / 1.05);
        var toShunt = new Complex(.06, (.04 + .05) * .97);
        injection.put("Bus1", series.add(expectedFrom.multiply(fromShunt)));
        injection.put("Bus2", series.negate().add(expectedTo.multiply(toShunt)));
        assertTrue(solver.networkSolutionStep());
        assertEquals(0., from.getVoltage().subtract(expectedFrom).abs(), 1.e-8,
                "from end with frequency dependence");
        assertEquals(0., to.getVoltage().subtract(expectedTo).abs(), 1.e-8,
                "to end with frequency dependence");

        var disabled = DStabRunConfigRec.fromJson("{\"networkFrequencyDependenceEnabled\":false}");
        assertTrue(disabled.applyTo(algorithm, true).isEmpty());
        series = voltageDrop.divide(new Complex(.015, .22));
        injection.put("Bus1", series.add(expectedFrom.multiply(.12)));
        injection.put("Bus2", series.negate().add(expectedTo.multiply(new Complex(.06, .09))));
        assertTrue(solver.networkSolutionStep());
        assertEquals(0., from.getVoltage().subtract(expectedFrom).abs(), 1.e-8,
                "from end with frequency dependence disabled");
        assertEquals(0., to.getVoltage().subtract(expectedTo).abs(), 1.e-8,
                "to end with frequency dependence disabled");
    }

    @Test void strictApplicationRejectsPhysicalGapsBeforeMutatingAnySetting()throws Exception {
        var c=load("cmldblu1","{}");double step=c.getDynSimuAlgorithm().getSimuStepSec();
        var cfg=DStabRunConfigRec.fromJson("{\"stepSec\":0.01,\"sourceNetworkAccelerationFactor\":0.4}");
        assertThrows(IllegalArgumentException.class,()->cfg.applyTo(c.getDynSimuAlgorithm(),true));assertEquals(step,c.getDynSimuAlgorithm().getSimuStepSec());
    }
}
