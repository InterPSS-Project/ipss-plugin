package org.interpss;

import java.util.List;
import java.util.function.Function;

import org.interpss.datatype.DblBusValue;
import org.interpss.display.AclfOutFunc;
import org.interpss.display.DcSysResultOutput;
import org.interpss.display.AclfOutFunc.BusIdStyle;
import org.interpss.display.DclfOutFunc;
import org.interpss.display.impl.AclfOut_BusStyle;
import org.interpss.display.impl.AclfOut_PSSE;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.func.Function2Adapter;
import com.interpss.common.func.Function4Adapter;
import com.interpss.common.func.IFunction;
import com.interpss.common.func.IFunction2;
import com.interpss.common.func.IFunction4;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.algo.dclf.SenAnalysisAlgorithm;
import com.interpss.core.net.OriginalDataFormat;
import com.interpss.dc.DcNetwork;
import com.interpss.dstab.devent.BranchOutageType;

/**
 * Functions for Core plugin 
 * 
 * @author mzhou
 */
public class CorePluginFunction {
	/* **********************************************************
	 * 		Output functions
	 ************************************************************/
	public static IFunction<DcNetwork, String> OutputSolarNet = 
		new com.interpss.common.func.FunctionAdapter<DcNetwork, String>() {
			@Override public String fx(DcNetwork net) throws InterpssException {
				return DcSysResultOutput.solarAnalysisReuslt(net).toString();
		}};				
			
	/* **********************************************************
	 * 		Aclf Output function, including Sensitivity analysis
	 ************************************************************/
	
	public static Function<AclfNetwork, StringBuffer> aclfResultSummary = net -> {
				return AclfOutFunc.loadFlowSummary(net);
		};

	public static Function<BaseAclfNetwork<?, ?>, StringBuffer> aclfResultBusStyle = net -> {
				return AclfOut_BusStyle.lfResultsBusStyle(net, BusIdStyle.BusId_No);
			};

	public static IFunction2<BaseAclfNetwork<?, ?>, BaseAclfBus<?,?>, StringBuffer> BusLfResultBusStyle = 
		new Function2Adapter<BaseAclfNetwork<?, ?>, BaseAclfBus<?,?>, StringBuffer>() {
			@Override public StringBuffer f(BaseAclfNetwork<?, ?> net, BaseAclfBus<?,?> bus) {
				return AclfOut_BusStyle.busResult(net, bus);
			}
		};
			
	public static IFunction2<AclfNetwork, BusIdStyle, StringBuffer> AclfResultBusStyle2 = 
		new Function2Adapter<AclfNetwork, BusIdStyle, StringBuffer>() {
			@Override public StringBuffer f(AclfNetwork net, BusIdStyle style) {
				return AclfOut_BusStyle.lfResultsBusStyle(net, style);
			}
		};
	
	public static IFunction2<AclfNetwork, AclfOut_PSSE.Format, StringBuffer> AclfResultPsseStyle = 
		new Function2Adapter<AclfNetwork, AclfOut_PSSE.Format, StringBuffer>() {
			@Override public StringBuffer f(AclfNetwork net, AclfOut_PSSE.Format format) {
				return AclfOut_PSSE.lfResults(net, format);
			}
		};
	
	public static IFunction<AclfNetwork, StringBuffer> outputLF4Google = net -> {
				if (net.getOriginalDataFormat() == OriginalDataFormat.CIM)
					return AclfOutFunc.loadFlowSummary(net);
				return aclfResultBusStyle.apply(net);
			};
	
	public static IFunction2<BaseAclfBus<?,?>, OriginalDataFormat, String> OutputBusId = 
		new Function2Adapter<BaseAclfBus<?,?>, OriginalDataFormat, String>() {
			@Override public String f(BaseAclfBus<?,?> bus, OriginalDataFormat fmt) {
				if (fmt == OriginalDataFormat.CIM)
					return "Bus" + bus.getNumber();
				return bus.getId();
			}
	};
	
	public static Function<Double, String> formatKVStr = kv -> {
				if (kv > 1000.0)
					return String.format("%6.1f ", kv);
				else if (kv > 100.0)
					return String.format("%6.2f ", kv);
				else	
					return String.format("%6.3f ", kv);
			};

	/**
	 * Map branch outage type for contingency analysis
	 */
	public static Function<String, BranchOutageType> MapBranchOutageType = caty -> {
				if ("SINGLE_PHASE".equals(caty))
					return BranchOutageType.SINGLE_PHASE;
				else if ("DOUBLE_PHASE".equals(caty))
					return BranchOutageType.DOUBLE_PHASE;		
				return BranchOutageType.THREE_PHASE;
			};
		

	/* **********************************************************
	 * 		Dclf Output function, including Sensitivity analysis
	 ************************************************************/

	public static IFunction2<SenAnalysisAlgorithm, Boolean, StringBuffer> DclfResult = 
		new Function2Adapter<SenAnalysisAlgorithm, Boolean, StringBuffer>() {
			@Override public StringBuffer f(SenAnalysisAlgorithm algo, Boolean branchVioaltion) {
				return DclfOutFunc.dclfResults(algo, branchVioaltion);
			}};

	public static IFunction4<AclfNetwork, String, List<DblBusValue>, Boolean, StringBuffer> DclfGSFBranchInterfaceFlow = 
		new Function4Adapter<AclfNetwork, String, List<DblBusValue>, Boolean, StringBuffer>() {
			@Override public StringBuffer f(AclfNetwork net, String branchId, List<DblBusValue> gsfList, Boolean outage) {
				return DclfOutFunc.gsfBranchInterfaceFlow(net, branchId, gsfList, outage);
			}};
}
