  
import hazelcast

# Create a client using the configuration below
client = hazelcast.HazelcastClient()

# We can access maps on the server from the client. Let's access the greetings map that we created already
greetings_map = client.get_map("greetings-map").blocking()

# Get the entry set of the map
entry_set = greetings_map.entry_set()

# Print key-value pairs
for key, value in entry_set:
    print("%s -> %s" % (key, value))
    
# Disconnect the client and shutdown
client.shutdown()