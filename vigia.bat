@echo off
title vigia-autoclique
:loop
if exist "D:\Projetos\autoclique-live\stop.txt" (
  del "D:\Projetos\autoclique-live\stop.txt"
  exit
)
if exist "D:\Projetos\autoclique-live\cmd.txt" (
  ren "D:\Projetos\autoclique-live\cmd.txt" cmd-exec.txt
  for /f "usebackq delims=" %%c in ("D:\Projetos\autoclique-live\cmd-exec.txt") do call %%c
  del "D:\Projetos\autoclique-live\cmd-exec.txt"
  echo feito > "D:\Projetos\autoclique-live\vigia-ok.txt"
)
timeout /t 2 /nobreak >nul
goto loop
