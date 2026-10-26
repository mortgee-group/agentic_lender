# Agentic Lender — AL-Web

React + TypeScript + Vite portal for **mortgage brokers and bank underwriters**, matching the GroupProject-Year3-2026 specification.

## Current frontend features

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
ed:Demo1234!
student:College2026!
```

`server/demo-roles.json` maps `ed` to Broker and `student` to Underwriter. Role mapping is not secure or production-ready. Sessions are ephemeral and credentials are stored locally in plain text. Never use real passwords or deploy this authentication service.

To check AL-Core status, run FastAPI on http://127.0.0.1:8000 and put `VITE_API_BASE_URL=http://127.0.0.1:8000` into ignored `.env.local`.

## Integration limitations

- All application and agent audit content is **fabricated UI sample data**, not borrower records, machine-learning estimates, AI-generated outcomes, or real underwriting decisions.
- AL-Web currently checks only the AL-Core health endpoint; it does not submit loans or access protected APIs.
- AL-Core must enforce Firebase authentication and broker-assignment/underwriter permissions. Client-side filtering cannot enforce data security.
- The actual agent API output contract, DTI definitions, stress tests, credit memoranda and ledger approvals remain to be integrated.
- Do not use this prototype to make real lending decisions.

See `STAGE3-TESTING.md` for the acceptance checklist.
