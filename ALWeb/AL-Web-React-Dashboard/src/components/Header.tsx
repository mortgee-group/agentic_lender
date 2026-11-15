import type {PortalRole} from "../types/roles";
import {roleLabel} from "../types/roles";
export default function Header({title,username,role,onLogout,isDemo=false}:{title:string;username:string;role:PortalRole;onLogout:()=>void;isDemo?:boolean}){
 return <header className="topbar"><div className="crumb">{title}</div><div className="topright">
 <span className="tag">{isDemo?"SAMPLE DATA":"LIVE AL-CORE"}</span><span className="role-chip">{roleLabel(role)}</span>
 <span className="account-name">{username}</span><div className="avatar" title={username}>{username.slice(0,2).toUpperCase()}</div>
 <button className="btn secondary logout-btn" type="button" onClick={onLogout}>Log out</button>
 </div></header>;
}
