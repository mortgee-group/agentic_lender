# ai/crew.py
from crewai import Agent, Crew, Process, Task
from crewai.project import CrewBase, agent, crew, task

from ai.llms import get_cloud_llm_openai, get_cloud_llm_gemini, get_cloud_llm_groq, get_local_llm
from ai.tools import ValuationLookupTool
from ai.models import UnderwritingDecision, AffordabilityOutput, ValuationOutput, MarketResearchOutput


@CrewBase
class ALCrew:
    """AL-AI Engine: Agentic Lender underwriting committee."""

    agents_config = "config/agents.yaml"
    tasks_config = "config/tasks.yaml"

    @agent
    def affordability_agent(self) -> Agent:
        return Agent(
            config=self.agents_config["affordability_agent"],
            llm=get_cloud_llm_groq(),
            verbose=True,
        )

    @agent
    def valuation_agent(self) -> Agent:
        return Agent(
            config=self.agents_config["valuation_agent"],
            llm=get_cloud_llm_openai(),
            tools=[ValuationLookupTool()],
            verbose=True,
        )

    @agent
    def market_researcher(self) -> Agent:
        return Agent(
            config=self.agents_config["market_researcher"],
            llm=get_cloud_llm_openai(),
            verbose=True,
        )

    @agent
    def underwriting_strategist(self) -> Agent:
        return Agent(
            config={
                "role": "Chief Underwriting Strategist",
                "goal": (
                    "Synthesize affordability, valuation, and market research "
                    "to make a final lending decision and produce an audit-ready memo."
                ),
                "backstory": (
                    "You are a veteran credit committee chair with 20 years "
                    "of experience in mortgage lending."
                ),
            },
            llm=get_local_llm(),
            verbose=True,
        )

    @task
    def affordability_task(self) -> Task:
        return Task(
            config=self.tasks_config["affordability_task"],
            agent=self.affordability_agent(),
            output_pydantic=AffordabilityOutput
        )

    @task
    def valuation_task(self) -> Task:
        return Task(
            config=self.tasks_config["valuation_task"],
            agent=self.valuation_agent(),
            tools=[ValuationLookupTool()],
            output_pydantic=ValuationOutput
        )

    @task
    def market_research_task(self) -> Task:
        return Task(
            config=self.tasks_config["market_research_task"],
            agent=self.market_researcher(),
            output_pydantic=MarketResearchOutput
        )

    @task
    def underwriting_decision_task(self) -> Task:
        return Task(
            config=self.tasks_config["underwriting_decision_task"],
            agent=self.underwriting_strategist(),
            context=[
                self.affordability_task(),
                self.valuation_task(),
                self.market_research_task(),
            ],
            output_pydantic=UnderwritingDecision,       # 👈 NEW
        )

    @crew
    def crew(self) -> Crew:
        return Crew(
            agents=[
                self.affordability_agent(),
                self.valuation_agent(),
                self.market_researcher(),
                self.underwriting_strategist(),
            ],
            tasks=[
                self.affordability_task(),
                self.valuation_task(),
                self.market_research_task(),
                self.underwriting_decision_task(),
            ],
            process=Process.sequential,
            verbose=True,
        )