package org.interpss;

import java.util.List;

import org.ieee.odm.model.aclf.AclfModelParser;
import org.ieee.odm.schema.AcscFaultCategoryEnumType;
import org.ieee.odm.schema.LoadflowNetXmlType;
import org.interpss.datatype.DblBusValue;
import org.interpss.display.AclfOutFunc;
import org.interpss.display.DclfOutFunc;
import org.interpss.display.AclfOutFunc.BusIdStyle;
import org.interpss.display.impl.AclfOut_BusStyle;
import org.interpss.display.impl.AclfOut_PSSE;
import org.interpss.mapper.odm.ODMAclfNetMapper;
import org.interpss.spring.CorePluginSpringFactory;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.func.Function2Adapter;
import com.interpss.common.func.Function4Adapter;
import com.interpss.common.func.FunctionAdapter;
import com.interpss.common.func.IFunction;
import com.interpss.common.func.IFunction2;
import com.interpss.common.func.IFunction4;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.dclf.DclfAlgorithm;
import com.interpss.core.net.OriginalDataFormat;
import com.interpss.dstab.devent.BranchOutageType;

/**
 * Functions for Core plugin 
 * 
 * @author mzhou
 *
 */
public class CorePluginFunction {
	/* **********************************************************
	 * 		ODM Mapping functions
	 ************************************************************/

	/**
	 * Aclf ODM model parser to AclfNetwork object mapping function
	 */
	public static IFunction2<AclfModelParser, ODMAclfNetMapper.XfrBranchModel, AclfNetwork> AclfParser2AclfNet = 
		new Function2Adapter<AclfModelParser, ODMAclfNetMapper.XfrBranchModel, AclfNetwork>() {
			@Override public AclfNetwork fx(AclfModelParser parser, ODMAclfNetMapper.XfrBranchModel xfrBranchModel) throws InterpssException {
				return CorePluginSpringFactory.getOdm2AclfParserMapper(xfrBranchModel)
						.map2Model(parser)
						.getAclfNet();
		}};
	
	/**
	 * Aclf ODM network xml doc to AclfNetwork object mapping function
	 */
	public static IFunction2<LoadflowNetXmlType, ODMAclfNetMapper.XfrBranchModel, AclfNetwork> AclfXmlNet2AclfNet = 
		new Function2Adapter<LoadflowNetXmlType, ODMAclfNetMapper.XfrBranchModel, AclfNetwork>() {
			@Override public AclfNetwork fx(LoadflowNetXmlType xmlNet, ODMAclfNetMapper.XfrBranchModel xfrBranchModel) throws InterpssException {
				return CorePluginSpringFactory.getOdm2AclfNetMapper(xfrBranchModel)
						.map2Model(xmlNet)
						.getAclfNet();
		}};
	
	/* **********************************************************
	 * 		Aclf Output function, including Sensitivity analysis
	 ************************************************************/
	
	/**
	 * Create output text for Aclf result in the Summary format
	 * 
	 * Usage:
	 *   import static org.interpss.CorePluginFunction.AclfResultSummary;
	 *   
	 *   StringBuffer outText = aclfResultSummary.apply(aclfNet);
	 */
	public static IFunction<AclfNetwork, StringBuffer> AclfResultSummary = 
		new FunctionAdapter<AclfNetwork, StringBuffer>() {
			@Override public StringBuffer f(AclfNetwork net) {
				return AclfOutFunc.loadFlowSummary(net);
		}};

	/**
	 * Create output text for Aclf result in the BusStyle format
	 * 
	 * Usage:
	 *   import static org.interpss.CorePluginFunction.AclfResultBusStype;
	 *   
	 *   StringBuffer outText = aclfResultBusStype.apply(aclfNet);
	 */
	public static IFunction<AclfNetwork, StringBuffer> AclfResultBusStyle = 
		new FunctionAdapter<AclfNetwork, StringBuffer>() {
			@Override public StringBuffer f(AclfNetwork net) {
				return AclfOut_BusStyle.lfResultsBusStyle(net, BusIdStyle.BusId_No);
			}
		};

	/**
	 * Create output text for Aclf bus result in the BusStyle format
	 * 
	 * Usage:
	 *   import static org.interpss.CorePluginFunction.BusLfResultBusStyle;
	 *   
	 *   StringBuffer outText = BusLfResultBusStyle.f(aclfNet, bus);
	 */
	public static IFunction2<AclfNetwork, AclfBus, StringBuffer> BusLfResultBusStyle = 
		new Function2Adapter<AclfNetwork, AclfBus, StringBuffer>() {
			@Override public StringBuffer f(AclfNetwork net, AclfBus bus) {
				return AclfOut_BusStyle.busResult(net, bus);
			}
		};
			
