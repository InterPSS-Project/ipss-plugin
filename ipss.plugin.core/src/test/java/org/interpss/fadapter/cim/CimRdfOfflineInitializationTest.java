package org.interpss.fadapter.cim;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.management.ManagementFactory;
import java.net.ProtocolFamily;
import java.net.http.HttpClient;
import java.nio.channels.*;
import java.nio.channels.spi.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import org.apache.jena.rdf.model.ModelFactory;
import org.interpss.fadapter.cim.parser.CGMESRdfParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercise cold Jena initialization: an already initialized test JVM would hide this regression. */
class CimRdfOfflineInitializationTest {
    @TempDir Path temporary;

    @Test void localCimReadsDoNotInitializeHttpOrOpenSelectors() throws Exception {
        Path output = temporary.resolve("offline-reader.log");
        String java = ProcessHandle.current().info().command().orElseThrow();
        String classpath = ManagementFactory.getRuntimeMXBean().getClassPath();
        var process = new ProcessBuilder(java,
                "-Djava.nio.channels.spi.SelectorProvider=" + RejectingProvider.class.getName(),
                "-cp", classpath, Probe.class.getName())
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        boolean completed = process.waitFor(30, TimeUnit.SECONDS);
        if (!completed) process.destroyForcibly();
        assertTrue(completed, "Fresh-JVM offline read timed out");
        String log = Files.readString(output);
        assertEquals(0, process.exitValue(), log);
        assertTrue(log.contains("OFFLINE_CIM_OK_AND_HTTP_CONTROL_REJECTED"), log);
    }

    /** Only used in the child JVM; deliberately fails before a selector can open a socket. */
    public static final class RejectingProvider extends SelectorProvider {
        static boolean attempted;
        public RejectingProvider() {
            attempted = true;
            throw new IllegalStateException("OFFLINE_TEST_REJECTS_SELECTORS");
        }
        public DatagramChannel openDatagramChannel() { throw new AssertionError(); }
        public DatagramChannel openDatagramChannel(ProtocolFamily family) { throw new AssertionError(); }
        public Pipe openPipe() { throw new AssertionError(); }
        public AbstractSelector openSelector() { throw new AssertionError(); }
        public ServerSocketChannel openServerSocketChannel() { throw new AssertionError(); }
        public SocketChannel openSocketChannel() { throw new AssertionError(); }
    }

    public static final class Probe {
        public static void main(String[] args) throws Exception {
            String xml = """
                    <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                        xmlns:cim="http://iec.ch/TC57/CIM100#" xml:base="https://example.invalid/cim/">
                      <cim:EnergyConsumer rdf:ID="load">
                        <cim:EnergyConsumer.p rdf:datatype="http://www.w3.org/2001/XMLSchema#double">42.5</cim:EnergyConsumer.p>
                        <cim:EnergyConsumer.LoadResponse rdf:nodeID="response"/>
                      </cim:EnergyConsumer>
                      <rdf:Description rdf:nodeID="response"><cim:IdentifiedObject.name xml:lang="en">Local</cim:IdentifiedObject.name></rdf:Description>
                    </rdf:RDF>
                    """;
            var local = ModelFactory.createDefaultModel();
            var cgmes = new CGMESRdfParser().parseString(xml);
            try {
                CimRdfUtils.read(local, xml.getBytes(StandardCharsets.UTF_8), "https://example.invalid/base");
                if (!local.isIsomorphicWith(cgmes) || local.size() != 4)
                    throw new AssertionError("Local and CGMES RDF graph semantics differ");
                var load = local.getResource("https://example.invalid/cim/#load");
                if (load.getProperty(local.createProperty(CGMESConstants.CIM100_NS + "EnergyConsumer.p")).getDouble() != 42.5)
                    throw new AssertionError("Typed literal or XML base lost");
                if (!CGMESConstants.CIM100_NS.equals(local.getNsPrefixURI("cim")))
                    throw new AssertionError("Namespace prefix lost");
                if (RejectingProvider.attempted)
                    throw new AssertionError("Local RDF parsing attempted to create a selector");
            } finally { local.close(); cgmes.close(); }
            // Positive control: prove this JVM actually rejects HTTP client initialization.
            try {
                HttpClient.newHttpClient();
                throw new AssertionError("Expected the HTTP client control to fail");
            } catch (Exception | java.util.ServiceConfigurationError ex) {
                if (!RejectingProvider.attempted) throw ex;
                System.out.println("OFFLINE_CIM_OK_AND_HTTP_CONTROL_REJECTED");
            }
        }
    }
}
