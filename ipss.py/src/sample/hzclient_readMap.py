  
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

# We can access maps on the server from the client. Let's access the greetings map that we created already
greetings_map = client.get_map("greetings-map").blocking()

# Get the entry set of the map
entry_set = greetings_map.entry_set()

# Print key-value pairs
for key, value in entry_set:
    print("%s -> %s" % (key, value))
    
# Disconnect the client and shutdown
client.shutdown()