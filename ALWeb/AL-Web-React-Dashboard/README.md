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

## Run locally (Firebase Authentication test users)

Install Node.js 24.12.0+ (or Node.js 26), then from this folder:

```powershell
npm ci
npm run build
npm run dev
```

Open http://localhost:5173. The active login uses **Firebase Authentication**;
the local Node demo login server is no longer used. You do not need `npm run dev:all`.
To check AL-Core status, separately run FastAPI on http://127.0.0.1:8000 and set
`VITE_API_BASE_URL=http://127.0.0.1:8000` in the ignored `.env.local`.

Copy `.env.example` to `.env.local` and fill in the Firebase Web SDK configuration
from the shared `agentic-lender-8ad70` project. Restart Vite after editing env vars.

Firebase Console must have Email/Password sign-in enabled. Three manually created
fictional test users are recognised by the UI:
- `borrower@test.agenticlender.ie` -> borrower demo profile `borrower-test`
- `broker@test.agenticlender.ie` -> broker demo profile `ed`
- `underwriter@test.agenticlender.ie` -> underwriter demo profile `student`

Use the passwords configured in Firebase Console, **not** the old local demo
passwords. Login, refresh the page to confirm persistence, log out, then sign
in using each role. The existing `server/users.txt` and `server/demo-roles.json`
are legacy demo server fixtures and are not used by the current Web login.

**Important**: Role assignment is a hardcoded frontend allowlist solely for UI
testing, not a security mechanism. All loan applications remain fictional and
bundled in the browser code. Do not connect protected data based on this role
mapping. Authorised access must be enforced by verified Firebase ID tokens,
trusted role claims / profile provisioning, Firestore security rules and AL-Core.
The Register tab is a temporary *development-only* self-registration pathway. It creates a real Firebase Authentication user, but stores the selected role only in this browser's localStorage under the Firebase UID. It does not grant Firebase custom claims or database/API permissions. Disable or restrict registration before public deployment.

## Integration limitations

- All application and agent audit content is **fabricated UI sample data**, not borrower records, machine-learning estimates, AI-generated outcomes, or real underwriting decisions.
- AL-Web currently checks only the AL-Core health endpoint; it does not submit loans or access protected APIs.
- AL-Core must enforce Firebase authentication and broker-assignment/underwriter permissions. Client-side filtering cannot enforce data security, including borrower ownership and broker assignment.
- The actual agent API output contract, DTI definitions, stress tests, credit memoranda and ledger approvals remain to be integrated.
- Do not use this prototype to make real lending decisions.

See `STAGE3-TESTING.md` for the acceptance checklist.

## Firebase Authentication integration

The modular Firebase SDK is initialized in `src/firebase/firebase.ts` on demand.
`src/services/auth.ts` uses `signInWithEmailAndPassword`, `authStateReady`,
and `signOut`. The browser's Firebase persistence restores sessions after refresh.
Only three known demo emails receive a local display role. All other Firebase
users are denied access to the prototype portal screens, even if their Firebase
credentials are valid. The next milestone is trusted backend role resolution
and secured profile/application access.\n\n### Registration test\n\n1. In the login screen select **Register**. Enter a fresh fictional email and password, confirm the password, and choose Borrower, Broker, or Underwriter.\n2. Submit. Firebase creates a real Authentication user; the role changes only the demo dashboard.\n3. Refresh, sign out and sign back in on the same browser. Check the chosen dashboard is restored.\n4. Verify the new user appears in Firebase Console > Authentication > Users.\n5. Repeat for all roles using distinct test emails; existing three test accounts should still work.\n6. Do not use this demo role for AL-Core authorization or Firestore security rules.\n

