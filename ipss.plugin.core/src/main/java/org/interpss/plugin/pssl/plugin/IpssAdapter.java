/*
  * @(#)IpssAdapter.java   
  *
  * Copyright (C) 2006-2011 www.interpss.com
  *
  * This program is free software; you can redistribute it and/or
  * modify it under the terms of the GNU LESSER GENERAL PUBLIC LICENSE
  * as published by the Free Software Foundation; either version 2.1
  * of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  * GNU General Public License for more details.
  *
  * @Author Mike Zhou
  * @Version 1.0
  * @Date 04/15/2009
  * 
  *   Revision History
  *   ================
  *
  */
package org.interpss.plugin.pssl.plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;

import org.ieee.odm.adapter.IODMAdapter;
import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.raw.PSSERawAdapter;
import org.ieee.odm.model.IODMModelParser;
import org.ieee.odm.model.ODMModelParser;
import org.ieee.odm.model.aclf.AclfModelParser;
import org.ieee.odm.model.acsc.AcscModelParser;
import org.ieee.odm.model.dc.DcSystemModelParser;
import org.ieee.odm.model.dist.DistModelParser;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.ieee.odm.schema.AnalysisCategoryEnumType;
import org.ieee.odm.schema.NetworkCategoryEnumType;
import org.interpss.CorePluginFunction;
import static org.interpss.CorePluginFunction.AclfParser2AclfNet;
import static org.interpss.CorePluginFunction.AcscParser2AcscNet;
import static org.interpss.CorePluginFunction.DStabParser2DStabAlgo;
import org.interpss.fadapter.bpa.BPADirectParser;
import org.interpss.fadapter.ge.GEPslfDirectParser;
import org.interpss.fadapter.ieeecdf.IeeeCDFDirectParser;
import org.interpss.fadapter.matpower.MatpowerDirectParser;
import org.interpss.fadapter.psse.PSSEDirectParser;
import org.interpss.fadapter.psse.PSSEJsonDirectParser;
import org.interpss.fadapter.pwd.PWDDirectParser;
import org.interpss.fadapter.ucte.UCTEDirectParser;
import org.interpss.odm.mapper.ODMAclfNetMapper;
import org.interpss.plugin.pssl.plugin.IpssAdapter.PsseVersion;
import org.interpss.plugin.pssl.simu.BaseDSL;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.acsc.AcscNetwork;
import com.interpss.dc.DcNetwork;
import com.interpss.dist.DistNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;

/**
 * Class for wrapping custom input adapters
 * 
 * @author mzhou
 *
 */
public class IpssAdapter extends BaseDSL {
	
	// ================ public methods =======================
	
	/**
	 * custom file input format
	 */
	public static enum FileFormat { 
			IEEECommonFormat, 
			PSSE, 
			GE_PSLF, 
			UCTE, 
			IEEE_ODM, 
			BPA, 
			PWD, 
			MATPOWER,
			Custom,
			// PSASP   it is private now
			};
	
	/**
	 *  PSS/E version number 
	 *
	 */
	public static enum PsseVersion {
		    PSSE_JSON,
		    PSSE_36,
		    PSSE_35,
		    PSSE_34,
		    PSSE_33, 
			PSSE_32, 
			PSSE_31, 
			PSSE_30, 
			PSSE_29 };
			
	/**
	* Parses the PSSE version from a PSSE file by reading the REV field.
	* 
	* @param filename the path to the PSSE file
	* @return the corresponding PsseVersion
	* @throws InterpssException if an error occurs during file reading or parsing
	*/
	public static PsseVersion parsePsseVersion(String filename) throws InterpssException {
		try {
			return psseVersion(PSSEAdapter.parsePsseVersion(filename));
		} catch (Exception e) {
			throw new InterpssException(e.getMessage());
		}
	}

