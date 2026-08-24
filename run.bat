@echo off
REM Run Spring Boot with IDEA JDK
set IDEA_JDK=D:\Program Files\JetBrains\IntelliJ IDEA 2025.3.4\jbr
set JAVA_HOME=%IDEA_JDK%
set PATH=%IDEA_JDK%\bin;%PATH%

cd /d "%~dp0"
echo Using JDK: %JAVA_HOME%
mvn clean spring-boot:run -DskipTests
