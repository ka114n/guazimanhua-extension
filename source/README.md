# 瓜子漫画扩展源码

基于 [keiyoushi/extensions-source](https://github.com/keiyoushi/extensions-source) 裁剪,只保留 `guazimanhua` 一个源。

## 本地构建

前置:JDK 17 + Android SDK(platform `android-36`)。**注意**:构建根目录是 `source/`(它自己是一个 Gradle 项目)。

设置 `source/local.properties`:

```
sdk.dir=C\:\\Android\\Sdk
```

签名:release 包由仓库内 `signingkey.jks` 签名(密码 `guazi123`,alias `guazi`)。本地/CI 中签名用 `jarsigner` 手动执行(见 `.github/workflows/build.yml`);推公开仓库前请换成自己的 keystore,并把密码放进 GitHub Secrets(`KEY_STORE_PASSWORD` / `KEY_PASSWORD` / `ALIAS`,未配置时默认用 `guazi123` / `guazi`)。

构建:

```
Windows:  source\gradlew.bat :src:zh:guazimanhua:assembleRelease
Linux/CI: cd source && ./gradlew --no-daemon :src:zh:guazimanhua:assembleRelease
```

产物:

```
source/src/zh/guazimanhua/build/outputs/apk/release/tachiyomi-zh.guazimanhua-v<版本>.apk
```

该模块同时会根据 `build.gradle.kts` 生成 `build/keiyoushi-source-info.json`(记录 packageName / versionCode / versionName / source id),供生成索引时读取。

## 重新生成仓库索引

```
python tools/build_index.py \
  --source-info source/src/zh/guazimanhua/build/keiyoushi-source-info.json \
  --apk apk/tachiyomi-zh.guazimanhua-v<版本>.apk
```

依赖:python 3 + `protobuf` + `grpcio-tools`(后两者只需在 proto 变更时用于重新生成 `tools/index_pb2.py`)。

## 改动源逻辑后

改完推送即可,CI 会自动出包更新索引。若只想本地验证:

```
source\gradlew.bat :src:zh:guazimanhua:testDebugUnitTest
```