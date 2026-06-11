package org.interpss.threePhase.dataParser.opendss;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.common.IFileReader;
import org.ieee.odm.common.ODMException;
import org.ieee.odm.common.ODMLogger;
import org.ieee.odm.common.ODMTextFileReader;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.threePhase.basic.LineConfiguration;
import org.interpss.threePhase.basic.dstab.DStab1PLoad;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.basic.dstab.DStab3PLoad;
import org.interpss.threePhase.dataParser.opendss.timeseries.OpenDSSLoadShapeParser;
import org.interpss.threePhase.dataParser.opendss.timeseries.OpenDSSTemperatureShapeParser;
import org.interpss.threePhase.dataParser.opendss.timeseries.OpenDSSTimeSeriesData;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.powerflow.control.CapacitorControlData;
import org.interpss.threePhase.powerflow.control.InverterControlData;
import org.interpss.threePhase.powerflow.control.RegulatorControlData;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.acsc.AcscBranch;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;
import com.interpss.core.net.NetworkType;
import com.interpss.core.threephase.IBranch3Phase;
import com.interpss.core.threephase.IBus3Phase;
import com.interpss.core.threephase.AclfLoad3Phase;
import com.interpss.core.threephase.LoadConnectionType;
import com.interpss.core.threephase.Static3PBus;
import com.interpss.core.threephase.Static3PBranch;
import com.interpss.core.threephase.Static3PLoad;
import com.interpss.core.threephase.Static3PNetwork;
import com.interpss.dstab.DStabBranch;

public class OpenDSSDataParser {

	protected String busIdPrefix = "";
	// Line configuration table
	protected Hashtable<String,LineConfiguration> lineConfigTable = null;
	protected Hashtable<String,OpenDSSWireData> wireDataTable = null;

	protected DStabNetwork3Phase distNet = null;
	protected Static3PNetwork staticNet = null;
	protected OpenDSSLineCodeParser lineCodeParser = null;
	protected OpenDSSLineParser lineParser = null;
	protected OpenDSSLoadParser loadParser = null;
	protected OpenDSSGeneratorParser generatorParser = null;
	protected OpenDSSPVSystemParser pvSystemParser = null;
	protected OpenDSSStorageParser storageParser = null;
	protected OpenDSSInvControlParser invControlParser = null;
	protected OpenDSSXYCurveParser xyCurveParser = null;
	protected OpenDSSTransformerParser xfrParser = null;
	protected OpenDSSCapacitorParser capParser = null;
	protected OpenDSSRegulatorParser regulatorParser = null;
	protected OpenDSSWireDataParser wireDataParser = null;
	protected OpenDSSLineGeometryParser lineGeometryParser = null;
	protected OpenDSSTimeSeriesData timeSeriesData = null;
	protected OpenDSSLoadShapeParser loadShapeParser = null;
	protected OpenDSSTemperatureShapeParser temperatureShapeParser = null;
	private boolean regControlEnabled = true;
	private double minLineSeriesImpedancePu = 1.0E-7;


	boolean debug = false;

    public OpenDSSDataParser(){
	this(true);
    }

	public static OpenDSSStaticDataParser forStaticNetwork() {
		return new OpenDSSStaticDataParser();
	}

	public static OpenDSSDynamicDataParser forDynamicNetwork() {
		return new OpenDSSDynamicDataParser();
	}

	protected OpenDSSDataParser(boolean initializeDynamicNetwork){
	//create and initialize the distribution network model
	if(initializeDynamicNetwork && this.distNet == null){
			 this.distNet = ThreePhaseObjectFactory.create3PhaseDStabNetwork();
			 this.distNet.setNetworkType(NetworkType.DISTRIBUTION);
		}

	this.lineCodeParser = new  OpenDSSLineCodeParser (this);
	this.lineParser = new  OpenDSSLineParser (this);
	this.loadParser = new  OpenDSSLoadParser (this);
	this.generatorParser = new OpenDSSGeneratorParser(this);
	this.pvSystemParser = new OpenDSSPVSystemParser(this);
	this.storageParser = new OpenDSSStorageParser(this);
	this.invControlParser = new OpenDSSInvControlParser(this);
	this.xyCurveParser = new OpenDSSXYCurveParser(this);
	this.capParser =  new  OpenDSSCapacitorParser (this);
	this.xfrParser =  new  OpenDSSTransformerParser (this);
	this.regulatorParser = new  OpenDSSRegulatorParser(this);
	this.wireDataParser = new OpenDSSWireDataParser(this);
	this.lineGeometryParser = new OpenDSSLineGeometryParser(this);
	this.timeSeriesData = new OpenDSSTimeSeriesData();
	this.loadShapeParser = new OpenDSSLoadShapeParser(this.timeSeriesData);
	this.temperatureShapeParser = new OpenDSSTemperatureShapeParser(this.timeSeriesData);
    }

    public void setDebugMode(boolean enableDebug){
	this.debug = enableDebug;
    }

    public void setMinLineSeriesImpedancePu(double minLineSeriesImpedancePu) {
	this.minLineSeriesImpedancePu = minLineSeriesImpedancePu;
    }

	public Hashtable<String, LineConfiguration> getLineConfigTable() {
		if(lineConfigTable == null){
			lineConfigTable = new Hashtable<>();
		}
		return lineConfigTable;
	}

	public void setLineConfigTable(Hashtable<String, LineConfiguration> lineConfigTable) {
		this.lineConfigTable = lineConfigTable;
	}

	public Hashtable<String, OpenDSSWireData> getWireDataTable() {
		if(wireDataTable == null){
			wireDataTable = new Hashtable<>();
		}
		return wireDataTable;
	}

	public void setDistNetwork( DStabNetwork3Phase distNet){
		if(distNet != null){
			 this.distNet = distNet;
		}

	}

	public boolean isStaticNetworkMode() {
		return false;
	}

	public Static3PNetwork getStaticNetwork() {
		if(this.staticNet == null) {
			this.staticNet = ThreePhaseObjectFactory.createStatic3PhaseNetwork();
			this.staticNet.setNetworkType(NetworkType.DISTRIBUTION);
		}
		return this.staticNet;
	}

	public boolean hasDistNetwork() {
		return this.distNet != null;
	}

	public Static3PBus getOrCreateStaticBus(String busId) {
		Static3PBus bus = getStaticNetwork().getBus(busId);
		if(bus == null) {
			bus = ThreePhaseObjectFactory.createStatic3PBus(busId, getStaticNetwork());
		}
		return bus;
	}

	public double getNetworkBaseKva() {
		return isStaticNetworkMode() ? getStaticNetwork().getBaseKva() : getDistNetwork().getBaseKva();
	}

	public double getNetworkBaseMva() {
		return isStaticNetworkMode() ? getStaticNetwork().getBaseMva() : getDistNetwork().getBaseMva();
	}

	@SuppressWarnings("rawtypes")
	private BaseAclfNetwork activeAclfNetwork() {
		return isStaticNetworkMode() ? getStaticNetwork() : getDistNetwork();
	}

	public DStabNetwork3Phase getDistNetwork(){
		if(this.distNet == null){
			 this.distNet = ThreePhaseObjectFactory.create3PhaseDStabNetwork();
			 this.distNet.setNetworkType(NetworkType.DISTRIBUTION);
		}
		return this.distNet;
	}



	public OpenDSSLineCodeParser getLineCodeParser() {
		return lineCodeParser;
	}

	public OpenDSSLineParser getLineParser() {
		return lineParser;
	}

	public OpenDSSLoadParser getLoadParser() {
		return loadParser;
	}

	public OpenDSSGeneratorParser getGeneratorParser() {
		return generatorParser;
	}

	public OpenDSSPVSystemParser getPVSystemParser() {
		return pvSystemParser;
	}

	public OpenDSSStorageParser getStorageParser() {
		return storageParser;
	}

	public OpenDSSInvControlParser getInvControlParser() {
		return invControlParser;
	}

	public OpenDSSXYCurveParser getXYCurveParser() {
		return xyCurveParser;
	}

