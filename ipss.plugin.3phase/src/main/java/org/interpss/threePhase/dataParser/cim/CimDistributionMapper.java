package org.interpss.threePhase.dataParser.cim;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.jena.rdf.model.Resource;
import org.interpss.threePhase.basic.CableLineConstants;

/** Maps the CIMHub CIM100 distribution profile to the existing equipment builders. */
final class CimDistributionMapper {
    private final CimDistributionModel g;
    private final StringBuilder dss = new StringBuilder();
    final Map<String, String> busIds = new LinkedHashMap<>();
    final Map<String, String> names = new LinkedHashMap<>();
    final Map<String, String> equipmentIds = new LinkedHashMap<>();
    final Map<String, double[]> taps = new LinkedHashMap<>();
    final Map<String, Integer> clocks = new LinkedHashMap<>();
    final List<String> warnings = new ArrayList<>();
    private final Set<Resource> selected = new LinkedHashSet<>();
    private final Set<Resource> handled = new HashSet<>();
    private final Set<Resource> emittedWires = new HashSet<>();
    private final Map<String, String> identities = new HashMap<>();
    double sourceAngle;

    CimDistributionMapper(CimDistributionModel graph) { this.g = graph; }

    String map(String feederMrid) {
        var feeders = g.type("Feeder");
        if (feederMrid != null) feeders = feeders.stream().filter(f -> f.toString().equals(feederMrid)
                || g.text(f, "IdentifiedObject.mRID", "").equals(feederMrid)).toList();
        if (feeders.size() != 1) throw g.error(null, "Select exactly one Feeder by mRID or RDF URI; found " + feeders.size());
        Resource feeder = feeders.get(0);
        selected.addAll(g.linked("Equipment.EquipmentContainer", feeder));
        // Tank equipment is contained by a bank rather than directly by the feeder.
        for (Resource bank : List.copyOf(selected)) selected.addAll(g.linked("TransformerTank.PowerTransformer", bank));
        for (Resource r : selected) {
            if (!g.flag(r, "Equipment.inService", true)) throw g.error(r, "Out-of-service equipment is unsupported; remove it or use Switch.open");
        }
        var sources = selected.stream().filter(r -> g.is(r, "EnergySource")).toList();
        if (sources.size() != 1) throw g.error(feeder, "Exactly one EnergySource is required");
        source(sources.get(0));
        for (Resource r : selected) if (g.is(r, "ACLineSegment")) line(r);
        for (Resource r : selected) if (g.is(r, "PowerTransformer")) transformer(r);
        for (Resource r : selected) {
            if (g.is(r, "EnergyConsumer")) load(r);
            else if (g.is(r, "LinearShuntCompensator")) shunt(r);
            else if (g.is(r, "PowerElectronicsConnection")) injection(r);
            else if (isSwitch(r)) switchElement(r);
        }
        // A terminal means electrical equipment: unknown equipment must never disappear silently.
        for (Resource r : selected) if (!handled.contains(r) && !g.linked("Terminal.ConductingEquipment", r).isEmpty())
            throw g.error(r, "Unsupported conducting equipment");
        warnings.add("Static 60 Hz snapshot: tap positions and shunt sections are fixed; automatic controls and dynamic models are not imported.");
        warnings.add("Operational limits, geographical coordinates and time-series profiles are not mapped; this importer prepares power-flow data, not OPF limits.");
        return dss.toString();
    }

    private String id(Resource r) {
        String key = g.text(r, "IdentifiedObject.mRID", r.toString());
        String token = "cim_" + UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
        String previous = identities.putIfAbsent(token, r.toString());
        if (previous != null && !previous.equals(r.toString())) throw g.error(r, "Duplicate mRID");
        names.put(r.toString(), g.name(r));
        return token;
    }

    private String equipment(Resource r) {
        String id = id(r);
        equipmentIds.put(r.toString(), id);
        handled.add(r);
        return id;
    }

    private List<Resource> terminals(Resource r, int count) {
        var t = sorted(g.linked("Terminal.ConductingEquipment", r), "ACDCTerminal.sequenceNumber");
        if (t.size() != count) throw g.error(r, "Expected " + count + " terminals, found " + t.size());
        for (int i = 0; i < t.size(); i++) {
            if (g.number(t.get(i), "ACDCTerminal.sequenceNumber", i + 1) != i + 1)
                throw g.error(r, "Terminal sequence numbers must be consecutive from 1");
            if (!g.flag(t.get(i), "ACDCTerminal.connected", true)) throw g.error(r, "Disconnected terminals are unsupported; use Switch.open");
        }
        return t;
    }

    private String bus(Resource terminal) {
        if (!g.flag(terminal, "ACDCTerminal.connected", true))
            throw g.error(terminal, "Disconnected terminals are unsupported; use Switch.open");
        Resource node = g.ref(terminal, "Terminal.ConnectivityNode");
        if (!g.is(node, "ConnectivityNode")) throw g.error(node, "Expected ConnectivityNode");
        return busIds.computeIfAbsent(node.toString(), key -> id(node));
    }

    private List<Resource> sorted(List<Resource> items, String property) {
        return items.stream().sorted(Comparator.comparingDouble(r -> g.number(r, property, 1))).toList();
    }

    private double positive(Resource r, String p) {
        double n = g.number(r, p);
        if (n <= 0) throw g.error(r, p + " must be positive");
        return n;
    }
    private int integer(Resource r, String p) {
        double n = g.number(r, p);
        if (n != Math.rint(n) || n < 0 || n > Integer.MAX_VALUE) throw g.error(r, "Invalid nonnegative integer for " + p);
        return (int)n;
    }

