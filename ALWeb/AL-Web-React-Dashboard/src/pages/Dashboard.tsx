import { useState, type FormEvent } from "react";
import SummaryCard from "../components/SummaryCard";
import RecentValuations from "../components/RecentValuations";
import type { PropertyRecord } from "../types/property";
import BackendStatus from "../components/BackendStatus";

export type QuickProperty = { address: string; type: string };
type Props = { records: PropertyRecord[]; onNewValuation: (quick?: QuickProperty) => void; onInsights: () => void; onHistory: () => void; onViewRecord: (r: PropertyRecord) => void };

export default function Dashboard({records, onNewValuation, onInsights, onHistory, onViewRecord}: Props) {
  const [address, setAddress] = useState("");
  const [type, setType] = useState("");
  function handleQuickStart(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    onNewValuation({ address, type });
  }
  return <section className="page active" id="page-dashboard">

    <div className="flex-between">
      <div>
        <span className="eyebrow">Overview</span>
        <h1 className="page-title">Property Valuation Dashboard</h1>
        <p className="subtitle">
          AI-powered property estimates and insights, all in one place.
        </p>
      </div>

      <button
        type="button"
        className="btn primary"
        onClick={() => onNewValuation()}
      >
        ＋ Start New Valuation
      </button>
    </div>

    {/* AL-Core Backend Connection Status */}
    <BackendStatus />

    <div className="grid3">

      <SummaryCard title="Total Valuations" icon="▤" value={records.length} note="Sample & local demo records" />
      <SummaryCard title="Recent Valuations" icon="⌁" value={Math.min(records.length,3)} note="Visible in your demo history" />
      <SummaryCard title="Last Valuation Date" icon="▦" value={records[0]?.date ?? "—"} note="Most recent sample or demo entry" compact />
    </div>
    <div className="split"><div className="panel pad"><span className="eyebrow">Get started</span><h2 style={{marginTop:7}}>Get a Property Valuation</h2><p>Enter the basics below, then complete the detailed property form.</p>
      <form onSubmit={handleQuickStart}><div className="fields"><div className="field full"><label htmlFor="quickAddress">Property address</label><input id="quickAddress" placeholder="e.g. 12 Example Street, Dublin" value={address} onChange={e => setAddress(e.target.value)} /></div>
      <div className="field"><label htmlFor="quickType">Property type</label><select id="quickType" value={type} onChange={e => setType(e.target.value)}><option value="">Select a property type</option><option>Detached house</option><option>Semi-detached house</option><option>Terraced house</option><option>Apartment</option><option>Other</option></select></div></div><div className="form-actions"><button type="submit" className="btn primary">Enter Property Details →</button></div></form>
    </div><div className="panel pad"><div className="insight-icon">✧</div><h2>AI Valuation Insights</h2><p>Understand the main factors that may influence a property’s estimated value.</p><ul className="mini-list"><li>Estimated price range</li><li>Property feature summary</li><li>Illustrative AI explanation</li></ul><button type="button" className="btn ghost" onClick={onInsights}>View Example Insights →</button></div></div>
    <RecentValuations records={records} onView={onViewRecord} onViewAll={onHistory} />
  </section>;
}


