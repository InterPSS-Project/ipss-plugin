package org.interpss.fadapter.psse;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.core.acsc.AcscGen;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabLoad;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.BaseDStabBus;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.apache.commons.math3.complex.Complex;
import org.interpss.fadapter.psse.dyr.DynamicModelImportStatus;

/** Synthetic fixtures keep the combined import contract independent of private cases. */
class RawxDyrMultiFileLoaderTest {
    @TempDir Path directory;

    private Path rawx() throws Exception {
        Path file = directory.resolve("case.RAWX");
        Files.writeString(file, """
                {"general":{"version":"35.0"},"network":{
                  "caseid":{"fields":["sbase","basfrq"],"data":[100.0,60.0]},
                  "bus":{"fields":["ibus","name","basekv","ide"],
                    "data":[[1,"Source",230.0,3],[2,"Load",230.0,1]]},
                  "generator":{"fields":["ibus","machid","pg","qg","mbase","zx"],
                    "data":[[1,"1",40.0,5.0,100.0,0.2]]},
                  "load":{"fields":["ibus","loadid","pl","ql"],"data":[[2,"1",40.0,5.0]]},
                  "acline":{"fields":["ibus","jbus","ckt","xpu"],"data":[[1,2,"1",0.1]]}
                }}
                """);
        return file;
    }

    @Test void attachesMachineToSuppliedDynamicNetwork() throws Exception {
        Path dynamics = directory.resolve("case.dyr");
        Files.writeString(dynamics, "1 'GENCLS' '1' 3.5 0.0 /\n");
        var network = DStabObjectFactory.createDStabilityNetwork();
        var loader = new PSSEMultiFileLoader();
        var context = loader.loadDStab(network, rawx().toString(), dynamics.toString());
        assertSame(network, context.getDStabilityNet());
        assertSame(network, context.getDynSimuAlgorithm().getNetwork());
        assertNotNull(context.getDynSimuAlgorithm().getSolver());
        assertEquals(2, network.getNoBus());
        assertEquals(1, network.getNoBranch());
        var gen = assertInstanceOf(DStabGen.class,
                network.getDStabBus("Bus1").getContributeGenList().get(0));
        assertNotNull(gen.getMach());
        assertEquals(3.5, gen.getMach().getH(), 1.0e-10);
        assertEquals(0.4, gen.getGen().getReal(), 1.0e-10);
        assertInstanceOf(DStabLoad.class, network.getDStabBus("Bus2").getContributeLoadList().get(0));
        assertEquals(1, loader.getLastDynamicImportReport().count(DynamicModelImportStatus.ATTACHED));
    }

    @Test void importsRawxWithoutDynamicsAndWithRawLayoutOverride() throws Exception {
        var context = new PSSEMultiFileLoader(36).loadDStab(rawx().toString());
        assertEquals(2, context.getDStabilityNet().getNoBus());
        assertNotNull(context.getDynSimuAlgorithm().getSolver());
    }

    @Test void retainsRawDyrLoadingWithEquivalentMachineData() throws Exception {
        var staticNetwork = new PSSEJsonDirectParser().parse(rawx().toString());
        Path raw = directory.resolve("case.raw");
        new org.interpss.fadapter.psse.export.PSSERawExporter(staticNetwork, 35, false).export(raw);
        Path dyr = directory.resolve("case.dyr");
        Files.writeString(dyr, "1 'GENCLS' '1' 3.5 0.0 /\n");
        var loader = new PSSEMultiFileLoader();
        var context = loader.loadDStab(raw.toString(), dyr.toString());
        var network = context.getDStabilityNet();
        assertEquals(2, network.getNoBus());
        var gen = (DStabGen) network.getDStabBus("Bus1").getContributeGenList().get(0);
        assertEquals(3.5, gen.getMach().getH(), 1.0e-10);
        assertEquals(0.4, gen.getGen().getReal(), 1.0e-10);
        assertEquals(1, loader.getLastDynamicImportReport().count(DynamicModelImportStatus.ATTACHED));
    }

    @Test void importsRawxIntoShortCircuitNetwork() throws Exception {
        var network = new PSSEMultiFileLoader().loadAcsc(rawx().toString());
        assertEquals(2, network.getNoBus());
        assertInstanceOf(AcscGen.class, network.getBus("Bus1").getContributeGenList().get(0));
    }

    @Test void retainsPerLoadScopeAndSourceZoneNumbersForPreparation() throws Exception {
        Path path=rawx();
        var json=com.google.gson.JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        var net=json.getAsJsonObject("network");
        net.add("load",com.google.gson.JsonParser.parseString("""
                {"fields":["ibus","loadid","pl","ql","area","zone","owner"],
                 "data":[[2,"1",40,5,7,42,9]]}
                """));
        Files.writeString(path,json.toString());
        var network=new PSSEMultiFileLoader().loadDStab(path.toString()).getDStabilityNet();
        assertEquals(new PsseLoadScopeMetadata.Scope(7,42,9),PsseLoadScopeMetadata.find(network,"Bus2","1"));
        var topology=new AclfNetworkBuilder(network);
        assertEquals(4151,topology.addZone("4151",null,null).getNumber());
        assertEquals(390,topology.addOwner("390",null).getNumber());
    }

