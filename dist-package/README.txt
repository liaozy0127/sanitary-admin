卫浴管理系统 - 使用说明
========================

前提条件：
  安装 Docker Desktop for Windows
  下载：https://www.docker.com/products/docker-desktop/

启动系统：
  1. 确保 Docker Desktop 已启动（系统托盘有鲸鱼图标）
  2. 双击 start.bat
  3. 等待约30秒后自动打开浏览器

访问地址：http://localhost
账号密码：admin / admin123

停止系统：
  双击 stop.bat

注意：
  - 端口占用：80（前端）、8080（后端）、3307（数据库）、6379（缓存）
  - 数据保存在 Docker volume 中，重启不丢失
  - 首次启动需要加载镜像文件，约需1分钟
