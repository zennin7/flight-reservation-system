@echo off
setlocal enabledelayedexpansion

set EDGE="C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
set BASE_URL=http://localhost:7070
set OUT_DIR=c:\Users\tttttttttttttttttttt\Desktop\flight-reservation-system-shadow\docs\screenshots\dark
set REF=4GZUFL

if not exist "%OUT_DIR%" mkdir "%OUT_DIR%"

echo ==============================================================
echo Generating all Dark Web Screenshots
echo ==============================================================

:: 09 Web Home
echo [1/7] Dark Web Home...
set P=%TEMP%\p_dark_1_%RANDOM%
%EDGE% --headless --disable-gpu --user-data-dir="!P!" --blink-settings=forceDarkModeEnabled=true --window-size=1200,900 --screenshot="%OUT_DIR%\09-web-home.png" "%BASE_URL%/"
rd /s /q "!P!" 2>nul

:: 10 Web Flights
echo [2/7] Dark Web Flights...
set P=%TEMP%\p_dark_2_%RANDOM%
%EDGE% --headless --disable-gpu --user-data-dir="!P!" --blink-settings=forceDarkModeEnabled=true --window-size=1200,900 --screenshot="%OUT_DIR%\10-web-flights.png" "%BASE_URL%/flights?origin=Johannesburg+(JNB)&destination=Cape+Town+(CPT)&date=2026-11-02"
rd /s /q "!P!" 2>nul

:: 11 Web Seats
echo [3/7] Dark Web Seats...
set P=%TEMP%\p_dark_3_%RANDOM%
%EDGE% --headless --disable-gpu --user-data-dir="!P!" --blink-settings=forceDarkModeEnabled=true --window-size=1200,900 --screenshot="%OUT_DIR%\11-web-seats.png" "%BASE_URL%/flights/1/seats"
rd /s /q "!P!" 2>nul

:: 12 Web Confirmation
echo [4/7] Dark Web Confirmation...
set P=%TEMP%\p_dark_4_%RANDOM%
%EDGE% --headless --disable-gpu --user-data-dir="!P!" --blink-settings=forceDarkModeEnabled=true --window-size=1200,900 --screenshot="%OUT_DIR%\12-web-confirmation.png" "%BASE_URL%/confirmation/%REF%"
rd /s /q "!P!" 2>nul

:: 13 Web Login
echo [5/7] Dark Web Login...
set P=%TEMP%\p_dark_5_%RANDOM%
%EDGE% --headless --disable-gpu --user-data-dir="!P!" --blink-settings=forceDarkModeEnabled=true --window-size=1200,900 --screenshot="%OUT_DIR%\13-web-login.png" "%BASE_URL%/login"
rd /s /q "!P!" 2>nul

:: 14 Web About
echo [6/7] Dark Web About...
set P=%TEMP%\p_dark_6_%RANDOM%
%EDGE% --headless --disable-gpu --user-data-dir="!P!" --blink-settings=forceDarkModeEnabled=true --window-size=1200,900 --screenshot="%OUT_DIR%\14-web-about.png" "%BASE_URL%/about"
rd /s /q "!P!" 2>nul

:: 15 Web Contact
echo [7/7] Dark Web Contact...
set P=%TEMP%\p_dark_7_%RANDOM%
%EDGE% --headless --disable-gpu --user-data-dir="!P!" --blink-settings=forceDarkModeEnabled=true --window-size=1200,900 --screenshot="%OUT_DIR%\15-web-contact.png" "%BASE_URL%/contact"
rd /s /q "!P!" 2>nul

echo All dark web screenshots generated successfully!
dir "%OUT_DIR%"
