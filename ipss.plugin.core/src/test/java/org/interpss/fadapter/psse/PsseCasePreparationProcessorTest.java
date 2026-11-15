package org.interpss.fadapter.psse;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.dyr.DynamicModelImportStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.*;
import com.interpss.core.net.OriginalDataFormat;
import com.interpss.dstab.DStabObjectFactory;

/** Local preparation order, source identity, blank-field retention and electrical-balance coverage. */
class PsseCasePreparationProcessorTest {
    @TempDir Path temp;
    private Path write(String name,String text) throws Exception {
        Path path=temp.resolve(name);Files.createDirectories(path.getParent());Files.writeString(path,text);return path;
    }
    private com.interpss.dstab.DStabilityNetwork network() throws Exception {
        var net=DStabObjectFactory.createDStabilityNetwork();var b=new AclfNetworkBuilder(net);
        b.setNetworkInfo("preparation","preparation",100000,OriginalDataFormat.PSSE);
        b.addArea("1","area",null);b.addZone("1","zone",null);b.addOwner("1","owner");
        b.addBus("Bus1","host",1L,138000,1,0,"1","1","1");
        b.setPVBus("Bus1",.5,1,.5,-.5,true);
        b.addContributeGen("Bus1","1",true,.5,.1,100,1,.5,-.5,1,0,new Complex(0,.2),null,1,null,100,0);
        b.addContributeLoad("Bus1","D",true,new Complex(.05,.01),null,null,new Complex(.02,.005),true);
        PsseLoadScopeMetadata.put(net,"Bus1","D",1,1,1);return net;
    }
    private Complex injection(com.interpss.dstab.DStabilityNetwork net) {
        var bus=net.getDStabBus("Bus1");Complex sum=Complex.ZERO;
        for(Object x:bus.getContributeGenList()){var g=(AclfGen)x;if(g.isActive())sum=sum.add(g.getGen());}
        for(Object x:bus.getContributeLoadList()){var load=(AclfLoad)x;if(load.isActive())sum=sum.subtract(load.getLoad(bus.getVoltageMag()));}
        return sum;
    }

    @Test void deviceChangesPreserveDistributedGenerationPowerAndPrecedeModelAttachment() throws Exception {
        var net=network();Complex before=injection(net);
        Path idv=write("case.idv","""
                BAT_BUS_CHNG_4,1,0,2,,,,,,,,,,,,;
                BAT_LOAD_CHNG_6,1,'D',,,,,,,0,,,,,,,,,,;
                BAT_PLANT_DATA_4,1,0,0,0,1.01,100
                BAT_MACHINE_DATA_4,1,'#D',1,1,0,0,0,1,,2,.5,0,0,9999,-9999,2.2,0,.25,0,0,1,1,1,1,1,1,' '
                BAT_LOAD_CHNG_6,1,'D',,,4151,,,,,,,,,,,,,,;
                """);
        var processor=new PsseCasePreparationProcessor(net,true);processor.apply(idv);
        var bus=net.getDStabBus("Bus1");var gen=(com.interpss.dstab.DStabGen)bus.getContributeGen("#D");
        assertNotNull(gen);assertEquals(2.2,gen.getMvaBase());assertEquals(.02,gen.getGen().getReal(),1e-14);
        assertEquals(.005,gen.getGen().getImaginary(),1e-14);assertEquals(1.01,gen.getDesiredVoltMag());
        assertFalse(bus.getContributeLoad("D").isDistGenStatus());
        assertEquals(4151,PsseLoadScopeMetadata.find(net,"Bus1","D").zoneNumber());
        assertEquals(0,before.subtract(injection(net)).abs(),1e-12);
        Path dyr=write("models.dyr","1 'GENCLS' '#D' 3.5 0 /\n");
        var parser=new PSSEDStabDirectParser(new DStabNetworkBuilder(net)).setStrictImport(true);
        parser.parseDynFile(dyr.toString());assertNotNull(gen.getMach());
        assertEquals(5,processor.entries().size());
        assertTrue(processor.entries().stream().allMatch(e->e.status()==PsseCasePreparationProcessor.Status.APPLIED));
    }

