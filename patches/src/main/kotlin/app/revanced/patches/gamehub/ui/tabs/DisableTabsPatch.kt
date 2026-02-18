package app.revanced.patches.gamehub.ui.tabs

import app.revanced.patcher.extensions.InstructionExtensions.removeInstruction
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

@Suppress("unused")
val disableTabsPatch = bytecodePatch(
    name = "Disable Explore and Platform tabs",
    description = "Hides the Explore and Platform tabs from the main launcher.",
) {
    compatibleWith("com.xiaoji.egggame"("5.3.5"))

    execute {
        initViewFingerprint.method.apply {
            val exploreNewInstanceIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.NEW_INSTANCE &&
                        (this as? ReferenceInstruction)?.reference?.let {
                            it is TypeReference && it.type == "Lcom/xj/landscape/launcher/ui/main/w;"
                        } == true
            }
            val exploreAddIndex = indexOfFirstInstructionOrThrow(exploreNewInstanceIndex) {
                opcode == Opcode.INVOKE_INTERFACE &&
                        (this as? ReferenceInstruction)?.reference?.let {
                            it is MethodReference && it.name == "add"
                        } == true
            }

            val platformNewInstanceIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.NEW_INSTANCE &&
                        (this as? ReferenceInstruction)?.reference?.let {
                            it is TypeReference && it.type == "Lcom/xj/landscape/launcher/ui/main/x;"
                        } == true
            }
            val platformAddIndex = indexOfFirstInstructionOrThrow(platformNewInstanceIndex) {
                opcode == Opcode.INVOKE_INTERFACE &&
                        (this as? ReferenceInstruction)?.reference?.let {
                            it is MethodReference && it.name == "add"
                        } == true
            }

            // Remove in reverse order to preserve indices
            removeInstruction(platformAddIndex)
            removeInstruction(exploreAddIndex)
        }
    }
}
