package org.interpss.threePhase.qsts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.threePhase.dataParser.opendss.OpenDSSDataParser;
import org.eclipse.emf.ecore.impl.MinimalEObjectImpl;
import org.junit.jupiter.api.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.core.threephase.ILoad1Phase;
import com.interpss.core.threephase.IPhaseLoad;
import com.interpss.core.threephase.LoadConnectionType;
import com.interpss.core.threephase.Static3PLoad;
import com.interpss.core.threephase.Static3PhaseFactory;

public class QstsLoadStateStoreTest {

	@Test
	void resolverAppliesProfileAndGlobalMultiplier() {
		QstsProfileRegistry registry = new QstsProfileRegistry();
		registry.add(new QstsProfile("daily", new double[0], new double[] {0.5, 1.2}, null, null));
		QstsProfileBinding binding = new QstsProfileBinding("load", "load1", Map.of("daily", "daily"),
				QstsDeviceStatus.VARIABLE);

		QstsLoadMultiplier multiplier = new QstsLoadMultiplierResolver(registry)
				.resolve(binding, QstsMode.DAILY, 1, 0.8);

		assertEquals(0.96, multiplier.getPMultiplier(), 1.0e-12);
		assertEquals(0.96, multiplier.getQMultiplier(), 1.0e-12);
		assertEquals("daily", multiplier.getProfileId());
	}

	@Test
	void resolverSupportsIndependentPAndQMultipliers() {
		QstsProfileRegistry registry = new QstsProfileRegistry();
		registry.add(new QstsProfile("daily", new double[0], new double[] {0.5}, new double[] {0.7}, null));
		QstsProfileBinding binding = new QstsProfileBinding("load", "load1", Map.of("daily", "daily"),
				QstsDeviceStatus.VARIABLE);

		QstsLoadMultiplier multiplier = new QstsLoadMultiplierResolver(registry)
				.resolve(binding, QstsMode.DAILY, 0, 1.0);

		assertEquals(0.5, multiplier.getPMultiplier(), 1.0e-12);
		assertEquals(0.7, multiplier.getQMultiplier(), 1.0e-12);
	}

	@Test
	void resolverKeepsFixedAndExemptLoadsAtBaseState() {
		QstsProfileRegistry registry = new QstsProfileRegistry();
		registry.add(new QstsProfile("daily", new double[0], new double[] {0.5}, new double[] {0.7}, null));

		QstsLoadMultiplier fixed = new QstsLoadMultiplierResolver(registry).resolve(
				new QstsProfileBinding("load", "fixed", Map.of("daily", "daily"), QstsDeviceStatus.FIXED),
				QstsMode.DAILY, 0, 0.8);
		QstsLoadMultiplier exempt = new QstsLoadMultiplierResolver(registry).resolve(
				new QstsProfileBinding("load", "exempt", Map.of("daily", "daily"), QstsDeviceStatus.EXEMPT),
				QstsMode.DAILY, 0, 0.8);

		assertEquals(1.0, fixed.getPMultiplier(), 1.0e-12);
		assertEquals(1.0, fixed.getQMultiplier(), 1.0e-12);
		assertEquals(1.0, exempt.getPMultiplier(), 1.0e-12);
		assertEquals(1.0, exempt.getQMultiplier(), 1.0e-12);
	}

	@Test
	void loadBaseStateScalesAndRestoresThreePhaseLoad() {
		Static3PLoad load = Static3PhaseFactory.eINSTANCE.createStatic3PLoad();
		load.setId("load3p");
		load.setCode(AclfLoadCode.CONST_P);
		load.set3PhaseLoad(new Complex3x1(new Complex(1.0, 0.4),
				new Complex(1.0, 0.4), new Complex(1.0, 0.4)));
		QstsLoadBaseState state = new QstsLoadBaseState(load);

		state.applyMultiplier(0.5, 0.25);

		assertEquals(0.5, load.getInit3PhaseLoad().a_0.getReal(), 1.0e-12);
		assertEquals(0.1, load.getInit3PhaseLoad().a_0.getImaginary(), 1.0e-12);
		assertEquals(0.5, load.getInit3PhaseLoad().b_1.getReal(), 1.0e-12);
		assertEquals(0.1, load.getInit3PhaseLoad().b_1.getImaginary(), 1.0e-12);

		state.restore();

		assertEquals(1.0, load.getInit3PhaseLoad().a_0.getReal(), 1.0e-12);
		assertEquals(0.4, load.getInit3PhaseLoad().a_0.getImaginary(), 1.0e-12);
		assertEquals(1.0, load.getInit3PhaseLoad().c_2.getReal(), 1.0e-12);
		assertEquals(0.4, load.getInit3PhaseLoad().c_2.getImaginary(), 1.0e-12);
	}

