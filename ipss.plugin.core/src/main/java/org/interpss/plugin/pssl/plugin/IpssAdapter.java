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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import org.interpss.fadapter.bpa.BPADirectParser;
import org.interpss.fadapter.cim.CIMDirectParser;
import org.interpss.fadapter.epc.EpcDirectParser;
import org.interpss.fadapter.ieeecdf.IeeeCDFDirectParser;
import org.interpss.fadapter.matpower.MatpowerDirectParser;
import org.interpss.fadapter.psse.PSSEDirectParser;
import org.interpss.fadapter.psse.PSSEJsonDirectParser;
import org.interpss.fadapter.psse.PsseRev;
import org.interpss.fadapter.pwd.PWDDirectParser;
import org.interpss.fadapter.ucte.UCTEDirectParser;
import org.interpss.plugin.pssl.simu.BaseDSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetwork;

/**
 * Deprecated file-import DSL kept as a compatibility facade for external repos
 * (e.g. desktop). Prefer format-specific direct parsers inside ipss-plugin:
 * {@link org.interpss.fadapter.psse.PSSEDirectParser},
 * {@link org.interpss.fadapter.psse.PSSEJsonDirectParser},
 * {@link org.interpss.fadapter.ieeecdf.IeeeCDFDirectParser}, etc.
 * <p>
 * All formats use direct parsers that bypass the ODM intermediate layer.
 * 
 * @author mzhou
 * @deprecated Prefer direct parsers; this class remains for external callers.
 */
@Deprecated
public class IpssAdapter extends BaseDSL {
	private static final Logger log = LoggerFactory.getLogger(IpssAdapter.class);
	
