from colorama import init, Fore, Style
import json

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

