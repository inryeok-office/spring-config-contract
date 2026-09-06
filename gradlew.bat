@rem Gradle startup script for Windows
@echo off

set DIR=%~dp0
if "%DIR%"=="" set DIR=.
set APP_HOME=%DIR%
set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

if defined JAVA_HOME (
    "%JAVA_HOME%\bin\java.exe" -Dorg.gradle.appname=gradlew -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
) else (
    java -Dorg.gradle.appname=gradlew -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
)
