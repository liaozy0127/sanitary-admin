CREATE TABLE IF NOT EXISTS sys_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) COMMENT '操作用户',
    operation VARCHAR(200) COMMENT '操作描述',
    method VARCHAR(200) COMMENT '请求方法',
    params TEXT COMMENT '请求参数',
    ip VARCHAR(64) COMMENT 'IP地址',
    time BIGINT COMMENT '执行时长(ms)',
    status TINYINT DEFAULT 1 COMMENT '状态:1成功,0失败',
    error_msg TEXT COMMENT '错误信息',
    create_time DATETIME COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

INSERT INTO sys_log(username,operation,method,ip,time,status,create_time) VALUES
('admin','用户登录','POST /api/auth/login','127.0.0.1',120,1,NOW()),
('admin','查询用户列表','GET /api/users','127.0.0.1',45,1,NOW());
