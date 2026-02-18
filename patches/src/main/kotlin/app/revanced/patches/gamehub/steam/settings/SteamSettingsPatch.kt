package app.revanced.patches.gamehub.steam.settings

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.gamehub.misc.extension.sharedGamehubExtensionPatch
import app.revanced.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.value.ImmutableIntEncodedValue
import app.revanced.patcher.util.proxy.mutableTypes.MutableField.Companion.toMutable

private const val ENTITY_CLASS = "Lcom/xj/landscape/launcher/data/model/entity/SettingItemEntity;"
private const val EXTENSION = "Lapp/revanced/extension/gamehub/steam/SteamStoragePreference;"

@Suppress("unused")
val steamSettingsPatch = bytecodePatch(
    name = "Steam settings menu",
    description = "Adds SD card storage and API server settings to the GameHub settings menu.",
) {
    compatibleWith("com.xiaoji.egggame"("5.3.5"))

    dependsOn(sharedGamehubExtensionPatch)

    execute {
        // 1. Add new static fields to SettingItemEntity so the rest of the app can reference them.
        //    CONTENT_TYPE_SD_CARD_STORAGE = 0x18, CONTENT_TYPE_API = 0x1a.
        val entityMutableClass = proxy(settingItemEntityFingerprint.classDef).mutableClass
        for ((name, value) in listOf(
            "CONTENT_TYPE_SD_CARD_STORAGE" to 0x18,
            "CONTENT_TYPE_API" to 0x1a,
        )) {
            entityMutableClass.staticFields.add(
                ImmutableField(
                    ENTITY_CLASS,
                    name,
                    "I",
                    AccessFlags.PUBLIC.value or AccessFlags.STATIC.value or AccessFlags.FINAL.value,
                    ImmutableIntEncodedValue(value),
                    emptySet(),
                    null,
                ).toMutable(),
            )
        }

        // 2. In SettingItemViewModel.l(), after the Language item is added to the list, inject
        //    two new TYPE_SWITCH (5) items: SD card storage (0x18) and API server (0x1a).
        //
        //    The method uses:
        //      p0 = the ArrayList (list)
        //      v7 = SettingItemEntity.Companion (loaded once at the top, reused throughout)
        //      v0..v6 = entity construction scratch registers
        //
        //    The Language entity uses the same v0..v6 range, so after its add() call those
        //    registers are free to be reused for our injected entities.
        //
        //    Constructor: <init>(IILandroid/util/SparseArray;ZILkotlin/jvm/internal/DefaultConstructorMarker;)V
        //      v0 = this (new-instance)
        //      v1 = type  (TYPE_SWITCH = 5, via Companion.getTYPE_SWITCH())
        //      v2 = contentType (0x18 or 0x1a, via Companion.getCONTENT_TYPE_*())
        //      v3 = sparseArray param (null / 0)
        //      v4 = Z (boolean, default false = 0)
        //      v5 = I (defaultBitMask = 0xc, marks sparseArray+Z as defaulted)
        //      v6 = DefaultConstructorMarker (null / 0)
        settingItemViewModelFingerprint.method.apply {
            // Find the first invoke-interface list.add() call – this is the Language item add.
            val languageAddIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.INVOKE_INTERFACE &&
                    (this as? ReferenceInstruction)?.reference?.let {
                        it is MethodReference &&
                            it.definingClass == "Ljava/util/List;" &&
                            it.name == "add"
                    } == true
            }

            // Inject immediately after the Language item's add() call.
            addInstructions(
                languageAddIndex + 1,
                """
                    new-instance v0, $ENTITY_CLASS
                    const/4 v1, 0x5
                    const/16 v2, 0x18
                    const/4 v3, 0x0
                    const/4 v4, 0x0
                    const/16 v5, 0xc
                    const/4 v6, 0x0
                    invoke-direct/range {v0 .. v6}, $ENTITY_CLASS-><init>(IILandroid/util/SparseArray;ZILkotlin/jvm/internal/DefaultConstructorMarker;)V
                    invoke-interface {p0, v0}, Ljava/util/List;->add(Ljava/lang/Object;)Z
                    new-instance v0, $ENTITY_CLASS
                    const/4 v1, 0x5
                    const/16 v2, 0x1a
                    const/4 v3, 0x0
                    const/4 v4, 0x0
                    const/16 v5, 0xc
                    const/4 v6, 0x0
                    invoke-direct/range {v0 .. v6}, $ENTITY_CLASS-><init>(IILandroid/util/SparseArray;ZILkotlin/jvm/internal/DefaultConstructorMarker;)V
                    invoke-interface {p0, v0}, Ljava/util/List;->add(Ljava/lang/Object;)Z
                """,
            )
        }

        // 3. In SettingSwitchHolder.w(), intercept switch toggles for the two new content types.
        //
        //    Method signature (static):
        //      p0 = LlauncherItemSettingFocusableSwitchBinding (reused after cond_0 as contentType int)
        //      p1 = SettingItemEntity (reused after cond_0 as Companion object)
        //      p2 = View
        //
        //    We inject before the getCONTENT_TYPE_NOTIFICATION_NEWS call, at which point:
        //      p0 = contentType integer (guaranteed: last set by move-result p0 from getContentType())
        //      p1 = SettingItemEntity$Companion (set by sget-object after getContentType())
        //      v1..v4 = scratch registers (method has .locals 5)
        settingSwitchHolderFingerprint.method.apply {
            val newsCheckIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.INVOKE_VIRTUAL &&
                    (this as? ReferenceInstruction)?.reference?.let {
                        it is MethodReference && it.name == "getCONTENT_TYPE_NOTIFICATION_NEWS"
                    } == true
            }

            addInstructions(
                newsCheckIndex,
                "invoke-static {p0}, $EXTENSION->handleSettingToggle(I)V",
            )
        }
    }
}
