# 瓜子漫画 Mihon 扩展仓库

自建的 Mihon / Tachiyomi 第三方扩展源,提供「瓜子漫画」(guazimanhua)数据源。

## 目录结构

- `index.min.json` / `index.pb` — 扩源索引,Mihon 从这里发现并下载扩展
- `repo.json` — 仓库元数据 + 签名指纹,Mihon 用它校验并自动信任扩展(必填)
- `apk/` — 编译并签名好的扩展 APK(签名密钥见 `source/signingkey.jks`,密码 `guazi123`,alias `guazi`)
- `source/` — 基于 [keiyoushi/extensions-source](https://github.com/keiyoushi/extensions-source) 的 Gradle 源码,仅保留 `guazimanhua` 这一个源
- `source/index.proto` — 索引的 protobuf 定义
- `tools/` — 生成 `index.*` 的脚本与元数据

## 在 Mihon 中添加扩展源

Mihon → 设置 → 浏览 → 扩展仓库 → 输入 URL(把 `<用户名>` 换成你的 GitHub 用户名):

```
https://raw.githubusercontent.com/<用户名>/guazimanhua-extension/main/index.min.json
```

然后在「浏览 → 扩展」里刷新仓库列表,安装「瓜子漫画」。

## 更新一个扩展版本

1. 修改 `source/src/zh/guazimanhua/build.gradle.kts` 里的 `versionCode` / `versionName`
2. 推送到 GitHub 后,CI 会自动构建、签名、重新生成 `index.*` 与 `apk/` 并推回 `main`
3. 也可以本地构建后手动更新索引(见 `source/README.md`)

## CI 自动构建

`source/.github/workflows/build.yml`:push 到 `main` 且改动 `source/**` 时触发。流程:gradle 出包 → `apksigner` 签名 → 用 `keiyoushi-source-info.json` 重新生成 `index.*` 与 `apk/` → 提交回仓库。

签名凭据默认 `guazi123` / alias `guazi`(仓库内 keystore);如配置 GitHub Secrets(`KEY_STORE_PASSWORD`、`KEY_PASSWORD`、`ALIAS`)则优先使用,便于日后换密钥。

## 本地构建前置

JDK 17 + Android SDK(platform 36)。Gradle 与 Kotlin 由 wrapper 自动下载。