    @Test void omittedFieldsPreserveElectricalValuesAndScopes() throws Exception {
        var net=network();var bus=net.getDStabBus("Bus1");
        var processor=new PsseCasePreparationProcessor(net,false);
        processor.apply(write("scope.idv","BAT_LOAD_CHNG_6,1,'D',,,42,,,,,,,,,,,,,,;\n"));
        var load=bus.getContributeLoad("D");
        assertEquals(new Complex(.05,.01),load.getLoadCP());assertTrue(load.isDistGenStatus());
        assertEquals(new Complex(.02,.005),load.getDistGenPower());
        assertEquals(new PsseLoadScopeMetadata.Scope(1,42,1),PsseLoadScopeMetadata.find(net,"Bus1","D"));
        processor.apply(write("gen.idv","BAT_MACHINE_DATA_4,1,'1',,,,,,,,,.2\n"));
        assertEquals(.5,bus.getContributeGen("1").getGen().getReal(),1e-14);
    }

    @Test void newPlantBuildsZeroReactiveLimitBeforePowerFlow() throws Exception {
        var net=network();var b=new AclfNetworkBuilder(net);
        b.addBus("Bus2","new DER",2L,138000,1,0,"1","1","1");
        var bus=net.getDStabBus("Bus2");assertFalse(bus.isPVBusLimit());
        var p=new PsseCasePreparationProcessor(net,true);
        p.apply(write("new-plant.idv","""
                BAT_BUS_CHNG_4,2,0,2,,,,,,,,,,,,;
                BAT_PLANT_DATA_4,2,0,0,0,1.02,100
                BAT_MACHINE_DATA_4,2,'#D',1,1,0,0,0,1,,.03,0,0,0,9999,-9999,.033,0,.25,0,0,1,1,1,1,1,1,' '
                """));
        p.finalizeElectricalData();
        assertTrue(bus.isPVBusLimit(),"A fixed zero Q capability must create an actual PV limit");
        assertTrue(bus.isGenPQ(),"No reactive range means fixed Q, not voltage regulation");
        assertEquals(0,bus.getQGenLimit().getMax(),0);assertEquals(0,bus.getQGenLimit().getMin(),0);
        assertEquals(0,bus.getGenQ(),0);
        assertEquals(.0003,bus.getGenP(),1e-12);assertEquals(1.02,bus.getDesiredVoltMag(),1e-12);
        var limits=bus.getQGenLimit();p.finalizeElectricalData();
        assertSame(limits,bus.getQGenLimit(),"Finalization is idempotent without further edits");
    }

    @Test void localIncludesRunAtTheirSourcePositionAndQuotedCommasArePreserved() throws Exception {
        var net=network();var processor=new PsseCasePreparationProcessor(net,false);
        write("children/child, one.idv","BAT_PLANT_DATA_4,1,0,0,0,1.02,100\n");
        Path root=write("root.idv","""
                MENU,OFF /* native trailing comment
                BAT_PLANT_DATA_4,1,0,0,0,1.01,100
                IDEV 'children/child, one.idv'
                BAT_PLANT_DATA_4,1,0,0,0,1.03,100
                CONG
                @END
                """);
        processor.apply(root);assertEquals(1.03,net.getDStabBus("Bus1").getContributeGen("1").getDesiredVoltMag());
        assertEquals(3,processor.entries().stream().filter(e->e.command().equals("BAT_PLANT_DATA_4")).count());
        assertTrue(processor.entries().stream().anyMatch(e->e.command().equals("CONG")&&e.status()==PsseCasePreparationProcessor.Status.DEFERRED));
        int count=processor.entries().size();processor.apply(root);assertEquals(count,processor.entries().size());
    }

    @Test void rejectsIncludeCyclesRemotePathsAndUnsupportedDataCommandsWithLocations() throws Exception {
        for(String text:List.of("IDEV 'loop.idv'\n","IDEV 'https://example.invalid/config.idv'\n",
                "BAT_UNIMPLEMENTED_DEVICE_CHANGE,1\n","GNET\n1\n")) {
            var processor=new PsseCasePreparationProcessor(network(),false);
            Path root=write("loop.idv",text);
            var error=assertThrows(InterpssException.class,()->processor.apply(root));
            assertTrue(error.getMessage().contains("loop.idv"));
            assertTrue(processor.entries().stream().anyMatch(e->e.status()==PsseCasePreparationProcessor.Status.ERROR));
        }
    }

