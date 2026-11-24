package org.interpss.fadapter.psse.monitor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;

/** Evaluates the selected monitors against base and retained contingency solutions. */
public final class AcContingencyMonitorReport {
    public record Options(double ratingPercent, double minFlowChangeMw, double minVoltageChangePu,
            boolean checkVoltage, boolean includeInterfaces, int capacityLimit, double capacityCutoff) {
        public Options {
            if (!Double.isFinite(ratingPercent) || ratingPercent <= 0.0
                    || !Double.isFinite(minFlowChangeMw) || minFlowChangeMw < 0.0
                    || !Double.isFinite(minVoltageChangePu) || minVoltageChangePu < 0.0
                    || capacityLimit < 0 || !Double.isFinite(capacityCutoff)) {
                throw new IllegalArgumentException("Invalid AC contingency report options");
            }
        }

        public static Options defaults() {
            return new Options(100.0, 1.0, 0.02, true, true, 60_000, 99_999.0);
        }
    }

    public record Loading(String id, String limitingTerminal, String unit, double baseMw, double contingencyMw,
            double loading, double rating, double percentOfRating) { }
    public record Voltage(String busId, double basePu, double contingencyPu, double lowerPu, double upperPu) { }
    public record Capacity(String id, String unit, String limitingCase, double rating, double loading,
            double available) { }
    public record CaseReport(String contingency, List<Loading> overloads, List<Voltage> voltageViolations,
            List<Loading> interfaces, List<Loading> interfaceOverloads, List<String> incompleteInterfaces,
            Map<String, List<String>> unresolvedInterfaceMembers) { }

    private record BranchFlow(double fromMw, double toMw, double fromMva, double toMva) {
        static BranchFlow at(AclfBranch branch) {
            if (!branch.isActive()) return new BranchFlow(0, 0, 0, 0);
            var from = branch.powerFrom2To(UnitType.mVA);
            var to = branch.powerTo2From(UnitType.mVA);
            return new BranchFlow(from.getReal(), to.getReal(), from.abs(), to.abs());
        }

        double mw(boolean fromTerminal) {
            return fromTerminal ? fromMw : toMw;
        }
    }

    private final Options options;
    private final MonitoredElements monitors;
    private final Map<String, BranchFlow> baseBranchFlows = new LinkedHashMap<>();
    private final Map<String, Double> baseVoltages = new LinkedHashMap<>();
    private final Map<String, Double> baseInterfaceFlows = new LinkedHashMap<>();
    private final Map<String, Capacity> branchCapacity = new LinkedHashMap<>();
    private final Map<String, Capacity> interfaceCapacity = new LinkedHashMap<>();

    public AcContingencyMonitorReport(AclfNetwork base, MonitoredElements monitors, Options options) {
        this.monitors = monitors;
        this.options = options;
        Set<String> branchIds = new LinkedHashSet<>(monitors.getThermalBranchIds());
        branchIds.addAll(monitors.getTieBranchIds());
        for (String id : branchIds) {
            var branch = base.getBranch(id);
            if (branch == null) continue;
            var flow = BranchFlow.at(branch);
            baseBranchFlows.put(id, flow);
            if (branch.isActive()) keepWorst(branchCapacity, "BASE", id, "MVA",
                    branch.getRatingMva2(), Math.max(flow.fromMva(), flow.toMva()));
        }
        if (options.checkVoltage()) {
            for (String id : monitors.getVoltageBusIds()) {
                var bus = base.getBus(id);
                if (bus != null && bus.isActive()) baseVoltages.put(id, bus.getVoltageMag());
            }
        }
        if (options.includeInterfaces()) {
            monitors.getInterfaceDefinitions().forEach((id, definition) -> {
                double flow = interfaceFlow(base, definition);
                if (Double.isFinite(flow)) {
                    baseInterfaceFlows.put(id, flow);
                    keepWorst(interfaceCapacity, "BASE", id, "MW", definition.ratingMw(), Math.abs(flow));
                }
            });
        }
    }

