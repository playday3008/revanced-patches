package app.revanced.patches.gamehub.steam.api

import app.revanced.patcher.fingerprint

internal val eggGameHttpConfigFingerprint = fingerprint {
    strings("https://landscape-api.vgabc.com/")
    custom { _, classDef ->
        classDef.type == "Lcom/xj/common/http/EggGameHttpConfig;"
    }
}

internal val wifiuiHttpConfigFingerprint = fingerprint {
    strings("https://landscape-api.vgabc.com/")
    custom { method, classDef ->
        classDef.type == "Lcom/xj/adb/wifiui/http/HttpConfig;" && method.name == "b"
    }
}
