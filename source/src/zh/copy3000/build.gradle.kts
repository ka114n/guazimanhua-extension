import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Copy3000"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        name = "拷贝漫画"
        lang = "zh"
        baseUrl = "https://www.copy3000.com"
    }
}
