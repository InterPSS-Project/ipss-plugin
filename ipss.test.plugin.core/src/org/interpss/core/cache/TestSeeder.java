package org.interpss.core.cache;

import static org.junit.Assert.assertTrue;

import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.mapper.odm.ODMDStabParserMapper;
import org.interpss.pssl.plugin.IpssAdapter;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.core.HazelcastInstance;
import com.interpss.SimuObjectFactory;
import com.interpss.cache.UgidGenerator;
import com.interpss.cache.dstab.DStabNetCacheWrapper;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.exp.IpssCacheException;
import com.interpss.common.msg.IPSSMsgHub;
import com.interpss.core.acsc.AcscNetwork;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.EConstMachine;
import com.interpss.dstab.util.sample.SampleDStabCase;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.spring.CoreCommonSpringFactory;

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
		
		DynamicSimuAlgorithm dstabAlgo = IpssAdapter.importNet("testData/odm/dstab/Tran_2Bus_062011.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();	   
		DStabilityNetwork basenet = dstabAlgo.getNetwork();
		//System.out.println(basenet.net2String());
		
	    DStabNetCacheWrapper cache = new DStabNetCacheWrapper(client);

		long key = cache.put(basenet);
		 
		DStabilityNetwork net = cache.get(key);
		System.out.println(net.net2String());
	}
}
