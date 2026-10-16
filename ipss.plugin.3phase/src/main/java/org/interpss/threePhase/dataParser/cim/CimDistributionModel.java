package org.interpss.threePhase.dataParser.cim;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;
import org.interpss.fadapter.cim.CGMESConstants;
import org.interpss.fadapter.cim.CimRdfUtils;

/** CIM100 graph access, shared across equipment and steady-state RDF/XML profiles. */
final class CimDistributionModel implements AutoCloseable {
    static final String NS = CGMESConstants.CIM100_NS;
    private final Model model = ModelFactory.createDefaultModel();

    void read(Path file) throws Exception {
        CimRdfUtils.read(model, Files.readAllBytes(file), "https://interpss.org/cim/distribution");
    }

    List<Resource> type(String type) {
        return CimRdfUtils.resourcesOfType(model, NS + type).stream()
                .sorted(Comparator.comparing(Resource::toString)).toList();
    }

    List<Resource> linked(String property, Resource target) {
        return model.listResourcesWithProperty(model.createProperty(NS + property), target)
                .toList().stream().sorted(Comparator.comparing(Resource::toString)).toList();
    }

    boolean is(Resource r, String type) { return r.hasProperty(RDF.type, model.createResource(NS + type)); }

    RDFNode value(Resource r, String key) {
        return CimRdfUtils.value(r, NS + key, true);
    }

    Resource ref(Resource r, String key) {
        var v = value(r, key);
        if (v == null) throw error(r, "Missing " + key);
        if (!v.isURIResource() || !model.contains(v.asResource(), RDF.type))
            throw error(r, "Unresolved resource for " + key + ": " + v);
        return v.asResource();
    }

    String text(Resource r, String key, String fallback) {
        var v = value(r, key);
        return v == null ? fallback : v.isLiteral() ? v.asLiteral().getString() : v.asResource().getURI();
    }

    double number(Resource r, String key) {
        if (value(r, key) == null) throw error(r, "Missing " + key);
        return number(r, key, 0);
    }

    double number(Resource r, String key, double fallback) {
        try {
            double n = Double.parseDouble(text(r, key, Double.toString(fallback)));
            if (!Double.isFinite(n)) throw new NumberFormatException();
            return n;
        } catch (NumberFormatException ex) { throw error(r, "Invalid finite number for " + key); }
    }

    boolean flag(Resource r, String key, boolean fallback) {
        String v = text(r, key, Boolean.toString(fallback));
        if (v.equals("true") || v.equals("1")) return true;
        if (v.equals("false") || v.equals("0")) return false;
        throw error(r, "Invalid boolean for " + key);
    }

    String kind(Resource r, String key, String fallback) {
        String v = text(r, key, fallback);
        return v.substring(v.lastIndexOf('.') + 1);
    }

    String name(Resource r) { return text(r, "IdentifiedObject.name", r.toString()); }

    IllegalArgumentException error(Resource r, String message) {
        return new IllegalArgumentException(message + " [" + (r == null ? "CIM" : name(r) + "; " + r) + "]");
    }

    @Override public void close() { model.close(); }
}
