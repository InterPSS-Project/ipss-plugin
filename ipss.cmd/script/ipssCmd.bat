@echo off

REM define ipss lib location
set BasePath=D:\eclipse\ws\gitRepo\ipss-common
set IpssLib_HOME=%BasePath%\ipss.lib\lib
set Pty3rd_HOME=%BasePath%\ipss.lib.3rdPty\lib

REM define Java classpath

REM ODM and ipss lib files
set IPSS_CLASSPATH=%IpssLib_HOME%\ieee\ieee.odm_pss.jar;%IpssLib_HOME%\ieee\ieee.odm.schema.jar;%IpssLib_HOME%\ipss\ipss_core.impl.jar;%IpssLib_HOME%\ipss\ipss_core.jar;%IpssLib_HOME%\ipss\ipss_plugin.jar;%IpssLib_HOME%\ipss\ipss_pssl.jar;

REM EMF lib files
set IPSS_CLASSPATH=%IPSS_CLASSPATH%;%Pty3rd_HOME%\eclipse\org.eclipse.emf.common.jar;%Pty3rd_HOME%\eclipse\org.eclipse.emf.ecore.change.jar;%Pty3rd_HOME%\eclipse\org.eclipse.emf.ecore.jar;%Pty3rd_HOME%\eclipse\org.eclipse.emf.ecore.xmi.jar;%Pty3rd_HOME%\eclipse\org.eclipse.emf.jar;

REM third-party lib files
set IPSS_CLASSPATH=%IPSS_CLASSPATH%;%Pty3rd_HOME%\sparse\csparsej-1.1.1.jar;%Pty3rd_HOME%\apache\commons-math3-3.2.jar;%Pty3rd_HOME%\apache\commons-logging.jar;%Pty3rd_HOME%\spring\spring-2.5.6.jar;%Pty3rd_HOME%\cache\hazelcast-3.1.jar;%Pty3rd_HOME%\cache\hazelcast-client-3.1.jar;%Pty3rd_HOME%\json\gson-2.2.2.jar;

REM define Java cmd
set JAVA_CMD="C:\Program Files\Java\jre8\bin\java"

REM launch the IpssCmd 
REM sample "ipssCmd Aclf testData/aclf/aclfrun.json"
%JAVA_CMD% -classpath %IPSS_CLASSPATH% -Xms512m -Xmx1024m org.interpss.app.IpssCmd -t %1 -c %2