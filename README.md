ipss-plugin
========

##### Java Runtime

Starting from 2025/12, the default Java version for the plugin model is Java-21.

##### Maven-based Development

Starting from 2025/03, InterPSS development is based on Maven. ipss-plugin repo has the following active projects:

```
    ipss.plugin.core
    ipss.plugin.3phase
    ipss.test.plugin.core
    ipss.sample
```

##### Installation
- Step-1, download (git clone is recommended) the latest [ipss-common repository](https://github.com/InterPSS-Project/ipss-common)  which contains the dependent jar libs in [ipss.lib](https://github.com/InterPSS-Project/ipss-common/tree/master/ipss.lib)
- Step-2, install [maven](https://maven.apache.org/install.html) if you don't have it yet on your computer
- Step-3, open a terminal/command line and run the `maven.sh` file (in bash, you can run it with `sh maven.sh`) in this folder to install the dependent jars, you can check if the installation is successful through the info message in the terminal. You should see something like
```
    [INFO] ipss.plugin ........................................ SUCCESS [  0.121 s]
    [INFO] ipss.plugin.core ................................... SUCCESS [  5.167 s]
    [INFO] ipss.plugin.3phase ................................. SUCCESS [  2.090 s]
    [INFO] ipss.test.plugin.core .............................. SUCCESS [  1.928 s]
```
- Step-4, run the  test cases in the `ipss.test.plugin.core` project to further verify the installation
