export default function Header({title, username, onLogout}: {title: string;username:string;onLogout:()=>void}) {
  return <header className="topbar"><div className="crumb">{title}</div><div className="topright"><span className="tag">WIREFRAME PROTOTYPE</span><span className="account-name">{username}</span><div className="avatar" title={`Signed in as ${username}`}>{username.slice(0,2).toUpperCase()}</div><button className="btn secondary logout-btn" type="button" onClick={onLogout}>Log out</button></div></header>;
}
