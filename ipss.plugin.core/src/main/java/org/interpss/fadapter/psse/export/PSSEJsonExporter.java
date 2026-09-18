package org.interpss.fadapter.psse.export;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.math3.complex.Complex;
import org.interpss.fadapter.psse.bean.PSSESchema;
import org.interpss.fadapter.psse.PsseLoadflowSolutionSettings;
import org.interpss.fadapter.psse.export.psse.PSSEJSonAclineUpdater;
import org.interpss.fadapter.psse.export.psse.PSSEJSonBusUpdater;
import org.interpss.fadapter.psse.export.psse.PSSEJSonDc2TLCCUpdater;
import org.interpss.fadapter.psse.export.psse.PSSEJSonDc2TVSCUpdater;
import org.interpss.fadapter.psse.export.psse.PSSEJSonFactsDeviceUpdater;
import org.interpss.fadapter.psse.export.psse.PSSEJSonFixedShuntUpdater;
import org.interpss.fadapter.psse.export.psse.PSSEJSonGenUpdater;
import org.interpss.fadapter.psse.export.psse.PSSEJSonLoadUpdater;
import org.interpss.fadapter.psse.export.psse.PSSEJSonSwitchedShuntUpdater;
import org.interpss.fadapter.psse.export.psse.PSSEJSonSwitchingDeviceUpdater;
import org.interpss.fadapter.psse.export.psse.PSSEJSonXformerUpdater;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.datatype.XfrZCorrection;
import org.interpss.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.interpss.core.aclf.Aclf3WBranch;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.aclf.ShuntCompensator;
import com.interpss.core.aclf.XfrZTableEntry;
import com.interpss.core.aclf.adj.AclfAdjustControlMode;
import com.interpss.core.aclf.adj.AclfAdjustControlType;
import com.interpss.core.aclf.adj.RemoteQBus;
import com.interpss.core.aclf.adj.SwitchedShunt;
import com.interpss.core.aclf.netAdj.AreaInterchangeControl;
import com.interpss.core.aclf.hvdc.ConverterType;
import com.interpss.core.aclf.hvdc.HvdcControlMode;
import com.interpss.core.aclf.hvdc.HvdcControlSide;
import com.interpss.core.aclf.hvdc.HvdcLine2TLCC;
import com.interpss.core.aclf.hvdc.HvdcLine2TVSC;
import com.interpss.core.aclf.hvdc.ThyConverter;
import com.interpss.core.aclf.hvdc.VSCAcControlMode;
import com.interpss.core.aclf.hvdc.VSCConverter;
import com.interpss.core.aclf.facts.StaticVarCompensator;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.algo.LoadflowAlgorithmInitializer;

