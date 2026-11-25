package com.eastcompany.eastsub.resourcePackFlow.config

enum class ResourcePackMode(
    val configValue: String
) {
    FORCE("force"),
    MESSAGE("message"),
    OFF("off");

    companion object {
        fun fromConfig(value: String): ResourcePackMode {
            return entries.firstOrNull {
                it.configValue.equals(
                    value,
                    ignoreCase = true
                )
            } ?: MESSAGE
        }
    }
}