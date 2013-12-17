package org.interpss.core.cache;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.interpss.cache.UgidGenerator;

public class TestCacheServer {
	public static void main(String[] args) {
		Config cfg = new Config();
		
		HazelcastInstance hz = Hazelcast.newHazelcastInstance(cfg);
		
		UgidGenerator.IdGenerator = hz.getIdGenerator("GuidGenerator");
		UgidGenerator.IdGenerator.init(1000000L);
	}
}