package org.interpss.core.cache;

import org.interpss.IpssCorePlugin;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.core.HazelcastInstance;
import com.interpss.CoreObjectFactory;
import com.interpss.cache.UgidGenerator;
import com.interpss.cache.acsc.AcscNetCacheWrapper;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.exp.IpssCacheException;
import com.interpss.core.acsc.AcscNetwork;
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
		
  		AcscNetwork acscNet = CoreObjectFactory.createAcscNetwork();
		SampleCases.load_SC_5BusSystem(acscNet);
		System.out.println(acscNet.net2String());
		
		AcscNetCacheWrapper cache = new AcscNetCacheWrapper(client);

		long key = cache.put(acscNet);
		 
		AcscNetwork net = cache.get(key);
		System.out.println(net.net2String());
	}
}
