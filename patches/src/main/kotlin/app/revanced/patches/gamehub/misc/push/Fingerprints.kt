package app.revanced.patches.gamehub.misc.push

import app.revanced.patcher.fingerprint

internal val pushAppFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == "Lcom/xj/push/PushApp;" && method.name == "b"
    }
}