	public OpenDSSTimeSeriesData getTimeSeriesData() {
		return timeSeriesData;
	}

	public OpenDSSLoadShapeParser getLoadShapeParser() {
		return loadShapeParser;
	}

	public OpenDSSTemperatureShapeParser getTemperatureShapeParser() {
		return temperatureShapeParser;
	}

	public OpenDSSTransformerParser getXfrParser() {
		return xfrParser;
	}

	public OpenDSSCapacitorParser getCapacitorParser() {
		return capParser;
	}

	public OpenDSSRegulatorParser getRegulatorParser() {
		return regulatorParser;
	}

	public List<RegulatorControlData> getRegulatorControls() {
		return this.regulatorParser.toRegulatorControlData();
	}

	public List<CapacitorControlData> getCapacitorControls() {
		return this.capParser.toCapacitorControlData();
	}

	public List<InverterControlData> getInverterControls() {
		return this.timeSeriesData.getInverterControls();
	}

	public OpenDSSWireDataParser getWireDataParser() {
		return wireDataParser;
	}

	public OpenDSSLineGeometryParser getLineGeometryParser() {
		return lineGeometryParser;
	}

	public void setRegControlEnabled(boolean regControlEnabled) {
		this.regControlEnabled = regControlEnabled;
	}

	public boolean isRegControlEnabled() {
		return this.regControlEnabled;
	}

	public boolean parseFeederData(String folderPath,String feederFile){

	 boolean no_error = true;

	 //parse the master file, first create an network object as well as the sourceBus

		 String str ="", nextLine = "";
	     int lineCnt = 0;
	     boolean useLastLineString = false;
	     boolean inBlockComment = false;

	     List<String> redirectFiles  = new ArrayList<>();

	 if(!feederFile.equals("")){
		 try {
			    String fullFilePath = folderPath+"/"+feederFile;
				    final File file = new File(fullFilePath);
				final InputStream stream = new FileInputStream(file);
				final BufferedReader din = new BufferedReader(new InputStreamReader(stream));
				IFileReader reader = new ODMTextFileReader(din);

				ODMLogger.getLogger().info("Start to parse feeder file and create the parser object # " + fullFilePath);

			do {
				if(useLastLineString){
					str = nextLine;
					useLastLineString = false;
				}
				else{
					str = reader.readLine();
		                lineCnt++;
				}
				if (this.debug) {
							System.out.println("Parsing: "+str);
						}
			if (str != null && !str.trim().equals("")) {
				str = str.trim();
				if(inBlockComment){
					inBlockComment = !str.contains("*/");
				}
				else if(str.startsWith("/*")){
					inBlockComment = !str.contains("*/");
				}
				else if(str.startsWith("!") || str.startsWith("//")){
					//bypass the comments
				}
				else if(str.startsWith("New")||str.startsWith("new")){

					//Consider in-line comment using !
					if(str.indexOf("!")>0){
						str = str.substring(0, str.indexOf("!"));
					}

					String[] tempAry = str.split("\\s+");
					if(tempAry[1].contains("object=")||tempAry[1].contains("Object=")){
						tempAry[1] = tempAry[1].substring(7);
					}
					if(tempAry[1].contains("Circuit.") ||tempAry[1].contains("circuit.")){
						LogicalLine logicalLine = collectLogicalContinuationLine(str, reader);
						createSourceBus(logicalLine.logicalLine);
						nextLine = logicalLine.nextLine;
						lineCnt = lineCnt + logicalLine.consumedLineCount;
						useLastLineString = nextLine != null;
					}

					else if(tempAry[1].contains("Linecode.") ||tempAry[1].contains("linecode.")){
						List<String> lineCodeLines = new ArrayList<>();
						LogicalLine logicalLine = collectLogicalContinuationLine(str, reader);
						lineCodeLines.add(logicalLine.logicalLine);
						nextLine = logicalLine.nextLine;
						lineCnt = lineCnt + logicalLine.consumedLineCount;
						useLastLineString = nextLine != null;
						no_error = no_error && this.lineCodeParser.parseLineCodeBlock(lineCodeLines);
					}
					else if(tempAry[1].contains("WireData.") ||tempAry[1].contains("wiredata.")){
						no_error = no_error && this.wireDataParser.parseWireData(str);
					}
					else if(str.toLowerCase().contains("regcontrol.")){
						if(this.regControlEnabled) {
							this.regulatorParser.parseRegControlData(str);
						}
					}
					else if(str.toLowerCase().contains("capcontrol.")){
						this.capParser.parseCapControlData(str);
					}
					else if(str.toLowerCase().contains("invcontrol.")){
						LogicalLine invControlLine = collectLogicalContinuationLine(str, reader);
						lineCnt = lineCnt + invControlLine.consumedLineCount;
						nextLine = invControlLine.nextLine;
						useLastLineString = nextLine != null;
						no_error = no_error && this.invControlParser.parseInvControlData(invControlLine.logicalLine);
					}
					else if(tempAry[1].contains("LineGeometry.") ||tempAry[1].contains("linegeometry.")){
						List<String> geometryLines = new ArrayList<>();
						geometryLines.add(str);
						nextLine = reader.readLine();
						lineCnt++;
						while(nextLine != null && nextLine.trim().startsWith("~")) {
							geometryLines.add(nextLine.trim());
							nextLine = reader.readLine();
							lineCnt++;
						}
						if(nextLine != null) {
							useLastLineString = true;
						}
						no_error = no_error && this.lineGeometryParser.parseLineGeometryBlock(geometryLines);
					}
					else if(tempAry[1].contains("Line.") ||tempAry[1].contains("line.")){
						this.lineParser.parseLineData(str);
					}
					else if(tempAry[1].contains("Reactor.") ||tempAry[1].contains("reactor.")){
						no_error = no_error && parseReactorData(str);
					}
					else if(tempAry[1].contains("Transformer.") ||tempAry[1].contains("transformer.")){
						LogicalLine transformerLine = collectLogicalContinuationLine(str, reader);
						lineCnt = lineCnt + transformerLine.consumedLineCount;
						nextLine = transformerLine.nextLine;
						useLastLineString = transformerLine.nextLine != null;
						String[] legacyWindingLines = legacyWindingTransformerLines(str, transformerLine.logicalLine);
						no_error = no_error && (legacyWindingLines == null
								? this.xfrParser.parseTransformerDataOneLine(transformerLine.logicalLine)
								: this.xfrParser.parseTransformerDataMultiLines(legacyWindingLines));
					}
					else if(tempAry[1].contains("XfmrCode.") ||tempAry[1].contains("xfmrcode.")){
						no_error = no_error && this.xfrParser.parseXfmrCodeData(str);
					}
					else if(tempAry[1].contains("LoadShape.") ||tempAry[1].contains("loadshape.")){
						LogicalLine loadShapeLine = collectLogicalContinuationLine(str, reader);
						lineCnt = lineCnt + loadShapeLine.consumedLineCount;
						nextLine = loadShapeLine.nextLine;
						useLastLineString = nextLine != null;
						no_error = no_error && this.loadShapeParser.parseLoadShape(loadShapeLine.logicalLine,
								folderPath, feederFile, lineCnt);
					}
					else if(tempAry[1].contains("TShape.") ||tempAry[1].contains("tshape.")){
						LogicalLine temperatureShapeLine = collectLogicalContinuationLine(str, reader);
						lineCnt = lineCnt + temperatureShapeLine.consumedLineCount;
						nextLine = temperatureShapeLine.nextLine;
						useLastLineString = nextLine != null;
						no_error = no_error && this.temperatureShapeParser.parseTemperatureShape(
								temperatureShapeLine.logicalLine, feederFile, lineCnt);
					}
					else if(tempAry[1].contains("XYCurve.") ||tempAry[1].contains("xycurve.")){
						LogicalLine xyCurveLine = collectLogicalContinuationLine(str, reader);
						lineCnt = lineCnt + xyCurveLine.consumedLineCount;
						nextLine = xyCurveLine.nextLine;
						useLastLineString = nextLine != null;
						no_error = no_error && this.xyCurveParser.parseXYCurve(xyCurveLine.logicalLine);
					}
					else if(tempAry[1].contains("Load.") ||tempAry[1].contains("load.")){
						String loadStr = str;
						String[] nextStrAry = getNextDataInputString(reader);
						if(nextStrAry[0]!=null){
							nextLine = nextStrAry[0].trim();
							lineCnt = lineCnt + Integer.valueOf(nextStrAry[1]);
							while(nextLine.startsWith("~")){
								loadStr = loadStr + " " + nextLine;
								nextStrAry = getNextDataInputString(reader);
								if(nextStrAry[0]==null){
									nextLine = null;
									break;
								}
								nextLine = nextStrAry[0].trim();
								lineCnt = lineCnt + Integer.valueOf(nextStrAry[1]);
							}
							if(nextLine!=null){
								useLastLineString = true;
							}
						}
						this.loadParser.parseLoadData(loadStr);
					}
					else if(tempAry[1].contains("Generator.") ||tempAry[1].contains("generator.")){
						LogicalLine generatorLine = collectLogicalContinuationLine(str, reader);
						lineCnt = lineCnt + generatorLine.consumedLineCount;
						nextLine = generatorLine.nextLine;
						useLastLineString = nextLine != null;
						no_error = no_error && this.generatorParser.parseGeneratorData(generatorLine.logicalLine,
								feederFile, lineCnt);
					}
					else if(tempAry[1].contains("PVSystem.") ||tempAry[1].contains("pvsystem.")){
						LogicalLine pvSystemLine = collectLogicalContinuationLine(str, reader);
						lineCnt = lineCnt + pvSystemLine.consumedLineCount;
						nextLine = pvSystemLine.nextLine;
						useLastLineString = nextLine != null;
						no_error = no_error && this.pvSystemParser.parsePVSystemData(pvSystemLine.logicalLine,
								feederFile, lineCnt);
					}
					else if(tempAry[1].contains("Storage.") ||tempAry[1].contains("storage.")){
						LogicalLine storageLine = collectLogicalContinuationLine(str, reader);
						lineCnt = lineCnt + storageLine.consumedLineCount;
						nextLine = storageLine.nextLine;
						useLastLineString = nextLine != null;
						no_error = no_error && this.storageParser.parseStorageData(storageLine.logicalLine,
								feederFile, lineCnt);
					}
                                else if(tempAry[1].contains("Capacitor.") ||tempAry[1].contains("capacitor.")){
						this.capParser.parseCapDataString(str);
					}

                                else{
	ODMLogger.getLogger().severe("Non-supported object for line # "+str);
                                }
				}
				else if(str.startsWith("redirect")||str.startsWith("Redirect")){
					ODMLogger.getLogger().info(str);
					String redictFileName = str.split("\\s+")[1];
					if(redictFileName.toLowerCase().contains("linecode")){
						no_error=this.lineCodeParser.parseLineCodeFile(folderPath+"/"+redictFileName);
					}
					else{
						no_error = no_error&&parseFile(folderPath,redictFileName);
					}
				}
				else if(str.toLowerCase().startsWith("transformer.") && str.toLowerCase().contains(".taps=")){
					no_error = no_error && this.xfrParser.parseTransformerTapData(str);
				}
				else if(isSupportedGeneratorPropertyLine(str)){
					no_error = no_error && this.generatorParser.parseGeneratorPropertyData(str);
				}
				else if(isSupportedLoadPropertyLine(str)){
					no_error = no_error && this.loadParser.parseLoadPropertyData(str);
				}
				else{
					ODMLogger.getLogger().severe("Non-supported syntax/data model in line # "+lineCnt+"  :\n "+str);
				}
			}
			}while(str!=null);
		 } catch (Exception e) {
			    ODMLogger.getLogger().severe("processing line #"+str);
				ODMLogger.getLogger().severe(e.toString());

				e.printStackTrace();
				return false;
		}// end of try
	  } // end of if file name is not empty


         // we should separate the network initialization from the importing data stage  to provide more flexibility to users
	 boolean initFlag= true;//initNetwork();

	 no_error=no_error&initFlag;

         return no_error;
     }

