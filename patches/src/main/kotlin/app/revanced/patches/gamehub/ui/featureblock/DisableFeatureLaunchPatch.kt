package app.revanced.patches.gamehub.ui.featureblock

import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.util.smali.ExternalLabel
import app.revanced.patches.gamehub.misc.extension.sharedGamehubExtensionPatch

private const val EXTENSION = "Lapp/revanced/extension/gamehub/steam/SteamStoragePreference;"

@Suppress("unused")
val disableFeatureLaunchPatch = bytecodePatch(
    name = "Disable feature launch",
    description = "Blocks launching of Discover and Free games features.",
) {
    compatibleWith("com.xiaoji.egggame"("5.3.5"))

    dependsOn(sharedGamehubExtensionPatch)

    execute {
        launcherHelperFingerprint.method.apply {
            // Capture the first original instruction before we inject, so ExternalLabel can point to it.
            // If shouldBlockFeature() returns false, the if-eqz jumps here (skipping the block).
            val firstOriginalInstruction = instructions[0]

            addInstructionsWithLabels(
                0,
                """
                    invoke-virtual {p1}, Lcom/xj/launch/strategy/api/LauncherConfig;->h()I
                    move-result v0
                    invoke-static {v0}, $EXTENSION->shouldBlockFeature(I)Z
                    move-result v0
                    if-eqz v0, :skip_block
                    new-instance v0, Lcom/xj/launch/strategy/api/LaunchResult${'$'}Failure;
                    new-instance v1, Ljava/lang/Exception;
                    const-string v2, "Feature disabled by ReVanced"
                    invoke-direct {v1, v2}, Ljava/lang/Exception;-><init>(Ljava/lang/String;)V
                    invoke-direct {v0, v1}, Lcom/xj/launch/strategy/api/LaunchResult${'$'}Failure;-><init>(Ljava/lang/Exception;)V
                    return-object v0
                """,
                ExternalLabel("skip_block", firstOriginalInstruction),
            )
        }
    }
}