    @Test void retainsStandaloneAclfParserApi() throws Exception {
        var network = new PSSEJsonDirectParser().parse(rawx().toString());
        assertEquals(2, network.getNoBus());
        assertEquals(1, network.getNoBranch());
    }

    @Test void createsDynamicThreeWindingStarBusAndBranches() throws Exception {
        var network = new PSSEMultiFileLoader().loadDStab(rawx().toString()).getDStabilityNet();
        var builder = new AclfNetworkBuilder(network);
        builder.addBus("Bus3", "Tertiary", 3, 115000.0, 1.0, 0.0, null, null, null);
        var transformer = builder.addXformer3W("Bus1", "Bus2", "Bus3", "1",
                new Complex(0.0, 0.1), new Complex(0.0, 0.1), new Complex(0.0, 0.1),
                1.0, 1.0, 1.0, Complex.ZERO, 1.0, 0.0,
                false, false, false, false, 0.0, 0.0, 0.0, true);
        assertInstanceOf(BaseDStabBus.class, transformer.getStarBus());
        assertInstanceOf(com.interpss.dstab.DStabBranch.class, transformer.getFromAclfBranch());
        assertSame(network, transformer.getStarBus().getNetwork());
    }

    @Test void reportsInvalidDeferredPlantControlAndStrictModeRejectsIt() throws Exception {
        var network = new PSSEMultiFileLoader().loadDStab(rawx().toString()).getDStabilityNet();
        Path dynamics = directory.resolve("invalid-plant.dyr");
        var fields = new java.util.ArrayList<String>(java.util.Collections.nCopies(34, "0"));
        fields.set(16, "-1");
        fields.set(17, "1");
        Files.writeString(dynamics, "1 'REPCA1' 1 " + String.join(" ", fields) + " /\n");
        var parser = new PSSEDStabDirectParser(new org.interpss.fadapter.builder.DStabNetworkBuilder(network));
        parser.parseDynFile(dynamics.toString());
        assertEquals(1, parser.getLastImportReport().totalRecordCount());
        assertEquals(1, parser.getLastImportReport().count(DynamicModelImportStatus.REJECTED));
        parser.setStrictImport(true);
        assertThrows(com.interpss.common.exp.InterpssException.class,
                () -> parser.parseDynFile(dynamics.toString()));
    }

    @Test void honorsSuppliedObjectFactoryForRawx() throws Exception {
        int[] creations = new int[4];
        var factory = new org.interpss.fadapter.builder.AclfNetworkObjectFactory() {
            public com.interpss.core.aclf.BaseAclfBus<?, ?> createBus(String id,
                    com.interpss.core.aclf.BaseAclfNetwork<?, ?> net) {
                creations[0]++;
                return DStabObjectFactory.createDStabBus(id, (com.interpss.dstab.BaseDStabNetwork) net).get();
            }
            public com.interpss.core.aclf.AclfBranch createBranch() {
                creations[1]++;
                return DStabObjectFactory.createDStabBranch();
            }
            public com.interpss.core.aclf.Aclf3WBranch create3WBranch() {
                return (com.interpss.core.aclf.Aclf3WBranch) DStabObjectFactory.createDStab3WBranch();
            }
            public com.interpss.core.aclf.AclfGen createGen(String id) {
                creations[2]++;
                return DStabObjectFactory.createDStabGen(id);
            }
            public com.interpss.core.aclf.AclfLoad createLoad(String id) {
                creations[3]++;
                return DStabObjectFactory.createDStabLoad(id);
            }
        };
        var network = DStabObjectFactory.createDStabilityNetwork();
        var context = new PSSEMultiFileLoader().loadDStab(network, factory, rawx().toString());
        assertSame(network, context.getDStabilityNet());
        assertArrayEquals(new int[] {2, 1, 1, 1}, creations);
        Path preparation=directory.resolve("factory.idv");
        Files.writeString(preparation,"""
                GNET
                1
                0
                BAT_BUS_CHNG_4,2,0,2,,,,,,,,,,,,;
                BAT_PLANT_DATA_4,2,0,0,0,1.02,100
                BAT_MACHINE_DATA_4,2,'N',1,1,0,0,0,1,,1,0,0,0,9999,-9999,1.1,0,.25,0,0,1,1,1,1,1,1,' '
                """);
        new PsseCasePreparationProcessor(network,false,factory).apply(preparation);
        assertArrayEquals(new int[] {2,1,2,2},creations);
        assertInstanceOf(DStabGen.class,network.getDStabBus("Bus2").getContributeGen("N"));
        assertInstanceOf(DStabLoad.class,network.getDStabBus("Bus1").getContributeLoad("GNET-1"));
    }
}
