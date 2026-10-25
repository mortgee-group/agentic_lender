import type {PortalRole} from "../types/roles";
export type AuthUser = {username:string;role:PortalRole};
type AuthPayload = {user:AuthUser};
async function parseResponse(response:Response):Promise<AuthPayload>{
 const body:unknown=await response.json().catch(()=>({}));
 if(!response.ok){
   const message=typeof body==="object"&&body!==null&&"error" in body&&typeof body.error==="string"?body.error:"Server request failed";
   throw new Error(message);
 }
 if(typeof body!=="object"||body===null||!("user" in body))throw new Error("Invalid login response");
 const user=(body as {user?:unknown}).user;
 if(typeof user!=="object"||user===null||!("username" in user)||!("role" in user)
    ||typeof user.username!=="string"||(user.role!=="broker"&&user.role!=="underwriter"))throw new Error("Invalid user role from demo server");
 return {user:user as AuthUser};
}
export async function login(username:string,password:string):Promise<AuthUser>{
 const response=await fetch("/api/login",{method:"POST",headers:{"Content-Type":"application/json"},credentials:"same-origin",body:JSON.stringify({username,password})});
 return (await parseResponse(response)).user;
}
export async function getCurrentUser():Promise<AuthUser|null>{
 const response=await fetch("/api/me",{credentials:"same-origin"});
 if(response.status===401)return null;
 return (await parseResponse(response)).user;
}
export async function logout():Promise<void>{
 const response=await fetch("/api/logout",{method:"POST",credentials:"same-origin"});
 if(!response.ok)throw new Error("Logout failed");
}
