# 终末地下载站 / Better Endfield Downloads

独立部署的终末地社区下载站，提供中英双语界面，沿用仓库 Web 的 Preact、米白/深色主题、侧栏和黄色强调色。与 Toy 和战斗统计业务独立。

作者可以使用 GitHub 或 QQ 邮箱验证码登录，自助提交外部下载地址。公开浏览不按地区屏蔽；邮箱登录和注册只接受中国大陆网络。普通作者必填名字、类型、链接；开通文件上传的账号可以用本站文件替代下载链接。版本名字、图片、介绍和更新说明均可选，不设置发布审核。

## 作品隐藏

作者可以隐藏和恢复自己的作品；管理员可以隐藏任何作品并填写选填提示原因。管理隐藏优先于作者状态，作者不能解除管理隐藏，修改介绍或新增版本也不会更改隐藏状态。管理员恢复公开时会同时清除管理隐藏和作者隐藏。

隐藏作品不进入公开列表、搜索结果或分类计数，普通访问者打开旧详情链接会看到隐藏提示；详情、评论和本站上传文件接口同时限制访问。作者仍可在「我的发布」中查看和编辑隐藏作品，管理员在「管理作品」中查看所有作品并按状态筛选。作品、评论、点赞和文件记录继续保留；作者或管理员登录后仍可查看。

管理账号通过 `ADMIN_GITHUB_IDS` 配置稳定 GitHub 数字 ID，当前为 Dr-hydra（116339679），与文件上传权限分别配置。未设置该变量的旧部署沿用文件上传账号列表，便于升级。

## 评论、点赞与文件

作品详情提供公开评论区；评论和点赞需要登录。每个账号对同一作品只有一个赞，可以取消。评论最多 2000 字符，每小时 20 条、两次间隔至少 15 秒，作为纯文本展示；评论者可以删除自己的评论，站点管理员可以删除任何评论。

所有登录作者可以上传 JPG、PNG、WebP 展示图，每作品最多 6 张、单张 5 MB。第一张作为封面，发布表单支持预览、移除和调整封面。原有封面链接保留作为未上传展示图时的备用封面。

作品文件上传仅开放给 `FILE_UPLOAD_GITHUB_IDS` 配置的稳定 GitHub 数字 ID，不依据昵称或前端字段判断。当前服务器仅开通 Dr-hydra（116339679）。单文件 200 MB，流式写入磁盘；新版本保留历史文件，下载支持 Range 续传。网站不执行上传文件，文件下载使用 attachment 与 application/octet-stream。

上传目录默认在数据库同级的 `uploads` 中，也可用 `UPLOAD_DIRECTORY` 配置；当前为 `/var/lib/endfield-resource-center/uploads`。单账号图片额度为 100 MB，全部上传文件总额度为 12 GB，同时最多两个上传。同账号未发布上传最多 12 份；超过 24 小时仍未被作品或版本使用的上传由后台清理。自动备份只覆盖数据库；要保留已上传文件，需要另行复制这个目录。

## 感谢名单

感谢名单的维护表在 `supporters.json`，构建脚本会生成只包含排序后姓名的 `src/supporters.generated.ts`；金额、日期和来源不会进入浏览器资源。每条记录可以填写姓名、金额、日期和来源；页面会合并同名记录后按累计金额排序，只显示姓名，不显示金额或内部排序。没有可识别姓名的记录把 `name` 留空，页面会显示为“匿名 / Anonymous”。增量更新时追加记录并更新表格顶部的 `cutoffDate`，然后运行 `npm run mods:build` 即可。

## 本地开发

需要 Node.js 24（后端使用内置 `node:sqlite`）。在 `web` 目录执行：

```powershell
npm ci
npm run mods:build
npm run mods:test
npm run mods:serve
```

访问 `http://127.0.0.1:9017/endfield/`。本地未配置 GitHub 时只开放浏览与表单预览，不开放匿名发布。表单草稿只保存在当前浏览器。

热更新开发可另开终端运行 `npm run mods:dev`，后端的 `PUBLIC_URL` 应设为 `http://127.0.0.1:5174/endfield/`，然后访问该地址。

## 数据与版本

- SQLite 保存用户、会话、作品和发布记录。GitHub 用户数字 ID 绑定站内账号，改昵称不会改变归属。
- 自助发布必填名字、枚举类型和 HTTP(S) 下载链接。纯文本正文保留换行；页面不执行作者 HTML。
- 历史版本按发布顺序排列，版本名字可以留空；填写的版本名字在同一作品内不能重复。
- 更新下载链接或版本名字时新增发布记录；修改作品介绍不会新增记录。外部文件内容由作者托管，站点只记录链接。
- 写入验证登录、作品归属、Origin 和 CSRF；作者写入限制为每小时 60 次。GitHub 授权包含浏览器绑定的单次 state 和 PKCE；授权 token 不存入数据库。

## 当前服务器部署

公开地址：`https://146.235.16.65:8443/endfield/`。

服务：`endfield-resource-center.service`；代码目录：`/opt/endfield-resource-center`；数据库：`/var/lib/endfield-resource-center/catalog.sqlite`；环境配置：`/etc/endfield-resource-center.env`。

服务仅监听 `127.0.0.1:9017`，由现有 HTTPS 8443 入口转发 `/endfield` 和 `/endfield/`。现有 80、443 与其他 HTTPS 业务继续使用原有路由。使用现有 IP 证书和自动续期任务。

## 开通 GitHub 登录

