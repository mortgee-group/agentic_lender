import type { ReactNode } from "react";
export default function SummaryCard({title, value, icon, note, compact=false}: {title: string; value: ReactNode; icon: string; note: string; compact?: boolean}) {
  return <div className="panel stat">
    <div className="flex-between"><span className="label">{title}</span><span className="iconbox" aria-hidden="true">{icon}</span></div>
    <div className="value" style={compact ? {fontSize: 24} : undefined}>{value}</div>
    <div className="mini">{note}</div>
  </div>;
}
