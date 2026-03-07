SET NAMES utf8mb4;
SET character_set_client = utf8mb4;

CREATE DATABASE IF NOT EXISTS sanitary_admin DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE sanitary_admin;

CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(100) NOT NULL COMMENT '密码（BCrypt加密）',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `role` varchar(20) NOT NULL DEFAULT 'USER' COMMENT '角色：ADMIN/USER',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1正常 0禁用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- 默认管理员账号：admin / admin123（BCrypt加密）
INSERT INTO `sys_user` (`username`, `password`, `email`, `role`, `status`)
VALUES ('admin', '$2b$10$gVmswZWzA42HHQnh3CCE5.NFc7f7wpt8cLtBJNxX5fJ1P5ozNYAsK', 'admin@sanitary.com', 'ADMIN', 1)
ON DUPLICATE KEY UPDATE `username` = `username`;

-- 角色表
CREATE TABLE IF NOT EXISTS `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `role_name` varchar(50) NOT NULL COMMENT '角色名称',
  `role_code` varchar(100) NOT NULL COMMENT '角色编码',
  `description` varchar(500) DEFAULT NULL COMMENT '描述',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1正常 0禁用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色表';

-- 菜单表
CREATE TABLE IF NOT EXISTS `sys_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `menu_name` varchar(100) NOT NULL COMMENT '菜单名称',
  `menu_path` varchar(200) DEFAULT NULL COMMENT '菜单路径',
  `menu_icon` varchar(100) DEFAULT NULL COMMENT '菜单图标',
  `parent_id` bigint DEFAULT NULL COMMENT '父菜单ID',
  `sort` int DEFAULT 0 COMMENT '排序',
  `menu_type` tinyint DEFAULT '1' COMMENT '菜单类型：1目录 2菜单 3按钮',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1正常 0禁用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统菜单表';

-- 菜单初始数据（系统管理 + 基础数据）
INSERT INTO `sys_menu` (`id`, `menu_name`, `menu_path`, `menu_icon`, `parent_id`, `sort`, `menu_type`, `status`) VALUES
(1, '系统管理', NULL, 'Setting', NULL, 1, 1, 1),
(2, '用户管理', '/user', 'User', 1, 1, 2, 1),
(3, '角色管理', '/role', 'UserFilled', 1, 2, 2, 1),
(4, '菜单管理', '/menu', 'Menu', 1, 3, 2, 1),
(10, '基础数据', NULL, 'Files', NULL, 2, 1, 1),
(11, '客户管理', '/customer', 'OfficeBuilding', 10, 1, 2, 1),
(12, '工艺管理', '/process', 'Operation', 10, 2, 2, 1),
(13, '物料管理', '/material', 'Box', 10, 3, 2, 1)
ON DUPLICATE KEY UPDATE `menu_name` = VALUES(`menu_name`);

-- 客户档案表
CREATE TABLE IF NOT EXISTS `customer` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `customer_code` VARCHAR(20) NOT NULL UNIQUE COMMENT '客户代码',
    `customer_name` VARCHAR(100) NOT NULL COMMENT '客户名称',
    `area_name` VARCHAR(50) COMMENT '区域名称',
    `customer_type` VARCHAR(10) NOT NULL DEFAULT '现金' COMMENT '客户类型：现金/月结',
    `industry` VARCHAR(50) COMMENT '所属行业',
    `address` VARCHAR(200) COMMENT '地址',
    `contact_person` VARCHAR(50) COMMENT '联系人',
    `contact_phone` VARCHAR(20) COMMENT '联系电话',
    `email` VARCHAR(100) COMMENT '电子邮箱',
    `salesperson` VARCHAR(50) COMMENT '业务员',
    `bank_name` VARCHAR(100) COMMENT '开户银行',
    `bank_account` VARCHAR(50) COMMENT '银行账号',
    `tax_no` VARCHAR(50) COMMENT '税号',
    `finance_contact` VARCHAR(50) COMMENT '财务联系人',
    `finance_phone` VARCHAR(20) COMMENT '财务联系电话',
    `price_adjust_rate` DECIMAL(5,2) DEFAULT 0 COMMENT '调价率',
    `ship_warning_days` INT DEFAULT 0 COMMENT '发货预警天数',
    `remark` VARCHAR(500) COMMENT '备注',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0停用 1启用',
    `deleted` TINYINT NOT NULL DEFAULT 0,
    `create_time` DATETIME,
    `update_time` DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户档案';

-- 工艺数据表
CREATE TABLE IF NOT EXISTS `process` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `process_code` VARCHAR(20) NOT NULL UNIQUE COMMENT '工艺代码',
    `process_name` VARCHAR(100) NOT NULL COMMENT '工艺名称',
    `process_category` VARCHAR(50) COMMENT '工艺类别',
    `process_nature` VARCHAR(50) COMMENT '工艺性质',
    `thickness_req` VARCHAR(100) COMMENT '厚度要求',
    `default_quote` TINYINT DEFAULT 0 COMMENT '缺省报价',
    `priority_no` INT COMMENT '优先编号',
    `remark` VARCHAR(500) COMMENT '备注',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
    `deleted` TINYINT NOT NULL DEFAULT 0,
    `create_time` DATETIME,
    `update_time` DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工艺数据';