	private static PsseVersion psseVersion(PSSEAdapter.PsseVersion version) {
		switch (version) {
			case PSSE_29: return PsseVersion.PSSE_29;
			case PSSE_30: return PsseVersion.PSSE_30;
			case PSSE_31: return PsseVersion.PSSE_31;
			case PSSE_32: return PsseVersion.PSSE_32;
			case PSSE_33: return PsseVersion.PSSE_33;
			case PSSE_34: return PsseVersion.PSSE_34;
			case PSSE_35: return PsseVersion.PSSE_35;
			case PSSE_36: return PsseVersion.PSSE_36;
			default: return PsseVersion.PSSE_36;
		}
	}
			
	/**
	 * create an ImportAclfNetDSL object
	 * 		
	 * @param filename custom filename
	 * @return
	 */
	public static FileImportDSL importAclfNet(String filename) {
		return new FileImportDSL(filename);
	}
	
	/**
	 * create an ImportAclfNetDSL object
	 * 		
	 * @return
	 */	
	public static FileImportDSL importNet() {
		return new FileImportDSL();
	}

	/**
	 * create an ImportAclfNetDSL object by wrapping a ODM custom
	 * file adapter
	 * 		
	 * @param adapter ODM custom file adapter
	 * @return
	 */
	public static FileImportDSL wrapAdapter(IODMAdapter adapter) {
		return new FileImportDSL(adapter);
	}
	
	// ================ ImportAclfNetDSL implementation =======================

	/**
	 * File import DSL
	 * 
	 * @author mzhou
	 *
	 */
	public static class FileImportDSL {
		private String file1Name;
		private String file2Name;
		private String file3Name;
		
		private FileFormat format;
		private PsseVersion psseVersion;
		private ODMAclfNetMapper.XfrBranchModel xfrBranchModel = ODMAclfNetMapper.XfrBranchModel.InterPSS;
		
		/**
		 * full class name for custom ODM file adapter implementation
		 */
		private String classname;
		/**
		 * ODM custom file adapter. It holds an ODM parser, which parse
		 * the custom file into a JABX object
		 */
		private IODMAdapter adapter = null;
		
		// ODM parser object
		private IODMModelParser odmParser = null;
		
		// imported object, e.g., AclfNetwork, AcscNetwork, DStabAlgorithm, ...
		private Object importedObj = null;
		
		/**
		 * get the AclfParser object
		 * 
		 * @return the AclfParser object
		 */
		public AclfModelParser getAclfParser() { return (AclfModelParser)odmParser; }
		/**
		 * get the ODM parser object
		 * 
		 * @return the ODM parser object
		 */
		public IODMModelParser getOdmParser() { return odmParser; }
		
		/**
		 * get the imported object, for example, AclfNetwork, ... DynamicSimuAlgorithm
		 * 
		 * @return the imported object
		 * @throws InterpssException
		 */
		@SuppressWarnings(value="unchecked")
		public <T> T getImportedObj() throws InterpssException { 
			if (this.importedObj == null) {
				this.importedObj = odmParser instanceof AcscModelParser? mapAcscNet() :	
						   odmParser instanceof DStabModelParser? mapDStabAlgo().getNetwork() :	
						   odmParser instanceof DistModelParser? mapDistNet() :	
						   odmParser instanceof DcSystemModelParser? mapDcSysNet() :	
						   mapAclfNet();
			}
			return (T)this.importedObj;
		}

		/**
		 * map the parse object to an AclfNetwork object
		 * 
		 * @return the AclfNetwork object
		 * @throws InterpssException
		 */
		public AclfNetwork mapAclfNet() throws InterpssException { 
			return AclfParser2AclfNet.fx((AclfModelParser)odmParser, this.xfrBranchModel);
		}

		/**
		 * map the parse object to an AcscNetwork object
		 * 
		 * @return the AcscNetwork object
		 * @throws InterpssException
		 */
		public AcscNetwork mapAcscNet() throws InterpssException { 
			return AcscParser2AcscNet.fx((AcscModelParser)odmParser);
		}

		/**
		 * map the parse object to an DStabNetwork object
		 * 
		 * @return the DStabNetwork object
		 * @throws InterpssException
		 */
		public DynamicSimuAlgorithm mapDStabAlgo() throws InterpssException { 
			return DStabParser2DStabAlgo.fx((DStabModelParser)odmParser);
		}
		
