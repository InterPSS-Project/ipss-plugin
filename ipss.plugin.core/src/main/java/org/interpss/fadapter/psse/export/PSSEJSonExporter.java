package org.interpss.fadapter.psse.export;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.math3.complex.Complex;
import org.interpss.fadapter.psse.bean.PSSESchema;
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
import com.interpss.core.aclf.adj.AclfAdjustControlMode;
import com.interpss.core.aclf.adj.SwitchedShunt;
import com.interpss.core.aclf.hvdc.ConverterType;
import com.interpss.core.aclf.hvdc.HvdcControlMode;
import com.interpss.core.aclf.hvdc.HvdcLine2TLCC;
import com.interpss.core.aclf.hvdc.HvdcLine2TVSC;
import com.interpss.core.aclf.hvdc.VSCAcControlMode;
import com.interpss.core.aclf.hvdc.VSCConverter;

/**
 * Standalone PSS/E RAWX-style JSON exporter for the direct RAWX parser.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class PSSEJsonExporter {
	private static final Logger log = LoggerFactory.getLogger(PSSEJsonExporter.class);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

	private final BaseAclfNetwork aclfNet;
	private final double baseMva;
	private final PSSESchema psseJson;

	public PSSEJsonExporter(BaseAclfNetwork<?, ?> aclfNet) {
		this.aclfNet = aclfNet;
		this.baseMva = aclfNet.getBaseKva() > 0.0 ? aclfNet.getBaseKva() / 1000.0 : 100.0;
		this.psseJson = null;
	}

	public PSSEJsonExporter(BaseAclfNetwork<?, ?> aclfNet, PSSESchema psseJson) {
		this.aclfNet = aclfNet;
		this.baseMva = aclfNet.getBaseKva() > 0.0 ? aclfNet.getBaseKva() / 1000.0 : 100.0;
		this.psseJson = psseJson;
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
		network.add("area", areaSection());
		network.add("zone", zoneSection());
		network.add("owner", ownerSection());
		return root;
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
		for (Object obj : aclfNet.getBusList()) {
			AclfBus bus = (AclfBus) obj;
			for (Object loadObj : bus.getContributeLoadList()) {
				AclfLoad load = (AclfLoad) loadObj;
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
		return section;
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
		}
		return section;
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
				JsonArray data = row(busNumber(bus), shunt.getId(), modsw(shunt), 0,
						shunt.isActive() ? 1 : 0, limitMax(shunt.getDesiredControlRange(), 1.0),
						limitMin(shunt.getDesiredControlRange(), 1.0), busNumber(shunt.getRemoteBusBranchId(), busNumber(bus)),
						0, 100.0, "", shunt.getBInit() * baseMva);
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

	private JsonObject generatorSection() {
		JsonObject section = section(List.of("ibus", "machid", "pg", "qg", "qt", "qb", "vs",
				"ireg", "nreg", "mbase", "zr", "zx", "rt", "xt", "gtap", "stat", "rmpct",
				"pt", "pb", "baslod", "o1", "f1", "o2", "f2", "o3", "f3", "o4", "f4",
				"wmod", "wpf", "droopname", "name"));
		for (Object obj : aclfNet.getBusList()) {
			AclfBus bus = (AclfBus) obj;
			boolean wroteGen = false;
			for (Object genObj : bus.getContributeGenList()) {
				AclfGen gen = (AclfGen) genObj;
				Complex pq = nonNull(gen.getGen());
				LimitType qLimit = gen.getQGenLimit();
				LimitType pLimit = gen.getPGenLimit();
				Complex sourceZ = nonNull(gen.getSourceZ());
				Complex xfrZ = nonNull(gen.getXfrZ());
				boolean pqGenBus = bus.getGenCode() == AclfGenCode.GEN_PQ;
				section.getAsJsonArray("data").add(row(
						busNumber(bus), gen.getId(),
						pq.getReal() * baseMva, pq.getImaginary() * baseMva,
						(pqGenBus ? pq.getImaginary() : limitMax(qLimit, pq.getImaginary())) * baseMva,
						(pqGenBus ? pq.getImaginary() : limitMin(qLimit, pq.getImaginary())) * baseMva,
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
			if (!wroteGen && bus.getGenCode() == AclfGenCode.GEN_PQ) {
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
					branch.getXfrZTableNumber(),
					z.getReal(), z.getImaginary(), baseMva,
					null, null, null, null, null, null, null, null,
					branch.getFromTurnRatio(), ((AclfBus) branch.getFromBus()).getBaseVoltage() / 1000.0,
					angleDeg, branch.getRatingMva1(), branch.getRatingMva2(), branch.getRatingMva3(),
					branch.getRatingMva1(), branch.getRatingMva2(), branch.getRatingMva3(),
					branch.getRatingMva1(), branch.getRatingMva2(),
					0, 0, 0, 1.1, 0.9, 1.1, 0.9, 33, 0,
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
				0, 0, 0, 1.1, 0.9, 1.1, 0.9, 33, 0,
				0, 0, 0,
				toLeg.getToTurnRatio(), ((AclfBus) branch3W.getToBus()).getBaseVoltage() / 1000.0,
				Math.toDegrees(toLeg.getToPSXfrAngle()),
				tertLeg.getToTurnRatio(), ((AclfBus) branch3W.getTertiaryBus()).getBaseVoltage() / 1000.0,
				Math.toDegrees(tertLeg.getToPSXfrAngle()),
				0, 0);
	}

	private static int threeWStatus(Aclf3WBranch branch3W) {
		if (!branch3W.isActive()) return 0;
		if (!branch3W.getFromAclfBranch().isActive()) return 4;
		if (!branch3W.getToAclfBranch().isActive()) return 2;
		if (!branch3W.getTertAclfBranch().isActive()) return 3;
		return 1;
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
		JsonObject section = section(List.of("name", "mdc", "rdc", "setvl", "vschd", "rcomp",
				"delti", "met", "ipr", "ipi", "nbr", "anmnr", "anmxr", "rcr", "xcr", "ebasr",
				"trr", "tapr", "tmxr", "tmnr", "stpr", "xcapr", "nbi", "anmni", "anmxi",
				"rci", "xci", "ebasi", "tri", "tapi", "tmxi", "tmni", "stpi", "xcapi"));
		if (aclfNet.getSpecialBranchList() == null) {
			return section;
		}
		for (Object obj : aclfNet.getSpecialBranchList()) {
			if (obj instanceof HvdcLine2TLCC line) {
				section.getAsJsonArray("data").add(row(
						nullToEmpty(line.getName()),
						twoTerminalDcMode(line),
						line.getRdc(UnitType.Ohm),
						twoTerminalDcSetpoint(line),
						line.getScheduledDCVoltage(UnitType.kV),
						0.0, 0.0,
						line.getMeterEnd() == ConverterType.RECTIFIER ? "R" : "I",
						busNumber((AclfBus) line.getFromBus()),
						busNumber((AclfBus) line.getToBus()),
						1, 0.0, 0.0, 0.0, 0.0, 0.0,
						1.0, 1.0, 1.5, 0.51, 0.00625, 0.0,
						1, 0.0, 0.0, 0.0, 0.0, 0.0,
						1.0, 1.0, 1.5, 0.51, 0.00625, 0.0));
			}
		}
		return section;
	}

	private JsonObject vscDcSection() {
		JsonObject section = section(List.of("name", "mdc", "rdc", "o1", "f1", "o2", "f2",
				"o3", "f3", "o4", "f4",
				"ibus1", "type1", "mode1", "dcset1", "acset1", "aloss1", "bloss1",
				"minloss1", "smax1", "imax1", "pwf1", "maxq1", "minq1", "vsreg1",
				"nreg1", "rmpct1",
				"ibus2", "type2", "mode2", "dcset2", "acset2", "aloss2", "bloss2",
				"minloss2", "smax2", "imax2", "pwf2", "maxq2", "minq2", "vsreg2",
				"nreg2", "rmpct2"));
		if (aclfNet.getSpecialBranchList() == null) {
			return section;
		}
		for (Object obj : aclfNet.getSpecialBranchList()) {
			if (obj instanceof HvdcLine2TVSC line) {
				VSCConverter rec = (VSCConverter) line.getRecConverter();
				VSCConverter inv = (VSCConverter) line.getInvConverter();
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
						0.0,
						0.0,
						0.0,
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
						0.0,
						0.0,
						0.0,
						inv.getMvaRating(),
						inv.getAcCurrentRating(),
						vscPowerFactor(inv),
						limitMax(inv.getQMvarLimit(), 9999.0),
						limitMin(inv.getQMvarLimit(), -9999.0),
						busNumber(inv.getRemoteControlBusId(), busNumber((AclfBus) inv.getBus())),
						0,
						inv.getRemoteControlPercent()));
			}
		}
		return section;
	}

	private JsonObject areaSection() {
		JsonObject section = section(List.of("iarea", "isw", "pdes", "ptol", "arname"));
		for (Object obj : aclfNet.getAreaMap().values()) {
			com.interpss.core.net.Area area = (com.interpss.core.net.Area) obj;
			section.getAsJsonArray("data").add(row((int) area.getNumber(), 0, 0.0, 10.0, nullToEmpty(area.getName())));
		}
		return section;
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
			section.getAsJsonArray("data").add(row((int) owner.getNumber(), nullToEmpty(owner.getName())));
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

	private static int busIde(AclfBus bus) {
		if (!bus.isActive()) return 4;
		if (bus.getGenCode() == AclfGenCode.SWING) return 3;
		if (bus.getGenCode() == AclfGenCode.GEN_PV) return 2;
		return 1;
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

	private static int twoTerminalDcMode(HvdcLine2TLCC line) {
		if (!line.isActive() || line.getDcLineControlMode() == HvdcControlMode.BLOCKED) return 0;
		if (line.getDcLineControlMode() == HvdcControlMode.DC_CURRENT) return 2;
		return 1;
	}

	private static double twoTerminalDcSetpoint(HvdcLine2TLCC line) {
		if (!line.isActive() || line.getDcLineControlMode() == HvdcControlMode.BLOCKED) return 0.0;
		if (line.getDcLineControlMode() == HvdcControlMode.DC_CURRENT) {
			return line.getCurrentDemand();
		}
		return line.getPowerDemand(UnitType.mW);
	}

	private static int vscDcType(VSCConverter<?> converter) {
		if (converter.getDcControlMode() == HvdcControlMode.BLOCKED) return 0;
		if (converter.getDcControlMode() == HvdcControlMode.DC_VOLTAGE) return 1;
		return 2;
	}

	private static int vscAcMode(VSCConverter<?> converter) {
		return converter.getAcControlMode() == VSCAcControlMode.AC_VOLTAGE ? 1 : 2;
	}

	private static double vscDcSetpoint(VSCConverter<?> converter, boolean rectifier) {
		double setpoint = converter.getDcSetPoint();
		if (converter.getDcControlMode() == HvdcControlMode.DC_POWER) {
			return rectifier ? -Math.abs(setpoint) : Math.abs(setpoint);
		}
		return setpoint;
	}

	private static double vscAcSetpoint(VSCConverter<?> converter) {
		return converter.getAcControlMode() == VSCAcControlMode.AC_VOLTAGE
				? converter.getAcSetPoint()
				: vscPowerFactor(converter);
	}

	private static double vscPowerFactor(VSCConverter<?> converter) {
		return converter.getAcControlMode() == VSCAcControlMode.AC_VOLTAGE
				? 1.0
				: converter.getAcSetPoint();
	}

	private static int modsw(SwitchedShunt shunt) {
		if (shunt.getControlMode() == AclfAdjustControlMode.CONTINUOUS) return 2;
		if (shunt.getControlMode() == AclfAdjustControlMode.DISCRETE) return 1;
		return 0;
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
