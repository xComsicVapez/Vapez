@echo off
REM Vapez SMP Windows launcher — Java 21+, 60G Generational ZGC
cd /d "%~dp0"
set MEMORY=60G
set JAR=
for %%f in (purpur-*.jar) do set JAR=%%f
if "%JAR%"=="" (
  echo Missing purpur jar. Run scripts\bootstrap.sh from Git Bash or WSL.
  exit /b 1
)
java -Xms%MEMORY% -Xmx%MEMORY% ^
  -XX:+UnlockExperimentalVMOptions ^
  -XX:+UseZGC ^
  -XX:+ZGenerational ^
  -XX:+AlwaysPreTouch ^
  -XX:+DisableExplicitGC ^
  -XX:+ParallelRefProcEnabled ^
  -XX:+PerfDisableSharedMem ^
  --add-modules=jdk.incubator.vector ^
  -Dfile.encoding=UTF-8 ^
  -jar %JAR% nogui
pause
