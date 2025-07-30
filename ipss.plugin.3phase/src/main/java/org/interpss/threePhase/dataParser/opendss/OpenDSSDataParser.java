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

import org.ieee.odm.common.IFileReader;
import org.ieee.odm.common.ODMException;
import org.ieee.odm.common.ODMLogger;
import org.ieee.odm.common.ODMTextFileReader;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.threePhase.basic.LineConfiguration;
import org.interpss.threePhase.basic.dstab.DStab1PLoad;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.basic.dstab.DStab3PLoad;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;
import com.interpss.core.net.NetworkType;
import com.interpss.dstab.DStabBranch;

public class OpenDSSDataParser {

	protected String busIdPrefix = "";
	// Line configuration table
	protected Hashtable<String,LineConfiguration> lineConfigTable = null;

	protected DStabNetwork3Phase distNet = null;
	protected OpenDSSLineCodeParser lineCodeParser = null;
	protected OpenDSSLineParser lineParser = null;
	protected OpenDSSLoadParser loadParser = null;
	protected OpenDSSTransformerParser xfrParser = null;
	protected OpenDSSCapacitorParser capParser = null;
	protected OpenDSSRegulatorParser regulatorParser = null;


	boolean debug = false;

    public OpenDSSDataParser(){
    	//create and initialize the distribution network model
    	if(this.distNet == null){
			 this.distNet = ThreePhaseObjectFactory.create3PhaseDStabNetwork();
			 this.distNet.setNetworkType(NetworkType.DISTRIBUTION);
		}

    	this.lineCodeParser = new  OpenDSSLineCodeParser (this);
    	this.lineParser = new  OpenDSSLineParser (this);
    	this.loadParser = new  OpenDSSLoadParser (this);
    	this.capParser =  new  OpenDSSCapacitorParser (this);
    	this.xfrParser =  new  OpenDSSTransformerParser (this);
    	//TODO tentatively, treat regulator as a fixed tap transformer
    	this.regulatorParser = new  OpenDSSRegulatorParser(this);
    }

