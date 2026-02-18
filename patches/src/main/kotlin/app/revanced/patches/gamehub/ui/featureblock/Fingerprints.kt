package app.revanced.patches.gamehub.ui.featureblock

import app.revanced.patcher.fingerprint

internal val launcherHelperFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == "Lcom/xj/landscape/launcher/launcher/LauncherHelper;" &&
                method.name == "c" &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == "Lcom/xj/launch/strategy/api/LauncherConfig;"
    }
}
