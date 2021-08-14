  
import hazelcast

# Create a client using the configuration below
client = hazelcast.HazelcastClient(
    # Add member's host:port to the configuration.
    # For each member on your Hazelcast cluster, you should add its host:port pair to the configuration.
    # If the port is not specified, by default 5701, 5702 and 5703 will be tried.
    cluster_members=[
        "192.168.3.29:5701",
    ]
)

# Get a map that is stored on the server side. We can access it from the client
greetings_map = client.get_map("greetings-map").blocking()

# Map is empty on the first run. It will be non-empty if Hazelcast has data on this map
print("Size before:", greetings_map.size())

# Write data to map. If there is a data with the same key already, it will be overwritten
greetings_map.put("English", "hello world")
greetings_map.put("Spanish", "hola mundo")
greetings_map.put("Italian", "ciao mondo")
greetings_map.put("German", "hallo welt")
greetings_map.put("French", "bonjour monde")

# 5 data is added to the map. There should be at least 5 data on the server side
print("Size after:", greetings_map.size())

# Disconnect the client and shutdown
client.shutdown()