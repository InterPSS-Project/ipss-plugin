package org.interpss.core.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.interpss.CorePluginObjFactory;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.Unit.UnitType;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.core.HazelcastInstance;
import com.interpss.CoreObjectFactory;
import com.interpss.cache.UgidGenerator;
import com.interpss.cache.aclf.AclfNetCacheWrapper;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.exp.IpssCacheException;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBus;

public class TestSeeder {
	public static void main(String[] args) throws IpssCacheException, InterpssException {
		ClientConfig clientConfig = new ClientConfig();
		
		clientConfig.addAddress("127.0.0.1");
		HazelcastInstance client = HazelcastClient.newHazelcastClient(clientConfig);
		UgidGenerator.IdGenerator = client.getIdGenerator("GuidGenerator");		
		
		seedNetwork(client);
		
		client.shutdown();
	}
	
	private static void seedNetwork(final HazelcastInstance client) throws IpssCacheException, InterpssException {
		IpssCorePlugin.init();
		
		AclfNetwork net = CorePluginObjFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testdata/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();
		System.out.println(net.net2String());

		AclfNetCacheWrapper cache = new AclfNetCacheWrapper(client);

		long key = cache.put(net);
		 
		AclfNetwork net1 = cache.get(key);
		System.out.println(net1.net2String());
		
	  	net1.accept(CoreObjectFactory.createLfAlgoVisitor());
  		//System.out.println(net.net2String());
	  	
  		assertTrue(net1.isLfConverged());		
  		AclfSwingBus swing = net1.getBus("Bus1").toSwingBus();
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-2.32393)<0.0001);
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()+0.16549)<0.0001);		
		
/*		
		String id = "AclfNet12345";
		cache.put(net, id);

		AclfNetwork net1 = cache.get(id);
		System.out.println(net1.net2String());
*/		
/*		
		AclfBus bus = net.getBus("1");
		
		AclfBusCacheHelper busHelper = new AclfBusCacheHelper(client);
		NetworkElementKey buskey = busHelper.put(bus, key);
		
		AclfBus bus1 = busHelper.get(buskey);
		System.out.println(bus1.toString());

		AclfBranch branch = net.getBranch("1->2(1)");
		
		AclfBranchCacheHelper braHelper = new AclfBranchCacheHelper(client);
		NetworkElementKey brakey = braHelper.put(branch, key);
		
		AclfBranch bra1 = braHelper.get(brakey);
		System.out.println(bra1.toString());
*/			
	}
}