     private void createSourceBus(String circuitStr) throws Exception {
	 String sourceBusId = "sourcebus";
	 double basekv = 0.0;
	 double voltPu = 1.0;
	 double r1 = 0.0;
	 double x1 = 0.0;
	 double r0 = 0.0;
	 double x0 = 0.0;

	 String allCircuitData = circuitStr;
	 String[] tokens = splitOutsideLists(allCircuitData);
	 for(String token : tokens) {
		 String lowerToken = token.toLowerCase();
		 if(lowerToken.contains("circuit.")) {
			 String circuitId = token.substring(token.indexOf(".") + 1);
			 if(isStaticNetworkMode()) {
				 this.getStaticNetwork().setId(circuitId);
			 }
			 else {
				 this.getDistNetwork().setId(circuitId);
			 }
		 }
		 else if(lowerToken.startsWith("bus1=")) {
			 sourceBusId = token.substring(token.indexOf("=") + 1).toLowerCase();
		 }
		 else if(lowerToken.startsWith("basekv=")) {
			 basekv = Double.valueOf(token.substring(token.indexOf("=") + 1));
		 }
		 else if(lowerToken.startsWith("pu=")) {
			 voltPu = Double.valueOf(token.substring(token.indexOf("=") + 1));
		 }
		 else if(lowerToken.startsWith("r1=")) {
			 r1 = parseOpenDssNumber(token.substring(token.indexOf("=") + 1));
		 }
		 else if(lowerToken.startsWith("x1=")) {
			 x1 = parseOpenDssNumber(token.substring(token.indexOf("=") + 1));
		 }
		 else if(lowerToken.startsWith("r0=")) {
			 r0 = parseOpenDssNumber(token.substring(token.indexOf("=") + 1));
		 }
		 else if(lowerToken.startsWith("x0=")) {
			 x0 = parseOpenDssNumber(token.substring(token.indexOf("=") + 1));
		 }
	 }

	 if(basekv <= 0.0) {
		 basekv = 115.0;
	 }

	 String prefixedSourceBusId = this.busIdPrefix + sourceBusId;
	 String idealSourceBusId = prefixedSourceBusId + "_vsource";
	 if(isStaticNetworkMode()) {
		 Static3PBus sourceBus = getOrCreateStaticBus(prefixedSourceBusId);
		 sourceBus.setBaseVoltage(basekv, UnitType.kV);
		 Complex z1 = new Complex(r1, x1);
		 Complex z0 = new Complex(r0, x0);
		 if(z1.abs() > 0.0 || z0.abs() > 0.0) {
			 Static3PBus idealSourceBus = getOrCreateStaticBus(idealSourceBusId);
			 idealSourceBus.setGenCode(AclfGenCode.SWING);
			 idealSourceBus.setBaseVoltage(basekv, UnitType.kV);
			 idealSourceBus.setVoltageMag(voltPu);

			 sourceBus.setGenCode(AclfGenCode.NON_GEN);
			 Static3PBranch sourceBranch = ThreePhaseObjectFactory.createStatic3PBranch(idealSourceBusId,
					 prefixedSourceBusId, "vsource", this.staticNet);
			 sourceBranch.setName(this.busIdPrefix + "vsource_" + sourceBusId);
			 sourceBranch.setBranchCode(AclfBranchCode.LINE);
			 ((IBranch3Phase) sourceBranch).setPhaseCode(PhaseCode.ABC);
			 sourceBranch.setZabc(sourceSequenceImpedanceToZabc(z1, z0));
		 }
		 else {
			 sourceBus.setGenCode(AclfGenCode.SWING);
			 sourceBus.setVoltageMag(voltPu);
		 }
		 return;
	 }
	 DStab3PBus sourceBus = this.distNet.getBus(prefixedSourceBusId);
	 if(sourceBus == null) {
		 sourceBus = ThreePhaseObjectFactory.create3PDStabBus(prefixedSourceBusId, distNet);
	 }
	 sourceBus.setBaseVoltage(basekv, UnitType.kV);
	 Complex z1 = new Complex(r1, x1);
	 Complex z0 = new Complex(r0, x0);
	 if(z1.abs() > 0.0 || z0.abs() > 0.0) {
		 DStab3PBus idealSourceBus = this.distNet.getBus(idealSourceBusId);
		 if(idealSourceBus == null) {
			 idealSourceBus = ThreePhaseObjectFactory.create3PDStabBus(idealSourceBusId, distNet);
		 }
		 idealSourceBus.setGenCode(AclfGenCode.SWING);
		 idealSourceBus.setBaseVoltage(basekv, UnitType.kV);
		 idealSourceBus.setVoltageMag(voltPu);

		 sourceBus.setGenCode(AclfGenCode.NON_GEN);
		 DStab3PBranch sourceBranch = ThreePhaseObjectFactory.create3PBranch(idealSourceBusId,
				 prefixedSourceBusId, "vsource", this.distNet);
		 sourceBranch.setName(this.busIdPrefix + "vsource_" + sourceBusId);
		 sourceBranch.setBranchCode(AclfBranchCode.LINE);
		 sourceBranch.setPhaseCode(PhaseCode.ABC);
		 sourceBranch.setZabc(sourceSequenceImpedanceToZabc(z1, z0));
	 }
	 else {
		 sourceBus.setGenCode(AclfGenCode.SWING);
		 sourceBus.setVoltageMag(voltPu);
	 }
     }

