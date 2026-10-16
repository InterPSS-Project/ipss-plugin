/*
 * CGMESRdfParser.java
 *
 * Parses RDF/XML CIM files into Apache Jena Model.
 * Sanitizes non-standard UUIDs in ENTSO-E test files before parsing.
 */

package org.interpss.fadapter.cim.parser;

import java.io.BufferedReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.interpss.fadapter.cim.CimRdfUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses CIM RDF/XML file(s) into a Jena Model.
 * Pre-processes input to fix non-standard URIs in ENTSO-E test files.
 */
public class CGMESRdfParser {
    private static final Logger log = LoggerFactory.getLogger(CGMESRdfParser.class);

    /** Pattern matching invalid UUID suffixes like urn:uuid:..._EU */
    private static final Pattern BAD_UUID = Pattern.compile(
        "(urn:uuid:[0-9a-fA-F-]{36})_[a-zA-Z]+");

    /**
     * Pattern matching FullModel rdf:about with urn:uuid that may be malformed.
     * Jena's RDF/XML parser validates UUIDs and rejects non-hex characters.
     * Replace with a stable, valid base URI so all files share the same base.
     */
    private static final Pattern FULL_MODEL_ABOUT = Pattern.compile(
        "(<md:FullModel\\s+rdf:about=\")urn:uuid:[^\"]*(\")");

    /** Stable base URI for all CIM files */
    static final String BASE_URI = "http://cim.import/base";

    public Model parse(BufferedReader reader) throws Exception {
        return parseMultiple(new BufferedReader[]{reader});
    }

    public Model parseMultiple(BufferedReader[] readers) throws Exception {
        Model model = ModelFactory.createDefaultModel();
        try {
            for (BufferedReader reader : readers) {
                var text = new StringWriter();
                reader.transferTo(text);
                parseOne(text.toString(), model);
            }
            log.info("RDF model loaded with {} triples", model.size());
            return model;
        } catch (Exception ex) {
            model.close();
            throw ex;
        }
    }

    public Model parseString(String rdfXml) throws Exception {
        return parse(new BufferedReader(new StringReader(rdfXml)));
    }

    private void parseOne(String rdfXml, Model model) throws Exception {
        // 1. Strip non-standard UUID suffixes (e.g., urn:uuid:..._EU)
        String sanitized = BAD_UUID.matcher(rdfXml).replaceAll("$1");
        // 2. Replace FullModel's urn:uuid with a stable, valid base URI.
        //    This prevents Jena from rejecting malformed UUIDs and ensures
        //    all files use the same base URI for resource ID resolution.
        sanitized = FULL_MODEL_ABOUT.matcher(sanitized).replaceAll("$1" + BASE_URI + "$2");
        CimRdfUtils.read(model, sanitized.getBytes(StandardCharsets.UTF_8), BASE_URI);
    }
}
