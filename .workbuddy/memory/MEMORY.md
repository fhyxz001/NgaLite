# 项目长期记忆 · NgaLite

## 发布流程（用户约定）
当用户说「提交并推送」「推送并发布」或类似指令时，按以下固定流程执行：

1. **版本号 +1**：`app/build.gradle.kts` 的 `defaultConfig`
   - `versionCode` 自增 1（当前 61，即 60 → 61）
   - `versionName` 末位自增 1（如 `2.00` → `2.01`；`2.09` → `2.10`）
2. **提交**：`git add` 改动文件 + `git commit -m "发布vX.XX"`（严格使用 `发布v` + 新版本号，例：`发布v2.01`），不加多余前缀与 emoji。
3. **推送**：`git push origin main`；失败时如实报告错误原因，并给出本机手动推送命令。

注意：
- 版本号只改 `app/build.gradle.kts`，仓库内无其他版本号引用点（README/strings 均无）。
- 推送失败常见原因：本机代理 `127.0.0.1:61803` 返回 `CONNECT tunnel failed, response 502`，或直连 github.com:443 超时。
- `.workbuddy/preview_zebra.png` 等临时预览图不入库。

## 代码约定
- 导出/分享统一走 `data/ExportManager.kt` + `assets/article_export_template.html`；图片、HTML、分享链接共用同一模板。
- 分享图片：顶部 `body.image-export` 留白 48px、评论限 `IMAGE_MAX_COMMENTS = 10`、回复含头像与点赞、回复列表斑马纹 `--stripe: #F3F6F9`。