		/**
		 * map the parse object to an DistNetwork object
		 * 
		 * @return the DistNetwork object
		 * @throws InterpssException
		 */
		public DistNetwork mapDistNet() throws InterpssException { 
			return CorePluginFunction.DistXmlNet2DistNet.fx(((DistModelParser)odmParser).getDistNet());
		}

		/**
		 * map the parse object to an DcNetwork object
		 * 
		 * @return the DcNetwork object
		 * @throws InterpssException
		 */
		public DcNetwork mapDcSysNet() throws InterpssException { 
			return CorePluginFunction.DcSysXmlNet2DcSysNet.fx(((DcSystemModelParser)odmParser).getDcNet());
		}
		
		/**
		 * default constructor
		 */
		public FileImportDSL() {
		}
		
		/**
		 * constructor
		 * 
		 * @param filename customer filename
		 */
		public FileImportDSL(String filename) {
			this.file1Name = filename;
		}
		
		/**
		 * constructor
		 * 
		 * @param fileNameAry an array of import file names
		 */
		public FileImportDSL(String[] fileNameAry ) {
			if(fileNameAry.length>3){
			  try {
				throw new InterpssException("At most three input files, i.e., lf,sequence and dynamic, are supported!");
			} catch (InterpssException e) {
				e.printStackTrace();
			}
			}
			else if(fileNameAry.length==3){
				 this.file1Name = fileNameAry[0];
				 this.file2Name = fileNameAry[1];
				 this.file3Name = fileNameAry[2];
			}
			else if(fileNameAry.length==2){
				 this.file1Name = fileNameAry[0];
				 this.file2Name = fileNameAry[1];
				
			}
			else if(fileNameAry.length==1){
				 this.file1Name = fileNameAry[0];
			}
		}
		
		/**
		 * constructor
		 * 
		 * @param adapter ODM custom file adapter
		 */
		public FileImportDSL(IODMAdapter adapter) {
			this.adapter = adapter;
		}
		
		/**
		 * set custom file format
		 * 
		 * @param format
		 * @return
		 */
		public FileImportDSL setFormat(FileFormat format) { this.format = format; return this; }
		/**
		 * set custom file format
		 * 
		 * @param format
		 * @return
		 */
		public FileImportDSL format(FileFormat format) { return this.setFormat(format); }
		
		/**
		 * set Xfr branch model
		 * 
		 * @param model
		 * @return
		 */
		public FileImportDSL setXfrBranchModel(ODMAclfNetMapper.XfrBranchModel model) { this.xfrBranchModel = model; return this; }
		/**
		 * set Xfr branch model
		 * 
		 * @param model
		 * @return
		 */
		public FileImportDSL xfrBranchModel(ODMAclfNetMapper.XfrBranchModel model) { return this.setXfrBranchModel(model); }

		/**
		 * set PSS/E version, only applies to PSS/E file format
		 * 
		 * @param ver
		 * @return
		 */
		public FileImportDSL setPsseVersion(PsseVersion ver) { this.psseVersion = ver; return this; }
		/**
		 * set PSS/E version, only applies to PSS/E file format
		 * 
		 * @param ver
		 * @return
		 */
		public FileImportDSL psseVersion(PsseVersion ver) { return this.setPsseVersion(ver); }
		
		/**
		 * set custom ODM file adapter implementation classname
		 * 
		 * @param name full classname
		 * @return
		 */
		public FileImportDSL setClassname(String name) { this.classname = name; return this; }
		/**
		 * set custom ODM file adapter implementation classname
		 * 
		 * @param name full classname
		 * @return
		 */
		public FileImportDSL classname(String name) { return this.setClassname(name); }
		
		/**
		 * custom file name to be imported
		 * 
		 * @param name
		 * @return
		 */
		public FileImportDSL setFilename(String name) { this.file1Name = name; return this; }
		/**
		 * custom file name to be imported
		 * 
		 * @param name
		 * @return
		 */
		public FileImportDSL filename(String name) { return this.setFilename(name); }

