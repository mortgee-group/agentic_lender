# Stage 1: Broker / Underwriter Portal

This is a **local demonstration**, not a production authorisation system. Loan applications are fictional and kept in `src/data/sampleApplications.ts`.

## Run

From `ALWeb/AL-Web-React-Dashboard`:

```powershell
npm ci
npm run dev:all
```

The demo authentication server reads `server/users.txt` (ignored by Git). If it is absent, create it with **fictional** values:

```text
ed:Demo1234!
student:College2026!
```

Role mapping is held separately in `server/demo-roles.json`: `ed` is a broker; `student` is an underwriter. Other local accounts default to broker for testing. Never use actual passwords. Restart `dev:all` after changing the role map.

## Acceptance checklist

1. Sign in with `ed`: Broker Dashboard appears; only two fictional assigned applications are shown.
2. Broker navigation includes Assigned Pipeline, Loan Files and AI Audit; **not** Stress Testing or Settlement.
3. Log out and sign in with `student`: Underwriter Dashboard shows all four fictional applications and includes Stress Testing and Settlement.
4. Refresh the browser and verify the role remains correctly displayed in the header.
5. Switch between portal navigation placeholders without runtime errors.
6. Use **Legacy Demo Tools** to open the original valuation Dashboard, New Valuation, Results, AI Insights and History. Test the existing form.
7. Verify the original AL-Core health indicator still reads Connected when FastAPI is running.
8. Log out and verify the portal is no longer visible.
9. Run `npm run build` and check for TypeScript compilation errors.

## Deferred to Stage 2

Real loan pipeline, loan files, agent audit logs, risk simulator, approvals and exports require their agreed API contracts. No borrower financial data or blockchain transactions are submitted in this stage. Firebase/AL-Core must enforce access rights; hiding links is only a prototype behaviour.