-- 物料档案表
CREATE TABLE IF NOT EXISTS `material` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `material_code` VARCHAR(50) NOT NULL UNIQUE COMMENT '物料代码',
    `material_name` VARCHAR(200) NOT NULL COMMENT '物料名称',
    `spec` VARCHAR(200) COMMENT '规格型号',
    `customer_id` BIGINT COMMENT '所属客户ID',
    `customer_name` VARCHAR(100) COMMENT '客户名称（冗余）',
    `default_price` DECIMAL(10,4) DEFAULT 0 COMMENT '默认单价（价格记忆）',
    `unit` VARCHAR(20) DEFAULT '个' COMMENT '计量单位',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0停用 1启用',
    `deleted` TINYINT NOT NULL DEFAULT 0,
    `create_time` DATETIME,
    `update_time` DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物料档案';

-- ===== Phase 2: 收发货核心模块 =====

-- 收货单表
CREATE TABLE IF NOT EXISTS `receipt` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `receipt_no` VARCHAR(30) NOT NULL UNIQUE COMMENT '收货单号，如 RH202603070001',
    `receipt_date` DATE NOT NULL COMMENT '收货日期',
    `customer_id` BIGINT NOT NULL COMMENT '客户ID',
    `customer_name` VARCHAR(100) NOT NULL COMMENT '客户名称',
    `material_id` BIGINT NOT NULL COMMENT '物料ID',
    `material_name` VARCHAR(200) NOT NULL COMMENT '物料名称',
    `material_code` VARCHAR(50) COMMENT '物料代码',
    `spec` VARCHAR(200) COMMENT '规格型号',
    `process_id` BIGINT COMMENT '工艺ID',
    `process_name` VARCHAR(100) COMMENT '工艺名称',
    `quantity` DECIMAL(12,2) NOT NULL COMMENT '收货数量',
    `unit_price` DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT '单价',
    `amount` DECIMAL(12,2) COMMENT '金额（数量×单价）',
    `remark` VARCHAR(500) COMMENT '备注',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1正常 0作废',
    `deleted` TINYINT NOT NULL DEFAULT 0,
    `create_time` DATETIME,
    `update_time` DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收货单';

-- 排产单表
CREATE TABLE IF NOT EXISTS `production` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `production_no` VARCHAR(30) NOT NULL UNIQUE COMMENT '排产单号，如 PC202603070001',
    `production_date` DATE NOT NULL COMMENT '排产日期',
    `customer_id` BIGINT NOT NULL COMMENT '客户ID',
    `customer_name` VARCHAR(100) NOT NULL COMMENT '客户名称',
    `material_id` BIGINT COMMENT '物料ID',
    `material_name` VARCHAR(200) NOT NULL COMMENT '物料名称',
    `material_code` VARCHAR(50) COMMENT '物料代码',
    `spec` VARCHAR(200) COMMENT '规格型号',
    `process_id` BIGINT COMMENT '工艺ID',
    `process_name` VARCHAR(100) COMMENT '工艺名称',
    `planned_qty` DECIMAL(12,2) NOT NULL COMMENT '计划数量',
    `actual_qty` DECIMAL(12,2) DEFAULT 0 COMMENT '实际完成数量',
    `unit_price` DECIMAL(10,4) DEFAULT 0 COMMENT '单价',
    `amount` DECIMAL(12,2) DEFAULT 0 COMMENT '金额',
    `prod_status` VARCHAR(20) DEFAULT '待生产' COMMENT '生产状态：待生产/生产中/已完成',
    `remark` VARCHAR(500) COMMENT '备注',
    `deleted` TINYINT NOT NULL DEFAULT 0,
    `create_time` DATETIME,
    `update_time` DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='排产单';

-- 发货单表
CREATE TABLE IF NOT EXISTS `shipment` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `shipment_no` VARCHAR(30) NOT NULL UNIQUE COMMENT '发货单号，如 FH202603070001',
    `shipment_date` DATE NOT NULL COMMENT '发货日期',
    `customer_id` BIGINT NOT NULL COMMENT '客户ID',
    `customer_name` VARCHAR(100) NOT NULL COMMENT '客户名称',
    `material_id` BIGINT NOT NULL COMMENT '物料ID',
    `material_name` VARCHAR(200) NOT NULL COMMENT '物料名称',
    `material_code` VARCHAR(50) COMMENT '物料代码',
    `spec` VARCHAR(200) COMMENT '规格型号',
    `process_id` BIGINT COMMENT '工艺ID',
    `process_name` VARCHAR(100) COMMENT '工艺名称',
    `quantity` DECIMAL(12,2) NOT NULL COMMENT '发货数量',
    `unit_price` DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT '单价',
    `amount` DECIMAL(12,2) COMMENT '金额',
    `remark` VARCHAR(500) COMMENT '备注',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1正常 0作废',
    `deleted` TINYINT NOT NULL DEFAULT 0,
    `create_time` DATETIME,
    `update_time` DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发货单';

