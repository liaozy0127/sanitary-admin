-- 库存持久化改造 + 各单据字段补充

-- 1. 创建 inventory 表
CREATE TABLE IF NOT EXISTS inventory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    material_id BIGINT NOT NULL COMMENT '物料ID',
    customer_id BIGINT NOT NULL COMMENT '客户ID',
    process_id BIGINT COMMENT '工艺ID',
    material_code VARCHAR(100) COMMENT '物料编码',
    material_name VARCHAR(200) COMMENT '物料名称',
    customer_name VARCHAR(200) COMMENT '客户名称',
    spec VARCHAR(200) COMMENT '规格',
    process_name VARCHAR(100) COMMENT '工艺名称',
    quantity DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '数量',
    last_receive_time DATETIME COMMENT '最后收货时间',
    last_ship_time DATETIME COMMENT '最后发货时间',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    UNIQUE KEY uk_material_customer_process (material_id, customer_id, process_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存表';

-- 2. 创建 inventory_log 表
CREATE TABLE IF NOT EXISTS inventory_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    material_id BIGINT NOT NULL COMMENT '物料ID',
    customer_id BIGINT NOT NULL COMMENT '客户ID',
    process_id BIGINT COMMENT '工艺ID',
    material_code VARCHAR(100) COMMENT '物料编码',
    material_name VARCHAR(200) COMMENT '物料名称',
    customer_name VARCHAR(200) COMMENT '客户名称',
    spec VARCHAR(200) COMMENT '规格',
    process_name VARCHAR(100) COMMENT '工艺名称',
    change_type INT NOT NULL COMMENT '变动类型 1=收货 2=发货 3=返工',
    change_qty DECIMAL(12,2) NOT NULL COMMENT '变动数量',
    before_qty DECIMAL(12,2) NOT NULL COMMENT '变动前数量',
    after_qty DECIMAL(12,2) NOT NULL COMMENT '变动后数量',
    order_type VARCHAR(50) NOT NULL COMMENT '单据类型 RECEIPT/SHIPMENT/REWORK',
    order_id BIGINT NOT NULL COMMENT '单据ID',
    order_no VARCHAR(100) NOT NULL COMMENT '单据编号',
    order_date DATE COMMENT '单据日期',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME NOT NULL COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存日志表';

-- 3. 给各单据表添加字段

-- 给收货单表添加字段
ALTER TABLE receipt ADD COLUMN receipt_source VARCHAR(100) COMMENT '收货来源';
ALTER TABLE receipt ADD COLUMN customer_order_no VARCHAR(100) COMMENT '客户订单号';
ALTER TABLE receipt ADD COLUMN detail_remark TEXT COMMENT '详细备注';

-- 给生产单表添加字段
ALTER TABLE production ADD COLUMN unit VARCHAR(50) COMMENT '单位';
ALTER TABLE production ADD COLUMN receipt_type VARCHAR(50) COMMENT '收货类型';
ALTER TABLE production ADD COLUMN outsource_price DECIMAL(12,2) COMMENT '外协价格';
ALTER TABLE production ADD COLUMN plating_price DECIMAL(12,2) COMMENT '电镀价格';
ALTER TABLE production ADD COLUMN plating_amount DECIMAL(12,2) COMMENT '电镀金额';
ALTER TABLE production ADD COLUMN customer_order_no VARCHAR(100) COMMENT '客户订单号';
ALTER TABLE production ADD COLUMN production_type VARCHAR(50) COMMENT '生产类型';

-- 给发货单表添加字段
ALTER TABLE shipment ADD COLUMN customer_order_no VARCHAR(100) COMMENT '客户订单号';
ALTER TABLE shipment ADD COLUMN detail_remark TEXT COMMENT '详细备注';
ALTER TABLE shipment ADD COLUMN shipment_type VARCHAR(50) COMMENT '发货类型';

-- 4. 从客户表删除字段
ALTER TABLE customer DROP COLUMN area_name;
ALTER TABLE customer DROP COLUMN industry;
ALTER TABLE customer DROP COLUMN email;
ALTER TABLE customer DROP COLUMN finance_contact;
ALTER TABLE customer DROP COLUMN finance_phone;
ALTER TABLE customer DROP COLUMN price_adjust_rate;
ALTER TABLE customer DROP COLUMN ship_warning_days;