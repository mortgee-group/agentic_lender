export type AuthUser = { username: string };

async function parseResponse(response: Response): Promise<any> {
  const body: unknown = await response.json().catch(() => ({}));
  if (!response.ok) {
    const message = typeof body === "object" && body !== null && "error" in body && typeof body.error === "string"
      ? body.error : "Server request failed";
    throw new Error(message);
  }
  return body;
}

export async function login(username: string, password: string): Promise<AuthUser> {
  const response = await fetch("/api/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "same-origin",
    body: JSON.stringify({ username, password }),
  });
  const data = await parseResponse(response);
  return data.user as AuthUser;
}

export async function getCurrentUser(): Promise<AuthUser | null> {
  const response = await fetch("/api/me", { credentials: "same-origin" });
  if (response.status === 401) return null;
  const data = await parseResponse(response);
  return data.user as AuthUser;
}

export async function logout(): Promise<void> {
  await parseResponse(await fetch("/api/logout", { method: "POST", credentials: "same-origin" }));
}