    @Test void plainGnetNettingIsIdempotentAndPreservesInjection() throws Exception {
        var net=network();Complex before=injection(net);
        var processor=new PsseCasePreparationProcessor(net,false);
        Path file=write("local.gnet","1\n1\n0\n");processor.apply(file);processor.apply(file);
        assertEquals(1,processor.gnetRemoved().size());
        assertFalse(net.getDStabBus("Bus1").getContributeGen("1").isActive());
        assertEquals(0,before.subtract(injection(net)).abs(),1e-12);
        assertEquals(1,net.getDStabBus("Bus1").getContributeLoadList().stream()
                .filter(l->((AclfLoad)l).getId().startsWith("GNET-")).count());
    }

    @Test void loaderFindsGnetByStaticCaseStemAndAppliesExplicitGnet() throws Exception {
        Path original=Path.of("..","ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw");
        Path raw=temp.resolve("static.raw");Files.copy(original,raw);
        Path dyr=write("different-model-name.dyr","1 'GENCLS' 1 3.5 0 /\n2 'GENCLS' 1 99999 0 /\n");
        write("static_gnet.idv","GNET\n1\n0\n");
        var loader=new PSSEMultiFileLoader();var context=loader.loadDStab(raw.toString(),dyr.toString());
        assertFalse(context.getDStabilityNet().getDStabBus("Bus1").getContributeGen("1").isActive());
        assertEquals(1,loader.getLastDynamicImportReport().count(DynamicModelImportStatus.SKIPPED_GNET));
        var untouched=new PSSEMultiFileLoader().setAutoDiscoverCasePreparationFiles(false)
                .loadDStab(raw.toString(),dyr.toString());
        assertTrue(untouched.getDStabilityNet().getDStabBus("Bus1").getContributeGen("1").isActive());
        Files.delete(temp.resolve("static_gnet.idv"));
        Path gnet=write("selected.gnet","1\n0\n");
        context=loader.loadDStab(raw.toString(),gnet.toString(),dyr.toString());
        assertFalse(context.getDStabilityNet().getDStabBus("Bus1").getContributeGen("1").isActive());
        assertEquals(1,loader.getLastCasePreparationReport().size());
        loader.loadDStab(raw.toString());assertTrue(loader.getLastCasePreparationReport().isEmpty());
    }

    @Test void dyreReferencesResolveLocallyAndExplicitDyrTakesPrecedence() throws Exception {
        write("models.dyr","1 'GENCLS' 1 3.5 0 /\n");
        Path setup=write("setup.idv","DYRE\nmodels.dyr\nconec\nconet\n,,,\ncompile\n");
        var processor=new PsseCasePreparationProcessor(network(),false);processor.apply(setup);
        assertEquals(Set.of(temp.resolve("models.dyr").toRealPath()),processor.dynamicFiles());
        var explicit=new PsseCasePreparationProcessor(network(),true);explicit.apply(setup);
        assertTrue(explicit.dynamicFiles().isEmpty());
        assertTrue(explicit.entries().stream().anyMatch(e->e.message().contains("takes precedence")));
    }

    @Test void nativeIdentifierMatchingRetainsTheImportedDeviceSpelling() throws Exception {
        var net=network();var p=new PsseCasePreparationProcessor(net,false);
        p.apply(write("case-id.idv","BAT_LOAD_CHNG_6,1,'d',,,42,,,,,,,,,,,,,,;\n"));
        assertNotNull(net.getDStabBus("Bus1").getContributeLoad("D"));
        assertNull(net.getDStabBus("Bus1").getContributeLoad("d"));
        assertEquals(42,PsseLoadScopeMetadata.find(net,"Bus1","D").zoneNumber());
        assertNull(PsseLoadScopeMetadata.find(net,"Bus1","d"));
    }

    @Test void admittanceChangeUsesTheRawSignConventionAndBlanksRetainIt() throws Exception {
        var net=network();var p=new PsseCasePreparationProcessor(net,false);
        var f=new ArrayList<>(Collections.nCopies(19,""));
        f.set(0,"BAT_LOAD_CHNG_6");f.set(1,"1");f.set(2,"'D'");f.set(14,"2");f.set(15,"-3");
        p.apply(write("admittance.idv",String.join(",",f)+"\n"));
        var load=net.getDStabBus("Bus1").getContributeLoad("D");
        assertEquals(new Complex(.02,.03),load.getLoadCZ());
        p.apply(write("blank-admittance.idv","BAT_LOAD_CHNG_6,1,'D',,,43,,,,,,,,,,,,,,;\n"));
        assertEquals(new Complex(.02,.03),load.getLoadCZ());
    }
}
