package app.revanced.patches.gamehub.misc.push

import app.revanced.patcher.patch.bytecodePatch
import app.revanced.util.returnEarly

@Suppress("unused")
val disablePushPatch = bytecodePatch(
    name = "Disable push notifications",
    description = "Disables JPush notification service initialization.",
) {
    compatibleWith("com.xiaoji.egggame"("5.3.5"))

    execute {
        pushAppFingerprint.method.returnEarly()
    }
}