/**
 * Standalone PSS/E RAWX-style JSON exporter for the direct RAWX parser.
 *
 * <p>The default constructors export equipment/input state. Passing
 * {@code exportSolvedState=true} exports the accepted operating point and its
 * reproducible controller active set. That mode reconciles only generation
 * allowed to move during load flow, freezes limit-bound or unsettled voltage
 * controls when needed, and includes optional converter diagnostics.</p>
 *
 * <p>This class is also the canonical data source for
 * {@link PSSERawExporter}. Keeping solved-state decisions here ensures RAW and
 * RAWX use identical bus types, generator allocation, SVC/shunt treatment, and
 * fixed-LCC fallback behavior.</p>
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class PSSEJsonExporter {
	private static final Logger log = LoggerFactory.getLogger(PSSEJsonExporter.class);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

	private final BaseAclfNetwork aclfNet;
	private final double baseMva;
	private final PSSESchema psseJson;
	private final boolean exportSolvedState;

	public PSSEJsonExporter(BaseAclfNetwork<?, ?> aclfNet) {
		this(aclfNet, false);
	}

	public PSSEJsonExporter(BaseAclfNetwork<?, ?> aclfNet, boolean exportSolvedState) {
		this.aclfNet = aclfNet;
		this.baseMva = aclfNet.getBaseKva() > 0.0 ? aclfNet.getBaseKva() / 1000.0 : 100.0;
		this.psseJson = null;
		this.exportSolvedState = exportSolvedState;
	}

	public PSSEJsonExporter(BaseAclfNetwork<?, ?> aclfNet, PSSESchema psseJson) {
		this.aclfNet = aclfNet;
		this.baseMva = aclfNet.getBaseKva() > 0.0 ? aclfNet.getBaseKva() / 1000.0 : 100.0;
		this.psseJson = psseJson;
		this.exportSolvedState = false;
	}

	public void filterAndUpdate(Set<String> busIdSet) {
		if (psseJson == null) {
			throw new IllegalStateException("filterAndUpdate requires a PSSESchema template");
		}
		PSSEJSonBusUpdater busUpdater = new PSSEJSonBusUpdater(psseJson.getNetwork().getBus(), aclfNet);
		busUpdater.filter(busIdSet);
		busUpdater.update();
		log.debug("Bus Data: " + psseJson.getNetwork().getBus().getData());

		PSSEJSonGenUpdater genUpdater = new PSSEJSonGenUpdater(psseJson.getNetwork().getGenerator(), aclfNet);
		genUpdater.filter(busIdSet);
		genUpdater.update();
		log.debug("Gen Data: " + psseJson.getNetwork().getGenerator().getData());

		PSSEJSonLoadUpdater loadUpdater = new PSSEJSonLoadUpdater(psseJson.getNetwork().getLoad(), aclfNet);
		loadUpdater.filter(busIdSet);
		loadUpdater.update();
		log.debug("Load Data: " + psseJson.getNetwork().getLoad().getData());

		PSSEJSonSwitchedShuntUpdater swshuntUpdater = new PSSEJSonSwitchedShuntUpdater(psseJson.getNetwork().getSwshunt(), aclfNet);
		swshuntUpdater.filter(busIdSet);
		swshuntUpdater.update();
		log.debug("Switched Shunt Data: " + psseJson.getNetwork().getSwshunt().getData());

		PSSEJSonFixedShuntUpdater fShuntUpdater = new PSSEJSonFixedShuntUpdater(psseJson.getNetwork().getFixshunt(), aclfNet);
		fShuntUpdater.filter(busIdSet);
		fShuntUpdater.update();
		log.debug("Fixed shunt Data: " + psseJson.getNetwork().getFixshunt().getData());

		PSSEJSonFactsDeviceUpdater factsUpdater = new PSSEJSonFactsDeviceUpdater(psseJson.getNetwork().getFacts(), aclfNet);
		factsUpdater.filter(busIdSet);
		factsUpdater.update();
		log.debug("Facts Data: " + psseJson.getNetwork().getFacts().getData());

		PSSEJSonAclineUpdater aclineUpdater = new PSSEJSonAclineUpdater(psseJson.getNetwork().getAcline(), aclfNet);
		aclineUpdater.filter(busIdSet);
		aclineUpdater.update();
		log.debug("Acline Data: " + psseJson.getNetwork().getAcline().getData());

		PSSEJSonXformerUpdater xfrUpdater = new PSSEJSonXformerUpdater(psseJson.getNetwork().getTransformer(), aclfNet);
		xfrUpdater.filter(busIdSet);
		xfrUpdater.update();
		log.debug("Xfr Data: " + psseJson.getNetwork().getTransformer().getData());

		PSSEJSonSwitchingDeviceUpdater switchingUpdater = new PSSEJSonSwitchingDeviceUpdater(psseJson.getNetwork().getSysswd(), aclfNet);
		switchingUpdater.filter(busIdSet);
		switchingUpdater.update();
		log.debug("Switching Device Data: " + psseJson.getNetwork().getSysswd().getData());

		PSSEJSonDc2TLCCUpdater dc2tLccUpdater = new PSSEJSonDc2TLCCUpdater(psseJson.getNetwork().getTwotermdc(), aclfNet);
		dc2tLccUpdater.filter(busIdSet);
		dc2tLccUpdater.update();
		log.debug("DC 2T LCC Data: " + psseJson.getNetwork().getTwotermdc().getData());

		PSSEJSonDc2TVSCUpdater dc2tVscUpdater = new PSSEJSonDc2TVSCUpdater(psseJson.getNetwork().getVscdc(), aclfNet);
		dc2tVscUpdater.filter(busIdSet);
		dc2tVscUpdater.update();
		log.debug("DC 2T VSC Data: " + psseJson.getNetwork().getVscdc().getData());
	}

	public JsonObject export() {
		JsonObject root = new JsonObject();
		JsonObject general = new JsonObject();
		general.addProperty("version", "35.0");
		addSolutionSettings(general);
		root.add("general", general);

		JsonObject network = new JsonObject();
		root.add("network", network);
		network.add("caseid", section(List.of("ic", "sbase", "rev"),
				List.of(row(0, baseMva, 35))));
		network.add("bus", busSection());
		network.add("load", loadSection());
		network.add("fixshunt", fixedShuntSection());
		network.add("swshunt", switchedShuntSection());
		network.add("generator", generatorSection());
		network.add("acline", acLineSection());
		network.add("transformer", transformerSection());
		network.add("sysswd", systemSwitchingDeviceSection());
		network.add("twotermdc", twoTerminalDcSection());
		network.add("vscdc", vscDcSection());
		network.add("impcor", impedanceCorrectionSection());
		network.add("area", areaSection());
		network.add("zone", zoneSection());
		network.add("owner", ownerSection());
		network.add("facts", factsSection());
		return root;
	}

	private void addSolutionSettings(JsonObject general) {
		Object initializer = aclfNet.getExtraInfo().get(
				LoadflowAlgorithmInitializer.NETWORK_EXTRA_INFO_KEY);
		if (!(initializer instanceof PsseLoadflowSolutionSettings settings)
				|| settings.rawLines().isEmpty()) {
			return;
		}
		JsonObject extension = new JsonObject();
		extension.addProperty("source_version", settings.sourceVersion());
		JsonArray lines = new JsonArray();
		settings.rawLines().forEach(lines::add);
		extension.add("raw_lines", lines);
		general.add("ipss_loadflow_solution_settings", extension);
	}

	public String exportToString() {
		return GSON.toJson(export());
	}

	public void export(Path path) throws IOException {
		Files.writeString(path, exportToString());
	}

	public void export(String filename) throws IOException {
		if (psseJson != null) {
			FileUtil.writeText2File(filename, psseJson.toString());
		} else {
			export(Path.of(filename));
		}
	}

	private JsonObject busSection() {
		JsonObject section = section(List.of("ibus", "name", "baskv", "ide", "area", "zone", "owner",
				"vm", "va", "nvhi", "nvlo", "evhi", "evlo"));
		Set<Object> threeWStarBuses = threeWStarBuses();
		for (Object obj : aclfNet.getBusList()) {
			AclfBus bus = (AclfBus) obj;
			if (threeWStarBuses.contains(bus)) {
				continue;
			}
			section.getAsJsonArray("data").add(row(
					busNumber(bus),
					nullToEmpty(bus.getName()),
					bus.getBaseVoltage() / 1000.0,
					busIde(bus),
					intId(bus.getAreaId()),
					intId(bus.getZoneId()),
					bus.getOwner() != null ? (int) bus.getOwner().getNumber() : 0,
					bus.getVoltageMag(),
					Math.toDegrees(bus.getVoltageAng()),
					1.1, 0.9, 1.1, 0.9));
		}
		return section;
	}

	private JsonObject loadSection() {
		JsonObject section = section(List.of("ibus", "loadid", "stat", "area", "zone", "pl", "ql",
				"ip", "iq", "yp", "yq", "owner", "scale", "intrpt", "dgenp", "dgenq", "dgenm",
				"loadtype", "name"));
		Map<AclfBus, Set<String>> usedLoadIds = new HashMap<>();
		for (Object obj : aclfNet.getBusList()) {
			AclfBus bus = (AclfBus) obj;
			Set<String> busLoadIds = usedLoadIds.computeIfAbsent(bus,
					ignored -> new HashSet<>());
			for (Object loadObj : bus.getContributeLoadList()) {
				AclfLoad load = (AclfLoad) loadObj;
				busLoadIds.add(load.getId());
				Complex cp = nonNull(load.getLoadCP());
				Complex ci = nonNull(load.getLoadCI());
				Complex cz = nonNull(load.getLoadCZ());
				Complex dgen = nonNull(load.getDistGenPower());
				section.getAsJsonArray("data").add(row(
						busNumber(bus),
						load.getId(),
						load.isActive() ? 1 : 0,
						intId(bus.getAreaId()),
						intId(bus.getZoneId()),
						cp.getReal() * baseMva,
						cp.getImaginary() * baseMva,
						ci.getReal() * baseMva,
						ci.getImaginary() * baseMva,
						cz.getReal() * baseMva,
						-cz.getImaginary() * baseMva,
						bus.getOwner() != null ? (int) bus.getOwner().getNumber() : 0,
						1, 0,
						dgen.getReal() * baseMva,
						dgen.getImaginary() * baseMva,
						load.isDistGenStatus() ? 1 : 0,
						"", nullToEmpty(load.getName())));
			}
		}
		if (exportSolvedState && aclfNet.getSpecialBranchList() != null) {
			for (Object obj : aclfNet.getSpecialBranchList()) {
				if (obj instanceof HvdcLine2TLCC line && line.isActive()
						&& isFixedSolvedLcc(line)) {
					addFixedLccEquivalentLoad(section, usedLoadIds,
							line.getRectifier(), "HR",
							"Fixed solved LCC rectifier " + nullToEmpty(line.getName()));
					addFixedLccEquivalentLoad(section, usedLoadIds,
							line.getInverter(), "HI",
							"Fixed solved LCC inverter " + nullToEmpty(line.getName()));
				}
			}
		}
		return section;
	}

	/**
	 * Writes one terminal of an LCC that core fixed after measured outer-loop
	 * instability. Standard RAW cannot encode the complete frozen converter state;
	 * an active constant-P/Q load is therefore the only representation that
	 * preserves the accepted AC injection without restarting that control loop.
	 */
	private void addFixedLccEquivalentLoad(JsonObject section,
			Map<AclfBus, Set<String>> usedLoadIds, ThyConverter converter,
			String preferredId, String name) {
		AclfBus bus = (AclfBus) converter.getBus();
		Complex pq = converter.powerIntoConverter();
		section.getAsJsonArray("data").add(row(
				busNumber(bus), claimUniqueLoadId(usedLoadIds, bus, preferredId), 1,
				intId(bus.getAreaId()), intId(bus.getZoneId()),
				pq.getReal() * baseMva, pq.getImaginary() * baseMva,
				0.0, 0.0, 0.0, 0.0,
				bus.getOwner() != null ? (int) bus.getOwner().getNumber() : 0,
				1, 0, 0.0, 0.0, 0, "", name));
	}

	private static String claimUniqueLoadId(Map<AclfBus, Set<String>> usedLoadIds,
			AclfBus bus, String preferredId) {
		Set<String> used = usedLoadIds.computeIfAbsent(bus, ignored -> new HashSet<>());
		if (used.add(preferredId)) {
			return preferredId;
		}
		for (int index = 0; index < 100; index++) {
			String candidate = "H" + index;
			if (used.add(candidate)) {
				return candidate;
			}
		}
		throw new IllegalStateException("No free load ID for fixed LCC equivalent at "
				+ bus.getId());
	}

	private JsonObject fixedShuntSection() {
		JsonObject section = section(List.of("ibus", "shntid", "stat", "gl", "bl", "name"));
		for (Object obj : aclfNet.getBusList()) {
			AclfBus bus = (AclfBus) obj;
			Complex shuntY = nonNull(bus.getShuntY());
			if (shuntY.getReal() != 0.0 || shuntY.getImaginary() != 0.0) {
				section.getAsJsonArray("data").add(row(busNumber(bus), "EQ", 1,
						shuntY.getReal() * baseMva, shuntY.getImaginary() * baseMva, "Equivalent shunt"));
			}
			for (Object compObj : bus.getCompensatorList()) {
				ShuntCompensator comp = (ShuntCompensator) compObj;
				section.getAsJsonArray("data").add(row(busNumber(bus), comp.getId(),
						comp.isActive() ? 1 : 0, 0.0, comp.getB() * baseMva, nullToEmpty(comp.getName())));
			}
			int fixedSvcIndex = 1;
			for (Object svcObj : bus.getStaticVarCompensatorList()) {
				StaticVarCompensator svc = (StaticVarCompensator) svcObj;
				if (isFixedSolvedSvc(svc)) {
					section.getAsJsonArray("data").add(row(busNumber(bus),
							"S" + fixedSvcIndex++, 1, 0.0,
							svc.getBActual() * baseMva,
							"Fixed solved SVC " + nullToEmpty(svc.getName())));
				}
			}
		}
		return section;
	}

	private boolean isFixedSolvedSvc(StaticVarCompensator svc) {
		if (!exportSolvedState || !svc.isStatus()) {
			return false;
		}
		if (!svc.isControlStatus()) {
			return true;
		}
		LimitType bLimit = svc.getBLimit(false);
		if (bLimit == null) {
			return false;
		}
		double b = svc.getBActual();
		double limitTolerance = 1.0e-8 * Math.max(1.0,
				Math.max(Math.abs(bLimit.getMin()), Math.abs(bLimit.getMax())));
		return b <= bLimit.getMin() + limitTolerance
				|| b >= bLimit.getMax() - limitTolerance;
	}

	private JsonObject switchedShuntSection() {
		JsonObject section = section(List.of("ibus", "shntid", "modsw", "adjm", "stat", "vswhi",
				"vswlo", "swreg", "nreg", "rmpct", "rmidnt", "binit",
				"s1", "n1", "b1", "s2", "n2", "b2", "s3", "n3", "b3", "s4", "n4", "b4",
				"s5", "n5", "b5", "s6", "n6", "b6", "s7", "n7", "b7", "s8", "n8", "b8"));
		for (Object obj : aclfNet.getBusList()) {
			AclfBus bus = (AclfBus) obj;
			for (Object shuntObj : bus.getSwitchedShuntList()) {
				SwitchedShunt shunt = (SwitchedShunt) shuntObj;
				double exportedB = exportSolvedState ? shunt.getBActual() : shunt.getBInit();
				JsonArray data = row(busNumber(bus), shunt.getId(), modsw(shunt), 0,
						shunt.isActive() ? 1 : 0, limitMax(shunt.getDesiredControlRange(), 1.0),
						limitMin(shunt.getDesiredControlRange(), 1.0), busNumber(shunt.getRemoteBusBranchId(), busNumber(bus)),
						0, shunt.getRemoteControlPercentage(), "", exportedB * baseMva);
				int count = 0;
				for (Object compObj : shunt.getShuntCompensatorList()) {
					if (count++ >= 8) break;
					ShuntCompensator comp = (ShuntCompensator) compObj;
					add(data, comp.isActive() ? 1 : 0);
					add(data, comp.getSteps());
					add(data, comp.getUnitQMvar());
				}
				while (count++ < 8) {
					add(data, null);
					add(data, null);
					add(data, null);
				}
				section.getAsJsonArray("data").add(data);
			}
		}
		return section;
	}

	private JsonObject factsSection() {
		JsonObject section = section(List.of("name", "ibus", "jbus", "mode", "pdes", "qdes",
				"vset", "shmx", "trmx", "vtmn", "vtmx", "vsmx", "imx", "linx", "rmpct",
				"owner", "set1", "set2", "vsref", "fcreg", "nreg", "mname"));
		for (Object obj : aclfNet.getBusList()) {
			AclfBus bus = (AclfBus) obj;
			for (Object svcObj : bus.getStaticVarCompensatorList()) {
				StaticVarCompensator svc = (StaticVarCompensator) svcObj;
				double b = exportSolvedState ? svc.getBActual() : svc.getBInit();
				double qMvar = b * bus.getVoltageMag() * bus.getVoltageMag() * baseMva;
				/*
				 * PSS/E SHMX is the STATCON shunt capability at 1.0 pu voltage. It
				 * therefore maps to susceptance on the system base, not to the
				 * voltage-dependent Q limit at the solved operating point.
				 */
				LimitType bLimit = svc.getBLimit(UnitType.PU);
				double qMaxMvar = Math.max(Math.abs(bLimit.getMax()), Math.abs(bLimit.getMin())) * baseMva;
				section.getAsJsonArray("data").add(row(
						nullToEmpty(svc.getName()), busNumber(bus), 0,
						svc.isStatus() && !isFixedSolvedSvc(svc) ? 1 : 0,
						0.0, qMvar, svc.getVSpecified(UnitType.PU), qMaxMvar, 9999.0,
						0.9, 1.1, 1.0, 0.0, 0.05, svc.getRemoteControlPercentage(),
						bus.getOwner() != null ? (int) bus.getOwner().getNumber() : 0,
						0.0, 0.0, 0,
						busNumber(svc.getRemoteBusBranchId(), busNumber(bus)), 0, ""));
			}
		}
		return section;
	}

	private JsonObject generatorSection() {
		JsonObject section = section(List.of("ibus", "machid", "pg", "qg", "qt", "qb", "vs",
				"ireg", "nreg", "mbase", "zr", "zx", "rt", "xt", "gtap", "stat", "rmpct",
				"pt", "pb", "baslod", "o1", "f1", "o2", "f2", "o3", "f3", "o4", "f4",
				"wmod", "wpf", "droopname", "name"));
		Set<AclfBus> deviceInjectionBuses = deviceInjectionBuses();
		for (Object obj : aclfNet.getBusList()) {
			AclfBus bus = (AclfBus) obj;
			List<AclfGen> generators = new ArrayList<>();
			for (Object genObj : bus.getContributeGenList()) {
				generators.add((AclfGen) genObj);
			}
			List<Complex> exportedGeneration = solvedGeneratorAllocation(
					bus, generators, exportSolvedState);
			boolean wroteGen = false;
			for (int genIndex = 0; genIndex < generators.size(); genIndex++) {
				AclfGen gen = generators.get(genIndex);
				Complex pq = exportedGeneration.get(genIndex);
				LimitType qLimit = gen.getQGenLimit();
				LimitType pLimit = gen.getPGenLimit();
				Complex sourceZ = nonNull(gen.getSourceZ());
				Complex xfrZ = nonNull(gen.getXfrZ());
				section.getAsJsonArray("data").add(row(
						busNumber(bus), gen.getId(),
						pq.getReal() * baseMva, pq.getImaginary() * baseMva,
						limitMax(qLimit, pq.getImaginary()) * baseMva,
						limitMin(qLimit, pq.getImaginary()) * baseMva,
						gen.getDesiredVoltMag() != 0.0 ? gen.getDesiredVoltMag() : bus.getVoltageMag(),
						busNumber(gen.getRemoteVControlBusId(), busNumber(bus)), 0,
						gen.getMvaBase() != 0.0 ? gen.getMvaBase() : baseMva,
						sourceZ.getReal(), sourceZ.getImaginary(),
						xfrZ.getReal(), xfrZ.getImaginary(),
						gen.getXfrTap() != 0.0 ? gen.getXfrTap() : 1.0,
						gen.isActive() ? 1 : 0, 100.0,
						limitMax(pLimit, pq.getReal()) * baseMva,
						limitMin(pLimit, pq.getReal()) * baseMva,
						0, 1, 1.0, null, null, null, null, null, null,
						0, 1.0, null, nullToEmpty(gen.getName())));
				wroteGen = true;
			}
			if (!wroteGen && bus.getGenCode() == AclfGenCode.GEN_PQ
					&& !deviceInjectionBuses.contains(bus)) {
				section.getAsJsonArray("data").add(row(
						busNumber(bus), "1",
						bus.getGenP() * baseMva, bus.getGenQ() * baseMva,
						bus.getGenQ() * baseMva, bus.getGenQ() * baseMva,
						bus.getVoltageMag(), busNumber(bus), 0, baseMva,
						0.0, 0.0, 0.0, 0.0, 1.0,
						1, 100.0,
						bus.getGenP() * baseMva, bus.getGenP() * baseMva,
						0, 1, 1.0, null, null, null, null, null, null,
						0, 1.0, null, "Bus aggregate generation"));
			}
		}
		return section;
	}

	private Set<AclfBus> deviceInjectionBuses() {
		Set<AclfBus> buses = new HashSet<>();
		for (Object obj : aclfNet.getBusList()) {
			AclfBus bus = (AclfBus) obj;
			if (!bus.getStaticVarCompensatorList().isEmpty()) {
				buses.add(bus);
			}
		}
		if (aclfNet.getSpecialBranchList() != null) {
			for (Object obj : aclfNet.getSpecialBranchList()) {
				if (obj instanceof HvdcLine2TVSC line) {
					buses.add((AclfBus) line.getRecConverter().getBus());
					buses.add((AclfBus) line.getInvConverter().getBus());
				}
			}
		}
		return buses;
	}

	/**
	 * Reconciles machine records to solved bus generation without changing
	 * scheduled PG at ordinary PV/PQ buses. Active power can move only at the
	 * system or area swing. Reactive residual is allocated only to active machines
	 * with a usable range; fixed-Q machines retain their exact output.
	 */
	private static List<Complex> solvedGeneratorAllocation(
			AclfBus bus,
			List<AclfGen> generators,
			boolean solvedState) {
		List<Complex> allocation = generators.stream()
				.map(gen -> nonNull(gen.getGen()))
				.collect(java.util.stream.Collectors.toCollection(ArrayList::new));
		if (!solvedState) {
			return allocation;
		}
		List<Integer> active = new ArrayList<>();
		List<Integer> reactiveAdjustable = new ArrayList<>();
		Complex listedTotal = Complex.ZERO;
		double totalWeight = 0.0;
		double adjustableQWeight = 0.0;
		double fixedQ = 0.0;
		for (int i = 0; i < generators.size(); i++) {
			AclfGen gen = generators.get(i);
			if (!gen.isActive()) {
				continue;
			}
			active.add(i);
			listedTotal = listedTotal.add(allocation.get(i));
			totalWeight += generatorAllocationWeight(gen);
			LimitType qLimit = gen.getQGenLimit();
			if (qLimit != null && qLimit.getMax() > qLimit.getMin()) {
				reactiveAdjustable.add(i);
				adjustableQWeight += generatorAllocationWeight(gen);
			}
			else {
				double fixedGeneratorQ = qLimit != null
						? qLimit.getMax() : allocation.get(i).getImaginary();
				Complex original = allocation.get(i);
				allocation.set(i, new Complex(original.getReal(), fixedGeneratorQ));
				fixedQ += fixedGeneratorQ;
			}
		}
		if (active.isEmpty()) {
			return allocation;
		}

		boolean restoredZeroZTerminal = bus.isConnect2ZeroZBranch();
		Complex solvedVoltageControlledGeneration = !restoredZeroZTerminal
				&& (bus.isSwing() || bus.isGenPV())
				? bus.calNetGenResults()
				: null;
		double solvedP = restoredZeroZTerminal
				? listedTotal.getReal()
				: bus.isSwing()
				? solvedVoltageControlledGeneration.getReal()
				: reconcilesActivePower(bus) ? bus.getGenP() : listedTotal.getReal();
		double solvedQ = restoredZeroZTerminal
				? listedTotal.getImaginary()
				: solvedVoltageControlledGeneration != null
				? solvedVoltageControlledGeneration.getImaginary()
				: bus.getGenQ();
		double pDelta = solvedP - listedTotal.getReal();
		double allocatedP = 0.0;
		for (int activeIndex = 0; activeIndex < active.size(); activeIndex++) {
			int generatorIndex = active.get(activeIndex);
			double adjustedP;
			if (activeIndex == active.size() - 1) {
				adjustedP = solvedP - allocatedP;
			}
			else {
				double share = generatorAllocationWeight(generators.get(generatorIndex)) / totalWeight;
				adjustedP = allocation.get(generatorIndex).getReal() + pDelta * share;
				allocatedP += adjustedP;
			}
			Complex original = allocation.get(generatorIndex);
			allocation.set(generatorIndex,
					new Complex(adjustedP, original.getImaginary()));
		}

		if (!reactiveAdjustable.isEmpty()) {
			double targetAdjustableQ = solvedQ - fixedQ;
			double listedAdjustableQ = reactiveAdjustable.stream()
					.mapToDouble(index -> allocation.get(index).getImaginary())
					.sum();
			double qDelta = targetAdjustableQ - listedAdjustableQ;
			double allocatedQ = 0.0;
			for (int adjustableIndex = 0;
					adjustableIndex < reactiveAdjustable.size(); adjustableIndex++) {
				int generatorIndex = reactiveAdjustable.get(adjustableIndex);
				double adjustedQ;
				if (adjustableIndex == reactiveAdjustable.size() - 1) {
					adjustedQ = targetAdjustableQ - allocatedQ;
				}
				else {
					double share = generatorAllocationWeight(generators.get(generatorIndex))
							/ adjustableQWeight;
					adjustedQ = allocation.get(generatorIndex).getImaginary() + qDelta * share;
					allocatedQ += adjustedQ;
				}
				Complex original = allocation.get(generatorIndex);
				allocation.set(generatorIndex,
						new Complex(original.getReal(), adjustedQ));
			}
		}
		return allocation;
	}

	private static double generatorAllocationWeight(AclfGen gen) {
		return gen.getMvaBase() > 0.0 ? gen.getMvaBase() : 1.0;
	}

	private static boolean reconcilesActivePower(AclfBus bus) {
		if (bus.isSwing() || bus.getArea() == null) {
			return bus.isSwing();
		}
		for (Object device : bus.getArea().getRegDeviceList()) {
			if (device instanceof AreaInterchangeControl control
					&& control.getSwingBus() == bus) {
				return true;
			}
		}
		return false;
	}

	private JsonObject acLineSection() {
		JsonObject section = section(List.of("ibus", "jbus", "ckt", "rpu", "xpu", "bpu", "name",
				"rate1", "rate2", "rate3", "rate4", "rate5", "rate6", "rate7", "rate8",
				"rate9", "rate10", "rate11", "rate12", "gi", "bi", "gj", "bj", "stat",
				"met", "len", "o1", "f1"));
		for (Object obj : aclfNet.getBranchList()) {
			AclfBranch branch = (AclfBranch) obj;
			if (branch.getBranchCode() != AclfBranchCode.LINE) {
				continue;
			}
			Complex z = nonNull(branch.getZ());
			Complex h = nonNull(branch.getHShuntY());
			Complex fy = nonNull(branch.getFromShuntY());
			Complex ty = nonNull(branch.getToShuntY());
			section.getAsJsonArray("data").add(row(
					busNumber((AclfBus) branch.getFromBus()),
					busNumber((AclfBus) branch.getToBus()),
					branch.getCircuitNumber(),
					z.getReal(), z.getImaginary(), h.getImaginary() * 2.0,
					nullToEmpty(branch.getName()),
					branch.getRatingMva1(), branch.getRatingMva2(), branch.getRatingMva3(),
					branch.getRatingMva1(), branch.getRatingMva2(), branch.getRatingMva3(),
					branch.getRatingMva1(), branch.getRatingMva2(), 0, 0, 0, 0,
					fy.getReal(), fy.getImaginary(), ty.getReal(), ty.getImaginary(),
					branch.isActive() ? 1 : 0, 1, 0.0, 1, 1.0));
		}
		return section;
	}

	private JsonObject transformerSection() {
		JsonObject section = section(transformerFields());
		Set<AclfBranch> threeWLegs = threeWLegs();
		for (Object obj : aclfNet.getBranchList()) {
			AclfBranch branch = (AclfBranch) obj;
			if (threeWLegs.contains(branch)) {
				continue;
			}
			if (branch.getBranchCode() != AclfBranchCode.XFORMER
					&& branch.getBranchCode() != AclfBranchCode.PS_XFORMER) {
				continue;
			}
			Complex z = nonNull(branch.getZ());
			Complex magY = nonNull(branch.getFromShuntY());
			double angleDeg = Math.toDegrees(branch.getFromPSXfrAngle());
			section.getAsJsonArray("data").add(row(
					busNumber((AclfBus) branch.getFromBus()),
					busNumber((AclfBus) branch.getToBus()),
					0, branch.getCircuitNumber(), 1, 1, 1,
					magY.getReal(), magY.getImaginary(), 2, nullToEmpty(branch.getName()),
					branch.isActive() ? 1 : 0, 1, 1.0, 0, 1.0, 0, 1.0, 0, 1.0, "",
					0,
					z.getReal(), z.getImaginary(), baseMva,
					null, null, null, null, null, null, null, null,
					branch.getFromTurnRatio(), ((AclfBus) branch.getFromBus()).getBaseVoltage() / 1000.0,
					angleDeg, branch.getRatingMva1(), branch.getRatingMva2(), branch.getRatingMva3(),
					branch.getRatingMva1(), branch.getRatingMva2(), branch.getRatingMva3(),
					branch.getRatingMva1(), branch.getRatingMva2(),
					0, 0, 0, 1.1, 0.9, 1.1, 0.9, 33, branch.getXfrZTableNumber(),
					0, 0, 0,
					branch.getToTurnRatio(), ((AclfBus) branch.getToBus()).getBaseVoltage() / 1000.0));
		}
		if (aclfNet.getSpecialBranchList() != null) {
			for (Object obj : aclfNet.getSpecialBranchList()) {
				if (obj instanceof Aclf3WBranch branch3W) {
					section.getAsJsonArray("data").add(transformer3WRow(branch3W));
				}
			}
		}
		return section;
	}

	private Set<AclfBranch> threeWLegs() {
		Set<AclfBranch> legs = new HashSet<>();
		if (aclfNet.getSpecialBranchList() == null) {
			return legs;
		}
		for (Object obj : aclfNet.getSpecialBranchList()) {
			if (obj instanceof Aclf3WBranch branch3W) {
				legs.add(branch3W.getFromAclfBranch());
				legs.add(branch3W.getToAclfBranch());
				legs.add(branch3W.getTertAclfBranch());
			}
		}
		return legs;
	}

	private JsonArray transformer3WRow(Aclf3WBranch branch3W) {
		AclfBranch fromLeg = branch3W.getFromAclfBranch();
		AclfBranch toLeg = branch3W.getToAclfBranch();
		AclfBranch tertLeg = branch3W.getTertAclfBranch();
		Complex z1 = nonNull(fromLeg.getZ());
		Complex z2 = nonNull(toLeg.getZ());
		Complex z3 = nonNull(tertLeg.getZ());
		Complex z12 = z1.add(z2);
		Complex z23 = z2.add(z3);
		Complex z31 = z3.add(z1);
		Complex magY = nonNull(fromLeg.getFromShuntY());
		AclfBus starBus = (AclfBus) branch3W.getStarBus();
		return row(
				busNumber((AclfBus) branch3W.getFromBus()),
				busNumber((AclfBus) branch3W.getToBus()),
				busNumber((AclfBus) branch3W.getTertiaryBus()),
				branch3W.getCircuitNumber(), 1, 1, 1,
				magY.getReal(), magY.getImaginary(), 2, nullToEmpty(branch3W.getName()),
				threeWStatus(branch3W), 1, 1.0, 0, 1.0, 0, 1.0, 0, 1.0, "",
				0,
				z12.getReal(), z12.getImaginary(), baseMva,
				z23.getReal(), z23.getImaginary(), baseMva,
				z31.getReal(), z31.getImaginary(), baseMva,
				starBus.getVoltageMag(),
				Math.toDegrees(starBus.getVoltageAng()),
				fromLeg.getFromTurnRatio(), ((AclfBus) branch3W.getFromBus()).getBaseVoltage() / 1000.0,
				Math.toDegrees(fromLeg.getFromPSXfrAngle()),
				fromLeg.getRatingMva1(), fromLeg.getRatingMva2(), fromLeg.getRatingMva3(),
				fromLeg.getRatingMva1(), fromLeg.getRatingMva2(), fromLeg.getRatingMva3(),
				fromLeg.getRatingMva1(), fromLeg.getRatingMva2(),
				0, 0, 0, 1.1, 0.9, 1.1, 0.9, 33, fromLeg.getXfrZTableNumber(),
				0, 0, 0,
				toLeg.getToTurnRatio(), ((AclfBus) branch3W.getToBus()).getBaseVoltage() / 1000.0,
				Math.toDegrees(toLeg.getToPSXfrAngle()),
				tertLeg.getToTurnRatio(), ((AclfBus) branch3W.getTertiaryBus()).getBaseVoltage() / 1000.0,
				Math.toDegrees(tertLeg.getToPSXfrAngle()),
				toLeg.getXfrZTableNumber(), tertLeg.getXfrZTableNumber());
	}

	private JsonObject impedanceCorrectionSection() {
		JsonObject section = section(List.of("itable", "tap", "refact", "imfact"));
		for (Object obj : aclfNet.getXfrZTable()) {
			XfrZTableEntry entry = (XfrZTableEntry) obj;
			int tableNumber = entry.getNumber() > 0
					? (int) entry.getNumber() : intId(entry.getId());
			if (tableNumber <= 0 || entry.getPointSet() == null) {
				continue;
			}
			for (XfrZCorrection point : entry.getPointSet().getPoints()) {
				section.getAsJsonArray("data").add(row(
						tableNumber, point.x, point.y.getReal(), point.y.getImaginary()));
			}
		}
		return section;
	}

	private static int threeWStatus(Aclf3WBranch branch3W) {
		boolean winding1 = branch3W.getFromAclfBranch().isStatus();
		boolean winding2 = branch3W.getToAclfBranch().isStatus();
		boolean winding3 = branch3W.getTertAclfBranch().isStatus();
		if (winding1 && winding2 && winding3) return 1;
		if (winding1 && !winding2 && winding3) return 2;
		if (winding1 && winding2 && !winding3) return 3;
		if (!winding1 && winding2 && winding3) return 4;
		return 0;
	}

	private Set<Object> threeWStarBuses() {
		Set<Object> buses = new HashSet<>();
		if (aclfNet.getSpecialBranchList() == null) {
			return buses;
		}
		for (Object obj : aclfNet.getSpecialBranchList()) {
			if (obj instanceof Aclf3WBranch branch3W) {
				buses.add(branch3W.getStarBus());
			}
		}
		return buses;
	}

	private JsonObject systemSwitchingDeviceSection() {
		JsonObject section = section(List.of("ibus", "jbus", "ckt", "xpu", "stat", "stype", "name"));
		for (Object obj : aclfNet.getBranchList()) {
			AclfBranch branch = (AclfBranch) obj;
			if (branch.getBranchCode() != AclfBranchCode.BREAKER
					&& branch.getBranchCode() != AclfBranchCode.ZBR) {
				continue;
			}
			Complex z = nonNull(branch.getZ());
			section.getAsJsonArray("data").add(row(
					busNumber((AclfBus) branch.getFromBus()),
					busNumber((AclfBus) branch.getToBus()),
					branch.getCircuitNumber(),
					z.getImaginary(),
					branch.isActive() ? 1 : 0,
					switchingDeviceType(branch),
					nullToEmpty(branch.getName())));
		}
		return section;
	}

	private JsonObject twoTerminalDcSection() {
		JsonObject section = section(List.of("name", "mdc", "rdc", "setvl", "vschd", "vcmod",
				"rcomp", "delti", "met", "dcvmin", "cccitmx", "cccacc",
				"ipr", "nbr", "anmxr", "anmnr", "rcr", "xcr", "ebasr", "trr", "tapr",
				"tmxr", "tmnr", "stpr", "icr", "ndr", "ifr", "itr", "idr", "xcapr",
				"ipi", "nbi", "anmxi", "anmni", "rci", "xci", "ebasi", "tri", "tapi",
				"tmxi", "tmni", "stpi", "ici", "ndi", "ifi", "iti", "idi", "xcapi",
				"ipss_tap_pos_r", "ipss_alpha_r_deg",
				"ipss_p_into_converter_r_pu", "ipss_q_into_converter_r_pu",
				"ipss_tap_pos_i", "ipss_gamma_i_deg",
				"ipss_p_into_converter_i_pu", "ipss_q_into_converter_i_pu"));
		if (aclfNet.getSpecialBranchList() == null) {
			return section;
		}
		for (Object obj : aclfNet.getSpecialBranchList()) {
			if (obj instanceof HvdcLine2TLCC line) {
				ThyConverter rec = line.getRectifier();
				ThyConverter inv = line.getInverter();
				Complex recZ = nonNull(rec.getCommutingZ());
				Complex invZ = nonNull(inv.getCommutingZ());
				Complex recPower = solvedLccPower(line, rec);
				Complex invPower = solvedLccPower(line, inv);
				LimitType recAngle = rec.getFiringAngLimit(UnitType.Deg);
				LimitType invAngle = inv.getFiringAngLimit(UnitType.Deg);
				LimitType recTap = rec.getXformerTapLimit();
				LimitType invTap = inv.getXformerTapLimit();
				section.getAsJsonArray("data").add(row(
						nullToEmpty(line.getName()),
						twoTerminalDcMode(line),
						line.getRdc(UnitType.Ohm),
						twoTerminalDcSetpoint(line),
						twoTerminalDcScheduledVoltage(line),
						0.0,
						line.getCompondR(UnitType.Ohm),
						line.getPowerCurrentMargin(),
						line.getMeterEnd() == ConverterType.RECTIFIER ? "R" : "I",
						0.0, 20, 1.0,
						busNumber((AclfBus) rec.getBus()), rec.getNBridges(),
						recAngle.getMax(), recAngle.getMin(), recZ.getReal(), recZ.getImaginary(),
						rec.getAcRatedVoltage(UnitType.kV, rec.getBus().getBaseVoltage()),
						rec.getXformerRatio(), solvedLccTap(line, rec),
						recTap.getMax(), recTap.getMin(), rec.getXformerTapStepSize(),
						0, 0, 0, 0, 0, 0.0,
						busNumber((AclfBus) inv.getBus()), inv.getNBridges(),
						invAngle.getMax(), invAngle.getMin(), invZ.getReal(), invZ.getImaginary(),
						inv.getAcRatedVoltage(UnitType.kV, inv.getBus().getBaseVoltage()),
						inv.getXformerRatio(), solvedLccTap(line, inv),
						invTap.getMax(), invTap.getMin(), inv.getXformerTapStepSize(),
						0, 0, 0, 0, 0, 0.0,
						lccTapPosition(rec), solvedLccAngle(line, rec),
						realOrNull(recPower), imaginaryOrNull(recPower),
						lccTapPosition(inv), solvedLccAngle(line, inv),
						realOrNull(invPower), imaginaryOrNull(invPower)));
			}
		}
		return section;
	}

	private Complex solvedLccPower(HvdcLine2TLCC line, ThyConverter converter) {
		return exportSolvedState && line.isActive() && converter.isInitialized()
				? converter.powerIntoConverter() : null;
	}

	private Double solvedLccAngle(HvdcLine2TLCC line, ThyConverter converter) {
		return exportSolvedState && line.isActive() && converter.isInitialized()
				? converter.getFiringAng() : null;
	}

	private static Long lccTapPosition(ThyConverter converter) {
		LimitType limits = converter.getXformerTapLimit();
		double step = converter.getXformerTapStepSize();
		if (limits == null || !Double.isFinite(step) || step <= 0.0) {
			return null;
		}
		return Math.round((converter.getXformerTapSetting() - limits.getMin()) / step);
	}

	/**
	 * Reconstructs the transformer tap implied by the accepted converter equation.
	 * Firing angle and terminal voltage are solved continuous values, so retaining
	 * an older stored tap can recreate a different Q injection on import. A result
	 * outside physical limits or with invalid arithmetic falls back to the stored
	 * tap rather than exporting a nonphysical record.
	 */
	private double solvedLccTap(HvdcLine2TLCC line, ThyConverter converter) {
		double storedTap = converter.getXformerTapSetting();
		if (!exportSolvedState || !line.isActive() || isFixedSolvedLcc(line)
				|| !converter.isInitialized()) {
			return storedTap;
		}
		double firingAngle = Math.toRadians(converter.getFiringAng());
		double cosAngle = Math.cos(firingAngle);
		double acBaseVoltage = converter.getAcRatedVoltage(UnitType.kV,
				converter.getBus().getBaseVoltage()) * 1.0e3;
		double noLoadVoltageNumerator = 3.0 * Math.sqrt(2.0) / Math.PI
				* converter.getNBridges()
				* ((AclfBus) converter.getBus()).getVoltageMag()
				* acBaseVoltage * converter.getXformerRatio();
		double commutatingResistance = converter.getCommutingZ().getImaginary()
				* converter.getNBridges() * 3.0 / Math.PI;
		double requiredNoLoadVoltage = (converter.getDcVoltage()
				+ commutatingResistance * converter.getIdc()) / cosAngle;
		double reconstructedTap = noLoadVoltageNumerator / requiredNoLoadVoltage;
		LimitType limits = converter.getXformerTapLimit();
		if (!Double.isFinite(reconstructedTap) || reconstructedTap <= 0.0
				|| limits == null || reconstructedTap < limits.getMin() - 1.0e-9
				|| reconstructedTap > limits.getMax() + 1.0e-9) {
			return storedTap;
		}
		return reconstructedTap;
	}

	private JsonObject vscDcSection() {
		JsonObject section = section(List.of("name", "mdc", "rdc", "o1", "f1", "o2", "f2",
				"o3", "f3", "o4", "f4",
				"ibus1", "type1", "mode1", "dcset1", "acset1", "aloss1", "bloss1",
				"minloss1", "smax1", "imax1", "pwf1", "maxq1", "minq1", "vsreg1",
				"nreg1", "rmpct1",
				"ibus2", "type2", "mode2", "dcset2", "acset2", "aloss2", "bloss2",
				"minloss2", "smax2", "imax2", "pwf2", "maxq2", "minq2", "vsreg2",
				"nreg2", "rmpct2",
				"ipss_p_into_converter_1_pu", "ipss_q_into_converter_1_pu",
				"ipss_p_into_converter_2_pu", "ipss_q_into_converter_2_pu"));
		if (aclfNet.getSpecialBranchList() == null) {
			return section;
		}
		for (Object obj : aclfNet.getSpecialBranchList()) {
			if (obj instanceof HvdcLine2TVSC line) {
				VSCConverter rec = (VSCConverter) line.getRecConverter();
				VSCConverter inv = (VSCConverter) line.getInvConverter();
				Complex recPower = solvedVscPower(line, rec);
				Complex invPower = solvedVscPower(line, inv);
				section.getAsJsonArray("data").add(row(
						nullToEmpty(line.getName()),
						line.isActive() ? 1 : 0,
						line.getRdc(UnitType.Ohm),
						1, 1.0, null, null, null, null, null, null,
						busNumber((AclfBus) rec.getBus()),
						vscDcType(rec),
						vscAcMode(rec),
						vscDcSetpoint(rec, true),
						vscAcSetpoint(rec),
						rec.getLossA(),
						rec.getLossB(),
						rec.getMinimumLoss(),
						rec.getMvaRating(),
						rec.getAcCurrentRating(),
						vscPowerFactor(rec),
						limitMax(rec.getQMvarLimit(), 9999.0),
						limitMin(rec.getQMvarLimit(), -9999.0),
						busNumber(rec.getRemoteControlBusId(), busNumber((AclfBus) rec.getBus())),
						0,
						rec.getRemoteControlPercent(),
						busNumber((AclfBus) inv.getBus()),
						vscDcType(inv),
						vscAcMode(inv),
						vscDcSetpoint(inv, false),
						vscAcSetpoint(inv),
						inv.getLossA(),
						inv.getLossB(),
						inv.getMinimumLoss(),
						inv.getMvaRating(),
						inv.getAcCurrentRating(),
						vscPowerFactor(inv),
						limitMax(inv.getQMvarLimit(), 9999.0),
						limitMin(inv.getQMvarLimit(), -9999.0),
						busNumber(inv.getRemoteControlBusId(), busNumber((AclfBus) inv.getBus())),
						0,
						inv.getRemoteControlPercent(),
						realOrNull(recPower), imaginaryOrNull(recPower),
						realOrNull(invPower), imaginaryOrNull(invPower)));
			}
		}
		return section;
	}

	private Complex solvedVscPower(HvdcLine2TVSC line, VSCConverter converter) {
		return exportSolvedState && line.isActive() ? converter.powerIntoConverter() : null;
	}

	private JsonObject areaSection() {
		JsonObject section = section(List.of("iarea", "isw", "pdes", "ptol", "arname"));
		for (Object obj : aclfNet.getAreaMap().values()) {
			com.interpss.core.net.Area area = (com.interpss.core.net.Area) obj;
			AreaInterchangeControl control = areaInterchangeControl(area);
			section.getAsJsonArray("data").add(row(
					(int) area.getNumber(),
					control != null ? busNumber((AclfBus) control.getSwingBus()) : 0,
					control != null ? control.getPSpecOut(UnitType.mW, aclfNet.getBaseKva()) : 0.0,
					control != null ? control.getTolerance(UnitType.mW, aclfNet.getBaseKva()) : 10.0,
					nullToEmpty(area.getName())));
		}
		return section;
	}

	private static AreaInterchangeControl areaInterchangeControl(com.interpss.core.net.Area area) {
		for (Object device : area.getRegDeviceList()) {
			if (device instanceof AreaInterchangeControl control) {
				return control;
			}
		}
		return null;
	}

	private JsonObject zoneSection() {
		JsonObject section = section(List.of("i", "zoname"));
		for (Object obj : aclfNet.getZoneMap().values()) {
			com.interpss.core.net.Zone zone = (com.interpss.core.net.Zone) obj;
			section.getAsJsonArray("data").add(row((int) zone.getNumber(), nullToEmpty(zone.getName())));
		}
		return section;
	}

	private JsonObject ownerSection() {
		JsonObject section = section(List.of("i", "owname"));
		for (Object obj : aclfNet.getOwnerMap().values()) {
			com.interpss.core.net.Owner owner = (com.interpss.core.net.Owner) obj;
			int ownerNumber = owner.getNumber() > 0
					? (int) owner.getNumber() : intId(owner.getId());
			if (ownerNumber > 0) {
				section.getAsJsonArray("data").add(
						row(ownerNumber, nullToEmpty(owner.getName())));
			}
		}
		return section;
	}

	private static List<String> transformerFields() {
		return List.of("ibus", "jbus", "kbus", "ckt", "cw", "cz", "cm", "mag1", "mag2",
				"nmet", "name", "stat", "o1", "f1", "o2", "f2", "o3", "f3", "o4", "f4",
				"vecgrp", "zcod", "r1_2", "x1_2", "sbase1_2", "r2_3", "x2_3", "sbase2_3",
				"r3_1", "x3_1", "sbase3_1", "vmstar", "anstar", "windv1", "nomv1", "ang1",
				"wdg1rate1", "wdg1rate2", "wdg1rate3", "wdg1rate4", "wdg1rate5", "wdg1rate6",
				"wdg1rate7", "wdg1rate8", "cod1", "cont1", "node1", "rma1", "rmi1", "vma1",
				"vmi1", "ntp1", "tab1", "cr1", "cx1", "cnxa1", "windv2", "nomv2",
				"ang2", "windv3", "nomv3", "ang3", "tab2", "tab3");
	}

	private static JsonObject section(List<String> fields) {
		JsonObject section = new JsonObject();
		JsonArray fieldsAry = new JsonArray();
		fields.forEach(fieldsAry::add);
		section.add("fields", fieldsAry);
		section.add("data", new JsonArray());
		return section;
	}

	private static JsonObject section(List<String> fields, List<JsonArray> rows) {
		JsonObject section = section(fields);
		rows.forEach(section.getAsJsonArray("data")::add);
		return section;
	}

	private static JsonArray row(Object... values) {
		JsonArray row = new JsonArray();
		for (Object value : values) {
			add(row, value);
		}
		return row;
	}

	private static void add(JsonArray row, Object value) {
		if (value == null) {
			row.add((String) null);
		} else if (value instanceof Number number) {
			row.add(number);
		} else if (value instanceof Boolean bool) {
			row.add(bool);
		} else {
			row.add(value.toString());
		}
	}

	private int busIde(AclfBus bus) {
		if (!bus.isActive()) return 4;
		if (bus.getGenCode() == AclfGenCode.SWING) return 3;
		/*
		 * Generator remote-voltage control is represented internally as a PQ bus
		 * plus a RemoteQBus controller. PSS/E reconstructs that controller only
		 * when the generator bus remains IDE=2; exporting its runtime PQ code
		 * silently discards IREG on the next import.
		 */
		if (hasEnabledGeneratorRemoteVoltageControl(bus)) return 2;
		if (bus.getGenCode() == AclfGenCode.GEN_PV) {
			return exportSolvedState && violatesReactiveLimit(bus) ? 1 : 2;
		}
		return 1;
	}

	private static boolean hasEnabledGeneratorRemoteVoltageControl(AclfBus bus) {
		if (!bus.isRemoteQBus() || bus.getRemoteQBus() == null
				|| !bus.getRemoteQBus().isControlStatus()) {
			return false;
		}
		return bus.getContributeGenList().stream()
				.filter(AclfGen::isActive)
				.map(AclfGen::getRemoteVControlBusId)
				.anyMatch(remoteId -> remoteId != null && !remoteId.isBlank()
						&& !remoteId.equals(bus.getId()));
	}

	private static boolean violatesReactiveLimit(AclfBus bus) {
		if (bus.isConnect2ZeroZBranch()) {
			return false;
		}
		double qMax = 0.0;
		double qMin = 0.0;
		boolean foundActiveGenerator = false;
		for (Object object : bus.getContributeGenList()) {
			AclfGen gen = (AclfGen) object;
			if (!gen.isActive()) {
				continue;
			}
			LimitType limit = gen.getQGenLimit();
			if (limit == null) {
				return false;
			}
			foundActiveGenerator = true;
			qMax += limit.getMax();
			qMin += limit.getMin();
		}
		if (!foundActiveGenerator) {
			return false;
		}
		double solvedQ = bus.calNetGenResults().getImaginary();
		return solvedQ > qMax + 1.0e-8 || solvedQ < qMin - 1.0e-8;
	}

	private static int switchingDeviceType(AclfBranch branch) {
		String desc = branch.getDesc();
		if (desc != null) {
			int marker = desc.indexOf("stype=");
			if (marker >= 0) {
				try {
					return Integer.parseInt(desc.substring(marker + 6).trim());
				} catch (NumberFormatException ignored) {
					// Fall through to the code-derived default.
				}
			}
		}
		return 2;
	}

	private int twoTerminalDcMode(HvdcLine2TLCC line) {
		if (isFixedSolvedLcc(line)) return 0;
		if (!line.isActive() || line.getDcLineControlMode() == HvdcControlMode.BLOCKED) return 0;
		if (line.getDcLineControlMode() == HvdcControlMode.DC_CURRENT) return 2;
		return 1;
	}

	private double twoTerminalDcSetpoint(HvdcLine2TLCC line) {
		if (isFixedSolvedLcc(line)) return 0.0;
		if (!line.isActive() || line.getDcLineControlMode() == HvdcControlMode.BLOCKED) return 0.0;
		if (line.getDcLineControlMode() == HvdcControlMode.DC_CURRENT) {
			if (exportSolvedState && line.getRectifier() != null
					&& line.getRectifier().isInitialized()) {
				return Math.abs(line.getRectifier().getIdc());
			}
			return line.getCurrentDemand();
		}
		if (exportSolvedState) {
			ThyConverter controlled = line.getControlSide() == HvdcControlSide.INVERTER
					? line.getInverter() : line.getRectifier();
			if (controlled != null && controlled.isInitialized()) {
				double solvedPowerMw = Math.abs(
						controlled.getDcVoltage() * controlled.getIdc()) / 1.0e6;
				if (Double.isFinite(solvedPowerMw) && solvedPowerMw > 1.0e-9) {
					return line.getControlSide() == HvdcControlSide.INVERTER
							? -solvedPowerMw : solvedPowerMw;
				}
			}
		}
		double demand = line.getPowerDemand(UnitType.mW);
		return line.getControlSide() == HvdcControlSide.INVERTER ? -demand : demand;
	}

	private double twoTerminalDcScheduledVoltage(HvdcLine2TLCC line) {
		if (exportSolvedState && line.isActive() && line.getInverter() != null
				&& line.getInverter().isInitialized()) {
			double solvedVoltageKv = (line.getInverter().getDcVoltage()
					+ line.getInverter().getIdc() * line.getCompondR(UnitType.Ohm)) / 1.0e3;
			if (Double.isFinite(solvedVoltageKv) && solvedVoltageKv > 1.0e-9) {
				return solvedVoltageKv;
			}
		}
		return line.getScheduledDCVoltage(UnitType.kV);
	}

	private boolean isFixedSolvedLcc(HvdcLine2TLCC line) {
		if (!exportSolvedState) {
			return false;
		}
		Object value = aclfNet.getExtraInfo().get(
				LoadflowAlgorithm.FIXED_LCC_HVDC_IDS_EXTRA_INFO_KEY);
		return value instanceof Set<?> ids && ids.contains(line.getId());
	}

	private static int vscDcType(VSCConverter<?> converter) {
		if (converter.getDcControlMode() == HvdcControlMode.BLOCKED) return 0;
		if (converter.getDcControlMode() == HvdcControlMode.DC_VOLTAGE) return 1;
		return 2;
	}

	private int vscAcMode(VSCConverter<?> converter) {
		return converter.getAcControlMode() == VSCAcControlMode.AC_VOLTAGE
				&& !isSolvedVscFixedReactiveOutput(converter) ? 1 : 2;
	}

	private static double vscDcSetpoint(VSCConverter<?> converter, boolean rectifier) {
		double setpoint = converter.getDcSetPoint();
		if (converter.getDcControlMode() == HvdcControlMode.DC_POWER) {
			return rectifier ? -Math.abs(setpoint) : Math.abs(setpoint);
		}
		return setpoint;
	}

	private double vscAcSetpoint(VSCConverter<?> converter) {
		if (isSolvedVscFixedReactiveOutput(converter)) {
			Complex power = converter.calPowerIntoNetOnConverterBase(UnitType.mVA);
			double apparentPower = power.abs();
			if (apparentPower > 1.0e-9 && Math.abs(power.getReal()) > 1.0e-9) {
				double magnitude = Math.abs(power.getReal()) / apparentPower;
				return power.getReal() * power.getImaginary() < 0.0
						? -magnitude : magnitude;
			}
		}
		return converter.getAcControlMode() == VSCAcControlMode.AC_VOLTAGE
				? converter.getAcSetPoint() : vscPowerFactor(converter);
	}

	private boolean isSolvedVscFixedReactiveOutput(VSCConverter<?> converter) {
		if (!exportSolvedState
				|| converter.getAcControlMode() != VSCAcControlMode.AC_VOLTAGE) {
			return false;
		}
		Complex power = converter.calPowerIntoNetOnConverterBase(UnitType.mVA);
		if (Math.abs(power.getReal()) <= 1.0e-9) {
			return false;
		}
		if (converter.isRemoteVControl()) {
			return true;
		}
		LimitType limit = converter.getQMvarLimit();
		double tolerance = 1.0e-8 * Math.max(1.0,
				Math.max(Math.abs(limit.getMin()), Math.abs(limit.getMax())));
		return power.getImaginary() <= limit.getMin() + tolerance
				|| power.getImaginary() >= limit.getMax() - tolerance;
	}

	private static double vscPowerFactor(VSCConverter<?> converter) {
		return converter.getAcControlMode() == VSCAcControlMode.AC_VOLTAGE
				? 1.0
				: converter.getAcSetPoint();
	}

	private int modsw(SwitchedShunt shunt) {
		if (exportSolvedState && (!shunt.isAdjustStatus()
				|| isUnsettledSolvedShunt(shunt))) return 0;
		if (shunt.getControlMode() == AclfAdjustControlMode.CONTINUOUS) return 2;
		if (shunt.getControlMode() == AclfAdjustControlMode.DISCRETE) return 1;
		return 0;
	}

	private boolean isUnsettledSolvedShunt(SwitchedShunt shunt) {
		AclfBus parent = (AclfBus) shunt.getParentBus();
		RemoteQBus control = parent.getSwitchedShuntCtrlGroup() != null
				? parent.getSwitchedShuntCtrlGroup() : shunt;
		AclfBus regulated = (AclfBus) control.getRemoteBus();
		if (regulated == null || !regulated.isActive()) {
			return false;
		}
		double tolerance = 0.005;
		Object configured = aclfNet.getExtraInfo().get(
				LoadflowAlgorithm.SOLVED_VOLTAGE_CONTROL_TOLERANCE_EXTRA_INFO_KEY);
		if (configured instanceof Number number && number.doubleValue() >= 0.0) {
			tolerance = number.doubleValue();
		}
		double voltage = regulated.getVoltageMag();
		if (control.getAdjControlType() == AclfAdjustControlType.POINT_CONTROL) {
			return Math.abs(control.getVSpecified(UnitType.PU) - voltage) > tolerance;
		}
		LimitType range = control.getDesiredControlRange();
		return range != null && (voltage > range.getMax() + tolerance
				|| voltage < range.getMin() - tolerance);
	}

	private static int busNumber(AclfBus bus) {
		if (bus.getNumber() > 0) return (int) bus.getNumber();
		return busNumber(bus.getId(), 0);
	}

	private static int busNumber(String busId, int defaultValue) {
		if (busId == null || busId.isEmpty()) return defaultValue;
		String numeric = busId.startsWith("Bus") ? busId.substring(3) : busId;
		try {
			return Integer.parseInt(numeric);
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	private static int intId(String id) {
		if (id == null || id.isEmpty()) return 0;
		try {
			return Integer.parseInt(id);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static Complex nonNull(Complex value) {
		return value != null ? value : Complex.ZERO;
	}

	private static Double realOrNull(Complex value) {
		return value != null ? value.getReal() : null;
	}

	private static Double imaginaryOrNull(Complex value) {
		return value != null ? value.getImaginary() : null;
	}

	private static double limitMax(LimitType limit, double fallback) {
		return limit != null ? limit.getMax() : fallback;
	}

	private static double limitMin(LimitType limit, double fallback) {
		return limit != null ? limit.getMin() : fallback;
	}

	private static String nullToEmpty(String value) {
		return value != null ? value : "";
	}
}
