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

## Firebase Authentication and demo registration

The modular Firebase SDK is initialized in `src/firebase/firebase.ts` on demand.
`src/services/auth.ts` uses Firebase email/password sign-in, account creation, session
restoration and sign-out.

The three existing test email accounts retain their fixed demo roles. Additional
fictional accounts can be created from the Register tab by choosing a Borrower,
Broker or Underwriter *demo UI role*. For new accounts this selection is kept
in browser localStorage, keyed by Firebase UID, and is only available in the
same browser until local storage is cleared. It does not follow the account to
another browser or device.

This selection is not a trusted role, Firebase custom claim or Firestore profile,
and must never authorise AL-Core endpoints, Firestore rules, or sensitive
application data. Disable or restrict self-registration before public deployment.

### Registration test

1. Select **Register** on the login screen.
2. Enter a fresh fictional email, password and confirmation, and choose a role.
3. Submit. Firebase creates a real Authentication account and the chosen demo
   dashboard opens.
4. Refresh, then sign out and sign back in using the same browser. Confirm the
   dashboard role persists.
5. Confirm the new account appears in Firebase Console > Authentication > Users.
6. Repeat with a different email for the other roles. Confirm that the original
   three test accounts still work.

The next milestone is secure role provisioning with trusted backend-verified
profiles and properly protected data access.

## AL-Core Firebase-authenticated connectivity smoke test

An AL-Core connection panel appears after sign-in on every demo dashboard. It checks
`GET /health`, `GET /auth/me`, `GET /users/me`, and — when a backend profile exists —
the role-specific `GET /{role}/dashboard` endpoint. All protected calls send the Firebase
ID token as an Authorization Bearer header; never log or share the token.

The default backend URL is the documented Cloud Run development service. Override
`VITE_API_BASE_URL` in your local ignored `.env.local` if required and restart Vite.

An authenticated `/auth/me` result with matching UID proves that AL-Core accepts your
Firebase credentials. `404` from `/users/me` means a Firestore profile is missing,
not that authentication failed. `403` means access is denied by backend permissions.

If a demo account has no backend profile, the connection panel offers an explicit
**Create {role} profile** action using `POST /users/me`. This writes a real Firestore
profile and uses the logged-in user's selected demo role. The current AL-Core
backend permits client-selected Borrower, Broker and Underwriter roles for
**development testing only**. This is not suitable for production: unauthorised
role selection must be disabled or replaced with trusted role provisioning.

The loan pipeline still uses fictional client-side application data. Nikoll's
pending AI integration PR #18 is not required for this connectivity check.

### Profile creation contract (AL-Core current main / deployed API)

The AL-Core `POST /users/me` body requires `first_name`, `last_name` and
`role` (optional `phone_number`). Earlier `display_name`-only requests
return HTTP 422. The connection panel sends the selected demo role and requests test first and
last names rather than assuming the username is a real person's name. AL-Core persists the user profile in Firestore.

AL-Core currently accepts the three client-selected roles for **temporary testing**;
the Web profile form supports all three and labels privileged provisioning
accordingly. Role provisioning must be secured before deployment.

### Broker / Underwriter profile test

1. Log out of Borrower and sign in using an existing Broker or Underwriter
   Firebase development account.
2. Confirm backend health and Firebase token verification succeed, and that
   profile lookup returns HTTP 404 (if unprovisioned).
3. Enter fictional first and last names and explicitly create the profile.
4. Confirm the backend profile role and the matching role-specific dashboard
   permission are shown as successful.
5. Verify the `users/{uid}` document in Firestore and repeat for the other role.

Do not provision privileged test profiles for real users or deploy public self-
registration without backend-admin controls.

## Live mortgage application retrieval (read-only)

On the Dashboard, Loan Files and Pipeline pages, **Live mortgage applications**
calls `GET /applications` with the signed-in Firebase ID token. This is a
separate panel above the existing fictional sample dashboard. It never
substitutes sample data when the API returns an empty list.

AL-Core filters applications according to the **verified backend Firestore
profile**, not the UI-only selected role. Borrowers only receive their own
applications, brokers receive applications whose `brokerId` matches their UID,
and underwriters receive all accessible applications.

Click **Refresh** to re-fetch. Missing application records appear as a genuine
empty state; API errors including HTTP 403 are displayed instead of showing
synthetic records. A missing `riskTier` or `approvalLikelihood` is displayed
as **Not assessed**. The backend's Tier 1 (lowest risk) through Tier 5
(highest risk) nomenclature is retained.

The existing fictional pipeline and detail views are not yet wired to the
real application IDs. The next step is `GET /applications/{applicationId}`,
then Borrower `POST /applications`; do not open a demo file using a real ID.

## Borrower mortgage application submission (development-only)

