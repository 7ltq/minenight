@echo off
setlocal EnableExtensions
cd /d "%~dp0"
echo ===============================
echo MineNight All-in-One - Build
echo ===============================
set "SELECTED_JDK="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" (
  "%JAVA_HOME%\bin\javac.exe" -version 2>&1 | findstr /r /c:"javac 2[5-9]" >nul && set "SELECTED_JDK=%JAVA_HOME%"
)
if not defined SELECTED_JDK (
  for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-25*" "C:\Program Files\Java\jdk-25*") do (
    if not defined SELECTED_JDK if exist "%%~fD\bin\javac.exe" set "SELECTED_JDK=%%~fD"
  )
)
if not defined SELECTED_JDK (
  for /f "delims=" %%P in ('where javac 2^>nul') do (
    if not defined SELECTED_JDK (
      "%%P" -version 2>&1 | findstr /r /c:"javac 2[5-9]" >nul && for %%D in ("%%~dpP..") do set "SELECTED_JDK=%%~fD"
    )
  )
)
if not defined SELECTED_JDK (
  echo [ERROR] JDK 25 or newer not found. Set JAVA_HOME to JDK 25.
  pause
  exit /b 1
)
set "JAVA_HOME=%SELECTED_JDK%"
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo [INFO] Using JDK for Paper 1.21.11 ^(compiling Java 21 bytecode^):
"%JAVA_HOME%\bin\java.exe" -version
"%JAVA_HOME%\bin\javac.exe" -version
set "MAVEN=mvn"
where mvn >nul 2>nul
if not errorlevel 1 goto build
set "MAVEN=%~dp0.build-tools\apache-maven-3.9.11\bin\mvn.cmd"
if exist "%MAVEN%" goto build
for /d %%D in ("%USERPROFILE%\Downloads\MineNightAllInOne*\MineNightAllInOne\.build-tools\apache-maven-3.9.11") do (
  if exist "%%~fD\bin\mvn.cmd" set "MAVEN=%%~fD\bin\mvn.cmd"
)
if exist "%MAVEN%" goto build
set "MAVEN=%~dp0.build-tools\apache-maven-3.9.11\bin\mvn.cmd"
if not exist ".build-tools" mkdir ".build-tools"
set "MAVEN_ZIP=%~dp0.build-tools\apache-maven-3.9.11-bin.zip"
echo [INFO] Maven not found. Trying Maven Central...
curl.exe --fail --location --retry 2 --connect-timeout 15 --max-time 150 --output "%MAVEN_ZIP%" "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip"
if errorlevel 1 (
  if exist "%MAVEN_ZIP%" del /q "%MAVEN_ZIP%"
  echo [INFO] Trying Apache archive...
  curl.exe --fail --location --retry 2 --connect-timeout 15 --max-time 150 --output "%MAVEN_ZIP%" "https://archive.apache.org/dist/maven/maven-3/3.9.11/binaries/apache-maven-3.9.11-bin.zip"
)
if not exist "%MAVEN_ZIP%" (
  echo [ERROR] Maven could not be downloaded.
  echo [INFO] Manual option: Download apache-maven-3.9.11-bin.zip from https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/
  echo [INFO] Extract it so .build-tools\apache-maven-3.9.11\bin\mvn.cmd exists.
  pause
  exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; Expand-Archive -LiteralPath $env:MAVEN_ZIP -DestinationPath '.build-tools' -Force"
if errorlevel 1 (
  echo [ERROR] Maven archive extraction failed.
  pause
  exit /b 1
)
if not exist "%MAVEN%" (
  echo [ERROR] Maven not found after extraction.
  pause
  exit /b 1
)
del /q "%MAVEN_ZIP%" >nul 2>nul
:build
call "%MAVEN%" -U clean package
if errorlevel 1 (
  echo [ERROR] Build failed; check messages above.
  pause
  exit /b 1
)
if not exist "target\MineNightAllInOne.jar" (
  echo [ERROR] Complete plugin JAR not found.
  pause
  exit /b 1
)
echo [SUCCESS] target\MineNightAllInOne.jar
pause
