# EF Link 联机账号

`server.py` 是 `game.venshell.top:47617` 上的大厅。玩家要先用 QQ 邮箱注册，拿到会话后才能在这台服务器上列出房间、开房或加入。局域网直连不经过这里。

注册没有密码。客户端提交 `@qq.com`，服务端核对地址和中国大陆公网 IP 后发六位验证码。验证必须来自申请时的同一个 IP。通过后新邮箱会建成账号，已有账号直接登录。会话 30 天，之后每次使用这台服务器仍须来自中国大陆地址。

| 限制 | 值 |
| --- | --- |
| 同一 IP 发信 | 60 秒一次，写在数据库里 |
| 同一邮箱 | 15 分钟最多 3 次 |
| 全站 | 每天最多 100 封 |
| 验证码 | 10 分钟，最多 5 次 |
| 同一 IP 验证 | 10 分钟最多 20 次 |

地址库是 `cn-mainland.txt`，来自 APNIC 分配给 CN 的范围，不含港澳台。发信账号由服务器的 `SMTP_HOST`、`SMTP_PORT`、`SMTP_USER`、`SMTP_PASS`、`SMTP_FROM` 提供，不要求发信地址本身是 QQ 邮箱。未配置 SMTP 时不能注册。

```sh
export EFLINK_AUTH_DB=/var/lib/eflink/accounts.sqlite
python3 server.py --bind 0.0.0.0 --port 47617 --auth-db "$EFLINK_AUTH_DB"
```

账号库用 SQLite 备份接口每 6 小时做一次快照，做完整性检查后以 `0600` 保存，保留 28 份。不要在服务运行时直接复制数据库文件。

```sh
sudo cp deploy/eflink-account-backup.service deploy/eflink-account-backup.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now eflink-account-backup.timer
```

恢复时先停大厅，用快照替换 `accounts.sqlite`，删掉旁边的 `-wal` 和 `-shm`，再启动。
