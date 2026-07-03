package org.interpss.plugin.contingency.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.interpss.plugin.contingency.definition.ContingencyAction;
import org.interpss.plugin.contingency.definition.ContingencyActionType;
import org.interpss.plugin.contingency.definition.ContingencyDefinition;
import org.interpss.plugin.contingency.definition.ContingencyObjectType;
import org.interpss.plugin.contingency.definition.BranchContingencyRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ContingencyFileUtilTest {
    @TempDir
    Path tempDir;

    @Test
    public void importsGroupedJsonAsDefinitionsAndLegacyRecords() throws Exception {
        Path file = tempDir.resolve("grouped-contingencies.json");
        Files.writeString(file, """
                {
                  "contingency_definitions": [
                    {
                      "name": "CTG_A",
                      "actions": [
                        {
                          "object_type": "BRANCH",
                          "action_type": "OPEN",
                          "object_id": "Bus1->Bus2(1)",
                          "metadata": {
                            "from_bus": "Bus1",
                            "to_bus": "Bus2",
                            "circuit": "1",
                            "base_kv": "138.0"
                          }
                        },
                        {
                          "object_type": "BRANCH",
                          "action_type": "OPEN",
                          "object_id": "Bus3->Bus4(2)"
                        }
                      ]
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);

        List<ContingencyDefinition> definitions =
                ContingencyFileUtil.importContingencyDefinitionsFromJson(file.toFile());
        assertEquals(1, definitions.size());
        assertEquals("CTG_A", definitions.get(0).name);
        assertEquals(2, definitions.get(0).actions.size());
        assertEquals("Bus1->Bus2(1)", definitions.get(0).actions.get(0).objectId);

        List<BranchContingencyRecord> records =
                ContingencyFileUtil.importContingenciesFromJson(file.toFile());
        assertEquals(2, records.size());
        assertEquals("CTG_A", records.get(0).name);
        assertEquals("Bus1", records.get(0).fromBus);
        assertEquals("Bus2", records.get(0).toBus);
        assertEquals("1", records.get(0).ckt);
        assertEquals(138.0, records.get(0).baseKv, 1.0e-6);
    }

    @Test
    public void importsLegacyFlatJsonAsGroupedDefinitions() throws Exception {
        Path file = tempDir.resolve("flat-contingencies.json");
        Files.writeString(file, """
                {
                  "contingencies": [
                    {
                      "name": "CTG_A",
                      "element_type": "Branch",
                      "action_type": "OPEN",
                      "from_bus": "Bus1",
                      "to_bus": "Bus2",
                      "circuit": "1"
                    },
                    {
                      "name": "CTG_A",
                      "element_type": "Branch",
                      "action_type": "OPEN",
                      "from_bus": "Bus3",
                      "to_bus": "Bus4",
                      "circuit": "2"
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);

        List<ContingencyDefinition> definitions =
                ContingencyFileUtil.importContingencyDefinitionsFromJson(file.toFile());

        assertEquals(1, definitions.size());
        assertEquals("CTG_A", definitions.get(0).name);
        assertEquals(2, definitions.get(0).actions.size());
        assertEquals("Bus3->Bus4(2)", definitions.get(0).actions.get(1).objectId);
    }

    @Test
    public void exportsGroupedDefinitionsWithObjectIdSchema() throws Exception {
        Path file = tempDir.resolve("exported-grouped-contingencies.json");
        ContingencyAction action = new ContingencyAction(
                ContingencyObjectType.BRANCH,
                ContingencyActionType.OPEN,
                "Bus1->Bus2(1)");
        action.extUID = "line_1_2_1";
        ContingencyDefinition definition = new ContingencyDefinition("CTG_A", List.of(action));

        ContingencyFileUtil.exportContingencyDefinitionsToJson(
                file.toFile(),
                List.of(definition));

        String json = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"contingency_definitions\""));
        assertTrue(json.contains("\"object_id\""));
        assertFalse(json.contains("\"contingencies\""));
        assertFalse(json.contains("\"branch_id\""));
    }
}
