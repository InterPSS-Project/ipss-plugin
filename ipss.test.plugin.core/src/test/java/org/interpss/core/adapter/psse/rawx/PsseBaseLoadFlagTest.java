package org.interpss.core.adapter.psse.rawx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.interpss.state.aclf.AclfGenState;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.psse.PSSEDirectParser;
import org.interpss.fadapter.psse.PSSEJsonDirectParser;
import org.interpss.fadapter.psse.bean.PSSESchema;
import org.interpss.fadapter.psse.export.PSSEJsonExporter;
import org.interpss.fadapter.psse.export.PSSERawExporter;
import org.interpss.fadapter.psse.export.psse.PSSEJSonGenUpdater;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** BASLOD is generator data and is carried by both direct formats. */
class PsseBaseLoadFlagTest extends CorePluginTestSetup {
    @TempDir Path directory;

    @Test
    void machineFlagsSurviveRawxAndRawRoundTrips() throws Exception {
        Path input = directory.resolve("input.rawx");
        Files.writeString(input, """
            {"network":{
              "caseid":{"fields":["sbase","rev"],"data":[100,36]},
              "bus":{"fields":["ibus","name","baskv","ide"],
                     "data":[[1,"Plant",100,3]]},
              "generator":{"fields":["ibus","machid","baslod","pg","pt","pb","stat"],
                           "data":[[1,"1",2,40,45.3,38,1],[1,"2",3,12,20,0,0],[1,"3",1],[1,"4"]]}
            }}
            """);
        var network = new PSSEJsonDirectParser().parse(input.toString());
        var plant = network.getBus("Bus1");
        for (int i = 1; i <= 4; i++) {
            int expected = switch (i) { case 1 -> 2; case 2 -> 3; case 3 -> 1; default -> 0; };
            var machine = plant.getContributeGen(Integer.toString(i));
            assertEquals(expected, machine.getBaseLoadFlag());
            assertEquals(expected, AclfGenState.create(new AclfGenState(machine)).getBaseLoadFlag());
        }
        assertEquals(.453, plant.getContributeGen("1").getPGenLimit().getMax(), 1e-12);
        assertEquals(.38, plant.getContributeGen("1").getPGenLimit().getMin(), 1e-12);

        Path json = directory.resolve("exported.rawx");
        Files.writeString(json, new PSSEJsonExporter(network).export().toString());
        var reloaded = new PSSEJsonDirectParser().parse(json.toString()).getBus("Bus1");
        for (String id : new String[]{"1", "2", "3", "4"})
            assertEquals(plant.getContributeGen(id).getBaseLoadFlag(), reloaded.getContributeGen(id).getBaseLoadFlag());
        for (int version : new int[]{35, 36}) {
            Path raw = directory.resolve("exported-" + version + ".raw");
            new PSSERawExporter(network, version).export(raw);
            var restored = new PSSEDirectParser().parse(raw.toString()).getBus("Bus1");
            for (String id : new String[]{"1", "2", "3", "4"})
                assertEquals(plant.getContributeGen(id).getBaseLoadFlag(), restored.getContributeGen(id).getBaseLoadFlag());
        }
    }

    @Test
    void sparseGeneratorTemplateUsesDefaultMachineId() throws Exception {
        Path input = directory.resolve("template.rawx");
        Files.writeString(input, """
            {"network":{
              "caseid":{"fields":["sbase","rev"],"data":[100,36]},
              "bus":{"fields":["ibus","name","baskv","ide"],"data":[[1,"Plant",100,3]]},
              "generator":{"fields":["ibus","pg"],"data":[[1,40]]}
            }}
            """);
        var network = new PSSEJsonDirectParser().parse(input.toString());
        var machine = network.getBus("Bus1").getContributeGen("1");
        machine.setBaseLoadFlag(2);

        var template = new Gson().fromJson(Files.readString(input), PSSESchema.class);
        var generators = template.getNetwork().getGenerator();
        new PSSEJSonGenUpdater(generators, network).update();
        Files.writeString(input, new Gson().toJson(template));

        var restored = new PSSEJsonDirectParser().parse(input.toString());
        assertEquals(2, restored.getBus("Bus1").getContributeGen("1").getBaseLoadFlag());
    }

}
