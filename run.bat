@echo off

set "JAVAC=javac"
set "JAVA=java"

where javac > nul 2> nul
if errorlevel 1 (
    for /d %%D in ("%ProgramFiles%\Eclipse Adoptium\jdk-*") do (
        if exist "%%~fD\bin\javac.exe" (
            set "JAVAC=%%~fD\bin\javac.exe"
            set "JAVA=%%~fD\bin\java.exe"
        )
    )
)

if not exist "%JAVAC%" (
    where javac > nul 2> nul
    if errorlevel 1 (
        echo JDK was not found.
        echo Please install JDK 17 or later, then reopen PowerShell or restart Windows.
        pause
        exit /b 1
    )
)

if not exist out mkdir out
"%JAVAC%" -encoding UTF-8 -d out src\main\java\fatfs\*.java
if errorlevel 1 (
    echo Build failed.
    pause
    exit /b 1
)

"%JAVA%" -cp out fatfs.Main
