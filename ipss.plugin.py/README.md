InterPSS Python Plugin
=================

[Python Runtime Setup](docs/RuntimeSetup.md)

[InterPSS Simulation Env Setup](docs/InterPSSEnvSetup.md)

### Build ipss_runnable.jar

1. Build the plugin core JAR from the plugin repo, for example from the ipss-plugin root:
```
        cd ipss-plugin
        mvn clean install -pl ipss.plugin.core -am
```

2. Copy that JAR to the name the samples use:
```
        rm -rf ipss.plugin.py/lib/* (optional)
        cp ipss.plugin.core/target/ipss.plugin.core-*.jar ipss.plugin.py/lib/ipss_runnable.jar
```

3. Fill lib/deps (runtime copies for JPype):

```
        cd ipss.plugin.py
        mvn validate
```

