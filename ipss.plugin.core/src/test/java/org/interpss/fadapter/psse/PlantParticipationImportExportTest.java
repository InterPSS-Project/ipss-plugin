package org.interpss.fadapter.psse;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.interpss.fadapter.psse.export.PSSEJsonExporter;
import org.interpss.fadapter.psse.export.PSSERawExporter;
import org.interpss.fadapter.psse.bean.PSSESchema;
import org.interpss.fadapter.psse.export.psse.PSSEJSonGenUpdater;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import com.google.gson.*;
import com.interpss.core.aclf.*;
import com.interpss.dstab.DStabObjectFactory;

/** Repeated plant data is one control weight, including reordered and inactive records. */
public class PlantParticipationImportExportTest {
    @TempDir Path temp;
    private static final String SOURCE = """
        {"general":{"version":"35.0"},"network":{
        "caseid":{"fields":["sbase","rev"],"data":[100,35]},
        "bus":{"fields":["ibus","name","ide","baskv"],"data":[[1,"Grid",3,230],[2,"Plant",2,230],[3,"Load",1,230]]},
        "generator":{"fields":["ibus","machid","pg","qg","qt","qb","vs","ireg","rmpct","stat"],
        "data":[[1,"1",50,10,100,-100,1,0,100,1],[2,"1",35,5,100,-100,1.01,3,100,1],[2,"2",35,5,100,-100,1.01,3,25,1]]},
        "load":{"fields":["ibus","loadid","pl","ql"],"data":[[3,"1",100,20]]},
        "acline":{"fields":["ibus","jbus","ckt","xpu"],"data":[[1,3,"1",.1],[2,3,"1",.1]]}}}
        """;

    private AclfNetwork parse(String name, boolean reverse, boolean inactive) throws Exception {
        var json = JsonParser.parseString(SOURCE).getAsJsonObject();
        var rows = json.getAsJsonObject("network").getAsJsonObject("generator").getAsJsonArray("data");
        if (inactive) rows.get(2).getAsJsonArray().set(9, new JsonPrimitive(0));
        if (reverse) { var last = rows.remove(2); rows.add(rows.get(1)); rows.set(1,last); }
        var path = temp.resolve(name); Files.writeString(path, json.toString());
        return new PSSEJsonDirectParser().parse(path.toString());
    }

    @Test public void rawxLastRecordSetsCommonWeightWithoutDestroyingMachineData() throws Exception {
        for (boolean reverse : new boolean[]{false,true}) {
            var net = parse("source-"+reverse+".rawx", reverse, false);
            var bus = net.getBus("Bus2");
            assertEquals(reverse ? 1.0 : .25, bus.getPlantMvarControlPFactor(), 0);
            assertEquals(1.0, bus.getContributeGen("1").getMvarControlPFactor(), 0);
            assertEquals(.25, bus.getContributeGen("2").getMvarControlPFactor(), 0);
            assertEquals(reverse ? 100 : 25, bus.getRemoteQBus().getRemoteControlPercentage(), 1e-12);
        }
    }

    @Test public void inactiveLastRecordStillCarriesPlantData() throws Exception {
        var bus = parse("inactive.rawx", false, true).getBus("Bus2");
        assertFalse(bus.getContributeGen("2").isActive());
        assertEquals(.25, bus.getPlantMvarControlPFactor(), 0);
        assertEquals(25, bus.getRemoteQBus().getRemoteControlPercentage(), 1e-12);
    }

    @Test public void rawAndRawxRoundTripsPreserveCommonWeightAcrossRecordOrderAndVersions() throws Exception {
        for (boolean reverse : new boolean[]{false,true}) {
            var net = parse("roundtrip-source-"+reverse+".rawx", reverse, false);
            double expected = reverse ? 1 : .25;
            // Reorder the live machine list: export must not resurrect a superseded record value.
            net.getBus("Bus2").getContributeGenList().move(0,1);
            var rawx = temp.resolve("out-"+reverse+".rawx");
            new PSSEJsonExporter(net).export(rawx);
            assertEquals(expected, new PSSEJsonDirectParser().parse(rawx.toString()).getBus("Bus2").getPlantMvarControlPFactor(), 0);
            for (int version : new int[]{30,34,35,36}) {
                var raw = temp.resolve("out-"+reverse+"-"+version+".raw");
                new PSSERawExporter(net,version).export(raw);
                var restored = new PSSEDirectParser(version).parse(raw.toString());
                assertEquals(expected, restored.getBus("Bus2").getPlantMvarControlPFactor(), 0);
                assertEquals(expected*100, restored.getBus("Bus2").getRemoteQBus().getRemoteControlPercentage(), 1e-12);
            }
        }
    }

    @Test public void templateUpdaterWritesCanonicalWeightEvenWhenDefaultColumnWasOmitted() throws Exception {
        var net = parse("template.rawx",false,false);
        for (String fields : new String[]{"[\"ibus\",\"rmpct\"]", "[\"ibus\"]"}) {
            var template = new Gson().fromJson("{\"fields\":"+fields+",\"data\":[[2],[2]]}", PSSESchema.Generator.class);
            new PSSEJSonGenUpdater(template,net).update();
            int column = template.getFields().indexOf("rmpct");
            assertTrue(column>=0);
            for (Object row : template.getData())
                assertEquals(25, ((Number)((List<?>)row).get(column)).doubleValue(),0);
        }
    }

    @Test public void idvPlantCommandsUsePercentAtBoundaryAndFractionsInEngine() throws Exception {
        var net = DStabObjectFactory.createDStabilityNetwork();
        var builder = new AclfNetworkBuilder(net);
        builder.addBus("Bus2","plant",2,230000,1,0,null,null,null);
        var processor = new PsseCasePreparationProcessor(net,true);
        var idv = temp.resolve("plant.idv");
        Files.writeString(idv,"BAT_PLANT_DATA_4,2,0,0,0,1.01,25\nBAT_MACHINE_DATA_4,2,'1',1\n");
        processor.apply(idv);
        var bus = net.getDStabBus("Bus2");
        assertEquals(.25,bus.getPlantMvarControlPFactor(),0);
        assertEquals(.25,bus.getContributeGen("1").getMvarControlPFactor(),0);
        // Omitting RMPCT retains 25%, rather than reinterpreting .25 as .25%.
        var blank = temp.resolve("blank.idv"); Files.writeString(blank,"BAT_PLANT_DATA_4,2,0,0,0,1.02,\n");
        new PsseCasePreparationProcessor(net,true).apply(blank);
        assertEquals(.25,bus.getPlantMvarControlPFactor(),0);
        var zero = temp.resolve("zero.idv"); Files.writeString(zero,"BAT_PLANT_DATA_4,2,0,0,0,1.02,0\n");
        processor.apply(zero);
        assertEquals(0,bus.getPlantMvarControlPFactor(),0);
        assertEquals(0,bus.getContributeGen("1").getMvarControlPFactor(),0);
    }
}
