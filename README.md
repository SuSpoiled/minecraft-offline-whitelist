# minecraft-offline-whitelist
适用于paper26.2离线服务器的白名单插件
离线服务器UUID没有意义，故只通过ID识别用户（不区分大小写）。请一定安装登录插件（如AuthMe）防止撞id进入。

## 命令（权限节点为whitelist.admin，默认只有op）
/wl add <玩家名>  加入白名单（3-16 位字母/数字/下划线）
/wl remove <玩家名> 移出白名单
/wl list [页码] 分页查看白名单
wl reload 重载配置和白名单
wl toggle 开关白名单（全服广播）
