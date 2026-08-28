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

Run the testsuite
```
    mvn -pl ipss.test.plugin.core test -Dtest=CorePluginTestSuite
```

The suite class is `org.interpss.CorePluginTestSuite`. Using the simple class name matches the CI workflow and avoids Surefire pattern mismatches caused by an incorrect package name.

##### Maven Release Deployment

GitHub Actions verifies normal `master` branch pushes with:

```sh
mvn -B clean verify
```

The workflow deploys Maven artifacts to CodeArtifact only when a release tag
matching `v*` is pushed. To deploy a release version, first make sure the Maven
version in `pom.xml` has not already been published, then create and push a tag:

```sh
git tag v1.3.23
git push origin v1.3.23
```

Deploy each release version only once. CodeArtifact release artifacts are
immutable, so redeploying the same version can leave earlier uploaded files in
place and fail later uploads with a conflict. If the version was already
deployed, bump the Maven version first, then tag and deploy the new version.

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
