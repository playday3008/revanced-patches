package app.revanced.patches.gamehub.steam.api

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.gamehub.misc.extension.sharedGamehubExtensionPatch
import app.revanced.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val STEAM_EXTENSION = "Lapp/revanced/extension/gamehub/steam/SteamStoragePreference;"

@Suppress("unused")
val apiServerSwitchPatch = bytecodePatch(
    name = "API server switch",
    description = "Allows switching between the official GameHub API and the EmuReady API server.",
) {
    compatibleWith("com.xiaoji.egggame"("5.3.5"))

    dependsOn(sharedGamehubExtensionPatch)

    execute {
        // Patch EggGameHttpConfig.<clinit>
        // The clinit selects a URL based on environment flags and stores it in field "b" via sput-object.
        // We intercept just before the sput-object to optionally replace the URL with the EmuReady URL.
        eggGameHttpConfigFingerprint.method.apply {
            val sputIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.SPUT_OBJECT &&
                    (this as? ReferenceInstruction)?.reference?.let {
                        it is FieldReference &&
                            it.name == "b" &&
                            it.definingClass == "Lcom/xj/common/http/EggGameHttpConfig;"
                    } == true
            }
            val urlRegister = getInstruction<OneRegisterInstruction>(sputIndex).registerA

            addInstructions(
                sputIndex,
                """
                    invoke-static {v$urlRegister}, $STEAM_EXTENSION->getEffectiveApiUrl(Ljava/lang/String;)Ljava/lang/String;
                    move-result-object v$urlRegister
                """,
            )
        }

        // Patch wifiui HttpConfig.b(Context)
        // The method has a hardcoded const-string URL passed directly as the first argument to NetConfig.l().
        // Smali: invoke-virtual {p0, v1, p1, v0}, NetConfig;->l(String;Context;Function1;)V
        // In Instruction35c layout, registerD holds the first method argument (the URL string, v1).
        // We intercept just before the call to optionally replace the URL register.
        wifiuiHttpConfigFingerprint.method.apply {
            val netConfigCallIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.INVOKE_VIRTUAL &&
                    (this as? ReferenceInstruction)?.reference?.let {
                        it is MethodReference && it.name == "l" &&
                            it.definingClass == "Lcom/xj/adb/wifiui/net/NetConfig;"
                    } == true
            }
            // registerD is the first method argument (URL string) in invoke-virtual {instance, v1, ...}
            val urlRegister = getInstruction<Instruction35c>(netConfigCallIndex).registerD

            addInstructions(
                netConfigCallIndex,
                """
                    invoke-static {v$urlRegister}, $STEAM_EXTENSION->getEffectiveApiUrl(Ljava/lang/String;)Ljava/lang/String;
                    move-result-object v$urlRegister
                """,
            )
        }
    }
}