     private static Complex3x3 sourceSequenceImpedanceToZabc(Complex z1, Complex z0) {
	 Complex self = z0.add(z1.multiply(2.0)).divide(3.0);
	 Complex mutual = z0.subtract(z1).divide(3.0);
	 Complex3x3 zabc = new Complex3x3();
	 zabc.aa = self;
	 zabc.bb = self;
	 zabc.cc = self;
	 zabc.ab = mutual;
	 zabc.ac = mutual;
	 zabc.ba = mutual;
	 zabc.bc = mutual;
	 zabc.ca = mutual;
	 zabc.cb = mutual;
	 return zabc;
     }

     private boolean parseFile(String folderPath, String fileName){

	 boolean no_error = true;
	 String str ="", nextLine = "";
	     int lineCnt = 0;
	     boolean useLastLineString = true;
	     boolean inBlockComment = false;

	     List<String> redirectFiles  = new ArrayList<>();

	 if(!fileName.equals("")){
		 try {
			    String fullFilePath = folderPath+"/"+fileName;
				final File file = new File(fullFilePath);
				final InputStream stream = new FileInputStream(file);
				final BufferedReader din = new BufferedReader(new InputStreamReader(stream));
				IFileReader reader = new ODMTextFileReader(din);

				ODMLogger.getLogger().info("Start to parse file: " + fullFilePath);

			do {
				if(useLastLineString){
					str = nextLine;
					useLastLineString =false;
				}
				else{
					str = reader.readLine();
		                lineCnt++;
				}
				if(this.debug) {
							System.out.println("Parsing :" +str);
						}
			if (str != null && !str.trim().equals("")) {
				str = str.trim();
				if(inBlockComment){
					inBlockComment = !str.contains("*/");
				}
				else if(str.startsWith("/*")){
					inBlockComment = !str.contains("*/");
				}
				else if(str.startsWith("!") || str.startsWith("//")){
					//bypass the comment
				}
				else if(str.startsWith("New")||str.startsWith("new")){
					String[] tempAry = str.split("\\s+");
					if(tempAry[1].contains("object=")||tempAry[1].contains("Object=")){
						tempAry[1] = tempAry[1].substring(7);
					}
					if(tempAry[1].contains("Circuit.") ||tempAry[1].contains("circuit.")){
						LogicalLine logicalLine = collectLogicalContinuationLine(str, reader);
						createSourceBus(logicalLine.logicalLine);
						nextLine = logicalLine.nextLine;
						lineCnt = lineCnt + logicalLine.consumedLineCount;
						useLastLineString = nextLine != null;
					}

					else if(tempAry[1].contains("Linecode.") ||tempAry[1].contains("linecode.")){
						List<String> lineCodeLines = new ArrayList<>();
						LogicalLine logicalLine = collectLogicalContinuationLine(str, reader);
						lineCodeLines.add(logicalLine.logicalLine);
						nextLine = logicalLine.nextLine;
						lineCnt = lineCnt + logicalLine.consumedLineCount;
						useLastLineString = nextLine != null;
						no_error = no_error && this.lineCodeParser.parseLineCodeBlock(lineCodeLines);
					}
					else if(tempAry[1].contains("WireData.") ||tempAry[1].contains("wiredata.")){
						no_error = no_error && this.wireDataParser.parseWireData(str);
					}
					else if(str.toLowerCase().contains("regcontrol.")){
						if(this.regControlEnabled) {
							this.regulatorParser.parseRegControlData(str);
						}
					}
					else if(str.toLowerCase().contains("capcontrol.")){
						this.capParser.parseCapControlData(str);
					}
					else if(str.toLowerCase().contains("invcontrol.")){
						LogicalLine invControlLine = collectLogicalContinuationLine(str, reader);
						lineCnt = lineCnt + invControlLine.consumedLineCount;
						nextLine = invControlLine.nextLine;
						useLastLineString = nextLine != null;
						no_error = no_error && this.invControlParser.parseInvControlData(invControlLine.logicalLine);
					}
					else if(tempAry[1].contains("LineGeometry.") ||tempAry[1].contains("linegeometry.")){
						List<String> geometryLines = new ArrayList<>();
						geometryLines.add(str);
						nextLine = reader.readLine();
						lineCnt++;
						while(nextLine != null && nextLine.trim().startsWith("~")) {
							geometryLines.add(nextLine.trim());
							nextLine = reader.readLine();
							lineCnt++;
						}
						if(nextLine != null) {
							useLastLineString = true;
						}
						no_error = no_error && this.lineGeometryParser.parseLineGeometryBlock(geometryLines);
					}
					else if(tempAry[1].contains("Line.") ||tempAry[1].contains("line.")){
						this.lineParser.parseLineData(str);
					}
					else if(tempAry[1].contains("Reactor.") ||tempAry[1].contains("reactor.")){
						no_error = no_error && parseReactorData(str);
					}
					else if(tempAry[1].contains("Transformer.") ||tempAry[1].contains("transformer.")){
						LogicalLine transformerLine = collectLogicalContinuationLine(str, reader);
						lineCnt = lineCnt + transformerLine.consumedLineCount;
						nextLine = transformerLine.nextLine;
						useLastLineString = transformerLine.nextLine != null;
						String[] legacyWindingLines = legacyWindingTransformerLines(str, transformerLine.logicalLine);
						no_error = no_error && (legacyWindingLines == null
								? this.xfrParser.parseTransformerDataOneLine(transformerLine.logicalLine)
								: this.xfrParser.parseTransformerDataMultiLines(legacyWindingLines));
					}
					else if(tempAry[1].contains("XfmrCode.") ||tempAry[1].contains("xfmrcode.")){
						no_error = no_error && this.xfrParser.parseXfmrCodeData(str);
					}
					else if(tempAry[1].contains("LoadShape.") ||tempAry[1].contains("loadshape.")){
						LogicalLine loadShapeLine = collectLogicalContinuationLine(str, reader);
						lineCnt = lineCnt + loadShapeLine.consumedLineCount;
						nextLine = loadShapeLine.nextLine;
						useLastLineString = nextLine != null;
						no_error = no_error && this.loadShapeParser.parseLoadShape(loadShapeLine.logicalLine,
								folderPath, fileName, lineCnt);
					}
					else if(tempAry[1].contains("TShape.") ||tempAry[1].contains("tshape.")){
						LogicalLine temperatureShapeLine = collectLogicalContinuationLine(str, reader);
						lineCnt = lineCnt + temperatureShapeLine.consumedLineCount;
						nextLine = temperatureShapeLine.nextLine;
						useLastLineString = nextLine != null;
						no_error = no_error && this.temperatureShapeParser.parseTemperatureShape(
								temperatureShapeLine.logicalLine, fileName, lineCnt);
					}
					else if(tempAry[1].contains("XYCurve.") ||tempAry[1].contains("xycurve.")){
						LogicalLine xyCurveLine = collectLogicalContinuationLine(str, reader);
						lineCnt = lineCnt + xyCurveLine.consumedLineCount;
						nextLine = xyCurveLine.nextLine;
						useLastLineString = nextLine != null;
						no_error = no_error && this.xyCurveParser.parseXYCurve(xyCurveLine.logicalLine);
					}
					else if(tempAry[1].contains("Load.") ||tempAry[1].contains("load.")){
						String loadStr = str;
						String[] nextStrAry = getNextDataInputString(reader);
						if(nextStrAry[0]!=null){
							nextLine = nextStrAry[0].trim();
							lineCnt = lineCnt + Integer.valueOf(nextStrAry[1]);
							while(nextLine.startsWith("~")){
								loadStr = loadStr + " " + nextLine;
								nextStrAry = getNextDataInputString(reader);
								if(nextStrAry[0]==null){
									nextLine = null;
									break;
								}
								nextLine = nextStrAry[0].trim();
								lineCnt = lineCnt + Integer.valueOf(nextStrAry[1]);
							}
							if(nextLine!=null){
								useLastLineString = true;
							}
						}
						this.loadParser.parseLoadData(loadStr);
					}
					else if(tempAry[1].contains("Generator.") ||tempAry[1].contains("generator.")){
						LogicalLine generatorLine = collectLogicalContinuationLine(str, reader);
						lineCnt = lineCnt + generatorLine.consumedLineCount;
						nextLine = generatorLine.nextLine;
						useLastLineString = nextLine != null;
						no_error = no_error && this.generatorParser.parseGeneratorData(generatorLine.logicalLine,
								fileName, lineCnt);
					}
					else if(tempAry[1].contains("PVSystem.") ||tempAry[1].contains("pvsystem.")){
						LogicalLine pvSystemLine = collectLogicalContinuationLine(str, reader);
						lineCnt = lineCnt + pvSystemLine.consumedLineCount;
						nextLine = pvSystemLine.nextLine;
						useLastLineString = nextLine != null;
						no_error = no_error && this.pvSystemParser.parsePVSystemData(pvSystemLine.logicalLine,
								fileName, lineCnt);
					}
					else if(tempAry[1].contains("Storage.") ||tempAry[1].contains("storage.")){
						LogicalLine storageLine = collectLogicalContinuationLine(str, reader);
						lineCnt = lineCnt + storageLine.consumedLineCount;
						nextLine = storageLine.nextLine;
						useLastLineString = nextLine != null;
						no_error = no_error && this.storageParser.parseStorageData(storageLine.logicalLine,
								fileName, lineCnt);
					}
                                else if(tempAry[1].contains("Capacitor.") ||tempAry[1].contains("capacitor.")){
						this.capParser.parseCapDataString(str);
					}

                                else{
	ODMLogger.getLogger().severe("Non-supported object for line # "+str);
                                }
				}
				else if(str.startsWith("redirect")||str.startsWith("Redirect")){
					ODMLogger.getLogger().info(str);
				}
				else if(str.toLowerCase().startsWith("transformer.") && str.toLowerCase().contains(".taps=")){
					no_error = no_error && this.xfrParser.parseTransformerTapData(str);
				}
				else if(isSupportedGeneratorPropertyLine(str)){
					no_error = no_error && this.generatorParser.parseGeneratorPropertyData(str);
				}
				else if(isSupportedLoadPropertyLine(str)){
					no_error = no_error && this.loadParser.parseLoadPropertyData(str);
				}
				else{
					ODMLogger.getLogger().severe("Non-supported syntax/data model in line # "+lineCnt+"  :\n "+str);
				}
			}
			}while(str!=null);

			ODMLogger.getLogger().info("End of parsing file: " + fullFilePath);
		 } catch (Exception e) {

			    ODMLogger.getLogger().severe("processing line #"+str);
				ODMLogger.getLogger().severe(e.toString());

				e.printStackTrace();
				return false;
		}// end of try


	  } // end of if file name is not empty
	 return no_error;
     }


