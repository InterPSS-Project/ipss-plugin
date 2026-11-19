package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.lang.reflect.*;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import com.google.gson.Gson;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.annotate.AbstractAnnotateController;
import com.interpss.dstab.controller.cml.field.ICMLControlBlock;
import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;
import com.interpss.dstab.mach.Machine;
import org.interpss.CorePluginTestSetup;
import com.interpss.dstab.controller.gov.ggov1.*;
import com.interpss.dstab.controller.gov.hygov.*;
import com.interpss.dstab.controller.gov.tgov1.*;
import com.interpss.dstab.controller.gov.ieeehydro1981type3.*;
import com.interpss.dstab.controller.gov.weccwshygp.*;
import com.interpss.dstab.controller.gov.weccwsieg1.*;
import org.interpss.fadapter.psse.*;
import com.interpss.dstab.algo.dynamic.*;
import org.interpss.fadapter.psse.PsseGovernorBsgProcessor.Status;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Synthetic policy/lifecycle checks; no private case inputs or surrogate equations. */
public class GovernorBsgPolicyTest extends CorePluginTestSetup {
    @TempDir Path directory;
    private static final Path HSP=Path.of("testData/adpter/psse/v33/HspModels");
    private static final double[] HYDRO={.002,.0003,.08,5.5,.06,.12,8.5,.045,.12,.9,.15,.20,.18,1.10,0,.001,
        0,.02,.23,.20,.47,.48,.76,.78,1.05,1.08,-.30,.55,1.30,0};
    private record Fixture(Machine machine,DoubleSupplier position,DoubleSupplier maximum,
            DoubleConsumer setter,java.util.function.Supplier<Object> data) { }
    private Fixture fixture(String model) throws Exception {
        var builder=DStabBuilderTestFixture.createWithMachine();var m=builder.getDStabNetwork().getMachine("Bus1-mach1");
        m.setPm(.5);m.setPe(.5);m.setSpeed(1.);
        Fixture f=switch(model) {
            case "TGOV1" -> {var g=builder.addGovTgov1("Bus1","1",.05,.4,1.2,0,2,8,0);
                yield new Fixture(m,g::getValvePosition,()->g.vmax,g::applyInitialUpperTravelLimit,g::getData);}
            case "GGOV1" -> {var d=new PsseGgov1GovernorData();d.setTeng(.05);var g=builder.addGovGgov1("Bus1","1",d);
                yield new Fixture(m,g::getValveStroke,g::getEffectiveVmax,g::applyInitialUpperTravelLimit,g::getData);}
            case "HYGOV" -> {var g=builder.addGovHygov("Bus1","1",new PsseHygovGovernorData());
                yield new Fixture(m,g::getGatePosition,g::getEffectiveGmax,g::applyInitialUpperTravelLimit,g::getData);}
            case "IEEEG3" -> {var g=builder.addGovIeeeg3("Bus1","1",.52,.1,.1,-.1,1.2,0,.058,.4,8,1.4,.2,1,.6,1);
                yield new Fixture(m,g::getGatePosition,()->g.pmax,g::applyInitialUpperTravelLimit,g::getData);}
            case "WSHYGP" -> {var g=builder.addGovWshygp("Bus1","1",HYDRO.clone());
                yield new Fixture(m,g::getGatePosition,g::getEffectiveMaximum,g::applyInitialUpperTravelLimit,g::getData);}
            case "WSIEG1" -> {double[] c=new double[34];c[0]=20;c[1]=.08;c[2]=.02;c[3]=.15;c[4]=.2;c[5]=-.2;c[6]=1.2;
                c[8]=.1;c[9]=.3;c[11]=.3;c[12]=.4;c[14]=.2;c[15]=.3;c[25]=1;c[26]=1;
                var g=new Wsieg1Governor("synthetic",c);g.setMachine(m);
                yield new Fixture(m,g::getValvePosition,g::getEffectiveMaximum,g::applyInitialUpperTravelLimit,g::getConstants);}
            default -> throw new IllegalArgumentException(model);
        };
        assertTrue(m.getGovernor().initStates(m.getDStabBus(),m));
        network(m).setDStabNetInitialized(true);return f;
    }
    private static com.interpss.dstab.BaseDStabNetwork<?,?> network(Machine machine) {
        return (com.interpss.dstab.BaseDStabNetwork<?,?>) machine.getDStabBus().getNetwork();
    }
    private List<PsseGovernorBsgProcessor.Entry> rows(String text)throws Exception {
        Path path=directory.resolve("governor.bsg");Files.writeString(path,text);return PsseGovernorBsgProcessor.read(path);
    }
    private static List<?> histories(Machine m)throws Exception {
        Object g=m.getGovernor();List<Object> values=new ArrayList<>();
        for(String name:List.of("oldState","oldDerivatives","predictor","pref","reference","engineHistory","simulationTime",
                "committedFsr","currentFsr","committedDesiredGate","old","d0","d1","gateMemory","backlashInputMemory","referenceRevision")) {
            try {Field field=g.getClass().getDeclaredField(name);field.setAccessible(true);Object value=field.get(g);
                if(value instanceof double[] array)value=Arrays.toString(array);
                if(value instanceof Collection<?> collection)value=new ArrayList<>(collection);
                values.add(name);values.add(value);
            }catch(NoSuchFieldException absent){ }
        }
        if(g instanceof AbstractAnnotateController c && c.getFieldWrapperList()!=null)for(var wrapper:c.getFieldWrapperList())
            if(wrapper.getField() instanceof ICMLControlBlock block)values.add(List.of(wrapper.getFieldName(),block.getStateX(),(block instanceof com.interpss.dstab.controller.cml.field.adapt.CMLControlBlock1stOrderAdapter first ? first.getDX_dt() : 0.),block.getU()));
        return values;
    }
    private static Object states(Machine m) {
        return ((com.interpss.dstab.controller.cml.ICMLStateProvider)m.getGovernor()).getNamedStates();
    }
    @ParameterizedTest @ValueSource(strings={"TGOV1","GGOV1","HYGOV","IEEEG3","WSHYGP","WSIEG1"})
    void bothNonzeroCodesCapAtInitializedTravelWithoutChangingStatesDataOrHistories(String model)throws Exception {
        for(int code:new int[]{1,2}) {
            var f=fixture(model);double position=f.position.getAsDouble();double old=f.maximum.getAsDouble();
            String data=new Gson().toJson(f.data.get());Object states=states(f.machine);var history=histories(f.machine);
            double output=f.machine.getGovernor().getOutput(f.machine);
            var report=PsseGovernorBsgProcessor.apply(network(f.machine),rows("// source line\n1, 'GEN, O''Brien', 16.5, '1 ', "+code+"\n"));
            var e=report.getFirst();assertEquals(Status.APPLIED,e.status());assertEquals(2,e.line());assertTrue(e.source().endsWith("governor.bsg"));
            assertEquals(position,e.position(),0.);assertEquals(old,e.oldMaximum(),0.);assertEquals(position,e.newMaximum(),0.);
            assertEquals(position,f.maximum.getAsDouble(),1e-15,"no artificial epsilon");
            assertEquals(data,new Gson().toJson(f.data.get()));assertEquals(states,states(f.machine));assertEquals(history,histories(f.machine));
            assertEquals(output,f.machine.getGovernor().getOutput(f.machine),0.);
            assertThrows(IllegalArgumentException.class,()->f.setter.accept(Double.NaN));assertEquals(position,f.maximum.getAsDouble(),1e-15);
            assertThrows(UnsupportedOperationException.class,()->report.clear());
        }
    }
    @ParameterizedTest @ValueSource(strings={"TGOV1","GGOV1","HYGOV","IEEEG3","WSHYGP","WSIEG1"})
    void zeroCodeIsNormalAndReinitializationRestoresEnteredLimits(String model)throws Exception {
        var f=fixture(model);double original=f.maximum.getAsDouble();var before=histories(f.machine);Object states=states(f.machine);
        var report=PsseGovernorBsgProcessor.apply(network(f.machine),rows("1, 'GEN', 16.5, '1', 0"));
        assertEquals(Status.NORMAL,report.getFirst().status());assertEquals(original,f.maximum.getAsDouble(),0.);assertEquals(before,histories(f.machine));assertEquals(states,states(f.machine));
        f.setter.accept(f.position.getAsDouble());assertTrue(f.machine.getGovernor().initStates(f.machine.getDStabBus(),f.machine));
        assertEquals(original,f.maximum.getAsDouble(),0.);
        // An inactive cap cannot change a valid equilibrium under ordinary integration.
        f.setter.accept(f.position.getAsDouble());
        for(int tick=0;tick<10;tick++)for(int flag=0;flag<2;flag++)assertTrue(f.machine.getGovernor().nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,f.machine,flag));
        assertEquals(.5,f.machine.getGovernor().getOutput(f.machine),1e-10);
    }
    @Test void tgovUpdatesTheActualNumericLimiterUnderCompiledAndCompatibleExecution()throws Exception {
        var f=fixture("TGOV1");var g=(PsseTGov1SteamTurGovernor)f.machine.getGovernor();
        var wrapper=g.getFieldWrapperList().stream().filter(w->w.getFieldName().equals("t1DelayBlock")).findFirst().orElseThrow();
        var block=(DelayControlBlock)wrapper.getField();assertNull(block.getLimit().getMaxExp());double state=block.getStateX(),rate=block.getDX_dt();
        g.compileRuntimePlan();f.setter.accept(.55);assertSame(block,wrapper.getField());assertEquals(.55,block.getLimit().getMax(),1e-15);
        assertEquals(state,block.getStateX(),0.);assertEquals(rate,block.getDX_dt(),0.);
        f.machine.setSpeed(.95);
        for(int tick=0;tick<1000;tick++)for(int flag=0;flag<2;flag++)assertTrue(g.nextStep(.001,DynamicSimuMethod.MODIFIED_EULER,f.machine,flag));
        assertTrue(g.getValvePosition()<=.55+1e-12);assertTrue(g.getValvePosition()>.5);
    }
    @Test void formatValidationAndExplicitSkipOutcomes()throws Exception {
        var f=fixture("HYGOV");var net=network(f.machine);
        for(String bad:List.of("1 'GEN' 16.5 '1' 1","1, GEN, 16.5, '1', 1","1, 'GEN', 16.5, '1', 3","1, 'GEN', NaN, '1', 1",
                "1, 'unterminated, 16.5, '1', 1","1, 'GEN', 16.5, '1', 1\n1, 'GEN', 16.5, '1', 2"))
            assertThrows(java.io.IOException.class,()->rows(bad));
        var missing=PsseGovernorBsgProcessor.apply(net,rows("999, 'missing', 16.5, '1', 1"));assertEquals(Status.MISSING_TARGET,missing.getFirst().status());
        f.machine.setStatus(false);assertEquals(Status.INACTIVE_TARGET,PsseGovernorBsgProcessor.apply(net,rows("1, 'GEN', 16.5, '1', 1")).getFirst().status());f.machine.setStatus(true);
        f.machine.getGovernor().setName("HYGOVD");assertEquals(Status.UNSUPPORTED_MODEL,PsseGovernorBsgProcessor.apply(net,rows("1, 'GEN', 16.5, '1', 1")).getFirst().status());
        net.setDStabNetInitialized(false);assertThrows(com.interpss.common.exp.InterpssException.class,()->PsseGovernorBsgProcessor.apply(net,rows("1, 'GEN', 16.5, '1', 1")));
        Path base=directory.resolve("base.bsg");Files.writeString(base,"1 \"GEN, NAME\" 16.5 \"1\" 1");assertFalse(PsseGovernorBsgProcessor.isGovernorFile(base));
    }
    @Test void bothBsgContractsCanBePassedTogetherWithoutCrossApplyingPolicies()throws Exception {
        Path source=Path.of("testData/adpter/psse/v33/renewable");
        Path renewable=directory.resolve("renewable.bsg"),governor=directory.resolve("synchronous.bsg");
        Files.writeString(renewable,"1 \"GEN, RENEWABLE\" 230 \"1\" 1\n");
        Files.writeString(governor,"1, 'GEN', 230, '1', 1\n");
        var loader=new PSSEMultiFileLoader().setAutoDiscoverCasePreparationFiles(false);
        var context=loader.loadDStab(source.resolve("regca_reeca_repca_bus1062.raw").toString(),
                source.resolve("regca_reeca_repca_bus1062.dyr").toString(),renewable.toString(),governor.toString());
        assertEquals(1,loader.getLastResponseEntries().size());assertEquals(1,loader.getLastGovernorBlockingEntries().size());
        var solver=(PsseDStabSolver)context.getDynSimuAlgorithm().getSolver();assertEquals(Status.REGISTERED,solver.getGovernorBlockingReport().getFirst().status());
        context.getDStabilityNet().setDStabNetInitialized(true);
        assertEquals(Status.UNSUPPORTED_MODEL,PsseGovernorBsgProcessor.apply(context.getDStabilityNet(),solver.getGovernorBlockingReport()).getFirst().status());
    }
    @Test void failedNumericLookupRetainsSourceAndContinuesReportingOtherRows()throws Exception {
        var f=fixture("TGOV1");var g=(PsseTGov1SteamTurGovernor)f.machine.getGovernor();
        g.getFieldWrapperList().removeIf(w->w.getFieldName().equals("t1DelayBlock"));
        var report=PsseGovernorBsgProcessor.apply(network(f.machine),rows("1, 'GEN', 16.5, '1', 1\n999, 'missing', 16.5, '1', 1"));
        assertEquals(List.of(Status.ERROR,Status.MISSING_TARGET),report.stream().map(PsseGovernorBsgProcessor.Entry::status).toList());
        assertEquals(1,report.getFirst().line());assertTrue(report.getFirst().source().endsWith("governor.bsg"));assertNull(report.getFirst().newMaximum());
    }
    @Test void nonlinearAndClippedWsiegUseProjectedValveAndRetainConflictingBounds()throws Exception {
        for(boolean clipped:new boolean[]{false,true}) {
            var f=fixture("WSIEG1");var old=(Wsieg1Governor)f.machine.getGovernor();double[] c=old.getConstants();
            c[25]=.4;c[26]=.3;c[27]=.7;c[28]=.75;c[29]=1;c[30]=1;c[6]=clipped?.2:1.2;
            f.machine.setPm(.5000100135803223);f.machine.getControllerList().remove(old);var g=new Wsieg1Governor("nonlinear",c);g.setMachine(f.machine);assertTrue(g.initStates(f.machine.getDStabBus(),f.machine));
            assertEquals(.5333399772644043,g.getInitialReference(),1e-6);assertEquals(clipped?.2:.5333399772644043,g.getValvePosition(),1e-6);
            var state=g.getIntegrationStates();var history=g.getIntegrationHistories();
            var e=PsseGovernorBsgProcessor.apply(network(f.machine),rows("1, 'GEN', 16.5, '1', 1")).getFirst();
            assertEquals(clipped?Status.UNCHANGED:Status.APPLIED,e.status());assertEquals(g.getValvePosition(),g.getEffectiveMaximum(),0.);
            assertArrayEquals(c,g.getConstants(),0.);assertEquals(state,g.getIntegrationStates());assertEquals(history,g.getIntegrationHistories());
        }
        var f=fixture("WSIEG1");var old=(Wsieg1Governor)f.machine.getGovernor();double[] c=old.getConstants();c[6]=0;c[7]=.7;c[33]=2;
        f.machine.getControllerList().remove(old);var g=new Wsieg1Governor("conflicting",c);g.setMachine(f.machine);assertTrue(g.initStates(f.machine.getDStabBus(),f.machine));
        double lower=g.getEffectiveMinimum();var state=g.getIntegrationStates();
        PsseGovernorBsgProcessor.apply(network(f.machine),rows("1, 'GEN', 16.5, '1', 2"));
        assertEquals(lower,g.getEffectiveMinimum(),0.);assertEquals(state,g.getIntegrationStates());assertArrayEquals(c,g.getConstants(),0.);
    }
    @Test void sharedShaftPolicyUpdatesTheOwnerOnce()throws Exception {
        var context=new PSSEMultiFileLoader().setAutoDiscoverCasePreparationFiles(false).loadDStab(HSP.resolve("dual-network.raw").toString(),HSP.resolve("wsieg1-dual.dyr").toString());
        var net=context.getDStabilityNet();for(String id:List.of("Bus1-mach1","Bus2-mach1")){var m=net.getMachine(id);m.setPm(.4);m.setPe(.4);m.setSpeed(1);assertTrue(m.getGovernor().initStates(m.getDStabBus(),m));}
        net.setDStabNetInitialized(true);var g=(Wsieg1Governor)net.getMachine("Bus1-mach1").getGovernor();var history=g.getIntegrationHistories();
        var report=PsseGovernorBsgProcessor.apply(net,rows("2, 'secondary', 16.5, '1', 1\n1, 'primary', 16.5, '1', 1"));
        assertEquals(List.of(Status.APPLIED,Status.DUPLICATE_OWNER),report.stream().map(PsseGovernorBsgProcessor.Entry::status).toList());assertEquals(history,g.getIntegrationHistories());
    }
    @Test void loaderRegistersAndSolverAppliesAfterFinalInitialization()throws Exception {
        var file=directory.resolve("governor.bsg");Files.writeString(file,"1, 'GEN', 13.8, '1', 2\n999, 'missing', 13.8, '1', 1");
        var loader=new PSSEMultiFileLoader().setAutoDiscoverCasePreparationFiles(false);
        var context=loader.loadDStab(HSP.resolve("network.raw").toString(),HSP.resolve("wsieg1.dyr").toString(),file.toString());
        var a=context.getDynSimuAlgorithm();var solver=assertInstanceOf(PsseDStabSolver.class,a.getSolver());
        assertEquals(2,loader.getLastGovernorBlockingEntries().size());assertEquals(Status.REGISTERED,solver.getGovernorBlockingReport().getFirst().status());
        assertTrue(a.getAclfAlgorithm().loadflow());a.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);a.setSimuStepSec(.0005);a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());assertTrue(a.initialization());
        var g=(Wsieg1Governor)context.getDStabilityNet().getMachine("Bus1-mach1").getGovernor();assertEquals(g.getValvePosition(),g.getEffectiveMaximum(),0.);
        assertEquals(Status.APPLIED,solver.getGovernorBlockingReport().getFirst().status());assertEquals(Status.MISSING_TARGET,solver.getGovernorBlockingReport().get(1).status());
        assertThrows(IllegalStateException.class,()->solver.setGovernorBlockingPolicy(List.of()));
        context.getDStabilityNet().setDStabNetInitialized(false);assertTrue(a.initialization());
        assertEquals(Status.APPLIED,solver.getGovernorBlockingReport().getFirst().status());
        assertEquals(g.getConstants()[6],solver.getGovernorBlockingReport().getFirst().oldMaximum(),0.);
        loader.loadDStab(HSP.resolve("network.raw").toString());assertTrue(loader.getLastGovernorBlockingEntries().isEmpty());
    }

    /** Import the public two-bus RAW/DYR seam so ID lookup sees actual generator
     * records and initialized hosts, including an inactive case-variant record.
     * Condensers retain negative source PG and deliberately have no governor. */
    private com.interpss.dstab.BaseDStabNetwork<?,?> idLookupFixture(String primaryId,
            boolean condenser,String... inactiveIds)throws Exception {
        Path source=Path.of("testData/adpter/psse/v33/SMIB");
        var lines=new ArrayList<>(Files.readAllLines(source.resolve("SMIB_v33.raw")));
        int index=java.util.stream.IntStream.range(0,lines.size())
                .filter(i->lines.get(i).startsWith("1,'1',")).findFirst().orElseThrow();
        String original=lines.get(index);var primary=original.split(",");
        primary[1]="'"+primaryId+"'";if(condenser)primary[2]="-0.1";
        lines.set(index,String.join(",",primary));
        for(String id:inactiveIds) {
            var other=original.split(",");other[1]="'"+id+"'";other[14]="0";
            lines.add(++index,String.join(",",other));
        }
        Path raw=directory.resolve("lookup.raw"),dyr=directory.resolve("lookup.dyr");
        Files.write(raw,lines);
        String dynamic=Files.readString(source.resolve("SMIB_v33_genrou_tgov1.dyr"))
                .replace("1 'GENROU' 1 ","1 'GENROU' '"+primaryId+"' ")
                .replace("1 'TGOV1' 1 ","1 'TGOV1' '"+primaryId+"' ");
        if(condenser)dynamic=dynamic.lines().filter(line->!line.contains("'TGOV1'"))
                .collect(java.util.stream.Collectors.joining("\n","", "\n"));
        Files.writeString(dyr,dynamic);
        var context=new PSSEMultiFileLoader().setAutoDiscoverCasePreparationFiles(false)
                .loadDStab(raw.toString(),dyr.toString());
        var algorithm=context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algorithm.setSimuStepSec(.001);
        algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        assertTrue(algorithm.initialization());
        return context.getDStabilityNet();
    }

    @Test void exactGeneratorIdWinsOverOtherCaseVariant()throws Exception {
        var net=idLookupFixture("S1",false,"s1");
        var selected=net.getDStabBus("Bus1").getContributeGen("S1");
        var report=PsseGovernorBsgProcessor.apply(net,rows("1, 'GEN', 100, 'S1', 1")).getFirst();
        assertEquals(Status.APPLIED,report.status());
        assertEquals("S1",report.generator().generatorId());assertEquals("S1",report.resolvedGeneratorId());
        assertEquals("S1",selected.getId());assertFalse(net.getDStabBus("Bus1").getContributeGen("s1").isActive());
    }

    @Test void uniqueCaseFallbackPreservesPolicyAndRawIdsAndOwnerIdentity()throws Exception {
        var net=idLookupFixture("S1",false);
        var policy=rows("1, 'GEN', 100, 's1', 1\n1, 'GEN', 100, 'S1', 1");
        var report=PsseGovernorBsgProcessor.apply(net,policy);
        assertEquals(List.of(Status.APPLIED,Status.DUPLICATE_OWNER),
                report.stream().map(PsseGovernorBsgProcessor.Entry::status).toList());
        var entry=report.getFirst();
        assertEquals("s1",entry.generator().generatorId());assertEquals("s1",entry.fields().get(3));
        assertEquals("S1",entry.resolvedGeneratorId());assertEquals("S1",report.get(1).resolvedGeneratorId());
        assertNull(policy.getFirst().resolvedGeneratorId());
        assertTrue(new Gson().toJson(entry).contains("\"resolvedGeneratorId\":\"S1\""));
        assertEquals("S1",net.getDStabBus("Bus1").getContributeGen("S1").getId());
        // Replay against a different membership must not retain stale resolution metadata.
        net.getDStabBus("Bus1").getContributeGen("S1").setId("Z1");
        var missing=PsseGovernorBsgProcessor.apply(net,List.of(entry)).getFirst();
        assertEquals(Status.MISSING_TARGET,missing.status());assertNull(missing.resolvedGeneratorId());
        assertEquals("s1",missing.generator().generatorId());
    }

    @ParameterizedTest @ValueSource(strings={"S1","S2"})
    void caseFallbackClassifiesNegativePowerCondenserWithoutGovernor(String id)throws Exception {
        var net=idLookupFixture(id,true);var gen=net.getDStabBus("Bus1").getContributeGen(id);
        assertTrue(gen.getGen().getReal()<0);assertNull(((com.interpss.dstab.DStabGen)gen).getMach().getGovernor());
        var entry=PsseGovernorBsgProcessor.apply(net,rows("1, 'COND', 100, '"+id.toLowerCase(java.util.Locale.ROOT)+"', 2")).getFirst();
        assertEquals(Status.UNSUPPORTED_MODEL,entry.status());assertEquals(id,entry.resolvedGeneratorId());
        assertEquals(id.toLowerCase(java.util.Locale.ROOT),entry.generator().generatorId());
        assertTrue(entry.message().contains("No synchronous machine/governor host"));
        assertNull(entry.newMaximum());
    }

    @Test void uniqueCaseFallbackStillReportsInactiveGenerator()throws Exception {
        var net=idLookupFixture("S1",false,"S2");
        var entry=PsseGovernorBsgProcessor.apply(net,rows("1, 'OFFLINE', 100, 's2', 1")).getFirst();
        assertEquals(Status.INACTIVE_TARGET,entry.status());assertEquals("S2",entry.resolvedGeneratorId());
        assertEquals("s2",entry.generator().generatorId());assertNull(entry.newMaximum());
    }

    @Test void ambiguousFallbackRejectsInactiveAndActiveCandidatesWithoutMutation()throws Exception {
        var net=idLookupFixture("Sa",false,"sA");
        var machine=((com.interpss.dstab.DStabGen)net.getDStabBus("Bus1").getContributeGen("Sa")).getMach();
        var governor=(PsseTGov1SteamTurGovernor)machine.getGovernor();
        double original=governor.vmax;var originalStates=states(machine);var originalHistory=histories(machine);
        var report=PsseGovernorBsgProcessor.apply(net,rows("1, 'GEN', 100, 'sa', 1\n999, 'ABSENT', 100, 'sa', 1"));
        assertEquals(List.of(Status.ERROR,Status.MISSING_TARGET),
                report.stream().map(PsseGovernorBsgProcessor.Entry::status).toList());
        var entry=report.getFirst();assertNull(entry.resolvedGeneratorId());assertNull(entry.newMaximum());
        assertEquals("sa",entry.generator().generatorId());
        assertTrue(entry.message().contains("Ambiguous"));assertTrue(entry.message().contains("Sa"));assertTrue(entry.message().contains("sA"));
        assertEquals(original,governor.vmax,0);assertEquals(originalStates,states(machine));assertEquals(originalHistory,histories(machine));
    }

}
