# Stage 2 — Loan Origination Pipeline

This stage uses fictional loan applications from `src/data/sampleApplications.ts`. There is no API persistence, automatic underwriting or blockchain interaction. UI filtering is not a security boundary; **AL-Core must enforce assignment permissions** on future endpoints.

## Start

In `ALWeb/AL-Web-React-Dashboard`:

```powershell
npm ci
npm run build
npm run dev:all
```

## Broker acceptance tests

1. Log in using the local broker demo account (`ed`, if configured in `server/users.txt`).
2. The Broker Dashboard shows **3** assigned fictional applications.
3. Open **Assigned Pipeline**: only AL-1001, AL-1002 and AL-1005 are visible.
4. Search for `Kildare`: only AL-1001 appears; clear search.
5. Filter **AI Deliberated**: only AL-1002 appears; reset.
6. Filter **Medium** risk: only AL-1002 and AL-1005 appear.
7. Filter **LTV 80% to under 90%**: only AL-1002 appears.
8. Filter an impossible combination and verify the empty-state + Clear filters.
9. Open AL-1001. Check its sample collateral values, illustrative borrower metrics and Back to Pipeline.
10. AL-1003 and AL-1004 must **not** appear in the broker's table.

## Underwriter acceptance tests

1. Log out, then sign in using the local underwriter demo account (`student`, if configured).
2. Dashboard shows **6** applications.
3. Open Loan Pipeline: all six records appear.
4. Filter **High** risk: AL-1003 appears.
5. Filter **90% and above**: AL-1003 appears.
6. Sort by **Highest requested amount**: AL-1002 appears first.
7. Open AL-1003 and verify loan-file details.
8. Open Loan Assessments sidebar entry; the shared listing works.
9. Refresh the browser; login and portal load successfully.
10. Stress Testing and Settlement remain placeholders; original valuation demo pages still function.

## Integration discussion with AL-Core

Proposed future protected read endpoints (not implemented here):
- `GET /applications` (server filters by assigned broker or underwriter role)
- `GET /applications/{id}` (server verifies entitlement)
- Application response shape: id, borrower, property details, asking price, ML valuation, loan amount, applicant income and debt, risk tier, lifecycle status, broker assignment, submission timestamp.

The specified DTI metric needs a shared definition across Core and AI. This demo shows annual existing-debt-payments divided by annual gross income and labels it as an **illustrative ratio**, not the underwriting DTI.

No real financial data, tokens, or permission decisions are derived from frontend demonstration code.