     public boolean initNetwork(){
	 double mvaBase = 10.0; // initial mvaBase
	 boolean no_error = calcVoltageBases() & convertActualValuesToPU(mvaBase);
	 return no_error;
     }
     public boolean calcVoltageBases(){
	 boolean no_error = true;

	 try {
		this.xfrParser.mergeParallelSinglePhaseRegulatorBranches();
	 } catch (Exception e) {
		ODMLogger.getLogger().severe("Failed to merge parallel single-phase transformer regulators: " + e.toString());
		return false;
	}

	 BaseAclfNetwork activeNet = activeAclfNetwork();
	 Queue<BaseAclfBus> onceVisitedBuses = new  LinkedList<>();

		// find the source bus, which is the swing bus for radial feeders;
		for(Object busObj: activeNet.getBusList()){
				BaseAclfBus b = (BaseAclfBus) busObj;
				if(b.isActive() && b.isSwing()){
					onceVisitedBuses.add(b);
					b.setIntFlag(1);
				}
				else{
					b.setIntFlag(0);
					b.setBooleanFlag(false);
				}
		}

		//make sure all internal branches are unvisited
		for(Object branchObj: activeNet.getBranchList()){
			AclfBranch bra = (AclfBranch) branchObj;
			bra.setBooleanFlag(false);
		}

		// perform BFS and set the bus sortNumber
		BFS(onceVisitedBuses);
		deactivateUnvisitedIslands(activeNet);
	 return no_error;
     }


    private void BFS (Queue<BaseAclfBus> onceVisitedBuses){
	int orderNumber = 0;
		//Retrieves and removes the head of this queue, or returns null if this queue is empty.
	    while(!onceVisitedBuses.isEmpty()){
		BaseAclfBus  startingBus = onceVisitedBuses.poll();
			startingBus.setSortNumber(orderNumber++);
			startingBus.setBooleanFlag(true);
			startingBus.setIntFlag(2);

			if(startingBus!=null){
				  for(Branch connectedBra: startingBus.getBranchIterable()){
					  AclfBranch aclfBra = (AclfBranch) connectedBra;
						if(connectedBra.isActive() && !connectedBra.isBooleanFlag()){
								Bus findBus = connectedBra.getOppositeBus(startingBus);

								//update status
								connectedBra.setBooleanFlag(true);

								//for first time visited buses
								if(findBus.getIntFlag()==0){
									findBus.setIntFlag(1);
									onceVisitedBuses.add((BaseAclfBus) findBus);

									//update the L-L basekV
									if(aclfBra.isLine()){
										findBus.setBaseVoltage(startingBus.getBaseVoltage());
									}
									else{
										//check from/ to relationship before using the turn ratio info
										if(aclfBra.getFromBus().getId().equals(startingBus.getId())) {
											findBus.setBaseVoltage(startingBus.getBaseVoltage()*aclfBra.getToTurnRatio()/aclfBra.getFromTurnRatio());
										} else {
											findBus.setBaseVoltage(startingBus.getBaseVoltage()*aclfBra.getFromTurnRatio()/aclfBra.getToTurnRatio());
										}
									}

								}
						}
				 }

			}

	      }
	}

	private void deactivateUnvisitedIslands(BaseAclfNetwork activeNet) {
	int inactiveBusCount = 0;
	int inactiveBranchCount = 0;
		for(Object busObj: activeNet.getBusList()) {
			BaseAclfBus bus = (BaseAclfBus) busObj;
			if(bus.isActive() && bus.getIntFlag() != 2) {
				bus.setStatus(false);
				inactiveBusCount++;
			}
		}
		for(Object branchObj: activeNet.getBranchList()) {
			AclfBranch branch = (AclfBranch) branchObj;
			if(branch.isActive()
					&& (!branch.getFromBus().isActive() || !branch.getToBus().isActive())) {
				branch.setStatus(false);
				inactiveBranchCount++;
			}
		}
		if(inactiveBusCount > 0 || inactiveBranchCount > 0) {
			ODMLogger.getLogger().info("Turned off OpenDSS island objects not connected to an active swing bus: buses="
					+ inactiveBusCount + ", branches=" + inactiveBranchCount);
		}
	}

