# 瓜子漫画 Mihon 扩展仓库

自建 Mihon / Tachiyomi 扩展源，提供「瓜子漫画」(guazimanhua) 漫画源。

## 目录结构

- `index.min.json` / `index.pb` — 仓库索引，Mihon 用来发现并安装扩展
- `index-store.json` — 新版 NetworkExtensionStore 格式（`repo.json` 与 `index_v2` 指向它）
- `repo.json` — 仓库元信息 + 签名指纹，Mihon 校验并自动更新扩展（在线）
- `apk/` — 构建并签名好的扩展 APK（签名密钥见 `source/signingkey.jks`，密码 `guazi123`，alias `guazi`）
- `source/` — 基于 [keiyoushi/extensions-source](https://github.com/keiyoushi/extensions-source) 的 Gradle 源码，含 `guazimanhua` 源
- `index.proto` — 索引 protobuf 定义
- `tools/` — 生成 `index.*` 的脚本与元数据

## 在 Mihon 中添加扩展源

Mihon → 设置 → 扩展 → 扩展仓库 → 添加 URL

```
https://raw.githubusercontent.com/ka114n/guazimanhua-extension/main/index.min.json
```

然后在「浏览 → 扩展」中刷新仓库列表，安装「瓜子漫画」。

## 发布一个新扩展版本

1. 修改 `source/src/zh/guazimanhua/build.gradle.kts` 中的 `versionCode` / `versionName`
2. 推送到 GitHub，CI 会自动构建、签名并更新 `index.*` 与 `apk/` 回 `main`
3. 也可以本地构建手动发布（见 `source/README.md`）

## CI 自动构建

`.github/workflows/build.yml`：push 到 `main` 且改动 `source/**` 时自动触发：gradle 构建 → `apksigner` 签名 → 用 `keiyoushi-source-info.json` 生成 `index.*` 与 `apk/` → 提交回仓库。

签名凭据默认为 `guazi123` / alias `guazi`（仓库自带 keystore）；如需更换，可在 GitHub Secrets 中设置 `KEY_STORE_PASSWORD`、`KEY_PASSWORD`、`ALIAS`，之后删除仓库里的 keystore 即可。

## 本地构建前提

JDK 17 + Android SDK（platform 36）、Gradle 与 Kotlin 由 wrapper 自动下载。构建时签名需环境变量 `KEY_STORE_PASSWORD` / `KEY_PASSWORD` / `ALIAS`（缺省回退 `guazi123` / `guazi`）。