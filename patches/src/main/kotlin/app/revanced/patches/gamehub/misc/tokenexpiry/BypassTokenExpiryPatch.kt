package app.revanced.patches.gamehub.misc.tokenexpiry

import app.revanced.patcher.patch.bytecodePatch
import app.revanced.util.returnEarly

@Suppress("unused")
val bypassTokenExpiryPatch = bytecodePatch(
    name = "Bypass token expiry",
    description = "Removes the token expiry redirect that forces re-login.",
) {
    compatibleWith("com.xiaoji.egggame"("5.3.5"))

    execute {
        routerUtilsTokenExpiryFingerprint.method.returnEarly()
    }
}
