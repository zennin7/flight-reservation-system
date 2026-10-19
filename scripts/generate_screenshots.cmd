@echo off
setlocal enabledelayedexpansion

set EDGE="C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
set BASE_URL=http://localhost:7070
set OUT_DIR=c:\Users\tttttttttttttttttttt\Desktop\flight-reservation-system-shadow\docs\screenshots\web
set REF=4GZUFL

if not exist "%OUT_DIR%" mkdir "%OUT_DIR%"

echo ==============================================================
echo Generating all 14 Web Screenshots (Desktop 1200x900 & Mobile 390x844)
echo ==============================================================

:: 01 Home
if not exist "%OUT_DIR%\01_home_desktop.png" (
    echo [1/7] Home Desktop...
    set P=%TEMP%\p1d_%RANDOM%
    %EDGE% --headless --disable-gpu --user-data-dir="!P!" --window-size=1200,900 --screenshot="%OUT_DIR%\01_home_desktop.png" "%BASE_URL%/"
    rd /s /q "!P!" 2>nul
) else (
    echo [1/7] Home Desktop already exists, skipping.
)

if not exist "%OUT_DIR%\01_home_mobile.png" (
    echo [1/7] Home Mobile...
    set P=%TEMP%\p1m_%RANDOM%
    %EDGE% --headless --disable-gpu --user-data-dir="!P!" --window-size=390,844 --screenshot="%OUT_DIR%\01_home_mobile.png" "%BASE_URL%/"
    rd /s /q "!P!" 2>nul
) else (
    echo [1/7] Home Mobile already exists, skipping.
)

:: 02 Flights
if not exist "%OUT_DIR%\02_flights_desktop.png" (
    echo [2/7] Flights Search Desktop...
    set P=%TEMP%\p2d_%RANDOM%
    %EDGE% --headless --disable-gpu --user-data-dir="!P!" --window-size=1200,900 --screenshot="%OUT_DIR%\02_flights_desktop.png" "%BASE_URL%/flights?origin=Johannesburg+(JNB)&destination=Cape+Town+(CPT)&date=2026-11-02"
    rd /s /q "!P!" 2>nul
) else (
    echo [2/7] Flights Search Desktop already exists, skipping.
)

if not exist "%OUT_DIR%\02_flights_mobile.png" (
    echo [2/7] Flights Search Mobile...
    set P=%TEMP%\p2m_%RANDOM%
    %EDGE% --headless --disable-gpu --user-data-dir="!P!" --window-size=390,844 --screenshot="%OUT_DIR%\02_flights_mobile.png" "%BASE_URL%/flights?origin=Johannesburg+(JNB)&destination=Cape+Town+(CPT)&date=2026-11-02"
    rd /s /q "!P!" 2>nul
) else (
    echo [2/7] Flights Search Mobile already exists, skipping.
)

:: 03 Seats
if not exist "%OUT_DIR%\03_seats_desktop.png" (
    echo [3/7] Seats Desktop...
    set P=%TEMP%\p3d_%RANDOM%
    %EDGE% --headless --disable-gpu --user-data-dir="!P!" --window-size=1200,900 --screenshot="%OUT_DIR%\03_seats_desktop.png" "%BASE_URL%/flights/1/seats"
    rd /s /q "!P!" 2>nul
) else (
    echo [3/7] Seats Desktop already exists, skipping.
)

if not exist "%OUT_DIR%\03_seats_mobile.png" (
    echo [3/7] Seats Mobile...
    set P=%TEMP%\p3m_%RANDOM%
    %EDGE% --headless --disable-gpu --user-data-dir="!P!" --window-size=390,844 --screenshot="%OUT_DIR%\03_seats_mobile.png" "%BASE_URL%/flights/1/seats"
    rd /s /q "!P!" 2>nul
) else (
    echo [3/7] Seats Mobile already exists, skipping.
)

