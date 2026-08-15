import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Guazimanhua"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        name = "瓜子漫画"
        lang = "zh"
        baseUrl = "https://www.guazimanhua.com"
    }
}