    private void out(String text) { dss.append(text).append('\n'); }
    private String list(List<?> values) { return values.stream().map(Object::toString).collect(Collectors.joining(" ", "[", "]")); }
    private String suffix(String phases) {
        StringBuilder b = new StringBuilder();
        for (char p : phases.toCharArray()) {
            if (p == '1' || p == '2') b.append('.').append(p);
            else if (p >= 'A' && p <= 'C') b.append('.').append(p - 'A' + 1);
            else throw new IllegalArgumentException("Unsupported phase code: " + phases);
        }
        return b.toString();
    }
    private String phase(Resource r, String property, String fallback) {
        String p = normalizedPhases(g.kind(r, property, fallback));
        if (!validPhases(p))
            throw g.error(r, "Unsupported phase designation " + p);
        return p;
    }
    private String normalizedPhases(String p) { return p.replace("N", "").replace(":", "").replace("s", ""); }
    private boolean validPhases(String p) {
        return (p.matches("[ABC]+") || p.matches("[12]+")) && p.chars().distinct().count() == p.length();
    }
    private String phases(Resource r, String childType, String relation, String property) {
        var children = sorted(g.linked(relation, r), childType + ".sequenceNumber");
        if (!children.isEmpty()) {
            String p = normalizedPhases(children.stream().map(c -> g.kind(c, property, "")).collect(Collectors.joining()));
            if (!validPhases(p)) throw g.error(r, "Unsupported/duplicate phases: " + p);
            for (Resource terminal : g.linked("Terminal.ConductingEquipment", r)) {
                if (g.value(terminal, "Terminal.phases") != null) {
                    String tp = phase(terminal, "Terminal.phases", "ABC");
                    if (!tp.chars().sorted().boxed().toList().equals(p.chars().sorted().boxed().toList()))
                        throw g.error(r, "Terminal phases disagree with equipment phase records");
                }
            }
            return p;
        }
        return equipmentPhases(r);
    }
    private String equipmentPhases(Resource r) {
        String result = g.value(r, "ConductingEquipment.phases") == null ? null : phase(r, "ConductingEquipment.phases", "ABC");
        for (Resource t : g.linked("Terminal.ConductingEquipment", r)) {
            if (g.value(t, "Terminal.phases") == null) continue;
            String tp = phase(t, "Terminal.phases", "ABC");
            if (result != null && !result.equals(tp)) throw g.error(r, "Inconsistent terminal phases");
            result = tp;
        }
        return result == null ? "ABC" : result;
    }
    private double baseVoltage(Resource r) { return positive(g.ref(r, "ConductingEquipment.BaseVoltage"), "BaseVoltage.nominalVoltage"); }

    private void source(Resource r) {
        if (!equipmentPhases(r).equals("ABC")) throw g.error(r, "Source must have ABC phases");
        double v = positive(r, "EnergySource.nominalVoltage");
        sourceAngle = g.number(r, "EnergySource.voltageAngle", 0);
        out("new circuit." + equipment(r) + " phases=3 bus1=" + bus(terminals(r, 1).get(0))
                + " basekv=" + v / 1000 + " pu=" + positive(r, "EnergySource.voltageMagnitude") / v
                + " angle=" + Math.toDegrees(sourceAngle)
                + " r1=" + g.number(r, "EnergySource.r", 0) + " x1=" + g.number(r, "EnergySource.x", 0)
                + " r0=" + g.number(r, "EnergySource.r0", 0) + " x0=" + g.number(r, "EnergySource.x0", 0));
    }

