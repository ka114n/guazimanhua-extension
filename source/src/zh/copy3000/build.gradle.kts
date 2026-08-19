import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Copy3000"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        name = "鎷疯礉婕敾"
        lang = "zh"
        baseUrl = "https://www.copy3000.com"
    }
}
