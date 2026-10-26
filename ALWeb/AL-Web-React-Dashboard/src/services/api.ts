const BASE_URL = import.meta.env.VITE_API_BASE_URL;

export async function checkBackendHealth(): Promise<boolean> {
  const response = await fetch(`${BASE_URL}/health`);

  if (!response.ok) {
    throw new Error(`Backend returned ${response.status}`);
  }

  const data: { status: string } = await response.json();

  return data.status === "ok";
}