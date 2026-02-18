package app.revanced.patches.gamehub.misc.tokenexpiry

import app.revanced.patcher.fingerprint

internal val routerUtilsTokenExpiryFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == "Lcom/xj/landscape/launcher/router/RouterUtils;" && method.name == "n"
    }
}
