@echo off
setlocal
set ADB=D:\Android\Sdk\platform-tools\adb.exe -s emulator-5554
set OUT=C:\Users\LeonardoJoseCordeiro\Desktop\autoclique-demo.webm

rem ---------- PREPARACAO (fora da gravacao) ----------
if exist "%OUT%" del "%OUT%"
%ADB% shell settings put system show_touches 1
%ADB% shell pm clear com.autoclique.live
%ADB% shell appops set com.autoclique.live SYSTEM_ALERT_WINDOW allow
%ADB% shell monkey -p com.autoclique.live -c android.intent.category.LAUNCHER 1
timeout /t 5 /nobreak >nul

rem ---------- INICIA GRAVACAO ----------
%ADB% emu screenrecord start %OUT%
timeout /t 6 /nobreak >nul

rem tela de consentimento: rolar ate o fim devagar
%ADB% shell input swipe 540 1900 540 700 1500
timeout /t 4 /nobreak >nul

rem aceitar
%ADB% shell input tap 754 2290
timeout /t 3 /nobreak >nul

rem acessibilidade: Conceder -> Settings
%ADB% shell input tap 894 424
timeout /t 3 /nobreak >nul
%ADB% shell input tap 486 864
timeout /t 3 /nobreak >nul
%ADB% shell input tap 929 880
timeout /t 3 /nobreak >nul
%ADB% shell input keyevent 4
timeout /t 2 /nobreak >nul
%ADB% shell input keyevent 4
timeout /t 3 /nobreak >nul

rem notificacoes: Conceder -> Allow
%ADB% shell input tap 894 676
timeout /t 3 /nobreak >nul
%ADB% shell input tap 539 1313
timeout /t 2 /nobreak >nul

rem criar ponto: Adicionar ponto -> arrastar mira -> confirmar
%ADB% shell input tap 840 1162
timeout /t 3 /nobreak >nul
%ADB% shell input swipe 540 1200 910 1587 1200
timeout /t 2 /nobreak >nul
%ADB% shell input tap 846 2172
timeout /t 4 /nobreak >nul

rem se o editor abriu sozinho, fecha com Cancelar (senao cai em area vazia)
%ADB% shell input tap 704 1978
timeout /t 2 /nobreak >nul

rem iniciar automacao e deixar clicar
%ADB% shell input tap 540 2286
timeout /t 8 /nobreak >nul

rem mostrar a notificacao permanente
%ADB% shell input swipe 540 10 540 1200 500
timeout /t 4 /nobreak >nul
%ADB% shell input keyevent 4
timeout /t 2 /nobreak >nul

rem parar automacao
%ADB% shell input tap 540 2286
timeout /t 3 /nobreak >nul

rem captura de tela (MediaProjection): Conceder -> Entire screen -> Start
%ADB% shell input tap 894 802
timeout /t 4 /nobreak >nul
%ADB% shell input keyevent 61
%ADB% shell input keyevent 66
timeout /t 2 /nobreak >nul
%ADB% shell input keyevent 20
%ADB% shell input keyevent 66
timeout /t 2 /nobreak >nul
%ADB% shell input keyevent 61
%ADB% shell input keyevent 61
%ADB% shell input keyevent 66
timeout /t 4 /nobreak >nul

rem ---------- PARA GRAVACAO ----------
%ADB% emu screenrecord stop
timeout /t 2 /nobreak >nul
%ADB% shell settings put system show_touches 0
%ADB% exec-out screencap -p > D:\Projetos\autoclique-live\shot.png
echo video-pronto > D:\Projetos\autoclique-live\video-ok.txt
endlocal
