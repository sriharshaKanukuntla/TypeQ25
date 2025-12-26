@echo off
echo ========================================
echo Compilazione e installazione TypeQ25
echo ========================================
echo.

REM Check if JAVA_HOME is already set
if defined JAVA_HOME (
    echo JAVA_HOME is set to: %JAVA_HOME%
    goto :build
)

echo JAVA_HOME not set. Searching for Java installation...
echo.

REM Search for Java in common locations
set JAVA_FOUND=0

REM Check Android Studio JBR (newer versions)
if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
    set JAVA_FOUND=1
    goto :javaFound
)

REM Check Android Studio JRE (older versions)
if exist "C:\Program Files\Android\Android Studio\jre\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Android\Android Studio\jre"
    set JAVA_FOUND=1
    goto :javaFound
)

REM Check local Android SDK JBR
if exist "%LOCALAPPDATA%\Android\Sdk\jbr\bin\java.exe" (
    set "JAVA_HOME=%LOCALAPPDATA%\Android\Sdk\jbr"
    set JAVA_FOUND=1
    goto :javaFound
)

REM Check Program Files Java
for /d %%i in ("C:\Program Files\Java\jdk*") do (
    if exist "%%i\bin\java.exe" (
        set "JAVA_HOME=%%i"
        set JAVA_FOUND=1
        goto :javaFound
    )
)

REM Check Program Files (x86) Java
for /d %%i in ("C:\Program Files (x86)\Java\jdk*") do (
    if exist "%%i\bin\java.exe" (
        set "JAVA_HOME=%%i"
        set JAVA_FOUND=1
        goto :javaFound
    )
)

REM Check if java is in PATH
where java >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    for /f "delims=" %%i in ('where java') do (
        set "JAVA_PATH=%%i"
        goto :findJavaHomeFromPath
    )
)

:findJavaHomeFromPath
if defined JAVA_PATH (
    REM Extract JAVA_HOME from java.exe path
    for %%i in ("%JAVA_PATH%") do set "JAVA_DIR=%%~dpi"
    for %%i in ("%JAVA_DIR%..") do set "JAVA_HOME=%%~fi"
    if exist "%JAVA_HOME%\bin\java.exe" (
        set JAVA_FOUND=1
        goto :javaFound
    )
)

:javaFound
if %JAVA_FOUND% EQU 1 (
    echo Found Java at: %JAVA_HOME%
    echo.
) else (
    echo ERROR: Java not found!
    echo.
    echo Please install Java or set JAVA_HOME manually.
    echo Common locations:
    echo   - Android Studio: C:\Program Files\Android\Android Studio\jbr
    echo   - JDK: C:\Program Files\Java\jdk-XX
    echo.
    pause
    exit /b 1
)

:build
REM Compila e installa l'app
call gradlew.bat installDebug

REM Verifica se l'installazione è riuscita
if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo Installazione completata con successo!
    echo ========================================
    echo.
    echo Avvio dell'app sul dispositivo...
    
    REM Lancia l'app sul dispositivo Android
    adb shell am start -n it.srik.TypeQ25/.MainActivity
    
    if %ERRORLEVEL% EQU 0 (
        echo.
        echo App avviata con successo!
    ) else (
        echo.
        echo ERRORE: Impossibile avviare l'app.
        echo Verifica che il dispositivo sia connesso e che ADB sia configurato correttamente.
    )
) else (
    echo.
    echo ERRORE: La compilazione/installazione non è riuscita.
    echo Verifica gli errori sopra.
    exit /b %ERRORLEVEL%
)

pause

