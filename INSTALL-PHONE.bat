@echo off
title AndroWatch - Installer sur telephone
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\install-to-device.ps1" %*
if errorlevel 1 pause
