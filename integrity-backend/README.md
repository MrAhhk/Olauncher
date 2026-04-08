# Play Integrity Backend

This service verifies Play Integrity tokens on the server side.

## 1) Prerequisites

- A Google Cloud project linked to your Play Console app.
- Play Integrity API enabled in that project.
- A service account with Play Console API access.
- Service account JSON key available on the server.

## 2) Configure Play Console access

1. In Play Console, go to `Setup` -> `API access`.
2. Link the same Google Cloud project.
3. Grant your service account access to this app (at least app-level view access).

## 3) Configure environment

Copy `.env.example` to `.env` and set values:

- `ANDROID_PACKAGE_NAME` must equal your Android package, e.g. `app.subconsciously`.
- `PORT` can stay `8080`.

Set Google credentials path:

- Windows PowerShell:
  - `$env:GOOGLE_APPLICATION_CREDENTIALS="C:\path\service-account.json"`
- Linux/macOS:
  - `export GOOGLE_APPLICATION_CREDENTIALS=/path/service-account.json`

## 4) Run backend

```bash
npm install
npm start
```

Health check:

- `GET /healthz`

## 5) Wire Android app

In `gradle.properties`:

```properties
INTEGRITY_BACKEND_URL=https://your-backend-domain
```

No trailing slash.
