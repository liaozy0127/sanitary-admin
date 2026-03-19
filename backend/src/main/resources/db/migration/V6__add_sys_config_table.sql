CREATE TABLE IF NOT EXISTS sys_config (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    config_key   VARCHAR(100) NOT NULL UNIQUE COMMENT '配置键',
    config_value VARCHAR(500)          COMMENT '配置值',
    remark       VARCHAR(200)          COMMENT '说明',
    create_time  DATETIME,
    update_time  DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

INSERT INTO sys_config (config_key, config_value, remark, create_time, update_time) VALUES
  ('print.factory_name', '', '打印单据工厂名称', NOW(), NOW()),
  ('print.maker_name',   '', '打印单据制单人',   NOW(), NOW());
