package org.interpss.fadapter.cim.mapper;

import static org.junit.jupiter.api.Assertions.*;

import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.interpss.fadapter.cim.CGMESModel;
import org.interpss.fadapter.cim.CGMESPropertyBag;
import org.junit.jupiter.api.Test;

class ReactiveCapabilityCurveTest {
    private static final String NS = "http://iec.ch/TC57/2013/CIM-schema-cim16#";

    record Fixture(Model rdf, Resource curve, Resource machine, CGMESGeneratorMapper mapper) {
        CGMESPropertyBag bag() { return new CGMESPropertyBag(machine, NS, "http://entsoe.eu/CIM/SchemaExtension/3/1#"); }
        void point(String id, double p, double min, double max) {
            rdf.createResource("urn:test:" + id)
                    .addProperty(rdf.createProperty(NS + "CurveData.Curve"), curve)
                    .addLiteral(rdf.createProperty(NS + "CurveData.xvalue"), p)
                    .addLiteral(rdf.createProperty(NS + "CurveData.y1value"), min)
                    .addLiteral(rdf.createProperty(NS + "CurveData.y2value"), max);
        }
    }

    private Fixture fixture(String style) {
        Model rdf = ModelFactory.createDefaultModel();
        rdf.createResource("urn:test:substation").addProperty(RDF.type, rdf.createResource(NS + "Substation"));
        Resource curve = rdf.createResource("urn:test:curve")
                .addProperty(rdf.createProperty(NS + "Curve.curveStyle"), rdf.createResource(NS + "CurveStyle." + style));
        Resource machine = rdf.createResource("urn:test:machine")
                .addProperty(rdf.createProperty(NS + "SynchronousMachine.InitialReactiveCapabilityCurve"), curve)
                .addLiteral(rdf.createProperty(NS + "SynchronousMachine.minQ"), 0.)
                .addLiteral(rdf.createProperty(NS + "SynchronousMachine.maxQ"), 0.);
        var mapper = new CGMESGeneratorMapper(100.);
        mapper.setCimModel(new CGMESModel(rdf));
        return new Fixture(rdf, curve, machine, mapper);
    }

    @Test void constantCurveUsesSignedScheduleAndExactBreakpoint() {
        var f = fixture("constantYValue");
        f.point("right", 100., -2., 3.);
        f.point("left", -100., -2., 3.);
        f.point("middle", 0., -4., 5.);
        assertArrayEquals(new double[] {-2., 3.}, f.mapper.reactiveCurveLimits(f.bag(), -50.));
        assertArrayEquals(new double[] {-4., 5.}, f.mapper.reactiveCurveLimits(f.bag(), 0.));
        assertArrayEquals(new double[] {-4., 5.}, f.mapper.reactiveCurveLimits(f.bag(), 50.));
        assertArrayEquals(new double[] {-2., 3.}, f.mapper.reactiveCurveLimits(f.bag(), 150.));
        assertArrayEquals(new double[] {-2., 3.}, f.mapper.reactiveCurveLimits(f.bag(), -150.));
    }

    @Test void linearCurveInterpolatesBothBounds() {
        var f = fixture("straightLineYValues");
        f.point("left", -100., -2., 3.);
        f.point("right", 0., -4., 5.);
        assertArrayEquals(new double[] {-3., 4.}, f.mapper.reactiveCurveLimits(f.bag(), -50.), 1e-12);
    }

    @Test void explicitAxisMultipliersNormalizeToMva() {
        for (String multiplier : new String[] {"none", "k", "M", "G"}) {
            double factor = switch (multiplier) { case "none" -> 1e6; case "k" -> 1e3; case "M" -> 1.; default -> 1e-3; };
            var f = fixture("straightLineYValues");
            for (String axis : new String[] {"x", "y1", "y2"})
                f.curve.addProperty(f.rdf.createProperty(NS + "Curve." + axis + "Multiplier"),
                        f.rdf.createResource(NS + "UnitMultiplier." + multiplier));
            f.point("left", -100. * factor, -2. * factor, 3. * factor);
            f.point("right", 0., -4. * factor, 5. * factor);
            assertArrayEquals(new double[] {-3., 4.}, f.mapper.reactiveCurveLimits(f.bag(), -50e6), 1e-12);
        }
    }

    @Test void absentCurveKeepsScalarBoundsAndMalformedCurveFails() {
        var f = fixture("straightLineYValues");
        assertThrows(IllegalArgumentException.class, () -> f.mapper.reactiveCurveLimits(f.bag(), 0.));
        f.point("left", 0., -2., 3.);
        f.point("duplicate", 0., -3., 4.);
        assertThrows(IllegalArgumentException.class, () -> f.mapper.reactiveCurveLimits(f.bag(), 0.));
        f.machine.removeAll(f.rdf.createProperty(NS + "SynchronousMachine.InitialReactiveCapabilityCurve"));
        assertNull(f.mapper.reactiveCurveLimits(f.bag(), 0.));
    }
}
