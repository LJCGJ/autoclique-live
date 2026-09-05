@echo off
setlocal
set ADB=D:\Android\Sdk\platform-tools\adb.exe
set LOG=D:\Projetos\autoclique-live\prep-log.txt
set APK=D:\Projetos\autoclique-live\app\build\outputs\apk\release\app-release.apk

echo === preparacao do video === > "%LOG%"
"%ADB%" -s emulator-5554 wait-for-device >> "%LOG%" 2>&1
"%ADB%" -s emulator-5554 shell getprop sys.boot_completed >> "%LOG%" 2>&1

"%ADB%" -s emulator-5554 shell pm list packages com.autoclique.live > "%TEMP%\ac_pkg.txt" 2>&1
findstr /c:"com.autoclique.live" "%TEMP%\ac_pkg.txt" >nul
if errorlevel 1 (
  echo app nao instalado, instalando release... >> "%LOG%"
  if exist "%APK%" (
    "%ADB%" -s emulator-5554 install -r "%APK%" >> "%LOG%" 2>&1
  ) else (
    echo APK de release nao encontrado em %APK% >> "%LOG%"
    goto fim
  )
) else (
  echo app ja instalado >> "%LOG%"
)

echo limpando dados para mostrar tela de consentimento... >> "%LOG%"
"%ADB%" -s emulator-5554 shell pm clear com.autoclique.live >> "%LOG%" 2>&1

echo abrindo o app... >> "%LOG%"
"%ADB%" -s emulator-5554 shell monkey -p com.autoclique.live -c android.intent.category.LAUNCHER 1 >> "%LOG%" 2>&1

echo pronto >> "%LOG%"
:fim
endlocal