     public boolean convertActualValuesToPU(double mvaBase){

	 if(mvaBase>0) {
		 if(mvaBase>20){
			 ODMLogger.getLogger().warning("The input mvaBase is beyond the normal range of [5, 20] MVA, input mvabase = "+mvaBase);
		 }
		 activeAclfNetwork().setBaseKva(mvaBase*1000.0);
	 }
	 else{
		 ODMLogger.getLogger().severe("The input mvabase <= 0. mvabase = 1.0 MVA will be used");
		 mvaBase = 1.0;
		 activeAclfNetwork().setBaseKva(mvaBase*1000.0);
	 }

	 boolean no_error = convertBranchZYMatrixToPU()&&convertLoadCapacitorToPU();
	 if(no_error) {
		 timeSeriesData.refreshNetworkBaseStates();
	 }

	 return no_error;
     }

     private boolean convertBranchZYMatrixToPU(){
          boolean no_error = true;

          BaseAclfNetwork activeNet = activeAclfNetwork();
          double mvabase = activeNet.getBaseMva();
          double vBase = 0.0, zBase = 0.0;
          for(Object branchObj: activeNet.getBranchList()){
	  AclfBranch bra = (AclfBranch) branchObj;
	  IBranch3Phase bra3Phase = (IBranch3Phase) bra;
	  if(!bra.isActive()) {
		  continue;
	  }

	  if(bra3Phase.hasExplicitYabc()) {
		  convertExplicitBranchYToPU(bra, bra3Phase, mvabase);
	  }
	  else if(bra.isLine()){
		  vBase = bra.getFromBus().getBaseVoltage();
		  zBase = vBase*vBase*1.0E-6/mvabase;

		  bra3Phase.setZabc(bra3Phase.getZabc().multiply(1.0/zBase));
		  enforceMinimumLineSeriesImpedancePu(bra, bra3Phase);
		  if(bra3Phase.getFromShuntYabc() != null) {
			  bra3Phase.setFromShuntYabc(bra3Phase.getFromShuntYabc().multiply(zBase));
		  }
		  if(bra3Phase.getToShuntYabc() != null) {
			  bra3Phase.setToShuntYabc(bra3Phase.getToShuntYabc().multiply(zBase));
		  }
	  }
	  else if(bra.isXfr() || bra.isPSXfr()){
		  // convert the Z to high voltage side
		  vBase = bra.getFromBus().getBaseVoltage()>=bra.getToBus().getBaseVoltage()?
				  bra.getFromBus().getBaseVoltage():bra.getToBus().getBaseVoltage();
		  zBase = vBase*vBase*1.0E-6/mvabase;
		  ((AcscBranch) bra).setZ(bra.getAdjustedZ().divide(zBase));
		  if(bra3Phase.getZabc() != null) {
			  bra3Phase.setZabc(bra3Phase.getZabc().multiply(1.0/zBase));
		  }

		  // convert the turn ratios
		  double vllfactor = 1.0;
		  if(bra3Phase.getPhaseCode()!=PhaseCode.ABC){
			  vllfactor = Math.sqrt(3);
		  }
		  bra.setFromTurnRatio(bra.getFromTurnRatio()*vllfactor /bra.getFromBus().getBaseVoltage());
		  bra.setToTurnRatio(bra.getToTurnRatio()*vllfactor/bra.getToBus().getBaseVoltage());
		  convertPhaseTurnRatiosToPU(bra, bra3Phase, vllfactor);
	  }
	  else{
		  ODMLogger.getLogger().severe("Sepcial branch type is not supported, branchId = "+bra.getId());
	  }

          }

	  return no_error;
     }

     private void enforceMinimumLineSeriesImpedancePu(AclfBranch branch, IBranch3Phase branch3P) {
	 Complex3x3 zabc = branch3P.getZabc();
	 double zAbsMax = zabc.absMax();
	 if(this.minLineSeriesImpedancePu <= 0.0 || zAbsMax >= this.minLineSeriesImpedancePu) {
		 return;
	 }
	 if(zAbsMax > 0.0) {
		 branch3P.setZabc(zabc.multiply(this.minLineSeriesImpedancePu / zAbsMax));
	 }
	 else {
		 branch3P.setZabc(activePhaseDiagonalImpedance(branch3P.getPhaseCode(),
				 new Complex(this.minLineSeriesImpedancePu, 0.0)));
	 }
	 if(this.debug) {
		 ODMLogger.getLogger().info("Replaced near-zero OpenDSS line impedance with "
				 + this.minLineSeriesImpedancePu + " pu floor: branch="
				 + branch.getId() + ", name=" + branch.getName());
	 }
     }

     private Complex3x3 activePhaseDiagonalImpedance(PhaseCode phaseCode, Complex impedance) {
	 Complex zero = new Complex(0.0);
	 Complex3x3 zabc = new Complex3x3();
	 zabc.aa = zero;
	 zabc.ab = zero;
	 zabc.ac = zero;
	 zabc.ba = zero;
	 zabc.bb = zero;
	 zabc.bc = zero;
	 zabc.ca = zero;
	 zabc.cb = zero;
	 zabc.cc = zero;

	 if(phaseCode == PhaseCode.A || phaseCode == PhaseCode.AB
			 || phaseCode == PhaseCode.AC || phaseCode == PhaseCode.ABC) {
		 zabc.aa = impedance;
	 }
	 if(phaseCode == PhaseCode.B || phaseCode == PhaseCode.AB
			 || phaseCode == PhaseCode.BC || phaseCode == PhaseCode.ABC) {
		 zabc.bb = impedance;
	 }
	 if(phaseCode == PhaseCode.C || phaseCode == PhaseCode.AC
			 || phaseCode == PhaseCode.BC || phaseCode == PhaseCode.ABC) {
		 zabc.cc = impedance;
	 }
	 return zabc;
     }

     private void convertExplicitBranchYToPU(AclfBranch branch, IBranch3Phase branch3P, double mvabase) {
	 double fromBaseKV = branch.getFromBus().getBaseVoltage()*1.0E-3;
	 double toBaseKV = branch.getToBus().getBaseVoltage()*1.0E-3;
	 branch3P.setExplicitYabc(
			 branch3P.getYffabc().multiply(fromBaseKV*fromBaseKV/mvabase),
			 branch3P.getYftabc().multiply(fromBaseKV*toBaseKV/mvabase),
			 branch3P.getYtfabc().multiply(toBaseKV*fromBaseKV/mvabase),
			 branch3P.getYttabc().multiply(toBaseKV*toBaseKV/mvabase));

	 branch.setFromTurnRatio(branch.getFromTurnRatio()/branch.getFromBus().getBaseVoltage());
	 branch.setToTurnRatio(branch.getToTurnRatio()/branch.getToBus().getBaseVoltage());
     }

     private void convertPhaseTurnRatiosToPU(AclfBranch branch, IBranch3Phase bra3Phase, double vllfactor) {
	 if(!bra3Phase.hasPhaseTurnRatio()) {
		 return;
	 }
	 double[] fromRatios = bra3Phase.getFromTurnRatioABC();
	 double[] toRatios = bra3Phase.getToTurnRatioABC();
	 for(int i = 0; i < 3; i++) {
		 fromRatios[i] = fromRatios[i] * vllfactor / branch.getFromBus().getBaseVoltage();
		 toRatios[i] = toRatios[i] * vllfactor / branch.getToBus().getBaseVoltage();
	 }
	 bra3Phase.setFromTurnRatioABC(fromRatios[0], fromRatios[1], fromRatios[2]);
	 bra3Phase.setToTurnRatioABC(toRatios[0], toRatios[1], toRatios[2]);
     }

