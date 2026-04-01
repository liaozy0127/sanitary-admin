-- MySQL dump 10.13  Distrib 8.0.45, for Linux (aarch64)
--
-- Host: localhost    Database: sanitary_admin
-- ------------------------------------------------------
-- Server version	8.0.45

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `customer`
--

DROP TABLE IF EXISTS `customer`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `customer` (
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
) ENGINE=InnoDB AUTO_INCREMENT=484 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户档案';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `inventory`
--

DROP TABLE IF EXISTS `inventory`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `material_id` bigint NOT NULL,
  `customer_id` bigint NOT NULL,
  `process_id` bigint NOT NULL DEFAULT '0',
  `material_code` varchar(100) DEFAULT NULL,
  `material_name` varchar(200) DEFAULT NULL,
  `customer_name` varchar(200) DEFAULT NULL,
  `spec` varchar(200) DEFAULT NULL,
  `process_name` varchar(100) DEFAULT NULL,
  `quantity` decimal(12,2) NOT NULL DEFAULT '0.00',
  `last_receive_time` datetime DEFAULT NULL,
  `last_ship_time` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `rework_qty` decimal(12,2) DEFAULT '0.00' COMMENT '其中返工库存数量',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_material_customer_process` (`material_id`,`customer_id`,`process_id`)
) ENGINE=InnoDB AUTO_INCREMENT=83845 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `inventory_log`
--

DROP TABLE IF EXISTS `inventory_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_log` (
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
) ENGINE=InnoDB AUTO_INCREMENT=700927 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `material`
--

DROP TABLE IF EXISTS `material`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `material` (
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
) ENGINE=InnoDB AUTO_INCREMENT=24032 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物料档案';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `material_process_price`
--

DROP TABLE IF EXISTS `material_process_price`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `material_process_price` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `customer_id` bigint NOT NULL COMMENT '客户ID',
  `customer_name` varchar(100) DEFAULT NULL COMMENT '客户名称（冗余）',
  `material_id` bigint NOT NULL COMMENT '物料ID',
  `material_name` varchar(200) DEFAULT NULL COMMENT '物料名称（冗余）',
  `material_code` varchar(50) DEFAULT NULL COMMENT '物料代码（冗余）',
  `spec` varchar(200) DEFAULT NULL COMMENT '规格型号（冗余）',
  `process_id` bigint NOT NULL COMMENT '工艺ID',
  `process_name` varchar(100) DEFAULT NULL COMMENT '工艺名称（冗余）',
  `unit_price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '单价',
  `remark` varchar(200) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_customer_material_process` (`customer_id`,`material_id`,`process_id`)
) ENGINE=InnoDB AUTO_INCREMENT=33792 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物料工艺价格表（客户+物料+工艺 → 单价）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `payment`
--

DROP TABLE IF EXISTS `payment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment` (
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
) ENGINE=InnoDB AUTO_INCREMENT=3982 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收款记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `process`
--

DROP TABLE IF EXISTS `process`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process` (
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
) ENGINE=InnoDB AUTO_INCREMENT=156 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工艺数据';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `production`
--

DROP TABLE IF EXISTS `production`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `production` (
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
) ENGINE=InnoDB AUTO_INCREMENT=10820 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='排产单主表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `production_item`
--

DROP TABLE IF EXISTS `production_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `production_item` (
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
) ENGINE=InnoDB AUTO_INCREMENT=73545 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='排产单明细';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `receipt`
--

DROP TABLE IF EXISTS `receipt`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `receipt` (
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
) ENGINE=InnoDB AUTO_INCREMENT=18195 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收货单主表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `receipt_item`
--

DROP TABLE IF EXISTS `receipt_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `receipt_item` (
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
) ENGINE=InnoDB AUTO_INCREMENT=110455 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收货单明细';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `rework`
--

DROP TABLE IF EXISTS `rework`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rework` (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='返工单主表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `rework_item`
--

DROP TABLE IF EXISTS `rework_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rework_item` (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='返工单明细';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `shipment`
--

DROP TABLE IF EXISTS `shipment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `shipment` (
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
) ENGINE=InnoDB AUTO_INCREMENT=17348 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='发货单主表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `shipment_item`
--

DROP TABLE IF EXISTS `shipment_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `shipment_item` (
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
) ENGINE=InnoDB AUTO_INCREMENT=127788 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='发货单明细';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `statement`
--

DROP TABLE IF EXISTS `statement`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `statement` (
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
  `goods_amount` decimal(14,2) DEFAULT '0.00' COMMENT '良品金额（已扣除返工）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `statement_no` (`statement_no`)
) ENGINE=InnoDB AUTO_INCREMENT=3627 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='对账单主表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `statement_item`
--

DROP TABLE IF EXISTS `statement_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `statement_item` (
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
  `rework_qty` decimal(12,2) DEFAULT '0.00' COMMENT 'æœ¬æœˆè¿”å·¥æ”¶è´§æ•°é‡ï¼ˆå…è´¹ï¼Œå·²ä»Žè®¡è´¹ä¸­æ‰£é™¤ï¼‰',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=84733 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='对账单明细';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_config`
--

DROP TABLE IF EXISTS `sys_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `config_key` varchar(100) NOT NULL COMMENT '配置键',
  `config_value` varchar(500) DEFAULT NULL COMMENT '配置值',
  `remark` varchar(200) DEFAULT NULL COMMENT '说明',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `config_key` (`config_key`)
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_menu`
--

DROP TABLE IF EXISTS `sys_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_menu` (
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
) ENGINE=InnoDB AUTO_INCREMENT=43 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统菜单表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_role`
--

DROP TABLE IF EXISTS `sys_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role` (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统角色表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_user`
--

DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
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
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统用户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping routines for database 'sanitary_admin'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-04-01 21:51:45