:: 04 Confirmation
if not exist "%OUT_DIR%\04_confirmation_desktop.png" (
    echo [4/7] Confirmation Voucher Desktop...
    set P=%TEMP%\p4d_%RANDOM%
    %EDGE% --headless --disable-gpu --user-data-dir="!P!" --window-size=1200,900 --screenshot="%OUT_DIR%\04_confirmation_desktop.png" "%BASE_URL%/confirmation/%REF%"
    rd /s /q "!P!" 2>nul
) else (
    echo [4/7] Confirmation Voucher Desktop already exists, skipping.
)

if not exist "%OUT_DIR%\04_confirmation_mobile.png" (
    echo [4/7] Confirmation Voucher Mobile...
    set P=%TEMP%\p4m_%RANDOM%
    %EDGE% --headless --disable-gpu --user-data-dir="!P!" --window-size=390,844 --screenshot="%OUT_DIR%\04_confirmation_mobile.png" "%BASE_URL%/confirmation/%REF%"
    rd /s /q "!P!" 2>nul
) else (
    echo [4/7] Confirmation Voucher Mobile already exists, skipping.
)

:: 05 Login
if not exist "%OUT_DIR%\05_login_desktop.png" (
    echo [5/7] Login Desktop...
    set P=%TEMP%\p5d_%RANDOM%
    %EDGE% --headless --disable-gpu --user-data-dir="!P!" --window-size=1200,900 --screenshot="%OUT_DIR%\05_login_desktop.png" "%BASE_URL%/login"
    rd /s /q "!P!" 2>nul
) else (
    echo [5/7] Login Desktop already exists, skipping.
)

if not exist "%OUT_DIR%\05_login_mobile.png" (
    echo [5/7] Login Mobile...
    set P=%TEMP%\p5m_%RANDOM%
    %EDGE% --headless --disable-gpu --user-data-dir="!P!" --window-size=390,844 --screenshot="%OUT_DIR%\05_login_mobile.png" "%BASE_URL%/login"
    rd /s /q "!P!" 2>nul
) else (
    echo [5/7] Login Mobile already exists, skipping.
)

:: 06 About
if not exist "%OUT_DIR%\06_about_desktop.png" (
    echo [6/7] About Desktop...
    set P=%TEMP%\p6d_%RANDOM%
    %EDGE% --headless --disable-gpu --user-data-dir="!P!" --window-size=1200,900 --screenshot="%OUT_DIR%\06_about_desktop.png" "%BASE_URL%/about"
    rd /s /q "!P!" 2>nul
) else (
    echo [6/7] About Desktop already exists, skipping.
)

if not exist "%OUT_DIR%\06_about_mobile.png" (
    echo [6/7] About Mobile...
    set P=%TEMP%\p6m_%RANDOM%
    %EDGE% --headless --disable-gpu --user-data-dir="!P!" --window-size=390,844 --screenshot="%OUT_DIR%\06_about_mobile.png" "%BASE_URL%/about"
    rd /s /q "!P!" 2>nul
) else (
    echo [6/7] About Mobile already exists, skipping.
)

:: 07 Contact
if not exist "%OUT_DIR%\07_contact_desktop.png" (
    echo [7/7] Contact Desktop...
    set P=%TEMP%\p7d_%RANDOM%
    %EDGE% --headless --disable-gpu --user-data-dir="!P!" --window-size=1200,900 --screenshot="%OUT_DIR%\07_contact_desktop.png" "%BASE_URL%/contact"
    rd /s /q "!P!" 2>nul
) else (
    echo [7/7] Contact Desktop already exists, skipping.
)

if not exist "%OUT_DIR%\07_contact_mobile.png" (
    echo [7/7] Contact Mobile...
    set P=%TEMP%\p7m_%RANDOM%
    %EDGE% --headless --disable-gpu --user-data-dir="!P!" --window-size=390,844 --screenshot="%OUT_DIR%\07_contact_mobile.png" "%BASE_URL%/contact"
    rd /s /q "!P!" 2>nul
) else (
    echo [7/7] Contact Mobile already exists, skipping.
)

echo ==============================================================
echo Completed! Listing captured screenshots:
echo ==============================================================
dir "%OUT_DIR%"
