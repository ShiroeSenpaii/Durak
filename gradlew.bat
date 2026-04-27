@ECHO OFF
IF "%JAVA_HOME%"=="" SET JAVA_HOME=%USERPROFILE%\.local\share\mise\installs\java\17.0.2
gradle %*
