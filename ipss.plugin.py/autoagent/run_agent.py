from func_tools import (
    make_tools,
    SessionStore,
)
from utils import pretty_print_event

import asyncio
from pathlib import Path
from datetime import datetime
from colorama import init

from autogen_agentchat.agents import AssistantAgent
from autogen_agentchat.teams import Swarm
from autogen_ext.models.openai import OpenAIChatCompletionClient
from autogen_core.tools import FunctionTool
from autogen_agentchat.conditions import (
    TextMentionTermination,
    MaxMessageTermination,
)
from autogen_agentchat.base import (
    OrTerminationCondition
)

import jpype
import jpype.imports
from jpype.types import *

import sys
from pathlib import Path

init(autoreset=True)  # 自动重置颜色

def load_prompt(path: str) -> str:
    return Path(path).read_text(encoding="utf-8")


async def main():
    # Get script directory for reliable path resolution
    script_dir = Path(__file__).resolve().parent
    project_root = script_dir.parent
    if str(project_root) not in sys.path:
        sys.path.append(str(project_root))
    #
    #  Configure and Start the JVM
    #
    from src.config import ConfigManager, JvmManager

    # Load configuration file
    config_path=str(project_root / "config" / "config.json")
    config = ConfigManager.load_config(config_path)
    # Initialize and start the JVM
    JvmManager.initialize_jvm(config)

    model_client = OpenAIChatCompletionClient(
        model="qwen3:14b",
        api_key="ollama",
        base_url="http://localhost:11434/v1",
        temperature=0.5,
        model_info={
            "vision": False,
            "function_calling": True,
            "json_output": True,
            "structured_output": True,
            "family": "qwen",
        },
    )

    store = SessionStore()
    tools = make_tools(store)

    loadflow_agent = AssistantAgent(
        name="loadflow_agent",
        system_message=load_prompt("autoagent/prompts/loadflow_system.md"),
        model_client=model_client,
        tools=tools,
        reflect_on_tool_use=False,
        handoffs=["power_grid_schedule_agent"],
    )

    scheduler_agent = AssistantAgent(
        name="power_grid_schedule_agent",
        system_message=load_prompt("autoagent/prompts/scheduler_system.md"),
        model_client=model_client,
        tools=[],
        reflect_on_tool_use=True,
        handoffs=["loadflow_agent"],
    )

    team = Swarm(
        participants=[scheduler_agent, loadflow_agent],
        termination_condition=OrTerminationCondition(
            TextMentionTermination(text="TERMINATE"),
            MaxMessageTermination(max_messages=30, include_agent_event=False)
        )
    )

    # 用户输入任务
    task_input = input("发消息：\n> ")

    # 运行团队
    async for event in team.run_stream(task=task_input):
        pretty_print_event(event)

    # 
    # Shutdown JVM
    #
    print("Shutting down JVM...")
    try:
        jpype.shutdownJVM()
        print("JVM shutdown complete.")
    except Exception as e:
        print(f"Error shutting down JVM: {e}")

if __name__ == "__main__":
    asyncio.run(main())