	@Test
	void loadBaseStateScalesAndRestoresSinglePhaseComponents() {
		TestLoad1Phase load = new TestLoad1Phase("load1p");
		load.setCode(AclfLoadCode.CONST_P);
		load.setLoadCP(new Complex(1.0, 0.4));
		load.setLoadCI(new Complex(0.2, 0.1));
		load.setLoadCZ(new Complex(0.3, 0.2));
		QstsLoadBaseState state = new QstsLoadBaseState(load);

		state.applyMultiplier(0.5, 0.25);

		assertEquals(0.5, load.getLoadCP().getReal(), 1.0e-12);
		assertEquals(0.1, load.getLoadCP().getImaginary(), 1.0e-12);
		assertEquals(0.1, load.getLoadCI().getReal(), 1.0e-12);
		assertEquals(0.025, load.getLoadCI().getImaginary(), 1.0e-12);

		state.restore();

		assertEquals(1.0, load.getLoadCP().getReal(), 1.0e-12);
		assertEquals(0.4, load.getLoadCP().getImaginary(), 1.0e-12);
		assertEquals(0.3, load.getLoadCZ().getReal(), 1.0e-12);
		assertEquals(0.2, load.getLoadCZ().getImaginary(), 1.0e-12);
	}

	@Test
	void loadBaseStateScalesAndRestoresTwoPhasePhaseLoad() {
		TestPhaseLoad load = new TestPhaseLoad("load-ab", PhaseCode.AB,
				new Complex3x1(new Complex(1.0, 0.4), new Complex(2.0, 0.8), Complex.ZERO));
		QstsLoadBaseState state = new QstsLoadStateStore().register(load);

		state.applyMultiplier(0.5, 0.25);

		assertEquals(0.5, load.getInit3PhaseLoad().a_0.getReal(), 1.0e-12);
		assertEquals(0.1, load.getInit3PhaseLoad().a_0.getImaginary(), 1.0e-12);
		assertEquals(1.0, load.getInit3PhaseLoad().b_1.getReal(), 1.0e-12);
		assertEquals(0.2, load.getInit3PhaseLoad().b_1.getImaginary(), 1.0e-12);
		assertEquals(0.0, load.getInit3PhaseLoad().c_2.abs(), 1.0e-12);

		state.restore();

		assertEquals(1.0, load.getInit3PhaseLoad().a_0.getReal(), 1.0e-12);
		assertEquals(2.0, load.getInit3PhaseLoad().b_1.getReal(), 1.0e-12);
		assertEquals(0.0, load.getInit3PhaseLoad().c_2.abs(), 1.0e-12);
	}

	@Test
	void openDssParserRegistersLoadBaseState() throws InterpssException {
		OpenDSSDataParser parser = new OpenDSSDataParser();
		parser.getLoadParser().parseLoadData(
				"New Load.load1 bus1=bus1.1.2.3 phases=3 conn=wye model=1 kv=12.47 kw=90 kvar=30 daily=daily");

		assertEquals(1, parser.getTimeSeriesData().getLoadStateStore().size());
		QstsLoadBaseState state = parser.getTimeSeriesData().getLoadStateStore().states().iterator().next();
		assertNotNull(state.getThreePhaseLoad());
		assertEquals(30.0, state.getThreePhaseLoad().a_0.getReal(), 1.0e-12);
	}

