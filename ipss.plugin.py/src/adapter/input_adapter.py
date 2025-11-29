
class IeeeFileAdapter:
    
    @staticmethod
    def createAclfNet(file_path=None, version=None):
         # ODM related classes
        from org.interpss.odm.mapper import ODMAclfParserMapper
       
        # IEEE CDF related classes
        from org.ieee.odm.adapter.ieeecdf import IeeeCDFAdapter
        from org.ieee.odm.adapter.ieeecdf.IeeeCDFAdapter import  IEEECDFVersion
        
        # create instances of the classes we are going to used
        fileAdapter = IeeeCDFAdapter(version)
        
        # Use platform-independent path handling for test data
        #file_path = str(script_dir.parent / "tests" / "testData" / "ieee" / "IEEE14Bus.ieee")
        fileAdapter.parseInputFile(file_path)
        aclfNet = ODMAclfParserMapper().map2Model(fileAdapter.getModel()).getAclfNet()       
        
        return aclfNet

class PsseRawFileAdapter:
    
    @staticmethod
    def createAclfNet(file_path=None, version=None):
         # ODM related classes
        from org.interpss.odm.mapper import ODMAclfParserMapper
       
        # PSSE RAW related classes
        from org.ieee.odm.adapter.psse.raw import PSSERawAdapter
        from org.ieee.odm.adapter.psse.PSSEAdapter import PsseVersion
        
        # create instances of the classes we are going to used
        fileAdapter = PSSERawAdapter(version)
        
        # Use platform-independent path handling for test data
        #file_path = str(script_dir.parent / "tests" / "testData" / "ieee" / "IEEE14Bus.ieee")
        fileAdapter.parseInputFile(file_path)
        aclfNet = ODMAclfParserMapper().map2Model(fileAdapter.getModel()).getAclfNet()       
        
        return aclfNet
