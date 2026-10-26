# Stage 3 — Navigation cleanup and AI Audit console

Run in `ALWeb/AL-Web-React-Dashboard`:

```powershell
npm ci
npm run build
npm run dev:all
```

## Acceptance checks

1. Log in as `ed` (Broker). The left sidebar has **no Legacy Demo Tools** section and no Valuation Demo, New Valuation, Valuation History or wireframe links.
2. Dashboard, Assigned Pipeline, Loan Files and AI Audit remain accessible.
3. Broker AI Audit selector shows only AL-1001, AL-1002, AL-1005. It never shows AL-1003, AL-1004 or AL-1006.
4. Select AL-1001 (Applied): **Awaiting AI deliberation** is shown; no synthetic recommendation.
5. Select AL-1002: illustrative consensus, four expandable agent cards and a deliberation trail appear.
6. The agent details explicitly say no current market research or real AI inference has run.
7. Click View Loan File from AI Audit; then Open AI Audit from the loan file and verify the same application is selected.
8. Log out and in as `student` (Underwriter). All six applications appear in the AI Audit selector.
9. Select AL-1003: a fictional high-risk recommendation appears, labelled synthetic and not an approval.
10. Stress Testing and Settlement remain underwriter-only placeholders.
11. Verify no wireframe heading is visible in login, header, navigation or footer; fictional-data indicators remain.
12. Verify AL-Core health connection, existing Stage 2 filters and logout work as before.

## Integration boundaries

These views are **not live AI outputs**. `exampleAudit` is a mock data function to exercise the user interface. Later, replace it with a typed response from AL-Core/AL-AI containing application ID, agent findings, consensus, decision trail, model/version and timestamps. Access control must be enforced in the backend.
