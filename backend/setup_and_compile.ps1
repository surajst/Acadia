$ErrorActionPreference = "Stop"

Write-Host "Downloading OpenJDK 21..."
Invoke-WebRequest -Uri "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.3%2B9/OpenJDK21U-jdk_x64_windows_hotspot_21.0.3_9.zip" -OutFile "jdk21.zip"

Write-Host "Extracting OpenJDK 21..."
Expand-Archive -Path "jdk21.zip" -DestinationPath "." -Force
Remove-Item "jdk21.zip"

Write-Host "Downloading Apache Maven..."
Invoke-WebRequest -Uri "https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip" -OutFile "maven.zip"

Write-Host "Extracting Apache Maven..."
Expand-Archive -Path "maven.zip" -DestinationPath "." -Force
Remove-Item "maven.zip"

Write-Host "Setting Environment Variables..."
$env:JAVA_HOME = "$PWD\jdk-21.0.3+9"
$env:PATH = "$env:JAVA_HOME\bin;$PWD\apache-maven-3.9.6\bin;" + $env:PATH

Write-Host "Verifying Java and Maven versions..."
java -version
mvn -version

Write-Host "Running Maven Compile..."
mvn clean compile