     private boolean convertLoadCapacitorToPU(){
         boolean no_error = true;

	     BaseAclfNetwork activeNet = activeAclfNetwork();
	     double baseKVA3P = activeNet.getBaseKva();
	     double baseKVA1P = baseKVA3P/3.0;

	     if(isStaticNetworkMode()) {
		 for(Static3PBus bus : getStaticNetwork().getBusList()) {
			 for(Static3PLoad load : bus.getContributeLoadList()) {
				 convertPhaseLoadToPU(bus, load, baseKVA1P);
			 }
		 }
		 return no_error;
	     }

         for(Object busObj: activeNet.getBusList()){
	 BaseAclfBus bus = (BaseAclfBus) busObj;
	 if(bus instanceof DStab3PBus) {
		 DStab3PBus bus3P = (DStab3PBus) bus;
		 for(DStab1PLoad load : bus3P.getSinglePhaseLoadList()) {
			 convertPhaseLoadToPU(bus, load, baseKVA1P);
		 }
		 for(DStab3PLoad load : bus3P.getThreePhaseLoadList()) {
			 convertPhaseLoadToPU(bus, load, baseKVA1P);
		 }
	 }
	 else {
		 IBus3Phase bus3P = (IBus3Phase) bus;
		 for(AclfLoad3Phase load : bus3P.getPhaseLoadList()){
			 double loadBaseKva = load instanceof DStab1PLoad && !(load instanceof DStab3PLoad)
					 ? baseKVA1P
					 : baseKVA3P;
			 convertPhaseLoadToPU(bus, load, loadBaseKva);
		 }
	 }
         }


	  return no_error;
    }

     private void convertPhaseLoadToPU(BaseAclfBus bus, AclfLoad3Phase load, double loadBaseKva) {
	 double voltageScale = threePhaseConstZVoltageScale(bus, load);
	 load.setLoadCP(load.getLoadCP().divide(loadBaseKva));
	 load.setLoadCI(load.getLoadCI().divide(loadBaseKva));
	 load.setLoadCZ(load.getLoadCZ().divide(loadBaseKva));
	 if(load instanceof DStab1PLoad && !(load instanceof DStab3PLoad)) {
		 return;
	 }
	 if(load instanceof Static3PLoad && isSinglePhase(load.getPhaseCode())) {
		 load.set3PhaseLoad(singlePhaseLoadVector(activeLoadPower(load).multiply(voltageScale), load.getPhaseCode()));
		 return;
	 }
	 if(load instanceof Static3PLoad && load.getLoadConnectionType() == LoadConnectionType.SINGLE_PHASE_DELTA) {
		 load.set3PhaseLoad(singlePhaseDeltaLoadVector(activeLoadPower(load).multiply(voltageScale),
				 load.getPhaseCode()));
		 load.setLoadConnectionType(LoadConnectionType.THREE_PHASE_DELTA);
		 return;
	 }
	 if(load.getInit3PhaseLoad() != null) {
		 load.set3PhaseLoad(load.getInit3PhaseLoad().multiply(voltageScale/loadBaseKva));
	 }
     }

     private static boolean isSinglePhase(PhaseCode phaseCode) {
	 return phaseCode == PhaseCode.A || phaseCode == PhaseCode.B || phaseCode == PhaseCode.C;
     }

     private static Complex activeLoadPower(AclfLoad3Phase load) {
	 if(load.getCode() == AclfLoadCode.CONST_Z) {
		 return load.getLoadCZ();
	 }
	 if(load.getCode() == AclfLoadCode.CONST_I) {
		 return load.getLoadCI();
	 }
	 return load.getLoadCP();
     }

     private static Complex3x1 singlePhaseLoadVector(Complex power, PhaseCode phaseCode) {
	 Complex zero = new Complex(0.0);
	 Complex value = power == null ? zero : power;
	 if(phaseCode == PhaseCode.B) {
		 return new Complex3x1(zero, value, zero);
	 }
	 if(phaseCode == PhaseCode.C) {
		 return new Complex3x1(zero, zero, value);
	 }
	 return new Complex3x1(value, zero, zero);
     }

     private static Complex3x1 singlePhaseDeltaLoadVector(Complex power, PhaseCode phaseCode) {
	 Complex zero = new Complex(0.0);
	 Complex value = power == null ? zero : power;
	 if(phaseCode == PhaseCode.BC) {
		 return new Complex3x1(zero, value, zero);
	 }
	 if(phaseCode == PhaseCode.AC) {
		 return new Complex3x1(zero, zero, value);
	 }
	 return new Complex3x1(value, zero, zero);
     }

     private double threePhaseConstZVoltageScale(BaseAclfBus bus3P, AclfLoad3Phase load3P) {
	 if(load3P.getCode() != AclfLoadCode.CONST_Z || load3P.getNominalKV() <= 0.0) {
		 return 1.0;
	 }

	 double busBaseKVLL = bus3P.getBaseVoltage()*1.0E-3;
	 double ratedKV = load3P.getNominalKV();
	 if(busBaseKVLL <= 0.0 || ratedKV <= 0.0) {
		 return 1.0;
	 }

	 double baseKV = ratedKV < busBaseKVLL*0.75 ? busBaseKVLL/Math.sqrt(3.0) : busBaseKVLL;
	 double scale = baseKV/ratedKV;
	 return scale*scale;
    }

     /**
      * skip all the comment lines (including in-line comments and block comments) to get the next data input line string, so that processing can be performed to check
      * if the next data input string is a valid input or only comments
      *
      * The first return string is the data string, while the second return string is the skip line numbers
      * @param din
      * @param useLastLineString
      * @return String[2]
      * @throws ODMException
      */
     private LogicalLine collectLogicalContinuationLine(String firstLine, IFileReader reader) throws ODMException {
	 String logicalLine = firstLine;
	 String nextDataLine = null;
	 int consumedLineCount = 0;
	 while(true) {
		 String[] nextStrAry = getNextDataInputString(reader);
		 consumedLineCount = consumedLineCount + Integer.valueOf(nextStrAry[1]);
		 if(nextStrAry[0] == null) {
			 break;
		 }
		 consumedLineCount++;
		 nextDataLine = nextStrAry[0].trim();
		 if(!nextDataLine.startsWith("~")) {
			 break;
		 }
		 logicalLine = logicalLine + " " + nextDataLine.substring(1).trim();
		 nextDataLine = null;
	 }
	 return new LogicalLine(logicalLine, nextDataLine, consumedLineCount);
     }

     private static String[] legacyWindingTransformerLines(String firstLine, String logicalLine) {
	 String lower = logicalLine.toLowerCase();
	 if(firstLine.toLowerCase().contains(" wdg=1 ")) {
		 return null;
	 }
	 int wdg1Idx = lower.indexOf(" wdg=1 ");
	 int wdg2Idx = lower.indexOf(" wdg=2 ");
	 if(wdg1Idx < 0 || wdg2Idx <= wdg1Idx) {
		 return null;
	 }
	 String wdg1 = logicalLine.substring(wdg1Idx + 1, wdg2Idx).trim();
	 String wdg2 = logicalLine.substring(wdg2Idx + 1).trim();
	 if(!wdg1.toLowerCase().contains(" bus=") || !wdg2.toLowerCase().contains(" bus=")) {
		 return null;
	 }
	 return new String[] {
			 firstLine,
			 "~ " + wdg1,
			 "~ " + wdg2
	 };
     }