-- ===== Phase 3: 财务模块 =====

-- 返工单表
CREATE TABLE IF NOT EXISTS `rework` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `rework_no` VARCHAR(30) NOT NULL UNIQUE COMMENT '返工单号，如 FG202603070001',
    `rework_date` DATE NOT NULL COMMENT '返工日期',
    `customer_id` BIGINT NOT NULL,
    `customer_name` VARCHAR(100) NOT NULL,
    `material_id` BIGINT,
    `material_name` VARCHAR(200) NOT NULL,
    `material_code` VARCHAR(50),
    `spec` VARCHAR(200),
    `process_id` BIGINT,
    `process_name` VARCHAR(100),
    `quantity` DECIMAL(12,2) NOT NULL COMMENT '返工数量',
    `unit_price` DECIMAL(10,4) DEFAULT 0,
    `amount` DECIMAL(12,2) DEFAULT 0,
    `rework_reason` VARCHAR(500) COMMENT '返工原因',
    `rework_status` VARCHAR(20) DEFAULT '待处理' COMMENT '待处理/处理中/已完成',
    `deleted` TINYINT NOT NULL DEFAULT 0,
    `create_time` DATETIME,
    `update_time` DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='返工单';

-- 收款记录表
CREATE TABLE IF NOT EXISTS `payment` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `payment_no` VARCHAR(30) NOT NULL UNIQUE COMMENT '收款单号，如 SK202603070001',
    `payment_date` DATE NOT NULL COMMENT '收款日期',
    `customer_id` BIGINT NOT NULL,
    `customer_name` VARCHAR(100) NOT NULL,
    `amount` DECIMAL(12,2) NOT NULL COMMENT '收款金额',
    `payment_method` VARCHAR(50) DEFAULT '银行转账' COMMENT '收款方式：现金/银行转账/微信/支付宝',
    `reference_no` VARCHAR(100) COMMENT '参考单号',
    `remark` VARCHAR(500),
    `deleted` TINYINT NOT NULL DEFAULT 0,
    `create_time` DATETIME,
    `update_time` DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收款记录';

-- 对账单表
CREATE TABLE IF NOT EXISTS `statement` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `statement_no` VARCHAR(30) NOT NULL UNIQUE COMMENT '对账单号，如 DZ2026030001',
    `statement_month` VARCHAR(7) NOT NULL COMMENT '对账月份，如 2026-03',
    `customer_id` BIGINT NOT NULL,
    `customer_name` VARCHAR(100) NOT NULL,
    `receipt_qty` DECIMAL(12,2) DEFAULT 0 COMMENT '本月收货数量',
    `shipment_qty` DECIMAL(12,2) DEFAULT 0 COMMENT '本月发货数量',
    `receipt_amount` DECIMAL(12,2) DEFAULT 0 COMMENT '本月收货金额',
    `shipment_amount` DECIMAL(12,2) DEFAULT 0 COMMENT '本月发货金额',
    `remark` VARCHAR(500),
    `status` VARCHAR(20) DEFAULT '未确认' COMMENT '未确认/已确认',
    `deleted` TINYINT NOT NULL DEFAULT 0,
    `create_time` DATETIME,
    `update_time` DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对账单';

-- ===== 菜单数据更新 =====
INSERT INTO `sys_menu` (`id`, `menu_name`, `menu_path`, `menu_icon`, `parent_id`, `sort`, `menu_type`, `status`) VALUES
(20, '生产管理', NULL, 'Factory', NULL, 3, 1, 1),
(21, '收货管理', '/receipt', 'Download', 20, 1, 2, 1),
(22, '排产管理', '/production', 'Calendar', 20, 2, 2, 1),
(23, '发货管理', '/shipment', 'Upload', 20, 3, 2, 1),
(24, '返工管理', '/rework', 'RefreshRight', 20, 4, 2, 1),
(30, '财务管理', NULL, 'Money', NULL, 4, 1, 1),
(31, '收款记录', '/payment', 'Wallet', 30, 1, 2, 1),
(32, '对账单', '/statement', 'Document', 30, 2, 2, 1),
(40, '库存报表', NULL, 'DataAnalysis', NULL, 5, 1, 1),
(41, '库存查询', '/inventory', 'Box', 40, 1, 2, 1),
(42, '月度报表', '/report', 'TrendCharts', 40, 2, 2, 1)
ON DUPLICATE KEY UPDATE `menu_name` = VALUES(`menu_name`);
