-- 古代沙船稳性仿真与装载优化系统 - PostgreSQL初始化脚本

CREATE DATABASE sand_ship_stability;
\c sand_ship_stability;

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS postgis;

-- 船舶信息表
CREATE TABLE IF NOT EXISTS ships (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL,
    ship_type VARCHAR(50) NOT NULL DEFAULT '方艄沙船',
    length_overall NUMERIC(10, 2) NOT NULL,
    breadth_molded NUMERIC(10, 2) NOT NULL,
    depth_molded NUMERIC(10, 2) NOT NULL,
    design_draft NUMERIC(10, 2) NOT NULL,
    displacement NUMERIC(12, 2) NOT NULL,
    lightship_weight NUMERIC(12, 2) NOT NULL,
    deadweight_tons NUMERIC(12, 2) NOT NULL,
    metacentric_height_design NUMERIC(8, 4) NOT NULL DEFAULT 0.8,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 货舱信息表
CREATE TABLE IF NOT EXISTS cargo_holds (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    ship_id UUID NOT NULL REFERENCES ships(id) ON DELETE CASCADE,
    hold_number INTEGER NOT NULL,
    hold_name VARCHAR(50) NOT NULL,
    capacity_cubic NUMERIC(12, 2) NOT NULL,
    max_weight NUMERIC(12, 2) NOT NULL,
    center_gravity_x NUMERIC(10, 2) NOT NULL,
    center_gravity_y NUMERIC(10, 2) NOT NULL,
    center_gravity_z NUMERIC(10, 2) NOT NULL,
    is_tank BOOLEAN DEFAULT false,
    tank_length NUMERIC(10, 2),
    tank_breadth NUMERIC(10, 2),
    liquid_density NUMERIC(10, 3),
    tank_fullness NUMERIC(5, 2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 货物类型表
CREATE TABLE IF NOT EXISTS cargo_types (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    cargo_code VARCHAR(20) NOT NULL UNIQUE,
    cargo_name VARCHAR(50) NOT NULL,
    density NUMERIC(10, 3) NOT NULL,
    unit_weight NUMERIC(10, 2) NOT NULL,
    color_hex VARCHAR(7) NOT NULL DEFAULT '#FFD700',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 传感器数据表
CREATE TABLE IF NOT EXISTS sensor_data (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    ship_id UUID NOT NULL REFERENCES ships(id) ON DELETE CASCADE,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    draft_forward NUMERIC(8, 3),
    draft_aft NUMERIC(8, 3),
    draft_mean NUMERIC(8, 3),
    roll_angle NUMERIC(8, 3),
    pitch_angle NUMERIC(8, 3),
    heel_angle NUMERIC(8, 3),
    bilge_water_level NUMERIC(8, 3),
    water_temperature NUMERIC(8, 2),
    wind_speed NUMERIC(8, 2),
    wind_direction NUMERIC(8, 2),
    wave_height NUMERIC(8, 3),
    mqtt_topic VARCHAR(200),
    raw_payload JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sensor_data_ship_time ON sensor_data(ship_id, timestamp DESC);

-- 货物装载记录表
CREATE TABLE IF NOT EXISTS cargo_loadings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    ship_id UUID NOT NULL REFERENCES ships(id) ON DELETE CASCADE,
    hold_id UUID NOT NULL REFERENCES cargo_holds(id) ON DELETE CASCADE,
    cargo_type_id UUID NOT NULL REFERENCES cargo_types(id),
    weight NUMERIC(12, 2) NOT NULL,
    volume NUMERIC(12, 2) NOT NULL,
    loading_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    loading_order INTEGER,
    is_optimized BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 稳性计算结果表
CREATE TABLE IF NOT EXISTS stability_results (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    ship_id UUID NOT NULL REFERENCES ships(id) ON DELETE CASCADE,
    sensor_data_id UUID REFERENCES sensor_data(id),
    calculation_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    displacement_actual NUMERIC(12, 2),
    center_gravity_x NUMERIC(10, 3),
    center_gravity_y NUMERIC(10, 3),
    center_gravity_z NUMERIC(10, 3),
    center_buoyancy_x NUMERIC(10, 3),
    center_buoyancy_y NUMERIC(10, 3),
    center_buoyancy_z NUMERIC(10, 3),
    metacentric_height_transverse NUMERIC(8, 4),
    metacentric_height_longitudinal NUMERIC(8, 4),
    righting_arm NUMERIC(10, 4),
    righting_moment NUMERIC(12, 2),
    roll_period NUMERIC(10, 3),
    gm_value NUMERIC(8, 4),
    free_surface_correction NUMERIC(8, 4),
    gm_uncorrected NUMERIC(8, 4),
    stability_status VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    warning_message TEXT,
    curve_points JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_stability_ship_time ON stability_results(ship_id, calculation_time DESC);

-- 装载优化结果表
CREATE TABLE IF NOT EXISTS loading_optimizations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    ship_id UUID NOT NULL REFERENCES ships(id) ON DELETE CASCADE,
    optimization_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    total_cargo_weight NUMERIC(12, 2),
    total_cargo_volume NUMERIC(12, 2),
    effective_payload NUMERIC(12, 2),
    min_gm_required NUMERIC(8, 4) NOT NULL DEFAULT 0.3,
    resulting_gm NUMERIC(8, 4),
    grain_weight NUMERIC(12, 2),
    salt_weight NUMERIC(12, 2),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    solution JSONB,
    objective_value NUMERIC(12, 2),
    solve_time_ms NUMERIC(12, 2),
    algorithm_used VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 告警记录表
CREATE TABLE IF NOT EXISTS alarms (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    ship_id UUID NOT NULL REFERENCES ships(id) ON DELETE CASCADE,
    sensor_data_id UUID REFERENCES sensor_data(id),
    stability_result_id UUID REFERENCES stability_results(id),
    alarm_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    alarm_type VARCHAR(50) NOT NULL,
    alarm_level VARCHAR(20) NOT NULL DEFAULT 'WARNING',
    severity VARCHAR(20),
    alarm_message TEXT NOT NULL,
    description TEXT,
    parameter_name VARCHAR(50),
    parameter_value NUMERIC(12, 4),
    threshold_value NUMERIC(12, 4),
    is_acknowledged BOOLEAN DEFAULT false,
    acknowledged BOOLEAN DEFAULT false,
    acknowledged_at TIMESTAMP,
    acknowledged_by VARCHAR(100),
    triggered_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_alarms_ship_time ON alarms(ship_id, alarm_time DESC);
CREATE INDEX IF NOT EXISTS idx_alarms_unack ON alarms(ship_id, is_acknowledged) WHERE is_acknowledged = false;

-- WebSocket连接表
CREATE TABLE IF NOT EXISTS ws_connections (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id VARCHAR(100) NOT NULL UNIQUE,
    ship_id UUID REFERENCES ships(id),
    user_name VARCHAR(100),
    connected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_ping TIMESTAMP,
    is_active BOOLEAN DEFAULT true
);

-- 初始化船舶数据 - 宋代沙船
INSERT INTO ships (name, ship_type, length_overall, breadth_molded, depth_molded, 
                   design_draft, displacement, lightship_weight, deadweight_tons, 
                   metacentric_height_design)
VALUES 
('宋代沙船-001', '方艄沙船', 35.00, 9.50, 3.20, 2.10, 420.00, 180.00, 240.00, 0.85),
('宋代沙船-002', '方艄沙船', 38.00, 10.00, 3.50, 2.30, 520.00, 210.00, 310.00, 0.82)
ON CONFLICT DO NOTHING;

-- 初始化货舱数据
INSERT INTO cargo_holds (ship_id, hold_number, hold_name, capacity_cubic, max_weight, 
                         center_gravity_x, center_gravity_y, center_gravity_z)
SELECT s.id, 1, '前货舱', 85.00, 60.00, -10.50, 0.00, 1.20
FROM ships s WHERE s.name = '宋代沙船-001'
ON CONFLICT DO NOTHING;

INSERT INTO cargo_holds (ship_id, hold_number, hold_name, capacity_cubic, max_weight, 
                         center_gravity_x, center_gravity_y, center_gravity_z)
SELECT s.id, 2, '中前货舱', 95.00, 70.00, -3.50, 0.00, 1.20
FROM ships s WHERE s.name = '宋代沙船-001'
ON CONFLICT DO NOTHING;

INSERT INTO cargo_holds (ship_id, hold_number, hold_name, capacity_cubic, max_weight, 
                         center_gravity_x, center_gravity_y, center_gravity_z)
SELECT s.id, 3, '中后货舱', 95.00, 70.00, 3.50, 0.00, 1.20
FROM ships s WHERE s.name = '宋代沙船-001'
ON CONFLICT DO NOTHING;

INSERT INTO cargo_holds (ship_id, hold_number, hold_name, capacity_cubic, max_weight, 
                         center_gravity_x, center_gravity_y, center_gravity_z)
SELECT s.id, 4, '后货舱', 85.00, 60.00, 10.50, 0.00, 1.20
FROM ships s WHERE s.name = '宋代沙船-001'
ON CONFLICT DO NOTHING;

-- 第二艘船货舱
INSERT INTO cargo_holds (ship_id, hold_number, hold_name, capacity_cubic, max_weight, 
                         center_gravity_x, center_gravity_y, center_gravity_z)
SELECT s.id, 1, '前货舱', 95.00, 70.00, -11.50, 0.00, 1.30
FROM ships s WHERE s.name = '宋代沙船-002'
ON CONFLICT DO NOTHING;

INSERT INTO cargo_holds (ship_id, hold_number, hold_name, capacity_cubic, max_weight, 
                         center_gravity_x, center_gravity_y, center_gravity_z)
SELECT s.id, 2, '中前货舱', 105.00, 80.00, -3.80, 0.00, 1.30
FROM ships s WHERE s.name = '宋代沙船-002'
ON CONFLICT DO NOTHING;

INSERT INTO cargo_holds (ship_id, hold_number, hold_name, capacity_cubic, max_weight, 
                         center_gravity_x, center_gravity_y, center_gravity_z)
SELECT s.id, 3, '中后货舱', 105.00, 80.00, 3.80, 0.00, 1.30
FROM ships s WHERE s.name = '宋代沙船-002'
ON CONFLICT DO NOTHING;

INSERT INTO cargo_holds (ship_id, hold_number, hold_name, capacity_cubic, max_weight, 
                         center_gravity_x, center_gravity_y, center_gravity_z)
SELECT s.id, 4, '后货舱', 95.00, 70.00, 11.50, 0.00, 1.30
FROM ships s WHERE s.name = '宋代沙船-002'
ON CONFLICT DO NOTHING;

-- 第一艘船液舱
INSERT INTO cargo_holds (ship_id, hold_number, hold_name, capacity_cubic, max_weight, 
                         center_gravity_x, center_gravity_y, center_gravity_z,
                         is_tank, tank_length, tank_breadth, liquid_density, tank_fullness)
SELECT s.id, 5, '淡水舱', 12.00, 12.00, -8.00, 3.50, 0.50,
       true, 4.00, 3.00, 1.000, 0.6
FROM ships s WHERE s.name = '宋代沙船-001'
ON CONFLICT DO NOTHING;

INSERT INTO cargo_holds (ship_id, hold_number, hold_name, capacity_cubic, max_weight, 
                         center_gravity_x, center_gravity_y, center_gravity_z,
                         is_tank, tank_length, tank_breadth, liquid_density, tank_fullness)
SELECT s.id, 6, '压载水舱-左', 18.00, 18.00, 0.00, -4.20, 0.30,
       true, 6.00, 3.00, 1.025, 0.4
FROM ships s WHERE s.name = '宋代沙船-001'
ON CONFLICT DO NOTHING;

INSERT INTO cargo_holds (ship_id, hold_number, hold_name, capacity_cubic, max_weight, 
                         center_gravity_x, center_gravity_y, center_gravity_z,
                         is_tank, tank_length, tank_breadth, liquid_density, tank_fullness)
SELECT s.id, 7, '压载水舱-右', 18.00, 18.00, 0.00, 4.20, 0.30,
       true, 6.00, 3.00, 1.025, 0.4
FROM ships s WHERE s.name = '宋代沙船-001'
ON CONFLICT DO NOTHING;

-- 第二艘船液舱
INSERT INTO cargo_holds (ship_id, hold_number, hold_name, capacity_cubic, max_weight, 
                         center_gravity_x, center_gravity_y, center_gravity_z,
                         is_tank, tank_length, tank_breadth, liquid_density, tank_fullness)
SELECT s.id, 5, '淡水舱', 15.00, 15.00, -9.00, 3.80, 0.55,
       true, 4.50, 3.30, 1.000, 0.7
FROM ships s WHERE s.name = '宋代沙船-002'
ON CONFLICT DO NOTHING;

INSERT INTO cargo_holds (ship_id, hold_number, hold_name, capacity_cubic, max_weight, 
                         center_gravity_x, center_gravity_y, center_gravity_z,
                         is_tank, tank_length, tank_breadth, liquid_density, tank_fullness)
SELECT s.id, 6, '压载水舱-左', 22.00, 22.00, 0.00, -4.50, 0.35,
       true, 6.50, 3.40, 1.025, 0.3
FROM ships s WHERE s.name = '宋代沙船-002'
ON CONFLICT DO NOTHING;

INSERT INTO cargo_holds (ship_id, hold_number, hold_name, capacity_cubic, max_weight, 
                         center_gravity_x, center_gravity_y, center_gravity_z,
                         is_tank, tank_length, tank_breadth, liquid_density, tank_fullness)
SELECT s.id, 7, '压载水舱-右', 22.00, 22.00, 0.00, 4.50, 0.35,
       true, 6.50, 3.40, 1.025, 0.3
FROM ships s WHERE s.name = '宋代沙船-002'
ON CONFLICT DO NOTHING;

-- 初始化货物类型
INSERT INTO cargo_types (cargo_code, cargo_name, density, unit_weight, color_hex)
VALUES 
('GRAIN', '粮食', 0.750, 0.75, '#DEB887'),
('SALT', '海盐', 2.160, 2.16, '#F0F8FF'),
('TEA', '茶叶', 0.450, 0.45, '#228B22'),
('PORCELAIN', '瓷器', 1.800, 1.80, '#E6E6FA'),
('SILK', '丝绸', 0.350, 0.35, '#FFB6C1')
ON CONFLICT DO NOTHING;

-- 创建更新时间触发器函数
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS update_ships_updated_at ON ships;
CREATE TRIGGER update_ships_updated_at
    BEFORE UPDATE ON ships
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- 稳性计算视图
CREATE OR REPLACE VIEW v_stability_status AS
SELECT 
    sr.id,
    sr.ship_id,
    s.name as ship_name,
    sr.calculation_time,
    sr.gm_value,
    sr.metacentric_height_transverse,
    sr.roll_period,
    sr.righting_moment,
    sr.stability_status,
    sr.warning_message,
    sd.roll_angle,
    sd.draft_mean,
    sd.bilge_water_level
FROM stability_results sr
JOIN ships s ON sr.ship_id = s.id
LEFT JOIN sensor_data sd ON sr.sensor_data_id = sd.id
WHERE sr.calculation_time >= NOW() - INTERVAL '24 hours';

-- 装载分布视图
CREATE OR REPLACE VIEW v_cargo_distribution AS
SELECT 
    cl.ship_id,
    s.name as ship_name,
    ch.hold_number,
    ch.hold_name,
    ct.cargo_name,
    ct.color_hex,
    cl.weight,
    cl.volume,
    cl.loading_time,
    cl.is_optimized
FROM cargo_loadings cl
JOIN ships s ON cl.ship_id = s.id
JOIN cargo_holds ch ON cl.hold_id = ch.id
JOIN cargo_types ct ON cl.cargo_type_id = ct.id
ORDER BY cl.ship_id, ch.hold_number;
