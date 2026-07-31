/*
 * CIMPropertyBag.java
 *
 * Typed accessor for RDF resource properties, similar to PowSyBl's PropertyBag.
 */

package org.interpss.fadapter.cim;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides typed access to properties of an RDF resource (CIM element).
 */
public class CIMPropertyBag {
    private static final Logger log = LoggerFactory.getLogger(CIMPropertyBag.class);

    private final Resource resource;
    private final String cimNamespace;
    private final String entsoeNamespace;

    public CIMPropertyBag(Resource resource, String cimNamespace, String entsoeNamespace) {
        this.resource = resource;
        this.cimNamespace = cimNamespace;
        this.entsoeNamespace = entsoeNamespace;
    }

    /** Get the RDF resource URI (e.g., "#_abc123") */
    public String getId() {
        return resource.getURI();
    }

    /** Get local ID part (after # or last /) */
    public String getLocalId() {
        String uri = resource.getURI();
        if (uri == null) return null;
        int hashIdx = uri.lastIndexOf('#');
        if (hashIdx >= 0) return uri.substring(hashIdx + 1);
        int slashIdx = uri.lastIndexOf('/');
        if (slashIdx >= 0) return uri.substring(slashIdx + 1);
        return uri;
    }

    /** Get cim:IdentifiedObject.name */
    public String getName() {
        return getString(CIMConstants.PROP_NAME);
    }

    /** Get cim:IdentifiedObject.description */
    public String getDescription() {
        return getString(CIMConstants.PROP_DESCRIPTION);
    }

    /** Get entsoe short name */
    public String getShortName() {
        String val = getStringFromNS(entsoeNamespace, CIMConstants.PROP_SHORT_NAME);
        return val;
    }

    /** Get a string property value by CIM local name */
    public String getString(String propertyLocalName) {
        return getStringFromNS(cimNamespace, propertyLocalName);
    }

    /** Get a string property from a specific namespace */
    public String getStringFromNS(String ns, String propertyLocalName) {
        Property prop = resource.getModel().createProperty(ns + propertyLocalName);
        Statement stmt = resource.getProperty(prop);
        if (stmt != null && stmt.getObject().isLiteral()) {
            return stmt.getObject().asLiteral().getString();
        }
        return null;
    }

    /** Get a double property value */
    public double getDouble(String propertyLocalName) {
        return getDouble(propertyLocalName, 0.0);
    }

    public double getDouble(String propertyLocalName, double defaultValue) {
        String val = getString(propertyLocalName);
        if (val == null || val.isEmpty()) return defaultValue;
        try {
            return Double.parseDouble(val);
        } catch (NumberFormatException e) {
            log.warn("Cannot parse double for property {}: {}", propertyLocalName, val);
            return defaultValue;
        }
    }

    /** Get an integer property value */
    public int getInt(String propertyLocalName) {
        return getInt(propertyLocalName, 0);
    }

    public int getInt(String propertyLocalName, int defaultValue) {
        String val = getString(propertyLocalName);
        if (val == null || val.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            log.warn("Cannot parse int for property {}: {}", propertyLocalName, val);
            return defaultValue;
        }
    }

    /** Get a long property value */
    public long getLong(String propertyLocalName) {
        return getLong(propertyLocalName, 0L);
    }

    public long getLong(String propertyLocalName, long defaultValue) {
        String val = getString(propertyLocalName);
        if (val == null || val.isEmpty()) return defaultValue;
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            log.warn("Cannot parse long for property {}: {}", propertyLocalName, val);
            return defaultValue;
        }
    }

    /** Get a boolean property value */
    public boolean getBoolean(String propertyLocalName) {
        return getBoolean(propertyLocalName, false);
    }

    public boolean getBoolean(String propertyLocalName, boolean defaultValue) {
        String val = getString(propertyLocalName);
        if (val == null || val.isEmpty()) return defaultValue;
        return Boolean.parseBoolean(val);
    }

    /**
     * Get a referenced resource (rdf:resource) property.
     * Returns the URI of the referenced resource.
     */
    public String getResourceId(String propertyLocalName) {
        return getResourceIdFromNS(cimNamespace, propertyLocalName);
    }

    public String getResourceIdFromNS(String ns, String propertyLocalName) {
        Property prop = resource.getModel().createProperty(ns + propertyLocalName);
        Statement stmt = resource.getProperty(prop);
        if (stmt != null && stmt.getObject().isResource()) {
            return stmt.getObject().asResource().getURI();
        }
        return null;
    }

    /**
     * Get a referenced resource as a CIMPropertyBag.
     */
    public CIMPropertyBag getResourceBag(String propertyLocalName) {
        Property prop = resource.getModel().createProperty(cimNamespace + propertyLocalName);
        Statement stmt = resource.getProperty(prop);
        if (stmt != null && stmt.getObject().isResource()) {
            return new CIMPropertyBag(stmt.getObject().asResource(), cimNamespace, entsoeNamespace);
        }
        return null;
    }

    /** Get the underlying Jena Resource */
    public Resource getResource() {
        return resource;
    }

    /** Get the CIM namespace in use */
    public String getCimNamespace() {
        return cimNamespace;
    }

    /**
     * Extract local name from a full URI (e.g., "#_abc123" → "_abc123").
     */
    public static String extractLocal(String uri) {
        if (uri == null) return null;
        int idx = uri.lastIndexOf('#');
        if (idx >= 0) return uri.substring(idx + 1);
        idx = uri.lastIndexOf('/');
        if (idx >= 0) return uri.substring(idx + 1);
        return uri;
    }

    @Override
    public String toString() {
        return "CIMPropertyBag{" + getLocalId() + " name=" + getName() + "}";
    }
}
