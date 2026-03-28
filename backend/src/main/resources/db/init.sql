SET NAMES utf8mb4;
SET character_set_client = utf8mb4;

CREATE DATABASE IF NOT EXISTS sanitary_admin DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE sanitary_admin;

-- ===== 系统用户表 =====
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

-- ===== 系统角色表 =====
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

-- ===== 系统菜单表 =====
CREATE TABLE IF NOT EXISTS `sys_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `menu_name` varchar(100) NOT NULL COMMENT '菜单名称',
  `menu_path` varchar(200) DEFAULT NULL COMMENT '菜单路径',
  `menu_icon` varchar(100) DEFAULT NULL COMMENT '菜单图标',
  `parent_id` bigint DEFAULT NULL COMMENT '父菜单ID',
  `sort` int DEFAULT '0' COMMENT '排序',
  `menu_type` tinyint DEFAULT '1' COMMENT '菜单类型：1目录 2菜单 3按钮',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1正常 0禁用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统菜单表';

-- 菜单初始数据
INSERT INTO `sys_menu` (`id`, `menu_name`, `menu_path`, `menu_icon`, `parent_id`, `sort`, `menu_type`, `status`) VALUES
(1,  '系统管理', NULL,         'Setting',       NULL, 1, 1, 1),
(2,  '用户管理', '/user',      'User',           1,    1, 2, 1),
(3,  '角色管理', '/role',      'UserFilled',     1,    2, 2, 1),
(4,  '菜单管理', '/menu',      'Menu',           1,    3, 2, 1),
(10, '基础数据', NULL,         'Files',          NULL, 2, 1, 1),
(11, '客户管理', '/customer',  'OfficeBuilding', 10,   1, 2, 1),
(12, '工艺管理', '/process',   'Operation',      10,   2, 2, 1),
(13, '物料管理', '/material',  'Box',            10,   3, 2, 1),
(20, '生产管理', NULL,         'Factory',        NULL, 3, 1, 1),
(21, '收货管理', '/receipt',   'Download',       20,   1, 2, 1),
(22, '排产管理', '/production','Calendar',       20,   2, 2, 1),
(23, '发货管理', '/shipment',  'Upload',         20,   3, 2, 1),
(24, '返工管理', '/rework',    'RefreshRight',   20,   4, 2, 1),
(30, '财务管理', NULL,         'Money',          NULL, 4, 1, 1),
(31, '收款记录', '/payment',   'Wallet',         30,   1, 2, 1),
(32, '对账单',   '/statement', 'Document',       30,   2, 2, 1),
(40, '库存报表', NULL,         'DataAnalysis',   NULL, 5, 1, 1),
(41, '库存查询', '/inventory', 'Box',            40,   1, 2, 1),
(42, '月度报表', '/report',    'TrendCharts',    40,   2, 2, 1)
ON DUPLICATE KEY UPDATE `menu_name` = VALUES(`menu_name`);

-- ===== 客户档案表 =====
CREATE TABLE IF NOT EXISTS `customer` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `customer_code` varchar(20) NOT NULL COMMENT '客户代码',
  `customer_name` varchar(100) NOT NULL COMMENT '客户名称',
  `area_name` varchar(50) DEFAULT NULL COMMENT '区域名称',
  `customer_type` varchar(10) NOT NULL DEFAULT '现金' COMMENT '客户类型：现金/月结',
  `industry` varchar(50) DEFAULT NULL COMMENT '所属行业',
  `address` varchar(200) DEFAULT NULL COMMENT '地址',
  `contact_person` varchar(50) DEFAULT NULL COMMENT '联系人',
  `contact_phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `email` varchar(100) DEFAULT NULL COMMENT '电子邮箱',
  `salesperson` varchar(50) DEFAULT NULL COMMENT '业务员',
  `bank_name` varchar(100) DEFAULT NULL COMMENT '开户银行',
  `bank_account` varchar(50) DEFAULT NULL COMMENT '银行账号',
  `tax_no` varchar(50) DEFAULT NULL COMMENT '税号',
  `finance_contact` varchar(50) DEFAULT NULL COMMENT '财务联系人',
  `finance_phone` varchar(20) DEFAULT NULL COMMENT '财务联系电话',
  `price_adjust_rate` decimal(5,2) DEFAULT '0.00' COMMENT '调价率',
  `ship_warning_days` int DEFAULT '0' COMMENT '发货预警天数',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用 1启用',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `customer_code` (`customer_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户档案';

