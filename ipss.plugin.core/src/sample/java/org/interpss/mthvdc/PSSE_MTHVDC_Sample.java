package org.interpss.mthvdc;

import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;

import org.interpss.plugin.pssl.plugin.IpssAdapter;

import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetModelType;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.hvdc.HvdcLine2TLCC;
import com.interpss.core.aclf.hvdc.HvdcLineMT;
import com.interpss.core.aclf.hvdc.HvdcMTConverter;
import com.interpss.core.algo.AclfMethodType;

/**
 * Load {@code psse_mthvdc.raw} and print the multi-terminal HVDC model.
 * Automated coverage: {@code org.interpss.core.adapter.psse.raw.aclf.PSSE_MTHVDC_Test}.
 */
public class PSSE_MTHVDC_Sample {
	public static void main(String args[]) throws Exception {
		AclfNetwork aclfNet = IpssAdapter.importAclfNet("ipss.plugin.core/testData/psse/v30/psse_mthvdc.raw")
				.setFormat(PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_30)
				.load()
				.getImportedObj();

		// Case has Bus153–Bus3006 with X=1e-4; default ZBR threshold 1e-5 misses it.
		aclfNet.setZeroZBranchThreshold(1.0e-3);
		aclfNet.setAclfNetModelType(AclfNetModelType.ZBR_DECONSOLIDATED);

		System.out.println("MTDC lines: " + aclfNet.getHvdcLineMTList().size());
		for (HvdcLineMT mt : aclfNet.getHvdcLineMTList()) {
			mt.initLoadflow();
			System.out.println("  " + mt.getId()
					+ " mode=" + mt.getControlMode()
					+ " VCONV=" + mt.getVConvBusId()
					+ " VCMOD=" + mt.getVcMod()
					+ " nConv=" + mt.getConverterList().size()
					+ " nDcBus=" + mt.getDcBusList().size()
					+ " nLink=" + mt.getDcLinkList().size()
					+ " topo=" + (mt.validateTopology() == null ? "ok" : mt.validateTopology()));
			for (HvdcMTConverter c : mt.getConverterList()) {
				System.out.println("    conv " + c.getAcBusId()
						+ " SETVL=" + c.getSetValue()
						+ " CNVCOD=" + c.getCnvCod()
						+ " dcBus=" + c.getDcBusNumber()
						+ " PacMW=" + c.getPac()
						+ " QacMvar=" + c.getQac()
						+ " PQpu=" + mt.powerIntoConverter(c.getAcBusId()));
			}
		}

		aclfNet.getSpecialBranchList().forEach(branch -> {
			if (branch instanceof HvdcLine2TLCC) {
				((HvdcLine2TLCC<?>) branch).initLoadflow();
			}
		});

		aclfNet.calExternalPowerIntoNet();

		System.out.println("Buses with |mismatch| > 0.1:");
		for (AclfBus bus : aclfNet.getBusList()) {
			if (bus.mismatch(AclfMethodType.NR).abs() > 1e-1)
				System.out.println(bus.getId() + ", " + bus.mismatch(AclfMethodType.NR));
		}
	}
}
