package org.interpss.fadapter.cim;

import java.io.ByteArrayInputStream;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.xml.sax.helpers.DefaultHandler;

/** Profile-independent RDF operations shared by CGMES and distribution CIM imports. */
public final class CimRdfUtils {
    private CimRdfUtils() { }

    /**
     * Merge RDF/XML using the caller's stable base URI. Reject DTDs and external
     * entities before Jena reads the document. URI normalization belongs to the
     * profile adapter, not this reader. The caller owns the destination model.
     */
    public static void read(Model model, byte[] xml, String baseUri) throws Exception {
        var factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var validator = factory.newSAXParser();
        validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        validator.parse(new ByteArrayInputStream(xml), new DefaultHandler());

        // The generic RDFParserBuilder eagerly initializes HttpEnv in Jena 4.10,
        // even for byte streams. Its HTTP client opens a selector/loopback pipe on
        // Windows. Invoke the same RDF/XML language reader directly for local CIM.
        var context = new org.apache.jena.sparql.util.Context();
        // Large CGMES files exceed ARP's rdf:ID checking limit (W137).
        context.set(org.apache.jena.riot.SysRIOT.sysRdfReaderProperties,
                java.util.Map.of("WARN_BIG_FILE", "EM_IGNORE"));
        var reader = new org.apache.jena.riot.lang.ReaderRIOTRDFXML(
                org.apache.jena.riot.system.ErrorHandlerFactory.getDefaultErrorHandler());
        reader.read(new ByteArrayInputStream(xml), baseUri, null,
                org.apache.jena.riot.system.StreamRDFLib.graph(model.getGraph()), context);
    }

    /** Resources of an exact RDF type; no inference or profile-specific ordering. */
    public static List<Resource> resourcesOfType(Model model, String typeUri) {
        var iterator = model.listResourcesWithProperty(RDF.type, model.createResource(typeUri));
        try { return iterator.toList(); }
        finally { iterator.close(); }
    }

    /**
     * Read an optional property. CGMES retains its first-value policy; strict
     * profiles can reject conflicting scalar values. Identical RDF triples merge.
     */
    public static RDFNode value(Resource resource, String propertyUri, boolean requireSingle) {
        if (resource == null) return null;
        var iterator = resource.listProperties(resource.getModel().createProperty(propertyUri));
        try {
            if (!iterator.hasNext()) return null;
            var value = iterator.nextStatement().getObject();
            if (requireSingle && iterator.hasNext())
                throw new IllegalArgumentException("Conflicting values for " + propertyUri + " [" + resource + "]");
            return value;
        } finally { iterator.close(); }
    }
}
