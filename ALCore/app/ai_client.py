import os

import httpx

AI_SERVICE_URL = os.getenv("AI_SERVICE_URL", "http://localhost:5000")


async def assess_application(application_data: dict) -> dict:
    """Request property valuation and affordability assessment from AL-AI."""
    async with httpx.AsyncClient(timeout=30.0) as client:
        response = await client.post(
            f"{AI_SERVICE_URL}/api/v1/ai/assess",
            json=application_data,
        )
        response.raise_for_status()
        return response.json()


async def underwrite_application(application_data: dict) -> dict:
    """Request a full underwriting decision from AL-AI."""
    async with httpx.AsyncClient(timeout=200.0) as client:
        response = await client.post(
            f"{AI_SERVICE_URL}/api/v1/ai/underwrite",
            json=application_data,
        )
        response.raise_for_status()
        return response.json()