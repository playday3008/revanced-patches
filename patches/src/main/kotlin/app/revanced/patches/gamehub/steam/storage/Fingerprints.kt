package app.revanced.patches.gamehub.steam.storage

import app.revanced.patcher.fingerprint
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal val steamDownloadInfoHelperFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == "Lcom/xj/standalone/steam/core/SteamDownloadInfoHelper;" &&
            method.name == "a" &&
            method.implementation?.instructions?.any { instruction ->
                (instruction as? ReferenceInstruction)?.reference?.let {
                    it is MethodReference && it.name == "setInstallPath"
                } == true
            } == true
    }
}
