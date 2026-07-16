package org.interpss;

import org.interpss.fadapter.BPAFormat;
import org.interpss.fadapter.GEFormat;
import org.interpss.fadapter.IeeeCDFFormat;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.fadapter.IpssInternalFormat;
import org.interpss.fadapter.MatpowerFormat;
import org.interpss.fadapter.PTIFormat;
import org.interpss.fadapter.PWDFormat;
import org.interpss.fadapter.UCTEFormat;

import com.interpss.common.CoreCommonFactory;
import com.interpss.common.exp.InterpssException;

/**
 * Core plugin factory for file adapters and other core components.
 * ODM mapper factory methods have been removed - use direct parsers instead.
 * 
 * @author mzhou
 */
public class CorePluginFactory extends CoreCommonFactory {

	/**
	 * get input file adapter for the file format
	 */
	public static IpssFileAdapter getFileAdapter(IpssFileAdapter.FileFormat f) throws InterpssException {
		IpssFileAdapter.Version version = 
				f == IpssFileAdapter.FileFormat.IEEECDF? IpssFileAdapter.Version.IEEECDF : 
						IpssFileAdapter.Version.NotDefined;
		return getFileAdapter(f, version);
	}
	
	/**
	 * get input file adapter for the file format and version
	 */
	public static IpssFileAdapter getFileAdapter(IpssFileAdapter.FileFormat f, IpssFileAdapter.Version v)
					throws InterpssException {
		if (f == IpssFileAdapter.FileFormat.IEEECDF) {
			return new IeeeCDFFormat();
		}
		else if (f == IpssFileAdapter.FileFormat.GE_PSLF) {
			return new GEFormat();
		} 
		else if (f == IpssFileAdapter.FileFormat.PSSE) {
			return new PTIFormat(v);
		} 
		else if (f == IpssFileAdapter.FileFormat.BPA) {
			return new BPAFormat();
		} 
		else if (f == IpssFileAdapter.FileFormat.PWD) {
			return new PWDFormat();
		} 
		else if (f == IpssFileAdapter.FileFormat.MATPOWER) {
			return new MatpowerFormat();
		}
		else if (f == IpssFileAdapter.FileFormat.UCTE) {
			return new UCTEFormat();
		} 
		else if (f == IpssFileAdapter.FileFormat.IpssInternal) {
			return new IpssInternalFormat(CoreCommonFactory.getIpssMsgHub());
		} 
		throw new InterpssException("Error - File adapter format/version not implemented");
	}	
}
