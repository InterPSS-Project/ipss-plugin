@echo off

REM define ipss lib location
set IpssLib_HOME=C:\Users\Qiuhua\git\ipss-common\ipss.lib\lib

REM define Java classpath

REM ODM and ipss lib files
set IPSS_CLASSPATH=%IpssLib_HOME%\ieee\ieee.odm_pss.jar;%IpssLib_HOME%\ieee\ieee.odm.schema.jar;%IpssLib_HOME%\ipss\ipss_core.impl.jar;%IpssLib_HOME%\ipss\ipss_core.jar;%IpssLib_HOME%\ipss\ipss_plugin.jar;%IpssLib_HOME%\ipss\ipss_pssl.jar;

REM EMF lib files
set IPSS_CLASSPATH=%IPSS_CLASSPATH%;%IpssLib_HOME%\eclipse\org.eclipse.emf.common.jar;%IpssLib_HOME%\eclipse\org.eclipse.emf.ecore.change.jar;%IpssLib_HOME%\eclipse\org.eclipse.emf.ecore.jar;%IpssLib_HOME%\eclipse\org.eclipse.emf.ecore.xmi.jar;%IpssLib_HOME%\eclipse\org.eclipse.emf.jar;

REM third-party lib files
set IPSS_CLASSPATH=%IPSS_CLASSPATH%;%IpssLib_HOME%\apache\commons-math3-3.0.jar;%IpssLib_HOME%\apache\commons-logging.jar;%IpssLib_HOME%\spring\spring-2.5.6.jar;

REM define Java cmd
set JAVA_CMD="C:\Program Files (x86)\Java\jre7\bin\java"

REM launch the IpssCmd 
%JAVA_CMD% -classpath %IPSS_CLASSPATH% -Xms512m -Xmx1024m com.interpss.app.IpssCmd -i %1 -f %2 -c %3