package app.revanced.patches.gamehub.misc.analytics

import app.revanced.patcher.patch.bytecodePatch
import app.revanced.util.returnEarly

@Suppress("unused")
val disableAnalyticsPatch = bytecodePatch(
    name = "Disable analytics",
    description = "Disables Umeng analytics initialization.",
) {
    compatibleWith("com.xiaoji.egggame"("5.3.5"))

    execute {
        umengAppFingerprint.method.returnEarly()
    }
}
