/*
 * CIMFormat.java
 *
 * File adapter facade for CIM/CGMES RDF/XML import.
 */

package org.interpss.fadapter;

import java.io.File;

import org.interpss.fadapter.cim.CIMDirectParser;
import org.interpss.fadapter.impl.IpssFileAdapterBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

/**
 * IpssFileAdapter for CIM/CGMES RDF/XML files.
 * Supports single-file (CIMHub) and multi-file (EQ+TP+SSH+SV[+BD]) loads.
 */
public class CIMFormat extends IpssFileAdapterBase {
    private static final Logger log = LoggerFactory.getLogger(CIMFormat.class);

    @Override
    public void load(final SimuContext simuCtx, final String filepath, boolean debug, String outfile)
            throws InterpssException {
        CIMDirectParser parser = new CIMDirectParser();
        AclfNetwork aclfNet = parser.parse(filepath);
        simuCtx.setNetType(SimuCtxType.ACLF_NETWORK);
        simuCtx.setAclfNet(aclfNet);
        simuCtx.setName(filepath.substring(filepath.lastIndexOf(File.separatorChar) + 1));
        simuCtx.setDesc("This project is created by input file " + filepath);
        log.debug("CIM Format file {} loaded successfully.", filepath);
    }

    @Override
    public void load(final SimuContext simuCtx, final String[] filepathAry, boolean debug, String outfile)
            throws InterpssException {
        if (filepathAry == null || filepathAry.length == 0) {
            throw new InterpssException("No CIM files specified");
        }
        if (filepathAry.length == 1) {
            load(simuCtx, filepathAry[0], debug, outfile);
            return;
        }
        CIMDirectParser parser = new CIMDirectParser();
        AclfNetwork aclfNet = parser.parse(filepathAry);
        simuCtx.setNetType(SimuCtxType.ACLF_NETWORK);
        simuCtx.setAclfNet(aclfNet);
        String name = filepathAry[0].substring(filepathAry[0].lastIndexOf(File.separatorChar) + 1);
        simuCtx.setName(name);
        simuCtx.setDesc("This project is created by " + filepathAry.length + " CIM profile files");
        log.debug("CIM multi-file load ({} files) completed successfully.", filepathAry.length);
    }

    @Override
    public AclfNetwork loadAclfNet(String filepath) throws InterpssException {
        return new CIMDirectParser().parse(filepath);
    }
}
