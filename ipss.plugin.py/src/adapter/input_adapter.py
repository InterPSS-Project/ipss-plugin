# Description: This module provides classes to create AclfNet objects from IEEE CDF and PSSE RAW files using ODM mappers.

class IeeeFileAdapter:
    # IEEECDFVersion enum
    from org.ieee.odm.adapter.ieeecdf.IeeeCDFAdapter import  IEEECDFVersion
    version = IEEECDFVersion
    
    # Create AclfNet from IEEE CDF file
    # arguments: 
    #     file_path - path to the IEEE CDF file
    #     version   - version of the IEEE CDF format
    # returns AclfNet object
    @staticmethod
    def createAclfNet(file_path=None, version=IEEECDFVersion.Default):
         # ODM related classes
        from org.interpss.odm.mapper import ODMAclfParserMapper
       
        # IEEE CDF related classes
        from org.ieee.odm.adapter.ieeecdf import IeeeCDFAdapter
        #from org.ieee.odm.adapter.ieeecdf.IeeeCDFAdapter import  IEEECDFVersion
        
        # create the file adapter and parse the input file
        fileAdapter = IeeeCDFAdapter(version)
        fileAdapter.parseInputFile(file_path)
        
        # map the ODM model to InterPSS AclfNet model
        aclfNet = ODMAclfParserMapper().map2Model(fileAdapter.getModel()).getAclfNet()       
        
        return aclfNet

class PsseRawFileAdapter:
    # PsseVersion enum
    from org.ieee.odm.adapter.psse.PSSEAdapter import PsseVersion
    version = PsseVersion

    # Create AclfNet from PSSE RAW file    
    # arguments:
    #     file_path - path to the PSSE RAW file
    #     version   - version of the PSSE RAW format
    # return AclfNet object
    @staticmethod
    def createAclfNet(file_path=None, version=None):
         # ODM related classes
        from org.interpss.odm.mapper import ODMAclfParserMapper
       
        # PSSE RAW related classes
        from org.ieee.odm.adapter.psse.raw import PSSERawAdapter
        # from org.ieee.odm.adapter.psse.PSSEAdapter import PsseVersion
        
        # create the file adapter and parse the input file
        fileAdapter = PSSERawAdapter(version)
        fileAdapter.parseInputFile(file_path)
        
        # map the ODM model to InterPSS AclfNet model
        aclfNet = ODMAclfParserMapper().map2Model(fileAdapter.getModel()).getAclfNet()       
        
        return aclfNet
