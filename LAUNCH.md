# Sky Royal — Quick Launch Guide (Windows)

Follow these 5 steps to run the **Sky Royal** website and desktop app on a fresh Windows laptop.

---

## Prerequisites

Install **Java 17**, **Maven**, and **PostgreSQL 18** if you do not have them yet (run in PowerShell):

```powershell
winget install EclipseAdoptium.Temurin.17.JDK
winget install Apache.Maven
winget install PostgreSQL.PostgreSQL.18
```

Close and reopen your terminal after installing (the launch scripts also refresh `PATH` automatically from the Windows registry).

---

## 5 Steps to Launch

1. **Open PowerShell or Command Prompt in the repo root** (`flight-reservation-system`).
2. **Run initial database, config, and Maven install setup** (auto-detects PostgreSQL port `5432`/`5433`, creates the DB if missing, runs schema + migrations + seeds, and writes local `app.json`):
   ```cmd
   .\scripts\setup.cmd
   ```
   To run the full test suite against your fresh database right away:
   ```cmd
   mvn clean install
   ```
3. **Refresh rolling 14-day flights & auth accounts before any demo** (optional if you just ran `setup.cmd`):
   ```cmd
   .\scripts\reseed.cmd
   ```
4. **Start the Sky Royal Website** (prints both `http://localhost:8080` and your LAN URL `http://<your-ip>:8080` after the build succeeds):
   ```cmd
   .\scripts\run-web.cmd
   ```
5. **Start the Sky Royal Desktop App** (in a second terminal window):
   ```cmd
   .\scripts\run-desktop.cmd
   ```

*(You can also run the underlying `.ps1` scripts directly if PowerShell execution policy allows it.)*

---

## Maven Verification Profiles

- **Standard tests** (`mvn clean install` or `mvn clean verify`): runs all unit, web, JavaFX layout, and self-contained PostgreSQL integration tests on a fresh `setup.cmd` database.
- **Full-cabin & demo-DB suite** (`mvn verify -Pfull-it`): additionally runs `@Tag("integration-full")` tests that expect full A320 cabin templates (`seed_full_cabin.sql`) and seeded bookings for demo user `12`.

---

## Demo Accounts (`core/sql/seed_auth_data.sql`)

Username or email both work on the login page (`/login`) and desktop app:

| Role | Username | Email | Password |
|---|---|---|---|
| `ADMIN` | `testadmin` | `testadmin@example.com` | `Passw0rd!` |
| `CUSTOMER` | `customer` | `customer@example.com` | `Passw0rd!` |

---

## If You See X

| If you see... | Why it happened & how to fix it |
|---|---|
| `running scripts is disabled on this system` | Use the `.cmd` wrappers (`.\scripts\setup.cmd`, `.\scripts\run-web.cmd`, etc.) which bypass PowerShell's script execution policy. |
| `The JAVA_HOME environment variable is not defined correctly` | `JAVA_HOME` is unset or points to a JRE (for example a VS Code embedded JRE without `bin\javac.exe`). The `scripts\*.cmd` / `scripts\*.ps1` scripts auto-detect an installed JDK 17+ for the current run and print `[FIX] JAVA_HOME set to ...`. If none is installed, run `winget install EclipseAdoptium.Temurin.17.JDK` and reopen PowerShell. |
| `Could not find artifact com.group2.flightres:flight-reservation-system:pom:1.0.0` | Run `.\scripts\setup.cmd` (or `mvn -pl core -am -DskipTests install`). `run-web.cmd` and `run-desktop.cmd` now pass `-am` automatically. |
| `PostgreSQL connection failed: wrong password for user ..., or wrong port` | Re-run `.\scripts\setup.cmd`, accept the detected port (`5432` or `5433`), and enter the exact `postgres` password set during PostgreSQL installation. |
| `Host must be a hostname or IP ... not a URL` | Enter `127.0.0.1` or `localhost` (without `http://` or `/`). |
| `Port 8080 already in use` | Set `$env:PORT = "8081"` (PowerShell) or `set PORT=8081` (CMD) before running `.\scripts\run-web.cmd`. |
| `java` / `mvn` / `psql` not found after `winget install` | Open a fresh terminal window; `setup.cmd` also refreshes `PATH` from the Windows Machine & User registry. |

---

## Demo on a phone

Every boarding pass embeds an HMAC-signed QR verification URL (`{publicBaseUrl}/verify/{PNR}?sig=...`). To scan a boarding pass QR code from a phone on the same Wi-Fi instead of `localhost`:

1. Run `.\scripts\run-web.cmd` and copy the `LAN URL: http://<LAN-IP>:8080` printed on startup.
2. Configure `publicBaseUrl = http://<LAN-IP>:8080` in `core/src/main/resources/com/group2/flightres/app.json` (or export `$env:SKY_ROYAL_PUBLIC_BASE_URL = "http://<LAN-IP>:8080"` before running `.\scripts\run-web.cmd`).
3. Ensure Windows Firewall allows TCP port `8080` on the local network, then scan the QR code on `/confirmation/{PNR}` or the downloaded PDF from your phone camera.

