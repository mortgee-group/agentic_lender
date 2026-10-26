# AL-Web – React + TypeScript (complete prototype with demo login)

Includes Dashboard, New Valuation, Valuation Results, AI Insights, Valuation History, About and Login.
The original AL-Web sample records and styling are retained. Valuations and AI insights are simulated.

## Start on Windows, macOS or Linux

Install an active Node.js LTS release, then run from this folder:

```bash
npm install
npm run dev:all
```

Open the Vite local address printed in the terminal (usually http://localhost:5173).
The combined command starts **both** the Vite frontend and the local login API server.

Alternatively, use two terminals:

- Terminal 1: `npm run server`
- Terminal 2: `npm run dev`

`npm run dev` **alone is not enough for login**.

## Test users

Credentials are read from **`server/users.txt`**, in `username:password` format:

- `ed` / `Demo1234!`
- `student` / `College2026!`

You can change these credentials in `server/users.txt`. Changes take effect on the next login attempt; no restart needed. Use *fictional test passwords only*. The file is excluded by `.gitignore`; if collaborating, create your own copy and don't commit real credentials. For a new checkout, the package contains the initial file.

## Login implementation

- Login screen built in `src/pages/Login.tsx` and styled consistently with the other pages.
- React sends credentials to local `POST /api/login`, and checks `GET /api/me` on refresh.
- Local Node server (`server/server.mjs`) reads `server/users.txt`, compares credentials, and creates a random server-side session.
- Browser receives an `HttpOnly`, `SameSite=Lax` cookie rather than a password or token in local storage.
- `POST /api/logout` invalidates the session; the app returns to Login.
- Vite proxy forwards `/api` to `http://127.0.0.1:3001` during development.
- Login is retained across browser refresh while the server is running. Sessions disappear if the server restarts.

## Important limitations

**This is a LOCAL DEVELOPMENT TEST ONLY, not production authentication.** Plain-text passwords are insecure and must **never** be used for real user accounts. The Node service is deliberately bound to the localhost interface and is not a production API. The React login is a frontend gate; it does *not* secure a future AL-Core API. AL-Core must independently authenticate and authorise every protected API operation before deployment. Add password hashes (e.g. Argon2id), HTTPS, proper session expiry, CSRF protection and database-backed users for production. Browser data and generated valuations are still mock/session data and are not saved to the backend.

## Build frontend

```bash
npm run build
```

A frontend production build does **not** include a deployed authentication backend; login requires the local Node demo server. This setup is designed only for local testing.
