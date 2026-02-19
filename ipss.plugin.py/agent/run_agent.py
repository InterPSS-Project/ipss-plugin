from tools import (
    load_ieee_network,
    run_load_flow,
    get_max_branch,
    control_branch_power,
)
from colorama import init, Fore, Style
import asyncio
import json
from pathlib import Path
from datetime import datetime
from colorama import init, Fore, Style

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
init(autoreset=True)  # 自动重置颜色


def pretty_print_event(event):
    """根据事件类型格式化打印事件内容"""
    source = getattr(event, "source", "unknown")
    event_type = type(event).__name__

    print(Fore.CYAN + f"[{event_type}] from {source}")

    # TextMessage
    if hasattr(event, "content") and isinstance(event.content, str):
        content = event.content.strip()
        if content:
            # 尝试格式化 JSON
            try:
                parsed = json.loads(content)
                formatted = json.dumps(parsed, ensure_ascii=False, indent=2)
                print(Fore.GREEN + formatted)
            except Exception:
                print(Fore.GREEN + content)

    # FunctionCall / ToolCall events
    elif hasattr(event, "content") and isinstance(event.content, list):
        for item in event.content:
            # FunctionCall
            if hasattr(item, "name") and hasattr(item, "arguments"):
                try:
                    args = json.loads(item.arguments)
                    args_str = json.dumps(args, ensure_ascii=False, indent=2)
                except Exception:
                    args_str = str(item.arguments)
                print(Fore.YELLOW +
                      f"FunctionCall: {item.name}\nArguments:\n{args_str}")
            # FunctionExecutionResult
            elif hasattr(item, "content"):
                try:
                    result = json.loads(item.content.replace("'", '"'))
                    result_str = json.dumps(
                        result, ensure_ascii=False, indent=2)
                except Exception:
                    result_str = str(item.content)
                print(Fore.MAGENTA + f"ExecutionResult:\n{result_str}")

    # HandoffMessage
    if event_type == "HandoffMessage":
        target = getattr(event, "target", "unknown")
        content = getattr(event, "content", "")
        print(Fore.BLUE + f"Handoff to {target}: {content}")

    print(Style.DIM + "-"*80)


def load_prompt(path: str) -> str:
    return Path(path).read_text(encoding="utf-8")


async def main():

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

    tools = [
        load_ieee_network,
        run_load_flow,
        get_max_branch,
        control_branch_power,
    ]

    loadflow_agent = AssistantAgent(
        name="loadflow_agent",
        system_message=load_prompt("agent/prompts/loadflow_system.md"),
        model_client=model_client,
        tools=tools,
        reflect_on_tool_use=False,
        handoffs=["power_grid_schedule_agent"],
    )

    scheduler_agent = AssistantAgent(
        name="power_grid_schedule_agent",
        system_message=load_prompt("agent/prompts/scheduler_system.md"),
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


if __name__ == "__main__":
    asyncio.run(main())
