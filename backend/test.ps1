$ErrorActionPreference = "Stop"
$env:JAVA_HOME = "$PWD\jdk-21.0.3+9"
$env:PATH = "$env:JAVA_HOME\bin;$PWD\apache-maven-3.9.6\bin;" + $env:PATH
mvn clean compile exec:java "-Dexec.mainClass=com.schoolos.TestHash"