    public void setDebugMode(boolean enableDebug){
    	this.debug = enableDebug;
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

	public void setDistNetwork( DStabNetwork3Phase distNet){
		if(distNet != null){
			 this.distNet = distNet;
		}

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

	public OpenDSSTransformerParser getXfrParser() {
		return xfrParser;
	}

	public OpenDSSCapacitorParser getCapacitorParser() {
		return capParser;
	}

	public OpenDSSRegulatorParser getRegulatorParser() {
		return regulatorParser;
	}

	public boolean parseFeederData(String folderPath,String feederFile){

    	 boolean no_error = true;

    	 //parse the master file, first create an network object as well as the sourceBus

		 String str ="", nextLine = "";
	     int lineCnt = 0;
	     boolean useLastLineString = false;

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
    		        		if(str.startsWith("!") || str.startsWith("//")){
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
    		        				//create a network object and add the source bus
    		        				String circuitId = tempAry[1].substring(8);
    		        				this.getDistNetwork().setId(circuitId);

    		        				String sourceBusId = "";
    		        				double basekv = 0.0;
    		        				double volt_pu = 1.0;
    		        				DStab3PBus sourceBus = null;

    		        				if(!str.contains("basekv=")){
    		        				  nextLine  = reader.readLine().trim();
    		        				  lineCnt++;

    		        				  int continueIdx = nextLine.indexOf("~");
    		        				  if(continueIdx==0){
    		        					  nextLine = nextLine.substring(continueIdx+1).trim();
    		        					  String[] tempAry2 = nextLine.split("\\s+");

    		        					  for (String element : tempAry2) {
    		        						  if(element.contains("basekv=")){
    		        							  basekv = Double.valueOf(element.substring(7));
    		        						  }
    		        						  else if(element.contains("Bus1=")){
    		        							  sourceBusId = element.substring(5);
    		        						  }
    		        						  else if(element.contains("pu=")){
    		        							  volt_pu = Double.valueOf(element.substring(3));
    		        						  }
    		        					  }

    		        				  }
    		        				  else{

    		        					  throw new Error("basekv is not defined for the source bus and no contiunation data");
    		        				  }

    		        				  // create the source bus
    		        				  if(sourceBusId.length()>0){
    		        					  if(distNet.getBus(sourceBusId)==null) {
											sourceBus = ThreePhaseObjectFactory.create3PDStabBus(this.busIdPrefix+sourceBusId, distNet);
										}

    		        					  sourceBus.setGenCode(AclfGenCode.SWING);
    		        					  sourceBus.setBaseVoltage(basekv, UnitType.kV);

    		        					  sourceBus.setVoltageMag(volt_pu);
    		        				  }
    		        				  else{
    		        					  throw new Error("source bus name is not properly parse! # "+ nextLine);
    		        				  }
    		        				}
    		        			}

    		        			else if(tempAry[1].contains("Line.") ||tempAry[1].contains("line.")){
    		        				this.lineParser.parseLineData(str);
    		        			}
                                else if(tempAry[1].contains("Transformer.") ||tempAry[1].contains("transformer.")){
                                	String [] xfrStrAry = new String[3];
                                	xfrStrAry[0] = str;

                                	String[] nextStrAry = getNextDataInputString(reader);
                                	if(nextStrAry[0]==null){
                                		break;
                                	}
                                	nextLine = nextStrAry[0].trim();
                                	lineCnt = lineCnt + Integer.valueOf(nextStrAry[1]);

                                	if(nextLine.startsWith("~")){
                                		xfrStrAry[1] = nextLine;
                                		// by default there are three line strings for one transformer definition
                                		nextLine = reader.readLine().trim();
                                    	lineCnt++;
                                    	if(nextLine.startsWith("~")){
                                    		xfrStrAry[2] = nextLine;
                                    	}
                                    	else{
                                    		useLastLineString = true;
                                    	}
                                	}
                                	else{
                                		useLastLineString = true;
                                	}


    		        				if(useLastLineString) { // if true, it means all xfr data is in one line
										this.xfrParser.parseTransformerDataOneLine(str);
									} else { // if true, it means all xfr data is in one line
										this.xfrParser.parseTransformerDataMultiLines(xfrStrAry);
									}
    		        			}
    		        			else if(tempAry[1].contains("Load.") ||tempAry[1].contains("load.")){
    		        				this.loadParser.parseLoadData(str);
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

     private boolean parseFile(String folderPath, String fileName){

    	 boolean no_error = true;
    	 String str ="", nextLine = "";
	     int lineCnt = 0;
	     boolean useLastLineString = true;

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
    		        		if(str.startsWith("!") || str.startsWith("//")){
    		        			//bypass the comment
    		        		}
    		        		else if(str.startsWith("New")||str.startsWith("new")){
    		        			String[] tempAry = str.split("\\s+");
    		        			if(tempAry[1].contains("object=")||tempAry[1].contains("Object=")){
    		        				tempAry[1] = tempAry[1].substring(7);
    		        			}
    		        			if(tempAry[1].contains("Circuit.") ||tempAry[1].contains("circuit.")){
    		        				//create a network object and add the source bus
    		        				String circuitId = tempAry[1].substring(8);
    		        				this.getDistNetwork().setId(circuitId);

    		        				String sourceBusId = "";
    		        				double basekv = 0.0;
    		        				double volt_pu = 1.0;
    		        				DStab3PBus sourceBus = null;

    		        				if(!str.contains("basekv=")){
    		        				  nextLine  = reader.readLine();
    		        				  lineCnt++;

    		        				  int continueIdx = nextLine.indexOf("~");
    		        				  if(continueIdx>0){
    		        					  nextLine = nextLine.substring(continueIdx+1);
    		        					  String[] tempAry2 = nextLine.split("\\s+");

    		        					  for (String element : tempAry2) {
    		        						  if(element.contains("basekv=")){
    		        							  basekv = Double.valueOf(element.substring(7));
    		        						  }
    		        						  else if(element.contains("Bus1=")||element.contains("bus1=")){
    		        							  sourceBusId = element.substring(4);
    		        						  }
    		        						  else if(element.contains("pu=")){
    		        							  volt_pu = Double.valueOf(element.substring(3));
    		        						  }
    		        					  }

    		        				  }
    		        				  else{
    		        					  no_error = false;
    		        					  throw new Error("basekv is not defined for the source bus and no contiunation data");
    		        				  }

    		        				  // create the source bus
    		        				  if(sourceBusId.length()>0){
    		        					  if(distNet.getBus(sourceBusId)==null) {
											sourceBus = ThreePhaseObjectFactory.create3PDStabBus(sourceBusId, distNet);
										}

    		        					  sourceBus.setBaseVoltage(basekv, UnitType.kV);

    		        					  sourceBus.setVoltageMag(volt_pu);
    		        				  }
    		        				  else{
    		        					  no_error = false;
    		        					  throw new Error("source bus name is not properly parse! # "+ nextLine);
    		        				  }
    		        				}
    		        			}

    		        			else if(tempAry[1].contains("Line.") ||tempAry[1].contains("line.")){
    		        				this.lineParser.parseLineData(str);
    		        			}
                                else if(tempAry[1].contains("Transformer.") ||tempAry[1].contains("transformer.")){
                                	String [] xfrStrAry = new String[3];
                                	xfrStrAry[0] = str;


                                	String[] nextStrAry = getNextDataInputString(reader);
                                	if(nextStrAry[0]==null){
                                		break;
                                	}
                                	nextLine = nextStrAry[0].trim();
                                	lineCnt = lineCnt + Integer.valueOf(nextStrAry[1]);

                                	if(nextLine.startsWith("~")){
                                		xfrStrAry[1] = nextLine;
                                		// by default there are three line strings for one transformer definition
                                		nextLine = reader.readLine().trim();
                                    	lineCnt++;
                                    	if(nextLine.startsWith("~")){
                                    		xfrStrAry[2] = nextLine;
                                    	}
                                    	else{
                                    		useLastLineString = true;
                                    	}
                                	}
                                	else{
                                		useLastLineString = true;
                                	}


    		        				if(useLastLineString) { // all data in one line
										this.xfrParser.parseTransformerDataOneLine(str);
									} else { // all data in one line
										this.xfrParser.parseTransformerDataMultiLines(xfrStrAry);
									}
    		        			}
    		        			else if(tempAry[1].contains("Load.") ||tempAry[1].contains("load.")){
    		        				this.loadParser.parseLoadData(str);
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

    	 Queue<DStab3PBus> onceVisitedBuses = new  LinkedList<>();

 		// find the source bus, which is the swing bus for radial feeders;
 		for(DStab3PBus b: distNet.getBusList()){
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
 		for(AclfBranch bra:distNet.getBranchList()){
 			bra.setBooleanFlag(false);
 		}

 		// perform BFS and set the bus sortNumber
 		BFS(onceVisitedBuses);



    	 return no_error;
     }


    private void BFS (Queue<DStab3PBus> onceVisitedBuses){
 	int orderNumber = 0;
		//Retrieves and removes the head of this queue, or returns null if this queue is empty.
	    while(!onceVisitedBuses.isEmpty()){
	    	DStab3PBus  startingBus = onceVisitedBuses.poll();
			startingBus.setSortNumber(orderNumber++);
			startingBus.setBooleanFlag(true);
			startingBus.setIntFlag(2);

			if(startingBus!=null){
				  for(Branch connectedBra: startingBus.getBranchList()){
					  AclfBranch aclfBra = (AclfBranch) connectedBra;
						if(!connectedBra.isBooleanFlag()){
								Bus findBus = connectedBra.getOppositeBus(startingBus).get();

								//update status
								connectedBra.setBooleanFlag(true);

								//for first time visited buses
								if(findBus.getIntFlag()==0){
									findBus.setIntFlag(1);
									onceVisitedBuses.add((DStab3PBus) findBus);

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

     public boolean convertActualValuesToPU(double mvaBase){

    	 if(mvaBase>0) {
    		 if(mvaBase>20){
    			 ODMLogger.getLogger().warning("The input mvaBase is beyond the normal range of [5, 20] MVA, input mvabase = "+mvaBase);
    		 }
    		 this.getDistNetwork().setBaseKva(mvaBase*1000.0);
    	 }
    	 else{
    		 ODMLogger.getLogger().severe("The input mvabase <= 0. mvabase = 1.0 MVA will be used");
    		 mvaBase = 1.0;
    		 this.getDistNetwork().setBaseKva(mvaBase*1000.0);
    	 }

    	 boolean no_error = convertBranchZYMatrixToPU()&&convertLoadCapacitorToPU();

    	 return no_error;
     }

     private boolean convertBranchZYMatrixToPU(){
          boolean no_error = true;

          double mvabase = this.getDistNetwork().getBaseMva();
          double vBase = 0.0, zBase = 0.0;
          for(DStabBranch bra: this.getDistNetwork().getBranchList()){
        	  DStab3PBranch bra3Phase = (DStab3PBranch) bra;

        	  if(bra3Phase.isLine()){
        		  vBase = bra.getFromBus().getBaseVoltage();
        		  zBase = vBase*vBase*1.0E-6/mvabase;

        		  bra3Phase.setZabc(bra3Phase.getZabc().multiply(1.0/zBase));
        	  }
        	  else if(bra3Phase.isXfr() || bra3Phase.isPSXfr()){
        		  // convert the Z to high voltage side
        		  vBase = bra3Phase.getFromBus().getBaseVoltage()>=bra3Phase.getToBus().getBaseVoltage()?
        				  bra3Phase.getFromBus().getBaseVoltage():bra3Phase.getToBus().getBaseVoltage();
        		  zBase = vBase*vBase*1.0E-6/mvabase;
        		  bra3Phase.setZ(bra3Phase.getAdjustedZ().divide(zBase));

        		  // convert the turn ratios
        		  double vllfactor = 1.0;
        		  if(bra3Phase.getPhaseCode()!=PhaseCode.ABC){
        			  vllfactor = Math.sqrt(3);
        		  }
        		  bra3Phase.setFromTurnRatio(bra3Phase.getFromTurnRatio()*vllfactor /bra3Phase.getFromBus().getBaseVoltage());
        		  bra3Phase.setToTurnRatio(bra3Phase.getToTurnRatio()*vllfactor/bra3Phase.getToBus().getBaseVoltage());
        	  }
        	  else{
        		  ODMLogger.getLogger().severe("Sepcial branch type is not supported, branchId = "+bra.getId());
        	  }

          }

    	  return no_error;
     }

     private boolean convertLoadCapacitorToPU(){
         boolean no_error = true;

   	     double baseKVA3P = this.getDistNetwork().getBaseKva();
   	     double baseKVA1P = baseKVA3P/3.0;

         for(DStab3PBus bus3P: this.getDistNetwork().getBusList()){

        	 if(bus3P.getSinglePhaseLoadList().size()>0){
        		 for(DStab1PLoad load:bus3P.getSinglePhaseLoadList()){
        			 DStab1PLoad ld1P = load;
        			 ld1P.setLoadCP(ld1P.getLoadCP().divide(baseKVA1P));
        			 ld1P.setLoadCI(ld1P.getLoadCI().divide(baseKVA1P));
        			 ld1P.setLoadCZ(ld1P.getLoadCZ().divide(baseKVA1P));
        		 }
        	 }

        	 if(bus3P.getThreePhaseLoadList().size()>0){
        		 for(DStab3PLoad load3P:bus3P.getThreePhaseLoadList()){
        			 load3P.set3PhaseLoad(load3P.getInit3PhaseLoad().multiply(1.0/baseKVA1P));
        		 }
        	 }
         }


   	  return no_error;
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

	public String getBusIdPrefix() {
		return busIdPrefix;
	}

	public void setBusIdPrefix(String busIdPrefix) {
		this.busIdPrefix = busIdPrefix;
	}


}




