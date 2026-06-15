package com.dualvulndroid.dualvulndroid

import com.intellij.AbstractBundle
import org.jetbrains.annotations.PropertyKey

object MyMessageBundle :
    AbstractBundle("messages.MyMessageBundle") {

    @JvmStatic
    fun message(
        @PropertyKey(resourceBundle = "messages.MyMessageBundle")
        key: String,
        vararg params: Any
    ): String {
        return getMessage(key, *params)
    }
}