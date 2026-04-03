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
-- Dumping data for table `sys_config`
--

/*!40000 ALTER TABLE `sys_config` DISABLE KEYS */;
INSERT INTO `sys_config` VALUES (1,'print.factory_name','广州某某电镀有限公司','打印单据工厂名称','2026-03-19 15:11:26','2026-03-19 15:11:26'),(2,'print.maker_name','张三','打印单据制单人','2026-03-19 15:11:26','2026-03-19 15:11:26'),(3,'print.title_production','致恒（致越）金属表面加工厂生产安排表',NULL,'2026-03-25 14:21:08','2026-03-25 14:21:08'),(4,'print.title_delivery','致恒（致越）金属表面加工厂送货单',NULL,'2026-03-25 14:21:08','2026-03-25 14:21:08'),(5,'print.company_name','致恒（致越）金属表面加工厂',NULL,'2026-03-25 14:21:08','2026-03-25 14:21:08'),(6,'print.company_phone','0750-2766036',NULL,'2026-03-25 14:21:08','2026-03-25 14:21:08'),(7,'print.company_address','开平市，水口镇，唐良良兴村矮岗山',NULL,'2026-03-25 14:21:08','2026-03-25 14:21:08'),(8,'print.contact_1','廖总：13536094788',NULL,'2026-03-25 14:21:08','2026-03-25 14:21:08'),(9,'print.contact_2','仓管：13672842611',NULL,'2026-03-25 14:21:08','2026-03-25 14:21:08'),(10,'print.signature_1_label','仓管：',NULL,'2026-03-25 14:21:08','2026-03-25 14:21:08'),(11,'print.signature_2_label','生产班长：',NULL,'2026-03-25 14:21:08','2026-03-25 14:21:08'),(12,'print.signature_3_label','签名：致恒',NULL,'2026-03-25 14:21:08','2026-03-25 14:21:08'),(13,'print.maker_label','制单人',NULL,'2026-03-25 14:21:08','2026-03-25 14:21:08'),(14,'print.delivery_remark','备注：1. 每批产品不良品请要15天内退回我厂返工，否则视为合格品计算。\n2. 如有送错非本厂的产品请在3天内退回，谢谢合作。\n注：白色存根联，红色客户联，黄色会计联。',NULL,'2026-03-25 14:21:08','2026-03-25 14:21:08'),(15,'print.delivery_sig1_label','制单人：致恒',NULL,'2026-03-25 15:33:19','2026-03-25 15:33:19'),(16,'print.delivery_sig2_label','仓管员：',NULL,'2026-03-25 15:33:19','2026-03-25 15:33:19'),(17,'print.delivery_sig3_label','收货单位：',NULL,'2026-03-25 15:33:19','2026-03-25 15:33:19');
/*!40000 ALTER TABLE `sys_config` ENABLE KEYS */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-04-03 11:59:27
