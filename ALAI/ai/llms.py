# ai/llms.py
import os
from crewai import LLM

def get_cloud_llm_openai():
    """OpenAI GPT-4o (native)"""
    return LLM(
        model="openai/gpt-4o",
        api_key=os.getenv("OPENAI_API_KEY"),
        temperature=0.2,
    )

def get_cloud_llm_gemini():
    return LLM(
        model="gemini/gemini-3.6-flash",
        api_key=os.getenv("GEMINI_API_KEY"),
        temperature=0.2,
        max_retries=5,      
        timeout=120,
    )
def get_cloud_llm_groq():
    return LLM(
       # model="groq/groq-llm-1.0",
        model="groq/openai/gpt-oss-120b",
        api_key=os.getenv("GROQ_API_KEY"),
        temperature=0.2,
        max_retries=5,      
        timeout=120,
    )

def get_local_llm():
    return LLM(
        model="ollama/llama3.2:latest",
        base_url="http://localhost:11434",
        temperature=0.1,
    )