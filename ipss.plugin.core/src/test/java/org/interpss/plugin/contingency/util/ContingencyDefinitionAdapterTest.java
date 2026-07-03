package org.interpss.plugin.contingency.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.interpss.plugin.contingency.definition.BranchContingencyRecord;
import org.interpss.plugin.contingency.definition.ContingencyDefinition;
import org.junit.jupiter.api.Test;

public class ContingencyDefinitionAdapterTest {

    @Test
    public void groupsSameNamedBranchRecordsIntoOneDefinition() {
        List<BranchContingencyRecord> records = List.of(
                new BranchContingencyRecord(
                        "CTG_A",
                        "Branch",
                        "OPEN",
                        "Bus1",
                        "Bus2",
                        "1",
                        "10",
                        "20",
                        138.0,
                        42.5),
                new BranchContingencyRecord("CTG_A", "Branch", "OPEN", "Bus3", "Bus4", "2"));

        List<ContingencyDefinition> definitions =
                ContingencyDefinitionAdapter.fromBranchRecords(records);

        assertEquals(1, definitions.size());
        assertEquals("CTG_A", definitions.get(0).name);
        assertEquals(2, definitions.get(0).actions.size());
        assertEquals("Bus1->Bus2(1)", definitions.get(0).actions.get(0).objectId);
        assertEquals("138.0", definitions.get(0).actions.get(0).metadata.get("base_kv"));
        assertEquals(List.of("Bus1->Bus2(1)", "Bus3->Bus4(2)"),
                ContingencyDefinitionAdapter.flattenBranchIds(definitions));

        List<BranchContingencyRecord> flattened =
                ContingencyDefinitionAdapter.toBranchRecords(definitions);
        assertEquals(2, flattened.size());
        assertEquals("CTG_A", flattened.get(0).name);
        assertEquals("Bus1", flattened.get(0).fromBus);
        assertEquals("Bus2", flattened.get(0).toBus);
        assertEquals("1", flattened.get(0).ckt);
        assertEquals(138.0, flattened.get(0).baseKv, 1.0e-6);
    }

    @Test
    public void blankNamesBecomeSeparateSingleActionDefinitions() {
        List<BranchContingencyRecord> records = List.of(
                new BranchContingencyRecord("", "Branch", "OPEN", "Bus1", "Bus2", "1"),
                new BranchContingencyRecord(null, "Branch", "OPEN", "Bus3", "Bus4", "2"));

        List<ContingencyDefinition> definitions =
                ContingencyDefinitionAdapter.fromBranchRecords(records);

        assertEquals(2, definitions.size());
        assertTrue(definitions.get(0).name.startsWith("BRANCH_OPEN_1_"));
        assertTrue(definitions.get(1).name.startsWith("BRANCH_OPEN_2_"));
        assertEquals(1, definitions.get(0).actions.size());
        assertEquals(1, definitions.get(1).actions.size());
    }

    @Test
    public void unsupportedLegacyActionIsRejectedForGroupedDefinitions() {
        List<BranchContingencyRecord> records = List.of(
                new BranchContingencyRecord("CTG_CLOSE", "Branch", "CLOSE", "Bus1", "Bus2", "1"));

        assertThrows(
                IllegalArgumentException.class,
                () -> ContingencyDefinitionAdapter.fromBranchRecords(records));
    }
}
