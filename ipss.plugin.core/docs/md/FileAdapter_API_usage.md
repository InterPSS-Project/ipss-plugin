# File Adapter Implementation

##### Custom (PSS/E) File Loading

There are three ways to use a custom file adapter to load a file and create an InterPSS object

  *  Use the File Import DSL
  
		AclfNetwork net = IpssAdapter.importAclfNet("testdata/adpter/psse/json/ieee9_output.rawx")
							.setFormat(PSSE)
							.setPsseVersion(PsseVersion.PSSE_JSON)
							.load()
							.getImportedObj();				
							
  * Use the CorePluginFactory
  
		AclfNetwork net = CorePluginFactory
							.getFileAdapter(IpssFileAdapter.FileFormat.PSSE, IpssFileAdapter.Version.PSSE_30)
							.load("testData/adpter/psse/v30/42bus_3winding_from_PSSE_V30_NoDC.raw")
							.getAclfNet();	
  
  * Use Custom Adapter Directly
  
		IODMAdapter adapter = new PSSERawAdapter(PSSEAdapter.PsseVersion.PSSE_35);
		assertTrue(adapter.parseInputFile("testData/psse/v35/Kundur_2area_vschvdc_remotebus_v35.raw"));
		
		AclfModelParser parser = (AclfModelParser)adapter.getModel();
		//parser.stdout();
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.ACLF_NETWORK);
		if (!new ODMAclfParserMapper()
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return null;
		}		
		
		AclfNetwork net = simuCtx.getAclfNet();			
  		
  
  		