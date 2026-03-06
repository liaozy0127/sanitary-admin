CREATE TABLE IF NOT EXISTS sys_menu (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    menu_name VARCHAR(50) NOT NULL,
    menu_path VARCHAR(200),
    menu_icon VARCHAR(100),
    parent_id BIGINT DEFAULT 0,
    sort INT DEFAULT 0,
    menu_type TINYINT DEFAULT 2,
    status TINYINT DEFAULT 1,
    create_time DATETIME,
    update_time DATETIME,
    deleted TINYINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sys_menu(menu_name,menu_path,parent_id,sort,menu_type,status) VALUES
('系统管理','/system',0,1,1,1),
('用户管理','/system/user',1,1,2,1),
('角色管理','/system/role',1,2,2,1),
('部门管理','/system/dept',1,3,2,1),
('菜单管理','/system/menu',1,4,2,1);