	private static class TestLoad1Phase extends MinimalEObjectImpl.Container implements ILoad1Phase {
		private final String id;
		private AclfLoadCode code = AclfLoadCode.CONST_P;
		private Complex loadCP = Complex.ZERO;
		private Complex loadCI = Complex.ZERO;
		private Complex loadCZ = Complex.ZERO;
		private PhaseCode phaseCode = PhaseCode.A;
		private LoadConnectionType connectionType = LoadConnectionType.SINGLE_PHASE_WYE;
		private double nominalKV;

		TestLoad1Phase(String id) {
			this.id = id;
		}

		@Override
		public String getId() {
			return id;
		}

		@Override
		public AclfLoadCode getCode() {
			return code;
		}

		@Override
		public void setCode(AclfLoadCode code) {
			this.code = code;
		}

		@Override
		public Complex getLoadCP() {
			return loadCP;
		}

		@Override
		public void setLoadCP(Complex load) {
			this.loadCP = load;
		}

		@Override
		public Complex getLoadCI() {
			return loadCI;
		}

		@Override
		public void setLoadCI(Complex load) {
			this.loadCI = load;
		}

		@Override
		public Complex getLoadCZ() {
			return loadCZ;
		}

		@Override
		public Complex getLoad(double vmag) {
			return loadCP.add(loadCI.multiply(vmag)).add(loadCZ.multiply(vmag * vmag));
		}

		@Override
		public void setLoadCZ(Complex load) {
			this.loadCZ = load;
		}

		@Override
		public PhaseCode getPhaseCode() {
			return phaseCode;
		}

		@Override
		public void setPhaseCode(PhaseCode phCode) {
			this.phaseCode = phCode;
		}

		@Override
		public LoadConnectionType getLoadConnectionType() {
			return connectionType;
		}

		@Override
		public void setLoadConnectionType(LoadConnectionType loadConnectType) {
			this.connectionType = loadConnectType;
		}

		@Override
		public double getNominalKV() {
			return nominalKV;
		}

		@Override
		public void setNominalKV(double ratedkV) {
			this.nominalKV = ratedkV;
		}
	}

	private static class TestPhaseLoad implements IPhaseLoad {
		private final String id;
		private PhaseCode phaseCode;
		private AclfLoadCode code = AclfLoadCode.CONST_P;
		private LoadConnectionType connectionType = LoadConnectionType.THREE_PHASE_WYE;
		private double nominalKV;
		private Complex3x1 load;

		TestPhaseLoad(String id, PhaseCode phaseCode, Complex3x1 load) {
			this.id = id;
			this.phaseCode = phaseCode;
			this.load = load;
		}

		@Override
		public String getId() {
			return id;
		}

		@Override
		public AclfLoadCode getCode() {
			return code;
		}

		@Override
		public void setCode(AclfLoadCode code) {
			this.code = code;
		}

		@Override
		public PhaseCode getPhaseCode() {
			return phaseCode;
		}

		@Override
		public void setPhaseCode(PhaseCode phaseCode) {
			this.phaseCode = phaseCode;
		}

		@Override
		public LoadConnectionType getLoadConnectionType() {
			return connectionType;
		}

		@Override
		public void setLoadConnectionType(LoadConnectionType loadConnectionType) {
			this.connectionType = loadConnectionType;
		}

		@Override
		public double getNominalKV() {
			return nominalKV;
		}

		@Override
		public void setNominalKV(double nominalKV) {
			this.nominalKV = nominalKV;
		}

		@Override
		public Complex3x1 getInit3PhaseLoad() {
			return load;
		}

		@Override
		public void set3PhaseLoad(Complex3x1 threePhaseLoad) {
			this.load = threePhaseLoad;
		}

		@Override
		public Complex getLoadCP() {
			return load.a_0.add(load.b_1).add(load.c_2);
		}

		@Override
		public void setLoadCP(Complex load) {
			this.load = IPhaseLoad.distribute(load, phaseCode);
		}

		@Override
		public Complex getLoadCI() {
			return null;
		}

		@Override
		public void setLoadCI(Complex load) {
		}

		@Override
		public Complex getLoadCZ() {
			return null;
		}

		@Override
		public void setLoadCZ(Complex load) {
		}
	}
}