	public static enum FileFormat { 
			IEEECommonFormat, 
			PSSE, 
			GE_PSLF, 
			UCTE, 
			BPA, 
			PWD, 
			MATPOWER,
			CIM,
			@Deprecated IEEE_ODM,
			};
	
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
	 * Parses the PSSE version from a PSSE file by reading the IC/SBASE/REV header line.
	 * Skips leading {@code @!} comment lines (common in v33+ RAW exports) before
	 * reading the REV field; otherwise REV is misread and the parser falls back to v30,
	 * which fails to skip the v34+ system-wide data section and creates bogus {@code Bus0} entries.
	 * <p>
	 * Prefer letting {@link org.interpss.fadapter.psse.PSSEDirectParser} auto-detect REV
	 * on load; use this only when a peek is needed without a full parse.
	 */
	public static PsseVersion parsePsseVersion(String filename) throws InterpssException {
		try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
			String firstLine = reader.readLine();
			while (firstLine != null && (firstLine.trim().isEmpty() || firstLine.trim().startsWith("@!"))) {
				firstLine = reader.readLine();
			}
			if (firstLine != null) {
				return mapIntToPsseVersion(PsseRev.fromHeaderLine(firstLine));
			}
		} catch (IOException e) {
			throw new InterpssException("Error parsing PSSE version from: " + filename);
		}
		return PsseVersion.PSSE_30;
	}

	private static PsseVersion mapIntToPsseVersion(int rev) {
		if (rev >= 36) return PsseVersion.PSSE_36;
		if (rev >= 35) return PsseVersion.PSSE_35;
		if (rev >= 34) return PsseVersion.PSSE_34;
		if (rev >= 33) return PsseVersion.PSSE_33;
		if (rev >= 32) return PsseVersion.PSSE_32;
		if (rev >= 31) return PsseVersion.PSSE_31;
		if (rev >= 30) return PsseVersion.PSSE_30;
		return PsseVersion.PSSE_29;
	}

	public static FileImportDSL importAclfNet(String filename) {
		return new FileImportDSL(filename);
	}
	
	public static FileImportDSL importNet() {
		return new FileImportDSL();
	}

	/**
	 * File import DSL - uses direct parsers for all formats.
	 */
	public static class FileImportDSL {
		private String file1Name;
		
		private FileFormat format;
		private PsseVersion psseVersion;
		
		private Object importedObj = null;
		
		@SuppressWarnings(value="unchecked")
		public <T> T getImportedObj() throws InterpssException { 
			return (T)this.importedObj;
		}

		public FileImportDSL() {
		}
		
		public FileImportDSL(String filename) {
			this.file1Name = filename;
		}
		
		public FileImportDSL(String[] fileNameAry) {
			if (fileNameAry.length >= 1) this.file1Name = fileNameAry[0];
		}
		
		public FileImportDSL setFormat(FileFormat format) { this.format = format; return this; }
		public FileImportDSL format(FileFormat format) { return this.setFormat(format); }
		
		/**
		 * Optional override of PSS/E section layout. When omitted, {@link PSSEDirectParser}
		 * auto-detects {@code REV} from the RAW header.
		 */
		public FileImportDSL setPsseVersion(PsseVersion ver) { this.psseVersion = ver; return this; }
		/** @see #setPsseVersion(PsseVersion) */
		public FileImportDSL psseVersion(PsseVersion ver) { return this.setPsseVersion(ver); }
		
		public FileImportDSL setFilename(String name) { this.file1Name = name; return this; }
		public FileImportDSL filename(String name) { return this.setFilename(name); }

		private AclfNetwork loadDirect(String filepath) throws InterpssException {
			if (this.format == FileFormat.IEEE_ODM) {
				throw new InterpssException("IEEE_ODM format is no longer supported. Migrate to a direct parser or supported format.");
			} else if (this.format == FileFormat.IEEECommonFormat) {
				return new IeeeCDFDirectParser().parse(filepath);
			} else if (this.format == FileFormat.PSSE) {
				if (this.psseVersion == PsseVersion.PSSE_JSON) {
					return new PSSEJsonDirectParser().parse(filepath);
				} else if (this.psseVersion == null) {
					return new PSSEDirectParser().parse(filepath);
				} else {
					return new PSSEDirectParser(mapPsseVersionToInt(this.psseVersion)).parse(filepath);
				}
			} else if (this.format == FileFormat.GE_PSLF) {
				return new EpcDirectParser().parse(filepath);
			} else if (this.format == FileFormat.UCTE) {
				return new UCTEDirectParser().parse(filepath);
			} else if (this.format == FileFormat.BPA) {
				return new BPADirectParser().parse(filepath);
			} else if (this.format == FileFormat.PWD) {
				return new PWDDirectParser().parse(filepath);
			} else if (this.format == FileFormat.MATPOWER) {
				return new MatpowerDirectParser().parse(filepath);
			} else if (this.format == FileFormat.CIM) {
				return new CIMDirectParser().parse(filepath);
			}
			return null;
		}

		private int mapPsseVersionToInt(PsseVersion ver) {
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

		public FileImportDSL load() {
			return load(false);
		}

		public FileImportDSL load(boolean debug) {
			return load(debug, null);
		}

		public FileImportDSL load(boolean debug, String filename) { 
			if (filename != null) this.file1Name = filename;
			
			try {
				AclfNetwork directNet = loadDirect(this.file1Name);
				if (directNet != null) {
					this.importedObj = directNet;
					return this;
				}
			} catch (InterpssException e) {
				log.error("Error in loading file: " + e.toString());
			}
			return null; 
		}
		
		/**
		 * Load multi-file PSS/E data (LF + sequence and/or dynamic).
		 * For single-file, delegates to load().
		 */
		public FileImportDSL load(String netType, String[] fileNameAry) { 
			if (fileNameAry == null || fileNameAry.length == 0) return null;
			if (fileNameAry.length == 1) {
				return load(false, fileNameAry[0]);
			}
			
			try {
				if (this.format == FileFormat.CIM) {
					this.importedObj = new CIMDirectParser().parse(fileNameAry);
					return this;
				}
				if (this.format != FileFormat.PSSE) {
					log.error("Multi-file loading only supported for PSS/E and CIM formats");
					return null;
				}

				AclfNetwork lfNet = this.psseVersion == null
						? new PSSEDirectParser().parse(fileNameAry[0])
						: new PSSEDirectParser(mapPsseVersionToInt(this.psseVersion)).parse(fileNameAry[0]);
				this.importedObj = lfNet;
				return this;
			} catch (Exception e) {
				log.error("Error in multi-file loading: {}", e.toString());
				log.error("Error in loading files: " + e.toString());
			}
			return null;
		}
	}
}
