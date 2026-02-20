import json
from autogen_core.tools import FunctionTool

class SessionStore:
    def __init__(self):
        self.net_id = None

def make_tools(store: SessionStore):
    async def load_ieee_network(model_name: str) -> dict[str, any]:
        """加载 IEEE 电网基准模型

        参数:
            model_name: IEEE基准模型名称，不带扩展名，例如 'ieee39'

        返回:
            dict: 成功时：
                {
                    "net_id": "唯一网络ID字符串"
                }
            说明: net_id 用于后续调用工具
            异常时返回:
                {
                    "error": "错误信息"
                }

        示例:
            {"net_id": "network_1770281885479"}
        """

        store.net_id = "network_1770281885479"
        print(f"Net ID(1): {store.net_id}")

        s = '{"net_id": "network_1770281885479"}'
        return json.loads(s)

    async def run_load_flow(net_id: str) -> dict[str, any]:
        """执行潮流计算

        参数:
            net_id: 通过 load_ieee_network 获取的网络ID

        返回:
            dict: 成功时：
                {
                    "convergence_status": "潮流收敛" 或 "潮流不收敛"
                }
            异常时返回:
                {
                    "error": "错误信息"
                }
        示例:
            {"convergence_status": "潮流收敛"}
        """

        print(f"Net ID(2): {store.net_id}")

        s = '{"convergence_status": "潮流收敛"}'
        return json.loads(s)

    async def get_max_branch(net_id: str) -> dict[str, any]:
        """获取最大潮流支路信息

        参数:
            net_id: 通过 load_ieee_network 获取的网络ID

        返回:
            dict: 成功时：
                {
                    "branch_id": "最大潮流支路ID, 例如 'Bus29->Bus38(1)'",
                    "p_pu": float,  # 当前有功功率标幺值，可为负表示方向相反
                    "q_pu": float   # 当前无功功率标幺值
                }
            异常时返回:
                {
                    "error": "错误信息"
                }

        示例:
            {
                "branch_id": "Bus29->Bus38(1)",
                "p_pu": -8.2476,
                "q_pu": 0.7933
            }
        """
        s = '{"branch_id": "Bus29->Bus38(1)", "p_pu": -8.2476, "q_pu": 0.7933}'
        return json.loads(s)

    async def control_branch_power(net_id: str, branch_id: str, factor: float) -> dict[str, any]:
        """控制支路潮流至目标比例（通过调整发电机出力）

        参数:
            net_id: 通过 load_ieee_network 获取的网络ID
            branch_id: 需要控制的支路ID
            factor: 调整比例因子，例如 0.95 表示将有功功率降至原值的 95%

        返回:
            dict: 成功时：
                {
                    "branch_id": "支路ID",
                    "p_pu": float,  # 调整后的有功功率标幺值
                    "q_pu": float,  # 调整后的无功功率标幺值
                    "control_strategy": {  # 控制策略，包含每台发电机的功率调整量，标幺值
                        "发电机名称": float,
                        ...
                    }
                }
            异常时返回:
                {
                    "error": "错误信息"
                }

        示例:
            {
                "branch_id": "Bus29->Bus38(1)",
                "p_pu": -7.8206,
                "q_pu": 0.7963,
                "control_strategy": {
                    "Bus30-G1": 0.43238,
                    "Bus38-G1": -0.43238
                }
            }
        """

        s = '{"branch_id": "Bus29->Bus38(1)", "p_pu": -7.8206, "q_pu": 0.7963, "control_strategy": {"Bus30-G1": 0.43238, "Bus38-G1": -0.43238}}'
        return json.loads(s)

    return [load_ieee_network, run_load_flow, get_max_branch, control_branch_power]
