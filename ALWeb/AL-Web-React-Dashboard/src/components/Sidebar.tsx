export type Page = "dashboard" | "new" | "insights" | "history" | "about" | "results";

const links: {page: Page; icon: string; label: string}[] = [
  {page: "dashboard", icon: "▦", label: "Dashboard"},
  {page: "new", icon: "⊕", label: "New Valuation"},
  {page: "insights", icon: "✧", label: "AI Insights"},
  {page: "history", icon: "▤", label: "Valuation History"},
  {page: "about", icon: "ⓘ", label: "About Project"},
];

export default function Sidebar({page, onNavigate}: {page: Page; onNavigate: (page: Page) => void}) {
  return <aside className="sidebar">
    <div className="logo"><span className="mark">⌂</span><span className="logotext">AL-Web</span></div>
    <div className="small-label">WORKSPACE</div>
    <nav className="nav" aria-label="Main navigation">
      {links.map(link => <button type="button" key={link.page} className={page === link.page ? "active" : ""} onClick={() => onNavigate(link.page)} aria-current={page === link.page ? "page" : undefined}>
        <span className="ico" aria-hidden="true">{link.icon}</span><span className="navtext">{link.label}</span>
      </button>)}
    </nav>
    <div className="sidebar-foot">AL-Web Prototype<br/>College group project<br/>Demo only · v1.0</div>
  </aside>;
}
