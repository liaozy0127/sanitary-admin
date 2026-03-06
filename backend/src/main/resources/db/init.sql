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
VALUES ('admin', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'admin@sanitary.com', 'ADMIN', 1)
ON DUPLICATE KEY UPDATE `username` = `username`;
