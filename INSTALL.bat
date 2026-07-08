@echo off
title AndroWatch - Installation
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\setup-and-install.ps1" %*
if errorlevel 1 pause
