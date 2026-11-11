# Agentic Lender — AL-Web

React + TypeScript + Vite portal for **borrowers, mortgage brokers and bank underwriters**, matching the GroupProject-Year3-2026 specification.

## Current frontend features

- Borrower dashboard, own sample application list and read-only progress detail
- Broker dashboard and assigned-client loan pipeline
- Underwriter dashboard and fictional portfolio overview
- Search, status/risk/LTV filters, sorting and read-only loan details
- AI Audit & Deliberation view showing **synthetic**, per-application agent findings and illustrative consensus
- Stress Testing and Settlement & Export placeholders
- Local AL-Core /health connectivity indicator

Earlier valuation form/results components remain in source for possible reuse, but are no longer linked to the active portal.

## Run locally

From this folder:

```powershell
npm ci
npm run build
npm run dev:all
```

Open http://localhost:5173. `dev:all` starts Vite and the local Node demo authentication service.

Create an ignored `server/users.txt` with **fictional test-only credentials** if it does not exist:

```text
borrower-test:BorrowerDemo123!
ed:Demo1234!
student:College2026!
```

`server/demo-roles.json` maps `borrower-test` to Borrower, `ed` to Broker and `student` to Underwriter. Role mapping is not secure or production-ready. Sessions are ephemeral and credentials are stored locally in plain text. Never use real passwords or deploy this authentication service.

To check AL-Core status, run FastAPI on http://127.0.0.1:8000 and put `VITE_API_BASE_URL=http://127.0.0.1:8000` into ignored `.env.local`.

## Integration limitations

- All application and agent audit content is **fabricated UI sample data**, not borrower records, machine-learning estimates, AI-generated outcomes, or real underwriting decisions.
- AL-Web currently checks only the AL-Core health endpoint; it does not submit loans or access protected APIs.
- AL-Core must enforce Firebase authentication and broker-assignment/underwriter permissions. Client-side filtering cannot enforce data security, including borrower ownership and broker assignment.
- The actual agent API output contract, DTI definitions, stress tests, credit memoranda and ledger approvals remain to be integrated.
- Do not use this prototype to make real lending decisions.

See `STAGE3-TESTING.md` for the acceptance checklist.

## Firebase SDK preparation (not yet live authentication)

Nikoll registered AL-Web in the Firebase project `agentic-lender-8ad70`.
The app provides `src/firebase/firebase.ts` with lazy Firebase app/Auth initialisation, but
the active login still uses the **local demonstration server**.

1. In this directory run `npm install firebase`. This updates both `package.json` and
   `package-lock.json`; commit **both files together**.
2. Copy `.env.example` to `.env.local` and populate the empty values from the exact
   Firebase Web SDK configuration received from Nikoll. Do not manually change
   the Firebase project's `apiKey` or `appId`.
3. Run `npm run build` and `npm run dev:all`. Verify existing three-role demo login.
4. For a configuration-only check, temporarily import `getFirebaseApp` from
   `./firebase/firebase` in `src/main.tsx` and run `console.log(getFirebaseApp().options.projectId)`,
   then remove the temporary test before committing. Firebase SDK initialization does
   **not** confirm that user authentication or Firestore permissions work.

Vite exposes `VITE_*` variables to browser code. They are not secrets.
Private service-account keys must never be used in the frontend.
Do not run `npm ci` after adding an unsynchronised dependency to `package.json`;
commit the npm-generated lockfile first. The real auth migration will be a separate change.
