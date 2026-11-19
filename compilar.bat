@echo off
setlocal EnableExtensions
cd /d "%~dp0"

echo ================================================
echo       COMPILANDO BYTEUP JEI 1.2.0
echo       Purpur 26.3 - Java 25 - Zero mods
echo ================================================

where java >nul 2>nul
if errorlevel 1 (
  echo ERRO: Java nao encontrado no PATH.
  echo Instale o JDK 25 e adicione a pasta bin ao PATH.
  pause
  exit /b 1
)

set "JAVA_VERSION="
for /f "tokens=3" %%V in ('java -XshowSettings:properties -version 2^>^&1 ^| findstr /C:"java.specification.version"') do set "JAVA_VERSION=%%V"
if not "%JAVA_VERSION%"=="25" (
  echo ERRO: este projeto exige JDK 25. Versao detectada: %JAVA_VERSION%
  java -version
  pause
  exit /b 1
)

echo JDK detectado corretamente:
java -version

echo.
where gradle >nul 2>nul
if errorlevel 1 (
  echo ERRO: Gradle nao encontrado no PATH.
  echo Instale Gradle 9 ou configure gradle no PATH.
  pause
  exit /b 1
)

gradle --no-daemon clean build
if errorlevel 1 (
  echo.
  echo FALHA NA COMPILACAO. Copie o log completo para a conversa.
  pause
  exit /b 1
)

echo.
echo ================================================
echo JAR GERADO:
echo build\libs\ByteUP-JEI-1.2.2.jar
echo ================================================
echo Copie somente esse JAR para a pasta plugins do Purpur 26.3.
pause