    public CaseReport inspect(String label, AclfNetwork network) {
        var overloads = new ArrayList<Loading>();
        var voltageViolations = new ArrayList<Voltage>();
        var interfaces = new ArrayList<Loading>();
        var interfaceOverloads = new ArrayList<Loading>();
        var incomplete = new ArrayList<String>();
        var unresolved = new LinkedHashMap<String, List<String>>();
        baseBranchFlows.forEach((id, base) -> {
            var branch = network.getBranch(id);
            if (branch == null || !branch.isActive() || !(branch.getRatingMva2() > 0.0)) return;
            var flow = BranchFlow.at(branch);
            boolean from = flow.fromMva() >= flow.toMva();
            double loading = from ? flow.fromMva() : flow.toMva();
            double rating = branch.getRatingMva2();
            keepWorst(branchCapacity, label, id, "MVA", rating, loading);
            Loading row = loading(id, from ? "FROM" : "TO", "MVA", base.mw(from), flow.mw(from), loading, rating);
            if (isOverload(row)) overloads.add(row);
        });
        baseVoltages.forEach((id, base) -> {
            var bus = network.getBus(id);
            if (bus == null || !bus.isActive()) return;
            double voltage = bus.getVoltageMag();
            if (Math.abs(voltage - base) < options.minVoltageChangePu()) return;
            for (var range : monitors.getVoltageRanges(id)) {
                if (voltage < range.minimumPu() || voltage > range.maximumPu()) {
                    voltageViolations.add(new Voltage(id, base, voltage, range.minimumPu(), range.maximumPu()));
                }
            }
        });
        if (options.includeInterfaces()) {
            monitors.getInterfaceDefinitions().forEach((id, definition) -> {
                Double base = baseInterfaceFlows.get(id);
                double flow = interfaceFlow(network, definition);
                if (base == null || !Double.isFinite(flow)) {
                    incomplete.add(id);
                    if (!definition.unresolvedMembers().isEmpty()) unresolved.put(id, definition.unresolvedMembers());
                    return;
                }
                double loading = Math.abs(flow);
                double rating = definition.ratingMw();
                keepWorst(interfaceCapacity, label, id, "MW", rating, loading);
                var row = loading(id, "INTERFACE", "MW", base, flow, loading, rating);
                interfaces.add(row);
                if (isOverload(row)) interfaceOverloads.add(row);
            });
        }
        return new CaseReport(label, List.copyOf(overloads), List.copyOf(voltageViolations),
                List.copyOf(interfaces), List.copyOf(interfaceOverloads), List.copyOf(incomplete),
                java.util.Collections.unmodifiableMap(unresolved));
    }

    private static double interfaceFlow(AclfNetwork network, MonitoredElements.InterfaceDefinition definition) {
        if (!definition.complete()) return Double.NaN;
        double mw = 0.0;
        for (var member : definition.members()) {
            var branch = network.getBranch(member.branchId());
            if (branch == null) return Double.NaN;
            if (branch.isActive()) {
                mw += member.fromTerminal() ? branch.powerFrom2To(UnitType.mVA).getReal()
                        : branch.powerTo2From(UnitType.mVA).getReal();
            }
        }
        return mw;
    }

    private static Loading loading(String id, String terminal, String unit, double baseMw, double contingencyMw,
            double loading, double rating) {
        return new Loading(id, terminal, unit, baseMw, contingencyMw, loading, rating,
                rating > 0.0 ? 100.0 * loading / rating : 0.0);
    }

    private boolean isOverload(Loading row) {
        return row.percentOfRating() > options.ratingPercent()
                && Math.abs(row.contingencyMw() - row.baseMw()) >= options.minFlowChangeMw();
    }

    private static void keepWorst(Map<String, Capacity> table, String label, String id, String unit,
            double rating, double loading) {
        if (Double.isFinite(loading) && Double.isFinite(rating) && rating > 0.0) {
            double available = rating - loading;
            var previous = table.get(id);
            if (previous == null || available < previous.available()) {
                table.put(id, new Capacity(id, unit, label, rating, loading, available));
            }
        }
    }

    public List<Capacity> availableCapacity() {
        return java.util.stream.Stream.concat(branchCapacity.values().stream(), interfaceCapacity.values().stream())
                .filter(row -> row.available() <= options.capacityCutoff())
                .sorted(Comparator.comparingDouble(Capacity::available))
                .limit(options.capacityLimit()).toList();
    }
}