-- ===== 工艺数据表 =====
CREATE TABLE IF NOT EXISTS `process` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `process_code` varchar(20) NOT NULL COMMENT '工艺代码',
  `process_name` varchar(100) NOT NULL COMMENT '工艺名称',
  `process_category` varchar(50) DEFAULT NULL COMMENT '工艺类别',
  `process_nature` varchar(50) DEFAULT NULL COMMENT '工艺性质',
  `thickness_req` varchar(100) DEFAULT NULL COMMENT '厚度要求',
  `default_quote` tinyint DEFAULT '0' COMMENT '缺省报价',
  `priority_no` int DEFAULT NULL COMMENT '优先编号',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '0禁用 1启用',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `process_code` (`process_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工艺数据';

-- ===== 物料档案表 =====
CREATE TABLE IF NOT EXISTS `material` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `material_code` varchar(50) NOT NULL COMMENT '物料代码',
  `material_name` varchar(200) NOT NULL COMMENT '物料名称',
  `spec` varchar(200) DEFAULT NULL COMMENT '规格型号',
  `customer_id` bigint DEFAULT NULL COMMENT '所属客户ID',
  `customer_name` varchar(100) DEFAULT NULL COMMENT '客户名称（冗余）',
  `default_price` decimal(10,2) DEFAULT '0.00' COMMENT '默认单价（价格记忆）',
  `unit` varchar(20) DEFAULT '个' COMMENT '计量单位',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '0停用 1启用',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `material_code` (`material_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物料档案';

-- ===== 收货单主表 =====
CREATE TABLE IF NOT EXISTS `receipt` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `receipt_no` varchar(30) NOT NULL COMMENT '收货单号',
  `receipt_date` date NOT NULL COMMENT '收货日期',
  `customer_id` bigint NOT NULL COMMENT '客户ID',
  `customer_name` varchar(100) NOT NULL COMMENT '客户名称',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1正常 0作废',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `receipt_no` (`receipt_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收货单主表';

-- ===== 收货单明细表 =====
CREATE TABLE IF NOT EXISTS `receipt_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `receipt_id` bigint NOT NULL COMMENT '收货单ID',
  `receipt_no` varchar(30) NOT NULL COMMENT '收货单号',
  `material_id` bigint DEFAULT NULL COMMENT '物料ID',
  `material_name` varchar(200) DEFAULT NULL COMMENT '物料名称',
  `material_code` varchar(50) DEFAULT NULL COMMENT '物料代码',
  `spec` varchar(200) DEFAULT NULL COMMENT '规格型号',
  `process_id` bigint DEFAULT NULL COMMENT '工艺ID',
  `process_name` varchar(100) DEFAULT NULL COMMENT '工艺名称',
  `receipt_source` varchar(100) DEFAULT NULL COMMENT '收货来源',
  `quantity` decimal(12,2) DEFAULT '0.00' COMMENT '收货数量',
  `shipped_qty` decimal(12,2) DEFAULT '0.00' COMMENT '已发数量',
  `unshipped_qty` decimal(12,2) DEFAULT '0.00' COMMENT '未发数量',
  `planned_qty` decimal(12,2) DEFAULT '0.00' COMMENT '排产数量',
  `warehoused_qty` decimal(12,2) DEFAULT '0.00' COMMENT '入库数量',
  `unwarehoused_qty` decimal(12,2) DEFAULT '0.00' COMMENT '未入库数量',
  `unit_price` decimal(10,2) DEFAULT '0.00' COMMENT '单价',
  `amount` decimal(12,2) DEFAULT '0.00' COMMENT '金额',
  `customer_order_no` varchar(100) DEFAULT NULL COMMENT '客户订单号',
  `detail_remark` text COMMENT '明细备注',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收货单明细';

-- ===== 排产单主表 =====
CREATE TABLE IF NOT EXISTS `production` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `production_no` varchar(30) NOT NULL COMMENT '排产单号',
  `production_date` date NOT NULL COMMENT '排产日期',
  `customer_id` bigint NOT NULL COMMENT '客户ID',
  `customer_name` varchar(100) NOT NULL COMMENT '客户名称',
  `dept_name` varchar(100) DEFAULT NULL COMMENT '部门名称',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `production_no` (`production_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='排产单主表';

-- ===== 排产单明细表 =====
CREATE TABLE IF NOT EXISTS `production_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `production_id` bigint NOT NULL COMMENT '排产单ID',
  `production_no` varchar(30) NOT NULL COMMENT '排产单号',
  `material_id` bigint DEFAULT NULL COMMENT '物料ID',
  `material_name` varchar(200) DEFAULT NULL COMMENT '物料名称',
  `material_code` varchar(50) DEFAULT NULL COMMENT '物料代码',
  `spec` varchar(200) DEFAULT NULL COMMENT '规格型号',
  `process_id` bigint DEFAULT NULL COMMENT '工艺ID',
  `process_name` varchar(100) DEFAULT NULL COMMENT '工艺名称',
  `receipt_type` varchar(50) DEFAULT NULL COMMENT '收货类型',
  `unit` varchar(50) DEFAULT NULL COMMENT '计量单位',
  `planned_qty` decimal(12,2) DEFAULT '0.00' COMMENT '排产数量',
  `actual_qty` decimal(12,2) DEFAULT '0.00' COMMENT '入库数量',
  `unwarehoused_qty` decimal(12,2) DEFAULT '0.00' COMMENT '未入库数量',
  `outsource_price` decimal(10,2) DEFAULT '0.00' COMMENT '委外单价',
  `plating_price` decimal(10,2) DEFAULT '0.00' COMMENT '电镀单价',
  `plating_amount` decimal(12,2) DEFAULT '0.00' COMMENT '电镀金额',
  `customer_order_no` varchar(100) DEFAULT NULL COMMENT '客户订单号',
  `production_type` varchar(50) DEFAULT NULL COMMENT '排产方式',
  `detail_remark` text COMMENT '明细备注',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='排产单明细';

-- ===== 发货单主表 =====
CREATE TABLE IF NOT EXISTS `shipment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `shipment_no` varchar(30) NOT NULL COMMENT '发货单号',
  `shipment_date` date NOT NULL COMMENT '发货日期',
  `customer_id` bigint NOT NULL COMMENT '客户ID',
  `customer_name` varchar(100) NOT NULL COMMENT '客户名称',
  `operator` varchar(50) DEFAULT NULL COMMENT '制单人',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1正常 0作废',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `shipment_no` (`shipment_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发货单主表';

-- ===== 发货单明细表 =====
CREATE TABLE IF NOT EXISTS `shipment_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `shipment_id` bigint NOT NULL COMMENT '发货单ID',
  `shipment_no` varchar(30) NOT NULL COMMENT '发货单号',
  `material_id` bigint DEFAULT NULL COMMENT '物料ID',
  `material_name` varchar(200) DEFAULT NULL COMMENT '物料名称',
  `material_code` varchar(50) DEFAULT NULL COMMENT '物料代码',
  `spec` varchar(200) DEFAULT NULL COMMENT '规格型号',
  `process_id` bigint DEFAULT NULL COMMENT '工艺ID',
  `process_name` varchar(100) DEFAULT NULL COMMENT '工艺名称',
  `quantity` decimal(12,2) DEFAULT '0.00' COMMENT '良品数量（实际发货量）',
  `defective_qty` decimal(12,2) DEFAULT '0.00' COMMENT '废品数量',
  `unit_price` decimal(10,2) DEFAULT '0.00' COMMENT '单价',
  `amount` decimal(12,2) DEFAULT '0.00' COMMENT '金额（良品数量×单价）',
  `detail_remark` text COMMENT '明细备注',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发货单明细';

-- ===== 返工单主表 =====
CREATE TABLE IF NOT EXISTS `rework` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `rework_no` varchar(30) NOT NULL COMMENT '返工单号',
  `rework_date` date NOT NULL COMMENT '返工日期',
  `customer_id` bigint NOT NULL,
  `customer_name` varchar(100) NOT NULL,
  `rework_status` varchar(20) DEFAULT '待处理' COMMENT '待处理/处理中/已完成',
  `remark` varchar(500) DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `rework_no` (`rework_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='返工单主表';

-- ===== 返工单明细表 =====
CREATE TABLE IF NOT EXISTS `rework_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `rework_id` bigint NOT NULL,
  `rework_no` varchar(30) NOT NULL,
  `material_id` bigint DEFAULT NULL,
  `material_name` varchar(200) DEFAULT NULL,
  `material_code` varchar(50) DEFAULT NULL,
  `spec` varchar(200) DEFAULT NULL,
  `process_id` bigint DEFAULT NULL,
  `process_name` varchar(100) DEFAULT NULL,
  `quantity` decimal(12,2) DEFAULT '0.00',
  `unit_price` decimal(10,4) DEFAULT '0.0000',
  `amount` decimal(12,2) DEFAULT '0.00',
  `rework_reason` varchar(500) DEFAULT NULL,
  `detail_remark` text,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='返工单明细';

-- ===== 收款记录表 =====
CREATE TABLE IF NOT EXISTS `payment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `payment_no` varchar(30) NOT NULL COMMENT '收款单号',
  `payment_date` date NOT NULL COMMENT '收款日期',
  `customer_id` bigint NOT NULL,
  `customer_name` varchar(100) NOT NULL,
  `amount` decimal(12,2) NOT NULL COMMENT '收款金额',
  `payment_method` varchar(50) DEFAULT '银行转账' COMMENT '收款方式：现金/银行转账/微信/支付宝',
  `reference_no` varchar(100) DEFAULT NULL COMMENT '参考单号',
  `remark` varchar(500) DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `payment_no` (`payment_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收款记录';

-- ===== 对账单主表 =====
-- goods_amount：良品金额汇总 = SUM(statement_item.goods_amount)
-- generate() 方法实时计算；importExcel() 从 Excel col11 汇总写入
CREATE TABLE IF NOT EXISTS `statement` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `statement_no` varchar(30) NOT NULL COMMENT '对账单号',
  `statement_month` varchar(7) NOT NULL COMMENT '对账月份，如 2026-03',
  `customer_id` bigint NOT NULL,
  `customer_name` varchar(100) NOT NULL,
  `receipt_qty` decimal(12,2) DEFAULT '0.00' COMMENT '本月收货数量',
  `shipment_qty` decimal(12,2) DEFAULT '0.00' COMMENT '本月发货数量',
  `receipt_amount` decimal(12,2) DEFAULT '0.00' COMMENT '本月收货金额',
  `shipment_amount` decimal(12,2) DEFAULT '0.00' COMMENT '本月发货金额',
  `remark` varchar(500) DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `goods_amount` decimal(14,2) DEFAULT '0.00' COMMENT '良品金额汇总（已扣除返工，= 各明细良品金额合计）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `statement_no` (`statement_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对账单主表';

-- ===== 对账单明细表 =====
-- rework_qty：本月收货明细中 receipt_source='返工' 的数量合计，用于良品金额扣减
-- goods_amount = MAX(0, shipment_qty - defective_qty - rework_qty) × unit_price
CREATE TABLE IF NOT EXISTS `statement_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `statement_id` bigint NOT NULL,
  `statement_no` varchar(30) NOT NULL,
  `material_id` bigint DEFAULT NULL,
  `material_code` varchar(50) DEFAULT NULL,
  `material_name` varchar(200) DEFAULT NULL,
  `process_id` bigint DEFAULT NULL,
  `process_name` varchar(100) DEFAULT NULL,
  `prev_balance_qty` decimal(12,2) DEFAULT '0.00',
  `receipt_qty` decimal(12,2) DEFAULT '0.00',
  `shipment_qty` decimal(12,2) DEFAULT '0.00',
  `defective_qty` decimal(12,2) DEFAULT '0.00' COMMENT '原件退回数量',
  `curr_balance_qty` decimal(12,2) DEFAULT '0.00',
  `unit_price` decimal(10,4) DEFAULT '0.0000',
  `goods_amount` decimal(12,2) DEFAULT '0.00' COMMENT '良品金额',
  `shipment_amount` decimal(12,2) DEFAULT '0.00',
  `remark` varchar(500) DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `rework_qty` decimal(12,2) DEFAULT '0.00' COMMENT '本月返工收货数量（免费，已从计费中扣除）',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对账单明细';

-- ===== 库存表 =====
-- quantity：当前库存总数；rework_qty：其中属于返工件的数量
-- 返工收货入库时 +rework_qty；发货时优先消耗 rework_qty（扣到0为止）
CREATE TABLE IF NOT EXISTS `inventory` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `material_id` bigint NOT NULL,
  `customer_id` bigint NOT NULL,
  `process_id` bigint NOT NULL DEFAULT '0',
  `material_code` varchar(100) DEFAULT NULL,
  `material_name` varchar(200) DEFAULT NULL,
  `customer_name` varchar(200) DEFAULT NULL,
  `spec` varchar(200) DEFAULT NULL,
  `process_name` varchar(100) DEFAULT NULL,
  `quantity` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '库存总数',
  `last_receive_time` datetime DEFAULT NULL,
  `last_ship_time` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `rework_qty` decimal(12,2) DEFAULT '0.00' COMMENT '其中返工库存数量',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_material_customer_process` (`material_id`, `customer_id`, `process_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存表';

-- ===== 库存日志表 =====
CREATE TABLE IF NOT EXISTS `inventory_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `material_id` bigint NOT NULL,
  `customer_id` bigint NOT NULL,
  `process_id` bigint DEFAULT NULL,
  `material_code` varchar(100) DEFAULT NULL,
  `material_name` varchar(200) DEFAULT NULL,
  `customer_name` varchar(200) DEFAULT NULL,
  `spec` varchar(200) DEFAULT NULL,
  `process_name` varchar(100) DEFAULT NULL,
  `change_type` int NOT NULL COMMENT '1=收货 2=发货 3=返工',
  `change_qty` decimal(12,2) NOT NULL,
  `before_qty` decimal(12,2) NOT NULL,
  `after_qty` decimal(12,2) NOT NULL,
  `order_type` varchar(50) NOT NULL,
  `order_id` bigint NOT NULL,
  `order_no` varchar(100) NOT NULL,
  `order_date` date DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存日志表';

-- ===== 系统配置表 =====
-- 用于存储打印配置等系统参数，通过 /api/config 接口读写
CREATE TABLE IF NOT EXISTS `sys_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `config_key` varchar(100) NOT NULL COMMENT '配置键',
  `config_value` varchar(500) DEFAULT NULL COMMENT '配置值',
  `remark` varchar(200) DEFAULT NULL COMMENT '说明',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- 打印配置初始数据（部署后按实际工厂信息修改）
INSERT INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
('print.factory_name',       '广州某某电镀有限公司',                                      '打印单据工厂名称'),
('print.maker_name',         '张三',                                                       '打印单据制单人'),
('print.title_production',   '请修改为实际排产单标题',                                      NULL),
('print.title_delivery',     '请修改为实际送货单标题',                                      NULL),
('print.company_name',       '请修改为实际公司名称',                                        NULL),
('print.company_phone',      '',                                                            NULL),
('print.company_address',    '',                                                            NULL),
('print.contact_1',          '',                                                            NULL),
('print.contact_2',          '',                                                            NULL),
('print.signature_1_label',  '仓管：',                                                     NULL),
('print.signature_2_label',  '生产班长：',                                                  NULL),
('print.signature_3_label',  '签名：',                                                      NULL),
('print.maker_label',        '制单人',                                                      NULL),
('print.delivery_remark',    '备注：1. 每批产品不良品请在15天内退回返工，否则视为合格品计算。', NULL),
('print.delivery_sig1_label','制单人：',                                                    NULL),
('print.delivery_sig2_label','仓管员：',                                                    NULL),
('print.delivery_sig3_label','收货单位：',                                                  NULL)
ON DUPLICATE KEY UPDATE `config_key` = `config_key`;