		/**
		 * Attempt to load the file directly via direct parsers (bypassing ODM).
		 * Returns the AclfNetwork or null if direct parsing is not available for this format.
		 */
		private AclfNetwork loadDirect(String filepath) throws InterpssException {
			if (this.format == FileFormat.IEEECommonFormat) {
				return new IeeeCDFDirectParser().parse(filepath);
			} else if (this.format == FileFormat.PSSE) {
				if (this.psseVersion == PsseVersion.PSSE_JSON) {
					return new PSSEJsonDirectParser().parse(filepath);
				} else {
					int ver = mapPsseVersionToInt(this.psseVersion);
					return new PSSEDirectParser(ver).parse(filepath);
				}
			} else if (this.format == FileFormat.GE_PSLF) {
				return new GEPslfDirectParser().parse(filepath);
			} else if (this.format == FileFormat.UCTE) {
				return new UCTEDirectParser().parse(filepath);
			} else if (this.format == FileFormat.BPA) {
				return new BPADirectParser().parse(filepath);
			} else if (this.format == FileFormat.PWD) {
				return new PWDDirectParser().parse(filepath);
			} else if (this.format == FileFormat.MATPOWER) {
				return new MatpowerDirectParser().parse(filepath);
			}
			return null;
		}

		private int mapPsseVersionToInt(PsseVersion ver) {
			if (ver == null) return 30;
			switch (ver) {
				case PSSE_29: return 29;
				case PSSE_30: return 30;
				case PSSE_31: return 31;
				case PSSE_32: return 32;
				case PSSE_33: return 33;
				case PSSE_34: return 34;
				case PSSE_35: return 35;
				case PSSE_36: return 36;
				default: return 30;
			}
		}

		/**
		 * Get ODM adapter for advanced analysis types (Acsc, DStab, etc.)
		 * that still require the ODM pipeline.
		 */
		public IODMAdapter getAdapter() {
			if (this.adapter == null) {
				try {
					if ( this.format == FileFormat.PSSE ) {
						adapter = new PSSERawAdapter(getPsseAptVer());
					}
					else if ( this.format == FileFormat.Custom ) {
						Class<?> klass = Class.forName(this.classname);
						Constructor<?> constructor = klass.getConstructor();
						adapter = (IODMAdapter)constructor.newInstance();
					}
				} catch (Exception e) {
					psslMsg.sendErrorMsg("Cannot load adapter: " + e.toString());
				}
			}
			return this.adapter;
		}
		
		/**
		 * get PSS/E adapter version, which maps PSS/E ODM adapter version
		 * to PSS/E PSSL adapter version
		 * 
		 * @return
		 */
		private PSSEAdapter.PsseVersion getPsseAptVer() throws InterpssException {
			if (this.psseVersion == PsseVersion.PSSE_JSON)
				return PSSEAdapter.PsseVersion.PSSE_JSON;
			//else if (this.psseVersion == PsseVersion.PSSE_26)
			//	return PSSEAdapter.PsseVersion.PSSE_26;
			else if (this.psseVersion == PsseVersion.PSSE_29)
				return PSSEAdapter.PsseVersion.PSSE_29;
			else if (this.psseVersion == PsseVersion.PSSE_30)
				return PSSEAdapter.PsseVersion.PSSE_30;
			else if (this.psseVersion == PsseVersion.PSSE_31)
				return PSSEAdapter.PsseVersion.PSSE_31;
			else if (this.psseVersion == PsseVersion.PSSE_32)
				return PSSEAdapter.PsseVersion.PSSE_32;
			else if (this.psseVersion == PsseVersion.PSSE_33)
				return PSSEAdapter.PsseVersion.PSSE_33;
			else if (this.psseVersion == PsseVersion.PSSE_34)
				return PSSEAdapter.PsseVersion.PSSE_34;
			else if (this.psseVersion == PsseVersion.PSSE_35)
				return PSSEAdapter.PsseVersion.PSSE_35;
			else if (this.psseVersion == PsseVersion.PSSE_36)
				return PSSEAdapter.PsseVersion.PSSE_36;
			else			
				throw new InterpssException("Wrong PSS/E version number " + this.psseVersion);			
		}
		