The Borrower navigation now includes **New Application**. Its form matches
`POST /applications` in current `ALCore/app/models.py`: numeric income,
liabilities, deposit, term, asking price, county, floor area, bedroom and
bathroom count, year built, property type, and BER rating.

The user must have a verified Borrower profile in AL-Core. The API uses their
verified Firebase UID as `borrowerId`, calculates requested loan amount and
estimated LTV, and persists a real Firestore application. The frontend does
not submit borrowerId or allow brokers or underwriters to create applications.

**Use fictional test information only.** A successful submission shows the
new document ID and refreshes the Live Applications panel in My Applications.
The sample application tables remain separate and fictional. Sign in as the
Underwriter to confirm the new application is visible through their API
access. The Broker should not see the unassigned application (`brokerId=null`).

## Live application details — GET /applications/{applicationId}

Each row in the **Live mortgage applications** panel has **View details**.
This opens a dedicated read-only page and requests the selected Firestore
application from AL-Core using the current Firebase ID token. The API itself
checks the verified role and record ownership; the client cannot bypass that
by changing its demo role. A 403 or 404 is shown explicitly. The **Back**
button returns to the Dashboard, My Applications or Pipeline page where the
live record was opened.

The details page presents the application's status, assessment, finance,
property, assigned borrower UID and optional broker UID. Not-yet-assessed
fields are clearly marked. It is separate from the pre-existing fictional
Loan File Details page. No AI actions or updates are performed by viewing.

## Live API dashboards and PR preparation

The Borrower, Broker and Underwriter Dashboard, Pipeline and My Applications
screens now use the authenticated `GET /applications` response, without
showing fictional summary metrics or pipelines. All displayed totals, pending
counts, high-risk counts and assessed counts are calculated from the user's
AL-Core-permitted records. Empty responses remain empty.

A dedicated **API connection status** link in the sidebar footer exposes
the Firebase token, Firestore profile and backend role diagnostic panel.
These diagnostics are removed from normal dashboard and application pages.

Synthetic AI Audit examples and their fictional supporting file view remain
available for development but are clearly labelled `SAMPLE DATA`. The
header labels normal workspaces `LIVE AL-CORE`.

The branch currently changes only
`ALWeb/AL-Web-React-Dashboard/`. Before creating a PR, update it from
`main`, run `npm ci`, `npm run build`, verify borrower/broker/underwriter
workflows, and review development-only client-selected role provisioning.

## AI integration stage 1 (feature/web-ai-integration)

The live mortgage application detail page now offers explicit, confirmed
AI operations backed by AL-Core:
- **Run property assessment** is available for Broker and Underwriter roles
  (`POST /applications/{id}/assess`). Broker access additionally requires
  backend assignment to the particular application. This saves a valuation
  to the shared Firestore project.
- **Run AI underwriting** is visible only to Underwriter role
  (`POST /applications/{id}/underwrite`). It saves an AI decision and updates
  the application lifecycle. It may take considerably longer than valuation.

These actions do not fire automatically. Results and backend errors appear
inline. Only fictional development test records may be used. An AI
recommendation is not a final lending approval. The underlying services must
be deployed and configured; HTTP 502/503 may indicate AL-Core cannot reach
AL-AI, not a React bug.

Initial AI results are displayed as returned. A persistent structured audit
view and workflow status transitions remain future milestones. All changes
are confined to AL-Web.

## AI response presentation (offline development)

The live application detail page now includes **Preview example results
(offline)** for Brokers and Underwriters. This toggles the same structured
`AiResults` component used for real `POST /assess` and `POST /underwrite`
responses; no API call is made and no Firestore document is written.

Example response data is explicitly labelled **EXAMPLE DATA — NO AI
INFERENCE** and does not represent the opened mortgage application or real
market conditions. The component renders: ML valuation and affordability,
AI recommendation, risk tiers, conditions, model-specific agent output,
raw trail, and credit memorandum. Actual service results, when available,
have a separate **AL-CORE API RESPONSE** label.

The API currently provides these results directly after executing the
POST operations. Persistent historical decision retrieval will require an
authorised read endpoint in AL-Core; reloading the page does not restore
the full detailed decision trail.

## Registration and persistent user profiles

New test registrations collect first name, last name, phone number, email, password and development role. After Firebase signup, AL-Web sends the Firebase ID token and profile fields (`first_name`, `last_name`, `phone_number`, `role`) to `POST /users/me`. Successful signup requires a confirmed AL-Core profile. Sign-in and restored sessions prefer the role from `GET /users/me`; legacy accounts without a profile may use the existing local recovery role and create a missing profile on the AL-Core connection status page. If profile creation fails after signup, sign in with the same account rather than creating another one. Do not use real customer information. Privileged role selection remains temporary and must be restricted by AL-Core before production.
