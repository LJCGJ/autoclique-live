@echo off
setlocal
set ADB=D:\Android\Sdk\platform-tools\adb.exe -s emulator-5554
set DIR=D:\Projetos\autoclique-live

if "%1"=="tap" %ADB% shell input tap %2 %3
if "%1"=="swipe" %ADB% shell input swipe %2 %3 %4 %5 %6
if "%1"=="text" %ADB% shell input text %2
if "%1"=="key" %ADB% shell input keyevent %2
if "%1"=="size" %ADB% shell wm size > "%DIR%\out.txt" 2>&1
if "%1"=="reset" (
  %ADB% shell pm clear com.autoclique.live > "%DIR%\out.txt" 2>&1
  %ADB% shell appops set com.autoclique.live SYSTEM_ALERT_WINDOW allow >> "%DIR%\out.txt" 2>&1
  %ADB% shell monkey -p com.autoclique.live -c android.intent.category.LAUNCHER 1 >> "%DIR%\out.txt" 2>&1
)
if "%1"=="recstart" %ADB% emu screenrecord start C:\Users\LeonardoJoseCordeiro\Desktop\autoclique-demo.webm > "%DIR%\out.txt" 2>&1
if "%1"=="recstop" %ADB% emu screenrecord stop > "%DIR%\out.txt" 2>&1

timeout /t 1 /nobreak >nul
%ADB% exec-out screencap -p > "%DIR%\shot.png" 2>nul
endlocal
