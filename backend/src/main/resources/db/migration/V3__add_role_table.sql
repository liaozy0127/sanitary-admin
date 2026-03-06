CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_name VARCHAR(50) NOT NULL COMMENT '角色名称',
    role_code VARCHAR(50) NOT NULL COMMENT '角色编码',
    description VARCHAR(200) COMMENT '描述',
    status TINYINT DEFAULT 1 COMMENT '状态:1正常,0停用',
    create_time DATETIME,
    update_time DATETIME,
    deleted TINYINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

INSERT INTO sys_role(role_name,role_code,status) VALUES('超级管理员','ADMIN',1),('普通用户','USER',1);