    private void line(Resource r) {
        var ts = terminals(r, 2);
        String p = phases(r, "ACLineSegmentPhase", "ACLineSegmentPhase.ACLineSegment", "ACLineSegmentPhase.phase");
        String eid = equipment(r);
        String params;
        if (g.value(r, "ACLineSegment.PerLengthImpedance") != null) {
            Resource z = g.ref(r, "ACLineSegment.PerLengthImpedance");
            if (g.is(z, "PerLengthPhaseImpedance")) {
                int n = integer(z, "PerLengthPhaseImpedance.conductorCount");
                if (n != p.length()) throw g.error(r, "Matrix conductor count must match energized phases (explicit neutrals require Kron reduction)");
                double[][] rr = new double[n][n], xx = new double[n][n], cc = new double[n][n];
                boolean[][] seen = new boolean[n][n];
                for (Resource v : g.linked("PhaseImpedanceData.PhaseImpedance", z)) {
                    int row = integer(v, "PhaseImpedanceData.row") - 1;
                    int col = integer(v, "PhaseImpedanceData.column") - 1;
                    if (row < 0 || col < 0 || row >= n || col >= n || seen[row][col]) throw g.error(v, "Invalid or duplicate matrix cell");
                    rr[row][col] = rr[col][row] = g.number(v, "PhaseImpedanceData.r");
                    xx[row][col] = xx[col][row] = g.number(v, "PhaseImpedanceData.x");
                    cc[row][col] = cc[col][row] = g.number(v, "PhaseImpedanceData.b", 0) * 1e9 / (2 * Math.PI * 60);
                    if (g.number(v, "PhaseImpedanceData.g", 0) != 0) throw g.error(v, "Nonzero line shunt conductance is unsupported");
                    seen[row][col] = seen[col][row] = true;
                }
                for (boolean[] row : seen) for (boolean cell : row) if (!cell) throw g.error(z, "Incomplete phase impedance matrix");
                // Matrix rows follow ACLineSegmentPhase.sequenceNumber, not alphabetic order.
                String canonical = p.chars().sorted().collect(StringBuilder::new, (b, c) -> b.append((char)c), StringBuilder::append).toString();
                out("new linecode." + eid + " nphases=" + n + " units=m rmatrix=" + matrix(rr, p, canonical)
                        + " xmatrix=" + matrix(xx, p, canonical) + " cmatrix=" + matrix(cc, p, canonical));
                p = canonical;
                params = "linecode=" + eid + " length=" + positive(r, "Conductor.length") + " units=m";
            } else if (g.is(z, "PerLengthSequenceImpedance")) {
                if (p.length() != 3) throw g.error(r, "Sequence impedance requires ABC phases");
                params = sequence(z, "PerLengthSequenceImpedance.") + " length=" + positive(r, "Conductor.length") + " units=m";
            } else throw g.error(z, "Unsupported per-length impedance class");
        } else if (g.value(r, "ACLineSegment.WireSpacingInfo") != null) {
            Resource spacing = g.ref(r, "ACLineSegment.WireSpacingInfo");
            boolean cable = g.flag(spacing, "WireSpacingInfo.isCable", false);
            if (cable) {
                cableGeometry(r, eid, p);
                p = p.chars().sorted().collect(StringBuilder::new, (b, c) -> b.append((char)c), StringBuilder::append).toString();
            }
            else geometry(r, eid, p);
            params = (cable ? "linecode=" : "geometry=") + eid + " length=" + positive(r, "Conductor.length") + " units=m";
        } else {
            if (p.length() != 3) throw g.error(r, "Instance sequence impedance requires ABC phases");
            params = sequence(r, "ACLineSegment.") + " length=1";
        }
        out("new line." + eid + " phases=" + p.length() + " bus1=" + bus(ts.get(0)) + suffix(p)
                + " bus2=" + bus(ts.get(1)) + suffix(p) + " " + params);
    }
    private String matrix(double[][] a, String original, String canonical) {
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < canonical.length(); i++) {
            List<String> row = new ArrayList<>();
            for (int j = 0; j <= i; j++) row.add(Double.toString(a[original.indexOf(canonical.charAt(i))][original.indexOf(canonical.charAt(j))]));
            rows.add(String.join(" ", row));
        }
        return "[" + String.join(" | ", rows) + "]";
    }
    private String sequence(Resource r, String prefix) {
        if (g.number(r, prefix + "gch", 0) != 0 || g.number(r, prefix + "g0ch", 0) != 0) throw g.error(r, "Nonzero line shunt conductance is unsupported");
        return "r1=" + g.number(r, prefix + "r") + " x1=" + g.number(r, prefix + "x")
                + " r0=" + g.number(r, prefix + "r0") + " x0=" + g.number(r, prefix + "x0")
                + " c1=" + g.number(r, prefix + "bch", 0) * 1e9 / (2 * Math.PI * 60)
                + " c0=" + g.number(r, prefix + "b0ch", 0) * 1e9 / (2 * Math.PI * 60);
    }

    private void geometry(Resource line, String eid, String phases) {
        String canonical = phases.chars().sorted().collect(StringBuilder::new,
                (b, c) -> b.append((char)c), StringBuilder::append).toString();
        if (!phases.equals(canonical)) throw g.error(line, "Geometry requires canonical phase order; supply phase impedance data for permuted conductors");
        Resource spacing = g.ref(line, "ACLineSegment.WireSpacingInfo");
        var positions = sorted(g.linked("WirePosition.WireSpacingInfo", spacing), "WirePosition.sequenceNumber");
        var wires = sorted(g.linked("ACLineSegmentPhase.ACLineSegment", line), "ACLineSegmentPhase.sequenceNumber");
        if (positions.size() != wires.size() || wires.size() < phases.length()) throw g.error(line, "Wire positions do not match line conductors");
        for (Resource wirePhase : wires) {
            Resource wire = g.ref(wirePhase, "ACLineSegmentPhase.WireInfo");
            if (!g.is(wire, "OverheadWireInfo")) throw g.error(wire, "Only overhead wire geometry is supported");
            if (!emittedWires.add(wire)) continue;
            // Preserve wire data; the facade selects Carson with the supplied AC resistance.
            out("new wiredata." + id(wire) + " rac=" + positive(wire, "WireInfo.rAC50") + " rdc=" + positive(wire, "WireInfo.rDC20")
                    + " runits=m gmrac=" + positive(wire, "WireInfo.gmr") + " gmrunits=m radius=" + positive(wire, "WireInfo.radius") + " radunits=m");
        }
        out("new linegeometry." + eid + " nconds=" + wires.size() + " nphases=" + phases.length() + " reduce=yes");
        for (int i = 0; i < wires.size(); i++) {
            String p = g.kind(wires.get(i), "ACLineSegmentPhase.phase", "");
            if (!p.equals("N")) p = normalizedPhases(p);
            if (i < phases.length() ? !p.equals(phases.substring(i, i + 1)) : !p.equals("N")) throw g.error(line, "Geometry requires phases first, followed by neutral");
            out("~ cond=" + (i + 1) + " wire=" + id(g.ref(wires.get(i), "ACLineSegmentPhase.WireInfo"))
                    + " x=" + g.number(positions.get(i), "WirePosition.xCoord") + " h=" + g.number(positions.get(i), "WirePosition.yCoord") + " units=m");
        }
    }

    private void cableGeometry(Resource line, String eid, String phases) {
        var spacing = g.ref(line, "ACLineSegment.WireSpacingInfo");
        var positions = sorted(g.linked("WirePosition.WireSpacingInfo", spacing), "WirePosition.sequenceNumber");
        var wires = sorted(g.linked("ACLineSegmentPhase.ACLineSegment", line), "ACLineSegmentPhase.sequenceNumber");
        if (positions.size() != wires.size() || wires.size() < phases.length()) throw g.error(line, "Wire positions do not match cable conductors");
        List<CableLineConstants.Conductor> conductors = new ArrayList<>();
        for (int i = 0; i < wires.size(); i++) {
            Resource wire = g.ref(wires.get(i), "ACLineSegmentPhase.WireInfo"), pos = positions.get(i);
            String ph = g.kind(wires.get(i), "ACLineSegmentPhase.phase", "");
            boolean neutral = i >= phases.length();
            if (neutral ? !ph.equals("N") : !normalizedPhases(ph).equals(phases.substring(i, i + 1)))
                throw g.error(line, "Cable geometry requires phase conductors followed by bare neutrals");
            boolean cn = g.is(wire, "ConcentricNeutralCableInfo"), ts = g.is(wire, "TapeShieldCableInfo");
            if (neutral ? !g.is(wire, "OverheadWireInfo") : !cn && !ts) throw g.error(wire, "Expected shielded cable or bare neutral data");
            int strands = neutral ? -1 : cn ? integer(wire, "ConcentricNeutralCableInfo.neutralStrandCount") : 0;
            if (cn && strands == 0) throw g.error(wire, "Neutral strand count must be positive");
            double thick = g.number(wire, "TapeShieldCableInfo.tapeThickness", 0);
            conductors.add(new CableLineConstants.Conductor(g.number(pos, "WirePosition.xCoord"), g.number(pos, "WirePosition.yCoord"),
                    positive(wire, "WireInfo.rAC50"), positive(wire, "WireInfo.gmr"), positive(wire, "WireInfo.radius"),
                    g.number(wire, "CableInfo.diameterOverInsulation", 0), g.number(wire, "WireInfo.insulationThickness", 0),
                    g.number(wire, "CableInfo.relativePermittivity", 2.3), g.number(wire, "CableInfo.diameterOverJacket", 0), strands,
                    g.number(wire, "ConcentricNeutralCableInfo.neutralStrandGmr", 0), g.number(wire, "ConcentricNeutralCableInfo.neutralStrandRadius", 0),
                    g.number(wire, "ConcentricNeutralCableInfo.neutralStrandRDC20", 0), g.number(wire, "CableInfo.diameterOverScreen", 0) + 2 * thick,
                    thick, g.number(wire, "TapeShieldCableInfo.tapeLap", 0)));
        }
        var result = CableLineConstants.calculate(conductors, phases.length());
        double[][] rr = new double[phases.length()][phases.length()], xx = new double[phases.length()][phases.length()];
        for (int i = 0; i < phases.length(); i++) for (int j = 0; j < phases.length(); j++) {
            rr[i][j] = result.impedanceOhmPerMetre()[i][j].getReal();
            xx[i][j] = result.impedanceOhmPerMetre()[i][j].getImaginary();
        }
        String canonical = phases.chars().sorted().collect(StringBuilder::new, (b, c) -> b.append((char)c), StringBuilder::append).toString();
        out("new linecode." + eid + " nphases=" + phases.length() + " units=m rmatrix=" + matrix(rr, phases, canonical)
                + " xmatrix=" + matrix(xx, phases, canonical) + " cmatrix=" + matrix(result.capacitanceNfPerMetre(), phases, canonical));
    }

    private void transformer(Resource bank) {
        var tanks = g.linked("TransformerTank.PowerTransformer", bank);
        if (!tanks.isEmpty()) {
            handled.add(bank);
            for (Resource tank : tanks) tank(tank);
            return;
        }
        var ends = sorted(g.linked("PowerTransformerEnd.PowerTransformer", bank), "TransformerEnd.endNumber");
        if (ends.size() == 3) threeWinding(bank, ends, ends, "PowerTransformerEnd.", 3);
        else if (ends.size() == 2) transformer(bank, ends, ends, "PowerTransformerEnd.", 3);
        else throw g.error(bank, "Expected two or three transformer windings");
    }

    private String tankPhases(Resource end) {
        return phase(end, g.value(end, "TransformerTankEnd.orderedPhases") != null
                ? "TransformerTankEnd.orderedPhases" : "TransformerTankEnd.phases", "ABC");
    }

    private void tank(Resource tank) {
        Resource info;
        if (g.value(tank, "TransformerTank.TransformerTankInfo") != null) info = g.ref(tank, "TransformerTank.TransformerTankInfo");
        else {
            var assets = g.linked("Asset.PowerSystemResources", tank);
            if (assets.size() != 1) throw g.error(tank, "Expected one asset referencing TransformerTankInfo");
            info = g.ref(assets.get(0), "Asset.AssetInfo");
        }
        var ends = sorted(g.linked("TransformerTankEnd.TransformerTank", tank), "TransformerEnd.endNumber");
        var ratings = sorted(g.linked("TransformerEndInfo.TransformerTankInfo", info), "TransformerEndInfo.endNumber");
        if (ends.size() != ratings.size() || (ends.size() != 2 && ends.size() != 3)) throw g.error(tank, "Expected matching two or three tank windings and ratings");
        String phases = tankPhases(ends.get(0));
        int nph = phases.equals("ABC") ? 3 : 1;
        if (ends.size() == 3) threeWinding(tank, ends, ratings, "TransformerEndInfo.", nph);
        else {
            if (phases.length() != 1 && !phases.equals("ABC")) throw g.error(tank, "Unsupported tank phases");
            transformer(tank, ends, ratings, "TransformerEndInfo.", nph);
        }
    }

    private double tapRatio(Resource end) {
        var changers = g.linked("RatioTapChanger.TransformerEnd", end);
        if (changers.size() > 1) throw g.error(end, "Multiple ratio tap changers on one winding");
        if (changers.isEmpty()) return 1;
        Resource tc = changers.get(0);
        double neutral = g.number(tc, "TapChanger.neutralStep", 0);
        double step = g.number(tc, "TapChanger.step", g.number(tc, "TapChanger.normalStep", neutral));
        if (step < g.number(tc, "TapChanger.lowStep") || step > g.number(tc, "TapChanger.highStep")) throw g.error(tc, "Tap step out of range");
        double ratio = 1 + (step - neutral) * g.number(tc, "RatioTapChanger.stepVoltageIncrement") / 100;
        if (ratio <= 0) throw g.error(tc, "Nonpositive tap ratio");
        return ratio;
    }

    private void threeWinding(Resource r, List<Resource> ends, List<Resource> ratings, String prefix, int nph) {
        String eid = equipment(r);
        List<String> buses = new ArrayList<>(), conns = new ArrayList<>();
        List<Double> kvs = new ArrayList<>(), kvas = new ArrayList<>(), ratios = new ArrayList<>();
        double[] zbase = new double[3], rp = new double[3];
        int[] clock = new int[3];
        double sbase = positive(ratings.get(0), prefix + "ratedS");
        for (int i = 0; i < 3; i++) {
            Resource e = ends.get(i), rating = ratings.get(i);
            if (integer(e, "TransformerEnd.endNumber") != i + 1 || integer(rating, prefix.equals("PowerTransformerEnd.") ? "TransformerEnd.endNumber" : prefix + "endNumber") != i + 1)
                throw g.error(e, "Invalid winding number");
            String conn = g.kind(rating, prefix + "connectionKind", "");
            if (!Set.of("Y", "Yn", "D", "I").contains(conn) || (nph == 3 && conn.equals("I"))) throw g.error(e, "Unsupported winding connection " + conn);
            if ((!conn.equals("D") && !g.flag(e, "TransformerEnd.grounded", conn.equals("Yn") || conn.equals("I")))
                    || g.number(e, "TransformerEnd.rground", 0) != 0 || g.number(e, "TransformerEnd.xground", 0) != 0)
                throw g.error(e, "Three-winding neutral must be solidly grounded");
            String p = tankPhases(e);
            String raw = g.kind(e, g.value(e, "TransformerTankEnd.orderedPhases") != null ? "TransformerTankEnd.orderedPhases" : "TransformerTankEnd.phases", "ABC");
            String nodes = suffix(p);
            clock[i] = Math.floorMod(integerOrDefault(rating, prefix + "phaseAngleClock", 0), 12);
            if (nph == 1) {
                if (p.length() > 2) throw g.error(e, "Single-phase winding requires one or two conductors");
                if (p.length() == 1) nodes = raw.startsWith("N") ? ".0" + nodes : nodes + ".0";
                // OrderedPhaseCode already fixes winding polarity (e.g. Ns2).
                // CIMHub exports clock 6 as well; applying both reverses it twice.
                if (clock[i] == 6 && g.value(e, "TransformerTankEnd.orderedPhases") == null) {
                    String[] ns = nodes.substring(1).split("\\.");
                    nodes = "." + ns[1] + "." + ns[0];
                } else if (clock[i] != 0 && clock[i] != 6) throw g.error(e, "Single-phase clock must be 0 or 6");
            } else if (!p.equals("ABC")) throw g.error(e, "Three-phase winding must have ABC phases");
            buses.add(bus(g.ref(e, "TransformerEnd.Terminal")) + nodes);
            conns.add(conn.equals("D") ? "delta" : "wye");
            double u = positive(rating, prefix + "ratedU"), s = positive(rating, prefix + "ratedS");
            zbase[i] = u * u / sbase;
            rp[i] = g.number(rating, prefix + "r", 0) / zbase[i] * 100;
            if (rp[i] < 0) throw g.error(rating, "Negative winding resistance");
            kvs.add(u / 1000); kvas.add(s / 1000); ratios.add(tapRatio(e));
        }
        double[] xp = new double[3]; // 12, 13, 23, all on winding-one's power base
        int[][] pairs = {{0,1},{0,2},{1,2}};
        for (int k = 0; k < 3; k++) {
            int i = pairs[k][0], j = pairs[k][1];
            if (prefix.equals("PowerTransformerEnd.")) {
                Resource mesh = null; int from = i;
                for (int a : new int[]{i, j}) for (Resource m : g.linked("TransformerMeshImpedance.FromTransformerEnd", ends.get(a)))
                    if (g.ref(m, "TransformerMeshImpedance.ToTransformerEnd").equals(ends.get(a == i ? j : i))) {
                        if (mesh != null) throw g.error(m, "Duplicate transformer mesh pair");
                        mesh = m; from = a;
                    }
                xp[k] = mesh == null ? 100 * (g.number(ends.get(i), prefix + "x") / zbase[i] + g.number(ends.get(j), prefix + "x") / zbase[j])
                        : 100 * g.number(mesh, "TransformerMeshImpedance.x") / zbase[from];
            } else {
                Resource test = null; int from = i;
                for (int a : new int[]{i, j}) for (Resource t : g.linked("ShortCircuitTest.EnergisedEnd", ratings.get(a)))
                    if (g.ref(t, "ShortCircuitTest.GroundedEnds").equals(ratings.get(a == i ? j : i))) {
                        if (test != null) throw g.error(t, "Duplicate short-circuit pair");
                        test = t; from = a;
                    }
                if (test == null) throw g.error(r, "Missing short-circuit test for winding pair " + (i + 1) + "-" + (j + 1));
                double z = positive(test, "ShortCircuitTest.leakageImpedance") / zbase[from] * 100;
                double testR = rp[i] + rp[j];
                if (g.value(test, "ShortCircuitTest.loss") != null) {
                    double power = positive(test, "TransformerTest.basePower");
                    testR = 100 * 1000 * g.number(test, "ShortCircuitTest.loss") * sbase / (power * power);
                    if (Math.abs(testR - rp[i] - rp[j]) > 1e-4 * Math.max(1, testR))
                        throw g.error(test, "Short-circuit loss disagrees with winding resistances");
                }
                if (testR < 0 || z <= testR) throw g.error(test, "Short-circuit impedance magnitude must exceed resistance");
                xp[k] = Math.sqrt(z * z - testR * testR);
            }
            if (xp[k] <= 0) throw g.error(r, "Nonpositive pair leakage reactance");
        }
        boolean lead = false;
        if (nph == 3) {
            for (int i = 1; i < 3; i++) {
                int relative = Math.floorMod(clock[i] - clock[0], 12);
                if (conns.get(i).equals(conns.get(0))) {
                    if (relative != 0) throw g.error(r, "Equal winding connections require equal clocks");
                } else {
                    if (relative != 1 && relative != 11) throw g.error(r, "Wye/delta clock must differ by 1 or 11");
                    boolean thisLead = conns.get(0).equals("delta") ? relative == 11 : relative == 1;
                    if (i == 2 && !conns.get(1).equals(conns.get(0)) && lead != thisLead) throw g.error(r, "Inconsistent winding clocks");
                    lead = thisLead;
                }
            }
        }
        double imag = 0, loss = 0;
        if (prefix.equals("PowerTransformerEnd.")) {
            for (int i = 0; i < 3; i++) for (Resource core : g.linked("TransformerCoreAdmittance.TransformerEnd", ends.get(i))) {
                imag += Math.abs(g.number(core, "TransformerCoreAdmittance.b", 0)) * zbase[i] * 100;
                loss += g.number(core, "TransformerCoreAdmittance.g", 0) * zbase[i] * 100;
            }
        } else for (int i = 0; i < 3; i++) for (Resource test : g.linked("NoLoadTest.EnergisedEnd", ratings.get(i))) {
            double active = g.number(test, "NoLoadTest.loss", 0) * 1000 / sbase * 100;
            double current = g.number(test, "NoLoadTest.excitingCurrent", 0) * positive(test, "TransformerTest.basePower") / sbase;
            if (active < 0 || current < active) throw g.error(test, "Inconsistent no-load losses and exciting current");
            loss += active; imag += Math.sqrt(current * current - active * active);
        }
        List<Double> ownResistance = new ArrayList<>();
        for (int i = 0; i < 3; i++) ownResistance.add(rp[i]);
        out("new transformer." + eid + " phases=" + nph + " windings=3 buses=" + list(buses) + " conns=" + list(conns)
                + " kvs=" + list(kvs) + " kvas=" + list(kvas) + " %rs=" + list(ownResistance) + " xhl=" + xp[0] + " xht=" + xp[1]
                + " xlt=" + xp[2] + " taps=" + list(ratios) + " leadlag=" + (lead ? "lead" : "lag") + " %imag=" + imag + " %noloadloss=" + loss);
    }

    private int integerOrDefault(Resource r, String key, int fallback) { return g.value(r, key) == null ? fallback : integer(r, key); }

    private void transformer(Resource r, List<Resource> ends, List<Resource> ratings, String prefix, int nph) {
        String eid = equipment(r);
        List<String> buses = new ArrayList<>(), conns = new ArrayList<>();
        List<Double> kvs = new ArrayList<>(), kvas = new ArrayList<>();
        double[] zbase = new double[2], tap = {1, 1};
        double resistance = 0;
        for (int i = 0; i < 2; i++) {
            Resource e = ends.get(i), rating = ratings.get(i);
            if (g.number(e, "TransformerEnd.endNumber") != i + 1) throw g.error(e, "Invalid winding number");
            String p = tankPhases(e);
            if (p.length() != nph || (i > 0 && !p.equals(tankPhases(ends.get(0))))) throw g.error(e, "Cross-phase transformer connections are unsupported");
            String conn = g.kind(rating, prefix + "connectionKind", "");
            if (!Set.of("Y", "Yn", "D", "I").contains(conn) || (conn.equals("I") && nph != 1)) throw g.error(e, "Unsupported winding connection " + conn);
            boolean grounded = g.flag(e, "TransformerEnd.grounded", conn.equals("Yn") || conn.equals("I"));
            if ((!conn.equals("D") && !grounded) || g.number(e, "TransformerEnd.rground", 0) != 0 || g.number(e, "TransformerEnd.xground", 0) != 0)
                throw g.error(e, "Only solidly grounded wye/independent windings and delta are supported");
            buses.add(bus(g.ref(e, "TransformerEnd.Terminal")) + suffix(p));
            conns.add(conn.equals("D") ? "delta" : "wye");
            double u = positive(rating, prefix + "ratedU"), s = positive(rating, prefix + "ratedS");
            zbase[i] = u * u / s;
            kvs.add(u / 1000); kvas.add(s / 1000);
            resistance += g.number(rating, prefix + "r") / zbase[i] * 100;
            tap[i] = tapRatio(e);
        }
        if (Math.abs(kvas.get(0) - kvas.get(1)) > 1e-6) throw g.error(r, "Unequal winding ratings are unsupported");
        double x;
        if (prefix.equals("PowerTransformerEnd.")) {
            var mesh = g.linked("TransformerMeshImpedance.FromTransformerEnd", ends.get(0));
            if (mesh.isEmpty()) x = g.number(ends.get(0), prefix + "x") / zbase[0] * 100 + g.number(ends.get(1), prefix + "x", 0) / zbase[1] * 100;
            else {
                if (mesh.size() != 1 || !g.ref(mesh.get(0), "TransformerMeshImpedance.ToTransformerEnd").equals(ends.get(1))) throw g.error(r, "Invalid transformer mesh");
                x = g.number(mesh.get(0), "TransformerMeshImpedance.x") / zbase[0] * 100;
            }
        } else {
            var tests = g.linked("ShortCircuitTest.EnergisedEnd", ratings.get(0));
            if (tests.size() != 1 || !g.ref(tests.get(0), "ShortCircuitTest.GroundedEnds").equals(ratings.get(1))) throw g.error(r, "Expected one two-winding short circuit test");
            Resource test = tests.get(0);
            double testBase = positive(test, "TransformerTest.basePower");
            double ratedS = kvas.get(0) * 1000;
            // CIMHub short-circuit leakageImpedance is |Z| in ohms; test losses are kW.
            resistance = 100 * 1000 * g.number(test, "ShortCircuitTest.loss") * ratedS / (testBase * testBase);
            double magnitude = g.number(test, "ShortCircuitTest.leakageImpedance") / zbase[0] * 100;
            if (magnitude <= resistance || resistance < 0) throw g.error(test, "Short-circuit impedance magnitude must exceed resistance");
            x = Math.sqrt(magnitude * magnitude - resistance * resistance);
        }
        int clock = Math.floorMod((int)(g.number(ratings.get(1), prefix + "phaseAngleClock", 0) - g.number(ratings.get(0), prefix + "phaseAngleClock", 0)), 12);
        if (conns.get(0).equals(conns.get(1)) ? clock != 0 : clock != 1 && clock != 11) throw g.error(r, "Unsupported transformer clock " + clock);
        double imag = 0, loss = 0;
        if (prefix.equals("PowerTransformerEnd.")) {
            for (int i = 0; i < 2; i++) for (Resource core : g.linked("TransformerCoreAdmittance.TransformerEnd", ends.get(i))) {
                imag += Math.abs(g.number(core, "TransformerCoreAdmittance.b", 0)) * zbase[i] * 100;
                loss += g.number(core, "TransformerCoreAdmittance.g", 0) * zbase[i] * 100;
            }
        } else for (Resource test : g.linked("NoLoadTest.EnergisedEnd", ratings.get(0))) {
            double active = g.number(test, "NoLoadTest.loss", 0) / kvas.get(0) * 100;
            double current = g.number(test, "NoLoadTest.excitingCurrent", 0)
                    * positive(test, "TransformerTest.basePower") / (kvas.get(0) * 1000);
            if (active < 0 || current < active) throw g.error(test, "Inconsistent no-load losses and exciting current");
            loss += active;
            imag += Math.sqrt(current * current - active * active);
        }
        if (x <= 0 || resistance < 0) throw g.error(r, "Invalid transformer impedance");
        if ((imag != 0 || loss != 0) && conns.get(1).equals("delta"))
            throw g.error(r, "Magnetizing admittance on delta secondary is unsupported");
        out("new transformer." + eid + " phases=" + nph + " windings=2 buses=" + list(buses) + " conns=" + list(conns)
                + " kvs=" + list(kvs) + " kvas=" + list(kvas) + " xhl=" + x + " %loadloss=" + resistance
                + " %imag=" + imag + " %noloadloss=" + loss);
        taps.put(eid, tap); clocks.put(eid, clock);
    }

    private String connection(Resource r, String prefix) {
        String c = g.kind(r, prefix + "phaseConnection", "Y");
        if (!Set.of("Y", "D").contains(c) || (c.equals("Y") && !g.flag(r, prefix + "grounded", true))) throw g.error(r, "Unsupported shunt/load connection " + c);
        return c;
    }

    private void load(Resource r) {
        String eid = equipment(r), bus = bus(terminals(r, 1).get(0));
        String conn = connection(r, "EnergyConsumer.");
        String model = " model=1";
        if (g.value(r, "EnergyConsumer.LoadResponse") != null) {
            Resource response = g.ref(r, "EnergyConsumer.LoadResponse");
            if (g.flag(response, "LoadResponseCharacteristic.exponentModel", false)) throw g.error(response, "Exponential load response is unsupported; supply ZIP fractions");
            List<Double> weights = new ArrayList<>();
            for (String pq : List.of("p", "q")) {
                double sum = 0;
                for (String kind : List.of("Impedance", "Current", "Power")) {
                    double w = g.number(response, "LoadResponseCharacteristic." + pq + "Constant" + kind);
                    if (w < 0) throw g.error(response, "Negative ZIP fraction");
                    weights.add(w); sum += w;
                }
                if (Math.abs(sum - 1) > 1e-6 && Math.abs(sum - 100) > 1e-6) throw g.error(response, "ZIP fractions must sum to 1 or 100");
                for (int j = weights.size() - 3; j < weights.size(); j++) weights.set(j, weights.get(j) / sum);
            }
            weights.add(0.0);
            model = " model=8 zipv=" + list(weights);
            if (weights.get(0) == 1 && weights.get(3) == 1) model = " model=2";
            else if (weights.get(1) == 1 && weights.get(4) == 1) model = " model=5";
            else if (weights.get(2) == 1 && weights.get(5) == 1) model = " model=1";
        }
        var children = g.linked("EnergyConsumerPhase.EnergyConsumer", r);
        if (children.isEmpty()) emitLoad(eid, bus, equipmentPhases(r), conn, baseVoltage(r), g.number(r, "EnergyConsumer.p"), g.number(r, "EnergyConsumer.q"), model);
        else for (Resource child : children) emitLoad(eid + "_" + id(child), bus, phase(child, "EnergyConsumerPhase.phase", ""), conn, baseVoltage(r), g.number(child, "EnergyConsumerPhase.p"), g.number(child, "EnergyConsumerPhase.q"), model);
    }

    private void emitLoad(String eid, String bus, String p, String conn, double volts, double watts, double vars, String model) {
        if (p.matches("[12]+")) {
            if (conn.equals("Y") && p.length() == 2) {
                for (char leg : p.toCharArray()) emitLoad(eid + "_s" + leg, bus, String.valueOf(leg), conn, volts, watts / 2, vars / 2, model);
                return;
            }
            if (conn.equals("D") && p.length() != 2) throw new IllegalArgumentException("Split-phase line-line load must specify s12");
            // CIMHub uses both 240-V service ratings and 208-V three-phase-equivalent
            // base ratings for 120-V split-phase legs (see upstream DistLoad.GetDSS).
            double divisor = conn.equals("D") ? 1 : (volts > 230 && volts < 260 ? 2 : Math.sqrt(3));
            out("new load." + eid + " bus1=" + bus + suffix(p) + " phases=1 conn=" + (conn.equals("D") ? "delta" : "wye")
                    + " kv=" + volts / 1000 / divisor + " kw=" + watts / 1000 + " kvar=" + vars / 1000
                    + model + " vminpu=0 vmaxpu=1000000");
            return;
        }
        // Represent balanced totals as three explicit winding loads, using the same
        // phase-resolved path as unbalanced EnergyConsumerPhase records.
        if (p.equals("ABC")) {
            for (String single : List.of("A", "B", "C"))
                emitLoad(eid + "_" + single.toLowerCase(Locale.ROOT), bus, single, conn, volts, watts / 3, vars / 3, model);
            return;
        }
        boolean delta = conn.equals("D");
        // CIM single-phase delta A/B/C identifies the AB/BC/CA winding.
        if (delta && p.length() == 1) p += switch (p) { case "A" -> "B"; case "B" -> "C"; default -> "A"; };
        int n = delta && p.length() == 2 ? 1 : p.length();
        if (n != 1 && n != 3) throw new IllegalArgumentException("Two-phase aggregate load requires separate phase records");
        double kv = volts / 1000 / (!delta && n == 1 ? Math.sqrt(3) : 1);
        out("new load." + eid + " bus1=" + bus + suffix(p) + " phases=" + n + " conn=" + (delta ? "delta" : "wye")
                + " kv=" + kv + " kw=" + watts / 1000 + " kvar=" + vars / 1000 + model + " vminpu=0 vmaxpu=1000000");
    }

    private void shunt(Resource r) {
        String eid = equipment(r), bus = bus(terminals(r, 1).get(0));
        String conn = connection(r, "ShuntCompensator.");
        var children = g.linked("ShuntCompensatorPhase.ShuntCompensator", r);
        if (children.isEmpty()) emitShunt(r, eid, bus, phase(r, "ConductingEquipment.phases", "ABC"), conn, positive(r, "ShuntCompensator.nomU"), "LinearShuntCompensator.", "ShuntCompensator.");
        else for (Resource c : children) emitShunt(c, eid + "_" + id(c), bus, phase(c, "ShuntCompensatorPhase.phase", ""), conn, positive(r, "ShuntCompensator.nomU"), "LinearShuntCompensatorPhase.", "ShuntCompensatorPhase.");
    }
    private void emitShunt(Resource r, String eid, String bus, String p, String conn, double volts, String prefix, String sectionsPrefix) {
        double sections = g.number(r, sectionsPrefix + "sections", g.number(r, sectionsPrefix + "normalSections", 1));
        if (sections < 0 || sections > g.number(r, sectionsPrefix + "maximumSections", sections)) throw g.error(r, "Invalid shunt sections");
        double scale = volts * volts * sections;
        // For a balanced three-phase wye, nomU is line-line. Per-phase nomU is winding voltage.
        if (p.length() == 3 && conn.equals("D")) scale *= 3;
        double effectiveBase = p.length() == 1 && conn.equals("Y") ? volts * (p.matches("[12]") ? 2 : Math.sqrt(3)) : volts;
        emitLoad(eid, bus, p, conn, effectiveBase, g.number(r, prefix + "gPerSection", 0) * scale,
                -g.number(r, prefix + "bPerSection") * scale, " model=2");
    }

    private void injection(Resource r) {
        String eid = equipment(r), bus = bus(terminals(r, 1).get(0));
        String conn = connection(r, "PowerElectronicsConnection.");
        var phases = g.linked("PowerElectronicsConnectionPhase.PowerElectronicsConnection", r);
        // CIMHub exports positive generation at a power electronics connection; negate for a load equivalent.
        if (phases.isEmpty()) emitLoad(eid, bus, phase(r, "ConductingEquipment.phases", "ABC"), conn, positive(r, "PowerElectronicsConnection.ratedU"), -g.number(r, "PowerElectronicsConnection.p"), -g.number(r, "PowerElectronicsConnection.q"), " model=1");
        else for (Resource p : phases) emitLoad(eid + "_" + id(p), bus, phase(p, "PowerElectronicsConnectionPhase.phase", ""), conn, positive(r, "PowerElectronicsConnection.ratedU"), -g.number(p, "PowerElectronicsConnectionPhase.p"), -g.number(p, "PowerElectronicsConnectionPhase.q"), " model=1");
        warnings.add("Fixed P/Q equivalent imported for power electronics connection " + g.name(r) + "; inverter controls/storage energy state are not represented.");
    }

    private boolean isSwitch(Resource r) {
        return List.of("Switch", "LoadBreakSwitch", "Breaker", "Fuse", "Recloser", "Disconnector", "Jumper", "Sectionaliser").stream().anyMatch(t -> g.is(r, t));
    }
    private void switchElement(Resource r) {
        var ts = terminals(r, 2);
        String eid = equipment(r);
        String p = phases(r, "SwitchPhase", "SwitchPhase.Switch", "SwitchPhase.phaseSide1");
        for (Resource sp : g.linked("SwitchPhase.Switch", r)) {
            if (!g.kind(sp, "SwitchPhase.phaseSide1", "").equals(g.kind(sp, "SwitchPhase.phaseSide2", ""))) throw g.error(r, "Cross-phase switches are unsupported");
        }
        boolean open = g.flag(r, "Switch.open", g.flag(r, "Switch.normalOpen", false));
        out("new line." + eid + " phases=" + p.length() + " bus1=" + bus(ts.get(0)) + suffix(p) + " bus2=" + bus(ts.get(1)) + suffix(p)
                + " r1=0.0000001 x1=0 r0=0.0000001 x0=0 c1=0 c0=0 length=1 enabled=" + !open);
        warnings.add("Switch " + g.name(r) + " uses CIMHub's 1e-7 ohm closed-contact equivalent.");
    }
}
