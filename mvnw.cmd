@REM Licensed to the Apache Software Foundation (ASF) under one or more
@REM contributor license agreements. Licensed under the Apache License 2.0.
@REM http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Apache Maven Wrapper — 3.3.1
@REM Downloads Maven automatically on first run; no system Maven install required.
@REM -----------------------------------------------------------------------------
@ECHO OFF

@IF "%BASE_DIR%"=="" SET "BASE_DIR=%~dp0"
@IF "%BASE_DIR:~-1%"=="\" SET "BASE_DIR=%BASE_DIR:~0,-1%"

@SET "WRAPPER_JAR=%BASE_DIR%\.mvn\wrapper\maven-wrapper.jar"
@SET "WRAPPER_PROPS=%BASE_DIR%\.mvn\wrapper\maven-wrapper.properties"

@REM ---------------------------------------------------------------------------
@REM Locate Java
@REM ---------------------------------------------------------------------------
@IF NOT "%JAVA_HOME%"=="" (
    @IF NOT EXIST "%JAVA_HOME%\bin\java.exe" (
        @ECHO Error: JAVA_HOME points to an invalid directory: %JAVA_HOME% >&2
        @EXIT /B 1
    )
    @SET "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) ELSE (
    @SET "JAVA_EXE=java"
    @WHERE java >NUL 2>&1
    @IF ERRORLEVEL 1 (
        @ECHO Error: 'java' command not found. Install a JDK and set JAVA_HOME or add java to PATH. >&2
        @EXIT /B 1
    )
)

@REM ---------------------------------------------------------------------------
@REM Download the wrapper JAR on first run
@REM ---------------------------------------------------------------------------
@IF NOT EXIST "%WRAPPER_JAR%" (
    @SET "WRAPPER_URL="
    @FOR /F "usebackq tokens=1,* delims==" %%A IN ("%WRAPPER_PROPS%") DO (
        @IF "%%A"=="wrapperUrl" @SET "WRAPPER_URL=%%B"
    )
    @IF "%WRAPPER_URL%"=="" (
        @ECHO Error: wrapperUrl not found in %WRAPPER_PROPS% >&2
        @EXIT /B 1
    )
    @ECHO Downloading Maven Wrapper JAR...
    @PowerShell -NoProfile -NonInteractive -Command ^
        "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; $ProgressPreference = 'SilentlyContinue'; Invoke-WebRequest -Uri '%WRAPPER_URL%' -OutFile '%WRAPPER_JAR%'" 2>&1
    @IF ERRORLEVEL 1 (
        @ECHO Error: PowerShell failed to download the Maven Wrapper JAR. >&2
        @EXIT /B 1
    )
    @ECHO Maven Wrapper JAR downloaded successfully.
)

@REM ---------------------------------------------------------------------------
@REM Run Maven via the wrapper
@REM ---------------------------------------------------------------------------
@"%JAVA_EXE%" ^
    -classpath "%WRAPPER_JAR%" ^
    "-Dmaven.multiModuleProjectDirectory=%BASE_DIR%" ^
    org.apache.maven.wrapper.MavenWrapperMain %*
