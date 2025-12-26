@echo off
REM Wrapper script for gradlew that forces native services to be disabled
REM This works around the DLL loading issue on Windows 10

set GRADLE_OPTS=-Dorg.gradle.native=false -Dorg.gradle.file.events=false -Dorg.gradle.vfs.watch=false
set JAVA_OPTS=-Dorg.gradle.native=false -Dorg.gradle.file.events=false

call gradlew.bat %* -Dorg.gradle.native=false -Dorg.gradle.file.events=false -Dorg.gradle.vfs.watch=false




