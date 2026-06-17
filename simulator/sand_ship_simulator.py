#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
古代沙船（方艄）传感器模拟器
模拟宋代沙船通过MQTT每1分钟上报传感器数据
"""

import json
import time
import random
import threading
from datetime import datetime
import uuid

try:
    import paho.mqtt.client as mqtt
except ImportError:
    print("请先安装paho-mqtt库: pip install paho-mqtt")
    exit(1)


class SandShipSimulator:
    """沙船传感器模拟器"""

    MQTT_BROKER = "localhost"
    MQTT_PORT = 1883
    MQTT_TOPIC_TEMPLATE = "ship/{ship_id}/sensor/data"

    def __init__(self, broker=None, port=None):
        self.broker = broker or self.MQTT_BROKER
        self.port = port or self.MQTT_PORT
        self.client = mqtt.Client(
            client_id=f"sand_ship_simulator_{uuid.uuid4().hex[:8]}",
            protocol=mqtt.MQTTv5
        )
        self.client.on_connect = self._on_connect
        self.client.on_disconnect = self._on_disconnect
        self.client.on_publish = self._on_publish

        self.ships = {}
        self.running = False
        self.threads = []

    def _on_connect(self, client, userdata, flags, rc, properties=None):
        if rc == 0:
            print(f"✓ 已连接到MQTT服务器 {self.broker}:{self.port}")
        else:
            print(f"✗ 连接失败，返回码: {rc}")

    def _on_disconnect(self, client, userdata, rc, properties=None):
        print(f"⚠  断开连接，返回码: {rc}")

    def _on_publish(self, client, userdata, mid, rc, properties=None):
        if rc == 0:
            pass
        else:
            print(f"✗ 消息 {mid} 发布失败")

    def add_ship(self, ship_id, ship_name="沙船", base_params=None):
        """添加一艘模拟沙船"""
        ship_id = str(ship_id)
        self.ships[ship_id] = {
            "name": ship_name,
            "base_params": base_params or self._default_base_params(),
            "current_state": self._initial_state(),
            "last_report": None
        }
        print(f"✓ 添加模拟船舶: {ship_name} (ID: {ship_id})")

    def _default_base_params(self):
        """默认船舶参数"""
        return {
            "design_draft": 2.5,
            "design_displacement": 120.0,
            "breadth": 6.0,
            "length": 30.0,
            "max_roll_angle": 15.0,
            "cargo_holds": 4,
            "max_bilge_water": 0.5
        }

    def _initial_state(self):
        """初始状态"""
        return {
            "draft_depth": 2.0 + random.uniform(-0.2, 0.2),
            "roll_angle": random.uniform(-2.0, 2.0),
            "pitch_angle": random.uniform(-1.0, 1.0),
            "cargo_distribution": [
                random.uniform(20.0, 30.0),
                random.uniform(20.0, 30.0),
                random.uniform(15.0, 25.0),
                random.uniform(15.0, 25.0)
            ],
            "cargo_types": ["GRAIN", "SALT", "GRAIN", "SALT"],
            "bilge_water": random.uniform(0.05, 0.15),
            "heading": random.uniform(0.0, 360.0),
            "speed": random.uniform(2.0, 5.0)
        }

    def _update_state(self, ship_id):
        """更新船舶状态（模拟真实传感器数据变化）"""
        ship = self.ships[ship_id]
        state = ship["current_state"]
        params = ship["base_params"]

        state["draft_depth"] += random.uniform(-0.05, 0.05)
        state["draft_depth"] = max(1.5, min(3.0, state["draft_depth"]))

        roll_drift = random.uniform(-0.5, 0.5)
        wave_effect = random.uniform(-3.0, 3.0) * random.choice([0, 0, 1])
        state["roll_angle"] += roll_drift + wave_effect
        state["roll_angle"] = max(-25.0, min(25.0, state["roll_angle"]))

        state["pitch_angle"] += random.uniform(-0.3, 0.3)
        state["pitch_angle"] = max(-10.0, min(10.0, state["pitch_angle"]))

        for i in range(len(state["cargo_distribution"])):
            change = random.uniform(-0.5, 0.5)
            state["cargo_distribution"][i] += change
            state["cargo_distribution"][i] = max(0.0, min(50.0, state["cargo_distribution"][i]))

        if random.random() < 0.02:
            state["bilge_water"] += random.uniform(0.02, 0.08)
        else:
            state["bilge_water"] = max(0.05, state["bilge_water"] - 0.005)
        state["bilge_water"] = min(1.5, state["bilge_water"])

        state["heading"] += random.uniform(-5.0, 5.0)
        if state["heading"] < 0:
            state["heading"] += 360.0
        elif state["heading"] >= 360:
            state["heading"] -= 360.0

        state["speed"] += random.uniform(-0.3, 0.3)
        state["speed"] = max(0.0, min(8.0, state["speed"]))

        if random.random() < 0.005:
            state["roll_angle"] = random.choice([-18.0, 18.0])
            print(f"⚠  {ship['name']} 模拟极端横摇工况: {state['roll_angle']:.1f}°")

        if random.random() < 0.003:
            state["bilge_water"] = 0.8
            print(f"⚠  {ship['name']} 模拟舱底进水工况: {state['bilge_water']:.2f}m")

    def _generate_sensor_data(self, ship_id):
        """生成传感器数据报文"""
        ship = self.ships[ship_id]
        state = ship["current_state"]
        params = ship["base_params"]

        cargo_distribution = []
        for i in range(params["cargo_holds"]):
            cargo_distribution.append({
                "hold_number": i + 1,
                "cargo_type": state["cargo_types"][i],
                "weight": round(state["cargo_distribution"][i], 2),
                "fill_percentage": round(state["cargo_distribution"][i] / 50.0 * 100, 1)
            })

        total_cargo_weight = sum(h["weight"] for h in cargo_distribution)
        displacement = params["design_displacement"] * (state["draft_depth"] / params["design_draft"])

        return {
            "sensor_id": f"SHIP-{ship_id}-SENSOR-001",
            "timestamp": datetime.utcnow().isoformat() + "Z",
            "location": {
                "latitude": round(31.2304 + random.uniform(-0.05, 0.05), 6),
                "longitude": round(121.4737 + random.uniform(-0.05, 0.05), 6),
                "heading": round(state["heading"], 1),
                "speed": round(state["speed"], 2)
            },
            "hull": {
                "draft_depth": round(state["draft_depth"], 3),
                "draft_forward": round(state["draft_depth"] + state["pitch_angle"] * 0.1, 3),
                "draft_aft": round(state["draft_depth"] - state["pitch_angle"] * 0.1, 3),
                "roll_angle": round(state["roll_angle"], 2),
                "pitch_angle": round(state["pitch_angle"], 2),
                "yaw_rate": round(random.uniform(-2.0, 2.0), 3)
            },
            "cargo": {
                "total_weight": round(total_cargo_weight, 2),
                "total_volume": round(total_cargo_weight / 0.8, 2),
                "distribution": cargo_distribution
            },
            "bilge": {
                "water_level": round(state["bilge_water"], 3),
                "pump_status": "ACTIVE" if state["bilge_water"] > 0.3 else "STANDBY",
                "compartment_levels": [
                    round(state["bilge_water"] * random.uniform(0.8, 1.2), 3)
                    for _ in range(4)
                ]
            },
            "stability": {
                "estimated_gm": round(0.5 + random.uniform(-0.2, 0.2), 3),
                "roll_period": round(8.0 + random.uniform(-1.0, 1.0), 2),
                "displacement": round(displacement, 2)
            },
            "weather": {
                "wind_speed": round(random.uniform(2.0, 15.0), 1),
                "wind_direction": round(random.uniform(0.0, 360.0), 0),
                "wave_height": round(random.uniform(0.2, 2.5), 2),
                "wave_period": round(random.uniform(4.0, 10.0), 1)
            },
            "metadata": {
                "ship_id": ship_id,
                "ship_name": ship["name"],
                "ship_type": "SAND_SHIP",
                "dynasty": "SONG",
                "simulation_mode": True,
                "data_quality": "GOOD"
            }
        }

    def _publish_sensor_data(self, ship_id):
        """发布传感器数据到MQTT"""
        self._update_state(ship_id)
        sensor_data = self._generate_sensor_data(ship_id)
        topic = self.MQTT_TOPIC_TEMPLATE.format(ship_id=ship_id)
        payload = json.dumps(sensor_data, ensure_ascii=False)

        try:
            result = self.client.publish(
                topic,
                payload,
                qos=1,
                retain=False
            )

            ship = self.ships[ship_id]
            ship["last_report"] = datetime.now()

            roll = sensor_data["hull"]["roll_angle"]
            gm = sensor_data["stability"]["estimated_gm"]
            status = "✓"
            if abs(roll) > 15.0 or gm < 0.3:
                status = "⚠"

            print(
                f"{status} {ship['name']} | "
                f"吃水: {sensor_data['hull']['draft_depth']:.2f}m | "
                f"横摇: {roll:+.1f}° | "
                f"GM: {gm:.3f}m | "
                f"舱底水: {sensor_data['bilge']['water_level']:.2f}m | "
                f"货重: {sensor_data['cargo']['total_weight']:.1f}吨"
            )

        except Exception as e:
            print(f"✗ 发布失败 ({ship_id}): {e}")

    def _ship_simulation_loop(self, ship_id, interval=60):
        """单船模拟循环"""
        while self.running:
            try:
                self._publish_sensor_data(ship_id)
            except Exception as e:
                print(f"✗ 船舶 {ship_id} 模拟异常: {e}")

            time.sleep(interval)

    def start(self, interval=60):
        """启动模拟器"""
        if self.running:
            print("⚠  模拟器已在运行")
            return

        print(f"\n{'=' * 70}")
        print("古代沙船（方艄）稳性仿真系统 - 传感器模拟器")
        print(f"{'=' * 70}")
        print(f"MQTT服务器: {self.broker}:{self.port}")
        print(f"上报间隔: {interval}秒")
        print(f"模拟船舶数量: {len(self.ships)}")
        print(f"{'=' * 70}\n")

        try:
            self.client.connect(self.broker, self.port, keepalive=60)
            self.client.loop_start()
        except Exception as e:
            print(f"✗ 无法连接MQTT服务器: {e}")
            print("请确保已启动MQTT服务器（如Mosquitto）")
            return

        time.sleep(1)

        self.running = True
        self.threads = []

        for ship_id in self.ships:
            t = threading.Thread(
                target=self._ship_simulation_loop,
                args=(ship_id, interval),
                daemon=True
            )
            t.start()
            self.threads.append(t)

        print("✓ 模拟器已启动，按 Ctrl+C 停止\n")

        try:
            while self.running:
                time.sleep(1)
        except KeyboardInterrupt:
            print("\n\n正在停止模拟器...")
            self.stop()

    def stop(self):
        """停止模拟器"""
        self.running = False

        for t in self.threads:
            if t.is_alive():
                t.join(timeout=2)

        self.client.loop_stop()
        self.client.disconnect()

        print("✓ 模拟器已停止")


def main():
    """主函数"""
    import argparse

    parser = argparse.ArgumentParser(
        description="古代沙船（方艄）传感器模拟器",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  # 使用默认配置
  python sand_ship_simulator.py

  # 指定MQTT服务器
  python sand_ship_simulator.py --broker 192.168.1.100 --port 1883

  # 修改上报间隔为30秒
  python sand_ship_simulator.py --interval 30

  # 模拟5艘船舶
  python sand_ship_simulator.py --ships 5
        """
    )

    parser.add_argument("--broker", default="localhost", help="MQTT服务器地址")
    parser.add_argument("--port", type=int, default=1883, help="MQTT服务器端口")
    parser.add_argument("--interval", type=int, default=60, help="上报间隔（秒）")
    parser.add_argument("--ships", type=int, default=2, help="模拟船舶数量")

    args = parser.parse_args()

    ship_ids = [
        "550e8400-e29b-41d4-a716-446655440000",
        "550e8400-e29b-41d4-a716-446655440001",
        "550e8400-e29b-41d4-a716-446655440002",
        "550e8400-e29b-41d4-a716-446655440003",
        "550e8400-e29b-41d4-a716-446655440004"
    ]

    ship_names = [
        "元丰号",
        "元祐号",
        "绍圣号",
        "崇宁号",
        "政和号"
    ]

    simulator = SandShipSimulator(broker=args.broker, port=args.port)

    for i in range(min(args.ships, len(ship_ids))):
        simulator.add_ship(
            ship_id=ship_ids[i],
            ship_name=ship_names[i]
        )

    simulator.start(interval=args.interval)


if __name__ == "__main__":
    main()
