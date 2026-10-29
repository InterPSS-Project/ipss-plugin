package org.interpss.fadapter.cim;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RiotException;
import org.interpss.fadapter.cim.parser.CGMESRdfParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xml.sax.SAXParseException;

class CimRdfUtilsTest {
    private static final String NS = CGMESConstants.CIM100_NS;
    private static final String BASE = "https://interpss.org/test";
    private final Model model = ModelFactory.createDefaultModel();

    @AfterEach void closeModel() { model.close(); }

    private static String document(String body) {
        return "<rdf:RDF xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#' xmlns:cim='"
                + NS + "'>" + body + "</rdf:RDF>";
    }

    private void read(String xml) throws Exception {
        CimRdfUtils.read(model, xml.getBytes(StandardCharsets.UTF_8), BASE);
    }

    @Test void equipmentAndStateProfilesMergeUsingStableIdentity() throws Exception {
        String eq = document("<cim:EnergyConsumer rdf:ID='load'/>");
        String state = document("<rdf:Description rdf:about='#load'><cim:EnergyConsumer.p>42</cim:EnergyConsumer.p></rdf:Description>");
        read(eq); read(state); read(state);
        var load = model.getResource(BASE + "#load");
        assertEquals(List.of(load), CimRdfUtils.resourcesOfType(model, NS + "EnergyConsumer"));
        assertEquals("42", CimRdfUtils.value(load, NS + "EnergyConsumer.p", true).asLiteral().getString());
        assertNull(CimRdfUtils.value(load, NS + "EnergyConsumer.q", true));
        assertEquals(2, model.size());

        var cgmes = new CGMESRdfParser().parseMultiple(new BufferedReader[]{
                new BufferedReader(new StringReader(eq)), new BufferedReader(new StringReader(state))});
        try {
            var loads = new CGMESModel(cgmes).energyConsumers();
            assertEquals(1, loads.size());
            assertEquals("load", loads.get(0).getLocalId());
            assertEquals(42, loads.get(0).getDouble("EnergyConsumer.p"));
        } finally { cgmes.close(); }
    }

    @Test void strictConflictsAndLegacyFallbackRemainSeparate() throws Exception {
        read(document("<cim:EnergyConsumer rdf:ID='load'><cim:EnergyConsumer.p>bad</cim:EnergyConsumer.p></cim:EnergyConsumer>"));
        var load = model.getResource(BASE + "#load");
        var bag = new CGMESPropertyBag(load, NS, CGMESConstants.ENTSOE_NS);
        assertEquals(12, bag.getDouble("EnergyConsumer.p", 12));
        load.addProperty(model.createProperty(NS + "EnergyConsumer.p"), "42");
        assertNotNull(bag.getString("EnergyConsumer.p"));
        assertThrows(IllegalArgumentException.class, () -> CimRdfUtils.value(load, NS + "EnergyConsumer.p", true));
        // Even a conflicting name must give a useful error, not recursive name lookup.
        load.addProperty(model.createProperty(NS + "IdentifiedObject.name"), "one");
        load.addProperty(model.createProperty(NS + "IdentifiedObject.name"), "two");
        var ex = assertThrows(IllegalArgumentException.class,
                () -> CimRdfUtils.value(load, NS + "IdentifiedObject.name", true));
        assertTrue(ex.getMessage().contains(load.getURI()));
    }

    @Test void literalAndReferencePropertiesKeepTheirTypes() throws Exception {
        read(document("""
                <cim:Terminal rdf:ID="terminal">
                  <cim:IdentifiedObject.name>Terminal</cim:IdentifiedObject.name>
                  <cim:Terminal.ConductingEquipment rdf:resource="#line"/>
                </cim:Terminal><cim:ACLineSegment rdf:ID="line"/>
                """));
        var bag = new CGMESPropertyBag(model.getResource(BASE + "#terminal"), NS, CGMESConstants.ENTSOE_NS);
        assertEquals("Terminal", bag.getName());
        assertNull(bag.getString("Terminal.ConductingEquipment"));
        assertNull(bag.getResourceId("IdentifiedObject.name"));
        assertEquals(BASE + "#line", bag.getResourceId("Terminal.ConductingEquipment"));
        assertEquals("line", bag.getResourceBag("Terminal.ConductingEquipment").getLocalId());
        assertNull(bag.getResourceBag("missing"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"<!DOCTYPE rdf:RDF>",
            "<!DOCTYPE rdf:RDF [<!ENTITY x SYSTEM 'file:///does-not-exist'>]>",
            "<!DOCTYPE rdf:RDF [<!ENTITY x 'expanded'>]>"})
    void bothProfilesRejectDtdsBeforeAddingTriples(String dtd) {
        String xml = dtd + document("<cim:EnergyConsumer rdf:ID='load'/>");
        assertThrows(SAXParseException.class, () -> read(xml));
        assertTrue(model.isEmpty());
        assertThrows(SAXParseException.class, () -> new CGMESRdfParser().parseString(xml));
    }

    @Test void nonstandardUuidRepairIsRestrictedToCgmes() throws Exception {
        String id = "urn:uuid:12345678-1234-1234-1234-123456789abc_EU";
        String xml = document("<cim:EnergyConsumer rdf:about='" + id + "'/>");
        // The generic reader must not silently normalize malformed resource IDs.
        assertThrows(RiotException.class, () -> read(xml));
        var cgmes = new CGMESRdfParser().parseString(xml);
        try {
            assertEquals(id.replace("_EU", ""), new CGMESModel(cgmes).energyConsumers().get(0).getId());
        } finally { cgmes.close(); }
    }
}