在 GitHub Settings → Developer settings → OAuth Apps → New OAuth App 创建应用：

- Application name：`Endfield Downloads`（可以自行改名）。
- Homepage URL：`https://146.235.16.65:8443/endfield/`。
- Authorization callback URL：`https://146.235.16.65:8443/endfield/auth/github/callback`。
- 使用精确回调匹配，关闭 wildcard matching。不需要仓库访问权限或 Device Flow。

在服务器编辑配置，填入 Client ID 和 Client Secret：

```sh
sudoedit /etc/endfield-resource-center.env
sudo systemctl restart endfield-resource-center
```

不要把 Client Secret 放到前端、Git 或聊天记录。凭据未填时，服务仍可浏览，真实 GitHub 登录和发布保持关闭。

## 登录与注册

发布、评论、点赞和上传需要已注册账号。公开作品可以不登录浏览。账号没有密码。

| 方式 | 行为 |
| --- | --- |
| GitHub | 用稳定的 GitHub 数字 ID 建立或登录账号。授权使用浏览器绑定的一次性 state 和 PKCE，访问令牌不写入数据库。 |
| QQ 邮箱 | 用户提交 `@qq.com` 地址后收到六位验证码。验证通过后，已有账号直接登录，没有账号则注册。 |

已有 GitHub 作者可以在「我的发布」中绑定 QQ 邮箱，之后用邮箱登录仍管理原来的作品。未绑定过的邮箱会建立新账号，不按第三方邮箱自动合并。公开作者资料不展示邮箱。

### QQ 邮箱

- 页面和 `auth/email/request` 都只接受 `@qq.com`。`@vip.qq.com`、看起来像 QQ 域名的其他地址，以及格式不正确的地址会在发信前拒绝。
- 地址会转成小写保存。发信账号由服务端 SMTP 配置提供，不要求发信地址本身是 QQ 邮箱。
- 验证码 10 分钟内有效，只能使用一次，并且绑定申请时的浏览器。

### 网络与次数

- 申请和验证都必须来自中国大陆公网地址。地址库是 APNIC `delegated-apnic-latest` 里分配给 CN 的范围，不含港澳台，文件为 `server/cn-mainland.txt`。
- 只有本机回环上的 HTTPS 代理写入的客户端地址会被采信。调用方自己提供的 `X-Forwarded-For` 无效。
- 同一 IP 60 秒只能发送一次。每个邮箱每 15 分钟最多 3 次，全站每天最多 100 封。这些次数写在数据库里，重启服务不能清掉。
- 验证必须使用申请验证码时的同一 IP。每个验证码最多 5 次尝试；同一 IP 每 10 分钟最多验证 20 次。
- 邮件发送失败也会占用该 IP 的 60 秒，避免反复请求 SMTP。

分配范围变化后，在 `web` 目录运行 `node mod-center/scripts/build-cn-ip.mjs`，再重新部署 `server/cn-mainland.txt`。

## 开通邮箱登录

在服务器填写 `SMTP_HOST`、`SMTP_PORT`、`SMTP_USER`、`SMTP_PASS` 和 `SMTP_FROM` 后重启。未配置 SMTP 时不显示邮箱登录，浏览不受影响。建议使用专用发件地址和应用专用密码，不要把邮箱主密码提交到仓库或聊天中。起步可准备专用 QQ 邮箱并开启 SMTP 授权码，也可以使用 OCI Email Delivery 等按服务商要求配置 SPF/DKIM 的域名邮件服务。

```sh
sudoedit /etc/endfield-resource-center.env
sudo systemctl restart endfield-resource-center
```

```dotenv
SMTP_HOST=smtp.qq.com
SMTP_PORT=465
SMTP_USER=专用发信邮箱@qq.com
SMTP_PASS=QQ邮箱生成的SMTP授权码
SMTP_FROM=专用发信邮箱@qq.com
```

部署包需包含构建后的 `dist`、`server`、`shared`、`deploy`、`README.md` 和 `web/node_modules/nodemailer`（放在部署包的 `node_modules/nodemailer`）。安装脚本会同时复制邮件运行依赖。

## 备份与运维

数据库使用 WAL。不要在服务运行时直接复制 `catalog.sqlite`，那样可能缺页。`deploy/backup-database.mjs` 通过 SQLite 备份接口写出完整快照，执行 `integrity_check` 后再保存，文件权限为 `0600`。

| 项目 | 值 |
| --- | --- |
| 定时器 | `endfield-resource-center-backup.timer`，每 6 小时一次，错过的周期会补跑 |
| 保留 | 28 份，大约 7 天 |
| 目录 | `/var/lib/endfield-resource-center/backups/` |
| 内容 | 账号、会话、作品记录和邮箱。不含 `uploads/` 里的作品文件 |

部署本版后在服务器启用定时器：

```sh
sudo cp deploy/endfield-resource-center-backup.service deploy/endfield-resource-center-backup.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now endfield-resource-center-backup.timer
sudo systemctl start endfield-resource-center-backup.service
sudo systemctl status endfield-resource-center --no-pager
sudo journalctl -u endfield-resource-center-backup.service -n 30 --no-pager
```

恢复数据库时先停止服务，用选中的快照替换 `catalog.sqlite`，删除旁边的 `-wal` 和 `-shm`，再启动。作品文件要另行从 `uploads/` 恢复。升级只替换代码和静态构建，不覆盖数据库、备份和环境配置。

域名迁移后，更新 `PUBLIC_URL`、GitHub 首页/回调 URL 与 TLS 入口，再重启服务。
