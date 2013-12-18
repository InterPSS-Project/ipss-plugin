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
import com.interpss.simu.util.sample.SampleCases;

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
		
  		AclfNetwork net = SampleCases.create5BusAclfReQBranchQ();
		System.out.println(net.net2String());

		AclfNetCacheWrapper cache = new AclfNetCacheWrapper(client);

		long key = cache.put(net);
		 
		AclfNetwork net1 = cache.get(key);
		System.out.println(net1.net2String());
	}
}
