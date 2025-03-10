import json

json_string = """
{
    "researcher": {
        "name": "Ford Prefect",
        "species": "Betelgeusian",
        "relatives": [
            {
                "name": "Zaphod Beeblebrox",
                "species": "Betelgeusian"
            }
        ]
    }
}
"""

data = json.loads(json_string)

print("researcher: ")
print(data.get("researcher"))

print("researcher.name: ")
print(data.get("researcher").get("name"))


jstr = json.dumps(data)

print("JSon String: " + jstr)
