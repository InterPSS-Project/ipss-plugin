import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import gov.pnnl.gridappsd.cimhub.*;
import gov.pnnl.gridappsd.cimhub.queryhandler.QueryHandler;
import gov.pnnl.gridappsd.cimhub.components.DistComponent;
import gov.pnnl.gridappsd.cimhub.dto.ModelState;

/** Runs upstream CIMHub with local Jena queries and higher output precision.
 * Compile/run with only the pinned CIMHub release jar, never InterPSS classes.
 */
class ExportCimHubReference implements QueryHandler {
    private final Model model;
    ExportCimHubReference(Model model) { this.model = model; }
    public ResultSetCloseable query(String query, String tag) {
        return ResultSetCloseable.closeableResultSet(QueryExecutionFactory.create(Q_PREFIX + query, model));
    }
    public ResultSet construct(String query) {
        try (var execution = QueryExecutionFactory.create(Q_PREFIX + query, model)) {
            return ResultSetFactory.copyResults(execution.execSelect());
        }
    }
    public boolean addFeederSelection(String id) { throw new UnsupportedOperationException("Single-feeder fixtures only"); }
    public boolean clearFeederSelections() { return false; }
    public String getFeederSelection() { return null; }
    public static void main(String[] args) throws Exception {
        ARQ.init();
        // Preserve source precision: upstream's 3-decimal kV output rounds
        // 208/sqrt(3) V to 120 V, changing constant-Z/I loads appreciably.
        // Only text formatting changes; all upstream electrical mappings stay intact.
        for (var field : DistComponent.class.getDeclaredFields()) {
            if (field.getType() == java.text.DecimalFormat.class) {
                field.setAccessible(true);
                var format = (java.text.DecimalFormat) field.get(null);
                format.setMaximumFractionDigits(12);
            }
        }
        Path input = Path.of(args[0]), output = Path.of(args[1]);
        Files.createDirectories(output);
        DistComponent.SetSystemFrequency(60);
        for (String row : Files.readAllLines(input.resolve("cases.csv")).stream().skip(1).toList()) {
            String name = row.split(",")[0];
            Model model = ModelFactory.createDefaultModel();
            try (var stream = new GZIPInputStream(Files.newInputStream(input.resolve(name + ".xml.gz")))) {
                model.read(stream, "https://interpss.org/cim/distribution", "RDF/XML");
            }
            try {
                new CIMImporter().start(new ExportCimHubReference(model), new CIMQuerySetter(),
                    "dss", output.resolve(name).toString(), "", 1.0, false, false, false, false,
                    0, 0, 0, false, new ModelState(new ArrayList<>(), new ArrayList<>()),
                    false, "carson", 0, false);
            } finally { model.close(); }
        }
    }
}