		/**
		 * load the custom file into the ODM parser object contained in
		 * the ODM adapter object
		 * 
		 * @return
		 */
		public FileImportDSL load() {
			return load(false);	}
		/**
		 * load the custom file into the ODM parser object contained in
		 * the ODM adapter object
		 * 
		 * @param debug
		 * @return
		 */
		public FileImportDSL load(boolean debug) {
			return load(debug, null); }
		/**
		 * load the custom file into the ODM parser object contained in
		 * the ODM adapter object
		 * 
		 * @param debug
		 * @param filename
		 * @return
		 */
		public FileImportDSL load(boolean debug, String filename) { 
			
			if(filename !=null) this.file1Name = filename;
			
			try {
				if ( this.format == FileFormat.IEEE_ODM) {
					ODMModelParser parser = new ODMModelParser();

					FileInputStream inStream = new FileInputStream(new File(this.file1Name));
					if (parser.parse(inStream)) {
						if (parser.getStudyCase().getNetworkCategory() == NetworkCategoryEnumType.DC_SYSTEM) {
							odmParser = parser.toDcSystemModelParser();
						}
						else if (parser.getStudyCase().getNetworkCategory() == NetworkCategoryEnumType.DISTRIBUTION) {
							odmParser = parser.toDistModelParser();
						}
						else {
							if (parser.getStudyCase().getAnalysisCategory() == AnalysisCategoryEnumType.SHORT_CIRCUIT)
								odmParser = parser.toAcscModelParser();
							else if (parser.getStudyCase().getAnalysisCategory() == AnalysisCategoryEnumType.TRANSIENT_STABILITY)
								odmParser = parser.toDStabModelParser();
							else
								odmParser = parser.toAclfModelParser();
						}
					}
					else {
						psslMsg.sendErrorMsg("Error in loading file: " + this.file1Name);
						return null;
					}
					inStream.close();
				}
				else {
					// Use direct parsers to load Aclf networks, bypassing ODM
					AclfNetwork directNet = loadDirect(this.file1Name);
					if (directNet != null) {
						this.importedObj = directNet;
						return this;
					}

					// Fallback to ODM adapter for Custom format or unsupported formats
					if (getAdapter() != null) {
						getAdapter().parseInputFile(this.file1Name);
						if (debug) {
							if (filename == null)
								System.out.println(getAdapter().getModel().toXmlDoc());
							else 
								getAdapter().getModel().toXmlDoc(filename);
						}
						odmParser = adapter.getModel();
					}
				}
				
				return this;
			} catch (IOException | InterpssException e) {
				psslMsg.sendErrorMsg("Error in loading file: " + e.toString());
			}
			return null; 
		}
		
		public FileImportDSL load(NetType type, String[] fileNameAry) { 
			
			if(fileNameAry.length>3){
				  try {
					throw new InterpssException("At most three input files, i.e., lf,sequence and dynamic, are supported!");
				} catch (InterpssException e) {
					e.printStackTrace();
				}
				}
				else if(fileNameAry.length>=2){
					 this.file1Name = fileNameAry[0];
					 this.file2Name = fileNameAry[1];
					 if(fileNameAry.length>2)
					       this.file3Name = fileNameAry[2];
					 
					 if(getAdapter() instanceof PSSEAdapter){
						// NetType type = fileNameAry.length ==3?NetType.DStabNet: NetType.AcscNet;
						 getAdapter().parseInputFile(type, fileNameAry);
						 odmParser = adapter.getModel();
					 }
					 else{
						 try {
							throw new InterpssException("Only PSS/E format is supported for importing files including sequence and/or dynamic data!");
						} catch (InterpssException e) {
							
							e.printStackTrace();
						}
					 }
					 
					return this;
				}
				
				else if(fileNameAry.length==1){
					return load(false, fileNameAry[0]);
				}
			
			return null;
		}
	}
}
