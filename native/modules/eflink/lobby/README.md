# EF Link 联机账号

`server.py` 是 `game.venshell.top:47617` 上的大厅。应用底部的「用户」页负责注册和登录。客户端只提交用户名、密码、邮箱和验证码，不自己判断邮箱。服务端核对 QQ 邮箱和中国大陆公网 IP，验证码必须在申请时的同一地址上填写。

用户名 2–16 位，忽略大小写后不能重复。密码至少 8 位，用每用户独立的盐做 PBKDF2-HMAC-SHA256（60 万次），数据库里不保存原文。登录失败一律回答「账号或密码不正确」。会话 30 天。

房间号由大厅分配为 6 位数字。创建房间时密码可选，大厅只记录有没有密码，不接收密码本身。有密码的房间，加入时必须带上密码；密码留在房主的游戏连接里。

| 限制 | 值 |
| --- | --- |
| 同一 IP 发信 | 60 秒一次，写在数据库里 |
| 同一邮箱 | 15 分钟最多 3 次 |
| 全站 | 每天最多 100 封 |
| 验证码 | 10 分钟，最多 5 次 |
| 同一 IP 验证 | 10 分钟最多 20 次 |

地址库是 `cn-mainland.txt`，来自 APNIC 分配给 CN 的范围，不含港澳台。发信账号由服务器的 `SMTP_HOST`、`SMTP_PORT`、`SMTP_USER`、`SMTP_PASS`、`SMTP_FROM` 提供，不要求发信地址本身是 QQ 邮箱。未配置 SMTP 时不能注册。

账号注册、登录和 30 天会话令牌只走 TLS。证书的主机名必须是客户端填写的大厅地址。游戏转发在验明身份之后另开一条连接，只携带一次性票据。

```sh
export EFLINK_AUTH_DB=/var/lib/eflink/accounts.sqlite
export EFLINK_TLS_CERT=/etc/eflink/lobby.crt
export EFLINK_TLS_KEY=/etc/eflink/lobby.key
python3 server.py --bind 0.0.0.0 --port 47617 --auth-db "$EFLINK_AUTH_DB" \
    --tls-cert "$EFLINK_TLS_CERT" --tls-key "$EFLINK_TLS_KEY"
```

账号库用 SQLite 备份接口每 6 小时做一次快照，做完整性检查后以 `0600` 保存，保留 28 份。不要在服务运行时直接复制数据库文件。

```sh
sudo cp deploy/eflink-account-backup.service deploy/eflink-account-backup.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now eflink-account-backup.timer
```

恢复时先停大厅，用快照替换 `accounts.sqlite`，删掉旁边的 `-wal` 和 `-shm`，再启动。
