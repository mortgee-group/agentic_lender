import type { PropertyRecord } from "../types/property";
const euro = (value: number) => new Intl.NumberFormat("en-IE", {style:"currency", currency:"EUR", maximumFractionDigits:0}).format(value);
export default function RecentValuations({records, onView, onViewAll}: {records: PropertyRecord[]; onView: (record: PropertyRecord) => void; onViewAll: () => void}) {
  return <div className="panel section"><div className="table-head"><div><h2>Recent Valuations</h2><p style={{fontSize:12, margin:0}}>Example records — not real property valuations</p></div><button type="button" className="btn ghost" onClick={onViewAll}>View All →</button></div>
    <div className="table-wrap"><table><thead><tr><th>Property Address</th><th>Property Type</th><th>Estimated Value</th><th>Date</th><th>Status</th><th></th></tr></thead>
      <tbody>{records.slice(0,3).map(r => <tr key={r.id}><td><span className="strong">{r.address}</span><div style={{fontSize:11,color:"#94a0af"}}>{r.county}</div></td><td>{r.type}</td><td className="strong">{euro(r.value)}</td><td>{r.date}</td><td><span className={`status ${r.demo ? "demo" : ""}`}>{r.demo ? "Sample" : "Demo created"}</span></td><td><button type="button" className="link" onClick={() => onView(r)}>View Details →</button></td></tr>)}</tbody>
    </table></div></div>;
}
