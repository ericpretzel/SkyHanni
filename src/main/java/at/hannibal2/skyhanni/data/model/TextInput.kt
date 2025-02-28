package at.hannibal2.skyhanni.data.model

import at.hannibal2.skyhanni.utils.KeyboardManager
import at.hannibal2.skyhanni.utils.KeyboardManager.isKeyClicked
import at.hannibal2.skyhanni.utils.KeyboardManager.isKeyHeld
import at.hannibal2.skyhanni.utils.LorenzColor
import at.hannibal2.skyhanni.utils.OSUtils
import at.hannibal2.skyhanni.utils.StringUtils.insert
import kotlinx.coroutines.runBlocking
import net.minecraft.client.settings.KeyBinding
import org.apache.commons.lang3.SystemUtils
import org.lwjgl.input.Keyboard
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

open class TextInput {

    var textBox: String = ""
    private var carriage: Int? = null

    fun editText(textColor: LorenzColor = LorenzColor.WHITE, carriageColor: LorenzColor = LorenzColor.GREEN) = textBox.let {
        with(carriage) {
            if (this == null) it
            else it.insert(this, "${carriageColor.getChatColor()}|${textColor.getChatColor()}")
        }
    }.replace("(?<!§.\\|)§(?!.\\|§.)".toRegex(), "&&")

    fun editTextWithAlwaysCarriage() = textBox.let {
        with(carriage) {
            if (this == null) it.plus('|')
            else it.insert(this, '|')
        }
    }.replace("§", "&&")

    fun finalText() = textBox.replace("&&", "§")

    fun makeActive() = if (!isActive) Companion.activate(this) else Unit
    fun disable() = if (isActive) Companion.disable() else Unit
    fun handle() = Companion.handleTextInput()
    fun clear() {
        textBox = ""
        carriage = null
    }

    val isActive get() = Companion.activeInstance == this

    private val updateEvents = mutableMapOf<Int, (TextInput) -> Unit>()

    protected fun update() {
        updateEvents.forEach { (_, it) -> it(this) }
    }

    fun registerToEvent(key: Int, event: (TextInput) -> Unit) {
        updateEvents[key] = event
    }

    fun removeFromEvent(key: Int) {
        updateEvents.remove(key)
    }

    companion object {
        private var activeInstance: TextInput? = null

        fun isActive() = activeInstance != null

        fun activate(instance: TextInput) {
            activeInstance = instance
            timeSinceKeyEvent = Keyboard.getEventNanoseconds()
        }

        fun disable() {
            activeInstance = null
        }

        @Suppress("UnusedParameter")
        fun onMinecraftInput(keyBinding: KeyBinding, cir: CallbackInfoReturnable<Boolean>) {
            if (activeInstance != null) {
                cir.returnValue = false
                return
            }
        }

        fun onGuiInput(ci: CallbackInfo) {
            if (activeInstance != null) {
                if (Keyboard.KEY_ESCAPE.isKeyHeld()) {
                    disable()
                } else {
                    ci.cancel()
                }
                return
            }
        }

        private var timeSinceKeyEvent = 0L

        private var carriage
            get() = activeInstance?.carriage
            set(value) {
                activeInstance?.carriage = value
            }

        private var textBox
            get() = activeInstance?.textBox.orEmpty()
            set(value) {
                activeInstance?.textBox = value
            }

        private fun updated() {
            with(activeInstance) {
                if (this == null) return
                update()
            }
        }

        private fun handleTextInput() {
            if (KeyboardManager.isCopyingKeysDown()) {
                OSUtils.copyToClipboard(textBox)
                return
            }
            if (KeyboardManager.isPastingKeysDown()) {
                runBlocking {
                    textBox = OSUtils.readFromClipboard()?.take(2024) ?: return@runBlocking
                    updated()
                }
                return
            }
            val carriage = carriage

            if (Keyboard.KEY_LEFT.isKeyClicked()) {
                this.carriage = carriage?.moveCarriageLeft() ?: (textBox.length - 1)
                return
            }
            if (Keyboard.KEY_RIGHT.isKeyClicked()) {
                this.carriage = when {
                    carriage == null -> null
                    (carriage >= textBox.length - 1) -> null
                    else -> moveCarriageRight(carriage)
                }
                return
            }
            if (Keyboard.KEY_BACK.isKeyClicked()) {
                if (carriage != null) {
                    textBox.removeRange(carriage, carriage + 1)
                } else {
                    textBox.dropLast(1)
                }
                updated()
                return
            }

            if (timeSinceKeyEvent == Keyboard.getEventNanoseconds()) return
            timeSinceKeyEvent = Keyboard.getEventNanoseconds()
            val char = Keyboard.getEventCharacter()
            textBox = when (char) {
                Char(0) -> return
                '\b' -> onRemove()
                Char(127) -> if (SystemUtils.IS_OS_MAC) {
                    onRemove()
                } else {
                    textBox
                }

                else -> if (carriage != null) {
                    this.carriage = carriage + 1
                    textBox.insert(carriage, char)
                } else {
                    textBox + char
                }
            }
            updated()
        }

        private fun onRemove(): String = carriage?.let {
            if (it == 0) {
                textBox.substring(1)
            } else {
                this.carriage = it.minus(1)
                textBox.removeRange(it - 1, it)
            }
        } ?: textBox.dropLast(1)

        private fun moveCarriageRight(carriage: Int) = carriage + 1

        private fun Int.moveCarriageLeft(): Int = when {
            this > 0 -> this - 1
            else -> 0
        }
    }
}