	/**
	 * Function for output LF result in the Bus Style format
	 */
	public static IFunction2<AclfNetwork, BusIdStyle, StringBuffer> AclfResultBusStyle2 = 
		new Function2Adapter<AclfNetwork, BusIdStyle, StringBuffer>() {
			@Override public StringBuffer f(AclfNetwork net, BusIdStyle style) {
				return AclfOut_BusStyle.lfResultsBusStyle(net, style);
			}
		};
	
	/**
	 * function for output LF result in PSS/E format
	 * 	
	 */
	public static IFunction2<AclfNetwork, AclfOut_PSSE.Format, StringBuffer> AclfResultPsseStyle = 
		new Function2Adapter<AclfNetwork, AclfOut_PSSE.Format, StringBuffer>() {
			@Override public StringBuffer f(AclfNetwork net, AclfOut_PSSE.Format format) {
				return AclfOut_PSSE.lfResults(net, format);
			}
		};
	
	/**
	 * function for output LF result for Google cloud edition
	 * 
	 */
	public static IFunction<AclfNetwork, StringBuffer> OutputLF4Google = 
		new FunctionAdapter<AclfNetwork, StringBuffer>() {
			@Override public StringBuffer f(AclfNetwork net) {
				if (net.getOriginalDataFormat() == OriginalDataFormat.CIM)
					return AclfOutFunc.loadFlowSummary(net);
				return AclfResultBusStyle.f(net);
			}
		};
	
	/**
	 * function to format bus id for output
	 * 
	 */
	public static IFunction2<AclfBus, OriginalDataFormat, String> OutputBusId = 
		new Function2Adapter<AclfBus, OriginalDataFormat, String>() {
			@Override public String f(AclfBus bus, OriginalDataFormat fmt) {
				if (fmt == OriginalDataFormat.CIM)
					return "Bus" + bus.getNumber();
				return bus.getId();
			}
	};
	
	/**
	 * function to format KV for output
	 * 
	 */
	public static IFunction<Double, String> FormatKVStr = 
		new FunctionAdapter<Double, String>() {
			@Override public String f(Double kv) {
				if (kv > 1000.0)
					return String.format("%6.1f ", kv);
				else if (kv > 100.0)
					return String.format("%6.2f ", kv);
				else	
					return String.format("%6.3f ", kv);
			}
	};

  
	/**
	 * Function to map ODM AcscFaultCategoryEnumType to InterPSS BranchOutageType
	 */
	public static IFunction<AcscFaultCategoryEnumType, BranchOutageType> MapBranchOutageType = 
		new FunctionAdapter<AcscFaultCategoryEnumType, BranchOutageType>() {
			@Override public BranchOutageType f(AcscFaultCategoryEnumType caty) {
				if (caty == AcscFaultCategoryEnumType.OUTAGE_1_PHASE)
					return BranchOutageType.SINGLE_PHASE;
				else if (caty == AcscFaultCategoryEnumType.OUTAGE_2_PHASE)
					return BranchOutageType.DOUBLE_PHASE;		
				return BranchOutageType.THREE_PHASE;
			}		
		};
		

	/* **********************************************************
	 * 		Dclf Output function, including Sensitivity analysis
	 ************************************************************/

	/**
	 * Usage:
	 *   import static rg.interpss.CorePluginFunction.DclfResult;
	 *   
	 *   StringBuffer outText = dclfResult.apply(dclfAlgo, true/false);
	 */
	public static IFunction2<DclfAlgorithm, Boolean, StringBuffer> DclfResult = 
		new Function2Adapter<DclfAlgorithm, Boolean, StringBuffer>() {
			@Override public StringBuffer f(DclfAlgorithm algo, Boolean branchVioaltion) {
				return DclfOutFunc.dclfResults(algo, branchVioaltion);
			}};

	/**
	 * Based on the gsf in the gsfList and gen P, compute dclf based branch power flow
	 * 
	 * Usage:
	 *   import static rg.interpss.CorePluginFunction.DclfGSFBranchFlow;
	 *   
	 *   StringBuffer outText = dclfGSFBranchFlow.apply(net, branchId, gsfList);
	 */
	public static IFunction4<AclfNetwork, String, List<DblBusValue>, Boolean, StringBuffer> DclfGSFBranchInterfaceFlow = 
		new Function4Adapter<AclfNetwork, String, List<DblBusValue>, Boolean, StringBuffer>() {
			@Override public StringBuffer f(AclfNetwork net, String branchId, List<DblBusValue> gsfList, Boolean outage) {
				return DclfOutFunc.gsfBranchInterfaceFlow(net, branchId, gsfList, outage);
			}};
}
