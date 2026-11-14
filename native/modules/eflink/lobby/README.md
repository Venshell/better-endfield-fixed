# EF Link 联机账号

`server.py` 是大厅。默认监听 `0.0.0.0:47617`，客户端没填地址时连 `127.0.0.1:47617`。公开部署时把客户端默认地址改成自己的主机名。应用底部的「我的」页负责注册和登录。客户端只提交用户名、密码、邮箱和验证码，不自己判断邮箱。服务端只检查邮箱格式，验证码必须在申请时的同一地址上填写。

用户名 2–16 位，忽略大小写后不能重复。密码至少 8 位，用每用户独立的盐做 PBKDF2-HMAC-SHA256（60 万次），数据库里不保存原文。登录失败一律回答「账号或密码不正确」。会话 30 天，登录响应里带到期时间。到期后，或服务端拒绝令牌时，用户页回到登录。

启动时如果 `accounts` 表还是只有邮箱的旧结构，进程直接退出，不删表。把 `accounts.sqlite` 连同 `-wal`、`-shm` 移走后再启动。旧记录只有邮箱，要保留的地址请先导出。发信、验证和登录记录保留 1 天，过期会话和验证码会删掉。

房间号由大厅分配为 6 位数字。创建房间时密码可选，大厅只记录有没有密码，不接收密码本身。房间列表上的房主名是登录会话里的注册名。有密码的互联网房间，房主先发一次性随机数，客人用密码作密钥回 HMAC，这个结果不能拿去进下一次连接。局域网仍用协议 1 的证明，以便和 Windows EF Link 1.6.6 进同一房间。

| 限制 | 值 |
| --- | --- |
| 同一 IP 发信 | 60 秒一次，写在数据库里 |
| 同一邮箱 | 15 分钟最多 3 次 |
| 全站 | 每天最多 100 封 |
| 验证码 | 10 分钟，最多 5 次 |
| 同一 IP 验证 | 10 分钟最多 20 次 |
| 未完成的入房 | 每个账号同时 2 个，全站同时 64 个 |
| 同时连接 | 全站 1280 条 TCP，同一 IP 32 条 |

发信账号由服务器的 `SMTP_HOST`、`SMTP_PORT`、`SMTP_USER`、`SMTP_PASS`、`SMTP_FROM` 提供。未配置 SMTP 时不能注册。

账号注册、登录、30 天会话令牌和游戏转发都走 TLS。证书的主机名必须是客户端填写的大厅地址。转发连接上的票据还要带上账号令牌：客人必须是申请加入的那个账号，房主必须是开房的那个账号。明文 `bridge` 会被拒绝。

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
