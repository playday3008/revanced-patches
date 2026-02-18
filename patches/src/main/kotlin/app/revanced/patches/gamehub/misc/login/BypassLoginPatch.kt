package app.revanced.patches.gamehub.misc.login

import app.revanced.patcher.patch.bytecodePatch
import app.revanced.util.returnEarly

@Suppress("unused")
val bypassLoginPatch = bytecodePatch(
    name = "Bypass login",
    description = "Bypasses the login requirement by spoofing user credentials.",
) {
    compatibleWith("com.xiaoji.egggame"("5.3.5"))

    execute {
        getAvatarFingerprint.method.returnEarly("🎮")
        getNicknameFingerprint.method.returnEarly("🎮 GameHubLite")
        getTokenFingerprint.method.returnEarly("fake-token")
        getUidFingerprint.method.returnEarly(99999)
        isLoginFingerprint.method.returnEarly(true)
    }
}
