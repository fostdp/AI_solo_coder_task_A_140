#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
古代沙船（方艄）传感器模拟器
模拟宋代沙船通过MQTT每1分钟上报传感器数据
支持不同货物种类和海况配置
"""

import json
import time
import random
import threading
import os
from datetime import datetime
import uuid

try:
    import paho.mqtt.client as mqtt
except ImportError:
    print("请先安装paho-mqtt库: pip install paho-mqtt")
    exit(1)

CARGO_PRESETS = {
    "grain_salt": {
        "name": "粮盐混装",
        "types": ["GRAIN", "SALT", "GRAIN", "SALT"],
        "weights": [25.0, 20.0, 25.0, 20.0]
    },
    "tea_porcelain": {
        "name": "茶瓷贸易",
        "types": ["TEA", "PORCELAIN", "TEA", "PORCELAIN"],
        "weights": [15.0, 30.0, 15.0, 30.0]
    },
    "silk_salt": {
        "name": "丝盐贸易",
        "types": ["SILK", "SALT", "SILK", "SALT"],
        "weights": [12.0, 28.0, 12.0, 28.0]
    },
    "full_grain": {
        "name": "满载粮食",
        "types": ["GRAIN", "GRAIN", "GRAIN", "GRAIN"],
        "weights": [30.0, 30.0, 30.0, 30.0]
    },
    "mixed": {
        "name": "五货混装",
        "types": ["GRAIN", "SALT", "TEA", "PORCELAIN", "SILK"],
        "weights": [20.0, 18.0, 15.0, 25.0, 10.0]
    }
}

SEA_CONDITION_PRESETS = {
    "calm": {
        "name": "平静海面",
        "wind_speed_range": [1.0, 5.0],
        "wave_height_range": [0.1, 0.5],
        "wave_period_range": [6.0, 10.0],
        "roll_amplitude": 2.0,
        "pitch_amplitude": 0.5,
        "extreme_roll_probability": 0.001,
        "extreme_roll_angles": [-8.0, 8.0],
        "bilge_leak_probability": 0.001,
        "bilge_leak_level": 0.4
    },
    "moderate": {
        "name": "中等海况",
        "wind_speed_range": [5.0, 15.0],
        "wave_height_range": [0.5, 2.0],
        "wave_period_range": [4.0, 8.0],
        "roll_amplitude": 5.0,
        "pitch_amplitude": 1.5,
        "extreme_roll_probability": 0.005,
        "extreme_roll_angles": [-15.0, 15.0],
        "bilge_leak_probability": 0.003,
        "bilge_leak_level": 0.6
    },
    "rough": {
        "name": "恶劣海况",
        "wind_speed_range": [15.0, 30.0],
        "wave_height_range": [2.0, 5.0],
        "wave_period_range": [3.0, 6.0],
        "roll_amplitude": 10.0,
        "pitch_amplitude": 3.0,
        "extreme_roll_probability": 0.02,
        "extreme_roll_angles": [-22.0, 22.0],
        "bilge_leak_probability": 0.01,
        "bilge_leak_level": 0.9
    },
    "storm": {
        "name": "暴风骤雨",
        "wind_speed_range": [30.0, 50.0],
        "wave_height_range": [5.0, 10.0],
        "wave_period_range": [2.0, 4.0],
        "roll_amplitude": 15.0,
        "pitch_amplitude": 5.0,
        "extreme_roll_probability": 0.05,
        "extreme_roll_angles": [-28.0, 28.0],
        "bilge_leak_probability": 0.03,
        "bilge_leak_level": 1.2
    },
    "typhoon": {
        "name": "台风过境",
        "wind_speed_range": [50.0, 80.0],
        "wave_height_range": [8.0, 15.0],
        "wave_period_range": [1.5, 3.0],
        "roll_amplitude": 20.0,
        "pitch_amplitude": 8.0,
        "extreme_roll_probability": 0.10,
        "extreme_roll_angles": [-35.0, 35.0],
        "bilge_leak_probability": 0.08,
        "bilge_leak_level": 1.5
    }
}


class SandShipSimulator:

    MQTT_BROKER = "localhost"
    MQTT_PORT = 1883
    MQTT_TOPIC_TEMPLATE = "ship/{ship_id}/sensor/data"

    def __init__(self, broker=None, port=None, cargo_preset="grain_salt", sea_condition="moderate"):
        self.broker = broker or self.MQTT_BROKER
        self.port = port or self.MQTT_PORT
        self.cargo_preset = cargo_preset
        self.sea_condition = sea_condition

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
            print(f"[OK] MQTT {self.broker}:{self.port}")
        else:
            print(f"[ERR] connect rc={rc}")

    def _on_disconnect(self, client, userdata, rc, properties=None):
        print(f"[WARN] disconnect rc={rc}")

    def _on_publish(self, client, userdata, mid, rc, properties=None):
        pass

    def _get_cargo_config(self):
        return CARGO_PRESETS.get(self.cargo_preset, CARGO_PRESETS["grain_salt"])

    def _get_sea_config(self):
        return SEA_CONDITION_PRESETS.get(self.sea_condition, SEA_CONDITION_PRESETS["moderate"])

    def add_ship(self, ship_id, ship_name="沙船", base_params=None):
        ship_id = str(ship_id)
        cargo_cfg = self._get_cargo_config()
        sea_cfg = self._get_sea_config()

        params = base_params or self._default_base_params()
        params["cargo_types"] = cargo_cfg["types"]
        if len(cargo_cfg["types"]) > params["cargo_holds"]:
            params["cargo_holds"] = len(cargo_cfg["types"])

        self.ships[ship_id] = {
            "name": ship_name,
            "base_params": params,
            "current_state": self._initial_state(cargo_cfg, sea_cfg),
            "last_report": None
        }
        print(f"[OK] {ship_name} (ID: {ship_id}) cargo={cargo_cfg['name']} sea={sea_cfg['name']}")

    def _default_base_params(self):
        return {
            "design_draft": 2.5,
            "design_displacement": 120.0,
            "breadth": 6.0,
            "length": 30.0,
            "max_roll_angle": 15.0,
            "cargo_holds": 4,
            "max_bilge_water": 0.5
        }

    def _initial_state(self, cargo_cfg, sea_cfg):
        n = len(cargo_cfg["types"])
        distribution = []
        for i in range(n):
            base_w = cargo_cfg["weights"][i] if i < len(cargo_cfg["weights"]) else 20.0
            distribution.append(base_w + random.uniform(-3.0, 3.0))

        return {
            "draft_depth": 2.0 + random.uniform(-0.2, 0.2),
            "roll_angle": random.uniform(-sea_cfg["roll_amplitude"] * 0.3,
                                          sea_cfg["roll_amplitude"] * 0.3),
            "pitch_angle": random.uniform(-sea_cfg["pitch_amplitude"] * 0.3,
                                           sea_cfg["pitch_amplitude"] * 0.3),
            "cargo_distribution": distribution,
            "cargo_types": cargo_cfg["types"],
            "bilge_water": random.uniform(0.05, 0.15),
            "heading": random.uniform(0.0, 360.0),
            "speed": random.uniform(2.0, 5.0)
        }

    def _update_state(self, ship_id):
        ship = self.ships[ship_id]
        state = ship["current_state"]
        params = ship["base_params"]
        sea = self._get_sea_config()

        state["draft_depth"] += random.uniform(-0.05, 0.05)
        state["draft_depth"] = max(1.5, min(3.0, state["draft_depth"]))

        roll_drift = random.uniform(-0.5, 0.5)
        wave_effect = random.uniform(-sea["roll_amplitude"],
                                      sea["roll_amplitude"]) * random.choice([0, 0, 1])
        state["roll_angle"] += roll_drift + wave_effect
        state["roll_angle"] = max(-30.0, min(30.0, state["roll_angle"]))

        pitch_drift = random.uniform(-0.3, 0.3)
        pitch_wave = random.uniform(-sea["pitch_amplitude"],
                                     sea["pitch_amplitude"]) * random.choice([0, 0, 1])
        state["pitch_angle"] += pitch_drift + pitch_wave
        state["pitch_angle"] = max(-10.0, min(10.0, state["pitch_angle"]))

        for i in range(len(state["cargo_distribution"])):
            change = random.uniform(-0.5, 0.5)
            state["cargo_distribution"][i] += change
            state["cargo_distribution"][i] = max(0.0, min(50.0, state["cargo_distribution"][i]))

        if random.random() < sea["bilge_leak_probability"]:
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

        if random.random() < sea["extreme_roll_probability"]:
            state["roll_angle"] = random.choice(sea["extreme_roll_angles"])
            print(f"[WARN] {ship['name']} extreme roll: {state['roll_angle']:.1f}deg")

        if random.random() < sea["bilge_leak_probability"]:
            state["bilge_water"] = sea["bilge_leak_level"]
            print(f"[WARN] {ship['name']} bilge leak: {state['bilge_water']:.2f}m")

    def _generate_sensor_data(self, ship_id):
        ship = self.ships[ship_id]
        state = ship["current_state"]
        params = ship["base_params"]
        sea = self._get_sea_config()

        cargo_distribution = []
        for i in range(len(state["cargo_types"])):
            cargo_distribution.append({
                "hold_number": i + 1,
                "cargo_type": state["cargo_types"][i],
                "weight": round(state["cargo_distribution"][i], 2),
                "fill_percentage": round(state["cargo_distribution"][i] / 50.0 * 100, 1)
            })

        total_cargo_weight = sum(h["weight"] for h in cargo_distribution)
        displacement = params["design_displacement"] * (state["draft_depth"] / params["design_draft"])

        wind_speed = round(random.uniform(*sea["wind_speed_range"]), 1)
        wave_height = round(random.uniform(*sea["wave_height_range"]), 2)
        wave_period = round(random.uniform(*sea["wave_period_range"]), 1)

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
                "wind_speed": wind_speed,
                "wind_direction": round(random.uniform(0.0, 360.0), 0),
                "wave_height": wave_height,
                "wave_period": wave_period,
                "sea_state": self.sea_condition
            },
            "metadata": {
                "ship_id": ship_id,
                "ship_name": ship["name"],
                "ship_type": "SAND_SHIP",
                "dynasty": "SONG",
                "simulation_mode": True,
                "cargo_preset": self.cargo_preset,
                "sea_condition": self.sea_condition,
                "data_quality": "GOOD"
            }
        }

    def _publish_sensor_data(self, ship_id):
        self._update_state(ship_id)
        sensor_data = self._generate_sensor_data(ship_id)
        topic = self.MQTT_TOPIC_TEMPLATE.format(ship_id=ship_id)
        payload = json.dumps(sensor_data, ensure_ascii=False)

        try:
            self.client.publish(topic, payload, qos=1, retain=False)

            ship = self.ships[ship_id]
            ship["last_report"] = datetime.now()

            roll = sensor_data["hull"]["roll_angle"]
            gm = sensor_data["stability"]["estimated_gm"]
            bilge = sensor_data["bilge"]["water_level"]
            cargo_w = sensor_data["cargo"]["total_weight"]
            status = "[OK]" if abs(roll) <= 15.0 and gm >= 0.3 else "[WARN]"

            print(
                f"{status} {ship['name']} | "
                f"draft={sensor_data['hull']['draft_depth']:.2f}m | "
                f"roll={roll:+.1f}deg | "
                f"GM={gm:.3f}m | "
                f"bilge={bilge:.2f}m | "
                f"cargo={cargo_w:.1f}t | "
                f"wind={sensor_data['weather']['wind_speed']:.0f}m/s | "
                f"wave={sensor_data['weather']['wave_height']:.1f}m"
            )

        except Exception as e:
            print(f"[ERR] publish failed ({ship_id}): {e}")

    def _ship_simulation_loop(self, ship_id, interval=60):
        while self.running:
            try:
                self._publish_sensor_data(ship_id)
            except Exception as e:
                print(f"[ERR] {ship_id}: {e}")
            time.sleep(interval)

    def start(self, interval=60):
        if self.running:
            print("[WARN] already running")
            return

        cargo_cfg = self._get_cargo_config()
        sea_cfg = self._get_sea_config()

        print(f"\n{'=' * 70}")
        print("Sand Ship (Fang Shao) Stability Simulation - Sensor Simulator")
        print(f"{'=' * 70}")
        print(f"MQTT:        {self.broker}:{self.port}")
        print(f"Interval:    {interval}s")
        print(f"Ships:       {len(self.ships)}")
        print(f"Cargo:       {cargo_cfg['name']} ({self.cargo_preset})")
        print(f"Sea:         {sea_cfg['name']} ({self.sea_condition})")
        print(f"{'=' * 70}\n")

        try:
            self.client.connect(self.broker, self.port, keepalive=60)
            self.client.loop_start()
        except Exception as e:
            print(f"[ERR] MQTT connect failed: {e}")
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

        print("[OK] Simulator started, Ctrl+C to stop\n")

        try:
            while self.running:
                time.sleep(1)
        except KeyboardInterrupt:
            print("\n\nStopping simulator...")
            self.stop()

    def stop(self):
        self.running = False
        for t in self.threads:
            if t.is_alive():
                t.join(timeout=2)
        self.client.loop_stop()
        self.client.disconnect()
        print("[OK] Simulator stopped")


def main():
    import argparse

    cargo_choices = list(CARGO_PRESETS.keys())
    sea_choices = list(SEA_CONDITION_PRESETS.keys())

    parser = argparse.ArgumentParser(
        description="Sand Ship (Fang Shao) Sensor Simulator",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Cargo presets:
  grain_salt     - grain + salt mix (default)
  tea_porcelain  - tea + porcelain trade
  silk_salt      - silk + salt trade
  full_grain     - full grain cargo
  mixed          - all 5 cargo types

Sea condition presets:
  calm           - calm sea (wind 1-5 m/s, wave 0.1-0.5m)
  moderate       - moderate sea (wind 5-15 m/s, wave 0.5-2m) [default]
  rough          - rough sea (wind 15-30 m/s, wave 2-5m)
  storm          - storm (wind 30-50 m/s, wave 5-10m)
  typhoon        - typhoon (wind 50-80 m/s, wave 8-15m)

Examples:
  python sand_ship_simulator.py
  python sand_ship_simulator.py --broker mqtt --sea-condition storm
  python sand_ship_simulator.py --cargo-preset tea_porcelain --sea-condition rough
  python sand_ship_simulator.py --ships 3 --interval 30
        """
    )

    parser.add_argument("--broker", default=os.environ.get("MQTT_BROKER", "localhost"),
                        help="MQTT broker address (env: MQTT_BROKER)")
    parser.add_argument("--port", type=int, default=int(os.environ.get("MQTT_PORT", "1883")),
                        help="MQTT broker port (env: MQTT_PORT)")
    parser.add_argument("--interval", type=int, default=int(os.environ.get("REPORT_INTERVAL", "60")),
                        help="Report interval in seconds (env: REPORT_INTERVAL)")
    parser.add_argument("--ships", type=int, default=2, help="Number of ships to simulate")
    parser.add_argument("--cargo-preset", default=os.environ.get("CARGO_PRESET", "grain_salt"),
                        choices=cargo_choices, help="Cargo type preset (env: CARGO_PRESET)")
    parser.add_argument("--sea-condition", default=os.environ.get("SEA_CONDITION", "moderate"),
                        choices=sea_choices, help="Sea condition preset (env: SEA_CONDITION)")

    args = parser.parse_args()

    ship_ids = [
        "550e8400-e29b-41d4-a716-446655440000",
        "550e8400-e29b-41d4-a716-446655440001",
        "550e8400-e29b-41d4-a716-446655440002",
        "550e8400-e29b-41d4-a716-446655440003",
        "550e8400-e29b-41d4-a716-446655440004"
    ]

    ship_names = ["Yuanfeng", "Yuanyou", "Shaosheng", "Chongning", "Zhenghe"]

    simulator = SandShipSimulator(
        broker=args.broker,
        port=args.port,
        cargo_preset=args.cargo_preset,
        sea_condition=args.sea_condition
    )

    for i in range(min(args.ships, len(ship_ids))):
        simulator.add_ship(ship_id=ship_ids[i], ship_name=ship_names[i])

    simulator.start(interval=args.interval)


if __name__ == "__main__":
    main()
