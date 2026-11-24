# tests/test_llms.py
import os
import pytest
#from ai.llms import get_cloud_llm_openai
from ai.llms import get_cloud_llm_gemini, get_cloud_llm_groq, get_local_llm


# @pytest.mark.skipif(
#     not os.getenv("OPENAI_API_KEY"),
#     reason="OPENAI_API_KEY not set",
# )
# def test_openai_responds():
#     reply = get_cloud_llm_openai().call("Reply with exactly the word: OK")
#     assert "OK" in reply.upper()


# @pytest.mark.skipif(
#     not os.getenv("GEMINI_API_KEY"),
#     reason="GEMINI_API_KEY not set",
# )
# def test_gemini_responds():
#     reply = get_cloud_llm_gemini().call("Reply with exactly the word: OK")
#     assert "OK" in reply.upper()


@pytest.mark.skipif(
    not os.getenv("GROQ_API_KEY"),
    reason="GROQ_API_KEY not set",
)
def test_groq_responds():
    reply = get_cloud_llm_groq().call("Reply with exactly the word: OK")
    assert "OK" in reply.upper()


def test_ollama_responds():
    reply = get_local_llm().call("Reply with exactly the word: OK")
    assert "OK" in reply.upper()