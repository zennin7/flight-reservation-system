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

Close and reopen PowerShell after installing so `java`, `mvn`, and `psql` are on your `PATH`.

---

## 5 Steps to Launch

1. **Open PowerShell in the repo root** (`flight-reservation-system`).
2. **Run initial database & config setup** (creates the DB if missing, runs schema + migrations + seeds, and creates your local `app.json`):
   ```powershell
   .\scripts\setup.ps1
   ```
3. **Refresh rolling 14-day flights before any demo** (optional if you just ran `setup.ps1`):
   ```powershell
   .\scripts\reseed.ps1
   ```
4. **Start the Sky Royal Website** (prints both `http://localhost:8080` and your LAN URL `http://<your-ip>:8080`):
   ```powershell
   .\scripts\run-web.ps1
   ```
5. **Start the Sky Royal Desktop App** (in a second PowerShell window):
   ```powershell
   .\scripts\run-desktop.ps1
   ```

---

## Demo Account (`core/sql/seed_auth_data.sql`)

| Role | Username | Email | Password |
|---|---|---|---|
| `ADMIN` | `testadmin` | `testadmin@example.com` | `Passw0rd!` |

---

## Troubleshooting

1. **`java` not found / wrong Java version**: Install Temurin JDK 17 (`winget install EclipseAdoptium.Temurin.17.JDK`), reopen PowerShell, and check `java -version` shows `17` or newer.
2. **`psql` not found**: Install PostgreSQL 18 (`winget install PostgreSQL.PostgreSQL.18`) or add `C:\Program Files\PostgreSQL\18\bin` to your Windows `PATH`.
3. **Connection refused / wrong port**: Make sure the PostgreSQL Windows service is running (`services.msc`) and check whether your instance listens on `5432` or `5433`. Re-run `.\scripts\setup.ps1` with the matching port.
4. **Password authentication failed**: Re-run `.\scripts\setup.ps1` and enter the exact `postgres` password you set during PostgreSQL installation.
5. **Port `8080` already in use**: Set a different port before launching the website:
   ```powershell
   $env:PORT = "8081"
   .\scripts\run-web.ps1
   ```
