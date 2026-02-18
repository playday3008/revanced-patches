package app.revanced.patches.gamehub.misc.analytics

import app.revanced.patcher.fingerprint

internal val umengAppFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == "Lcom/xj/umeng/UmengApp;" && method.name == "b"
    }
}