     private boolean parseReactorData(String reactorStr) throws Exception {
	 String reactorName = "";
	 String fromBusId = "";
	 String toBusId = "";
	 int phaseNum = 3;
	 double r = 0.0;
	 double x = 0.0;
	 for(String token : splitOutsideLists(reactorStr.trim().toLowerCase())) {
		 if(token.contains("reactor.")) {
			 reactorName = token.substring(token.indexOf("reactor.") + 8);
		 }
		 else if(token.startsWith("bus1=")) {
			 fromBusId = terminalBusId(token.substring(5));
		 }
		 else if(token.startsWith("bus2=")) {
			 toBusId = terminalBusId(token.substring(5));
		 }
		 else if(token.startsWith("phases=")) {
			 phaseNum = Integer.valueOf(token.substring(7));
		 }
		 else if(token.startsWith("r=")) {
			 r = parseOpenDssNumber(token.substring(2));
		 }
		 else if(token.startsWith("x=")) {
			 x = parseOpenDssNumber(token.substring(2));
		 }
	 }
	 if(fromBusId.equals("") || toBusId.equals("")) {
		 throw new Error("Reactor bus terminals are not defined: " + reactorStr);
	 }
	 fromBusId = this.busIdPrefix + fromBusId;
	 toBusId = this.busIdPrefix + toBusId;
	 AclfBranch reactor;
	 IBranch3Phase reactor3P;
	 if(isStaticNetworkMode()) {
		 this.getOrCreateStaticBus(fromBusId);
		 this.getOrCreateStaticBus(toBusId);
		 Static3PBranch staticReactor = ThreePhaseObjectFactory.createStatic3PBranch(fromBusId, toBusId,
				 reactorName.equals("") ? "1" : reactorName, this.staticNet);
		 reactor = staticReactor;
		 reactor3P = staticReactor;
	 }
	 else {
		 if(this.distNet.getBus(fromBusId) == null) {
			 ThreePhaseObjectFactory.create3PDStabBus(fromBusId, this.distNet);
		 }
		 if(this.distNet.getBus(toBusId) == null) {
			 ThreePhaseObjectFactory.create3PDStabBus(toBusId, this.distNet);
		 }
		 DStab3PBranch dynamicReactor = ThreePhaseObjectFactory.create3PBranch(fromBusId, toBusId,
				 reactorName.equals("") ? "1" : reactorName, this.distNet);
		 reactor = dynamicReactor;
		 reactor3P = dynamicReactor;
	 }
	 reactor.setName(this.busIdPrefix + reactorName);
	 reactor.setBranchCode(AclfBranchCode.LINE);
	 reactor3P.setPhaseCode(phaseNum == 1 ? PhaseCode.A : PhaseCode.ABC);
	 Complex z = new Complex(r, x);
	 Complex zero = new Complex(0.0);
	 Complex3x3 zabc = new Complex3x3();
	 zabc.aa = zero;
	 zabc.ab = zero;
	 zabc.ac = zero;
	 zabc.ba = zero;
	 zabc.bb = zero;
	 zabc.bc = zero;
	 zabc.ca = zero;
	 zabc.cb = zero;
	 zabc.cc = zero;
	 zabc.aa = z;
	 if(phaseNum > 1) {
		 zabc.bb = z;
	 }
	 if(phaseNum > 2) {
		 zabc.cc = z;
	 }
	 reactor3P.setZabc(zabc);
	 return true;
     }

     private static String terminalBusId(String openDssBusId) {
	 int dotIdx = openDssBusId.indexOf(".");
	 return dotIdx < 0 ? openDssBusId : openDssBusId.substring(0, dotIdx);
     }

     private static double parseOpenDssNumber(String value) {
	 String trimmed = value.trim();
	 if(trimmed.startsWith("(") && trimmed.endsWith(")")) {
		 return evaluateRpn(trimmed.substring(1, trimmed.length() - 1));
	 }
	 return Double.valueOf(trimmed).doubleValue();
     }

     private static double evaluateRpn(String expression) {
	 List<Double> stack = new ArrayList<>();
	 for(String token : expression.trim().split("\\s+")) {
		 if(token.equals("+") || token.equals("-") || token.equals("*") || token.equals("/")) {
			 double right = stack.remove(stack.size() - 1);
			 double left = stack.remove(stack.size() - 1);
			 if(token.equals("+")) {
				 stack.add(left + right);
			 }
			 else if(token.equals("-")) {
				 stack.add(left - right);
			 }
			 else if(token.equals("*")) {
				 stack.add(left * right);
			 }
			 else {
				 stack.add(left / right);
			 }
		 }
		 else if(token.equals("sqr")) {
			 double operand = stack.remove(stack.size() - 1);
			 stack.add(operand * operand);
		 }
		 else {
			 stack.add(Double.valueOf(token));
		 }
	 }
	 if(stack.size() != 1) {
		 throw new Error("Unable to evaluate OpenDSS RPN expression: " + expression);
	 }
	 return stack.get(0).doubleValue();
     }

     private static String[] splitOutsideLists(String value) {
	 List<String> tokens = new ArrayList<>();
	 StringBuilder current = new StringBuilder();
	 int bracketDepth = 0;
	 int parenDepth = 0;
	 for (int i = 0; i < value.length(); i++) {
		 char ch = value.charAt(i);
		 if (Character.isWhitespace(ch) && bracketDepth == 0 && parenDepth == 0) {
			 if (current.length() > 0) {
				 tokens.add(current.toString());
				 current.setLength(0);
			 }
			 continue;
		 }
		 if (ch == '[') {
			 bracketDepth++;
		 }
		 else if (ch == ']' && bracketDepth > 0) {
			 bracketDepth--;
		 }
		 else if (ch == '(') {
			 parenDepth++;
		 }
		 else if (ch == ')' && parenDepth > 0) {
			 parenDepth--;
		 }
		 current.append(ch);
	 }
	 if (current.length() > 0) {
		 tokens.add(current.toString());
	 }
	 return tokens.toArray(new String[0]);
     }

     private static class LogicalLine {
	 private final String logicalLine;
	 private final String nextLine;
	 private final int consumedLineCount;

	 private LogicalLine(String logicalLine, String nextLine, int consumedLineCount) {
		 this.logicalLine = logicalLine;
		 this.nextLine = nextLine;
		 this.consumedLineCount = consumedLineCount;
	 }
     }

     private static boolean isSupportedLoadPropertyLine(String str) {
	 String lower = str.toLowerCase().replaceAll("\\s*=\\s*", "=");
	 return lower.startsWith("load.")
			 && (lower.contains(".allocationfactor=")
					 || lower.contains(".daily=")
					 || lower.contains(".yearly=")
					 || lower.contains(".duty=")
					 || lower.contains(".status="));
     }

     private static boolean isSupportedGeneratorPropertyLine(String str) {
	 String lower = str.toLowerCase().replaceAll("\\s*=\\s*", "=");
	 return lower.startsWith("generator.")
			 && (lower.contains(".daily=")
					 || lower.contains(".yearly=")
					 || lower.contains(".duty="));
     }

     private String[] getNextDataInputString(IFileReader reader) throws ODMException{
	 String dataString = null;
	 int skipLineNum = 0;
	 do{
		 dataString =reader.readLine();
		 if(dataString!=null){
			 dataString = dataString.trim().toLowerCase();
			 if(dataString.trim().length()>0){
				 if(dataString.startsWith("!") ||dataString.startsWith("//")){
					 //it is a comment line, skip
					 skipLineNum++;
				 }
				 else if(dataString.startsWith("/*")){
					 //keep search until find the "*/", which denotes the end of the comment block.
					 skipLineNum++;
					 if(!dataString.contains("*/")){

						 do{
							 dataString =reader.readLine();
							 skipLineNum++;

						 if(dataString!=null){

							 if(dataString.contains("*/")){
								 break;
							 }

						 }

						 }while(dataString !=null);
					 }
				 }
				 else{
					 // now we find the next non-comment data string;
					 break;
				 }
			 }
		 }

	 }while(dataString !=null);

	String[] returnStr =  new String[]{dataString,Integer.toString(skipLineNum)};

	return returnStr;

     }

     public DStab3PBranch getBranchByName(String branchName){
	 for(Branch bra: this.getDistNetwork().getBranchList()){
		 if(bra.getName().equals(branchName)){
			 return (DStab3PBranch) bra;
		 }
	 }
	 return null;
     }

     public AclfBranch getThreePhaseBranchByName(String branchName){
	 List<? extends AclfBranch> branches = this.isStaticNetworkMode()
			 ? this.getStaticNetwork().getBranchList()
			 : this.getDistNetwork().getBranchList();
	 for(AclfBranch branch: branches){
		 if(branch.getName().equals(branchName)){
			 return branch;
		 }
	 }
	 return null;
     }

	public String getBusIdPrefix() {
		return busIdPrefix;
	}

	public void setBusIdPrefix(String busIdPrefix) {
		this.busIdPrefix = busIdPrefix;
	}


}
