package com.eastcompany.eastsub.resourcePackFlow.config

enum class AutoApplyMode(
    val configValue: String
) {
    APPLY("apply"),
    MESSAGE("message"),
    OFF("off");

    companion object {
        fun fromConfig(value: String): AutoApplyMode {
            return entries.firstOrNull {
                it.configValue.equals(
                    value,
                    ignoreCase = true
                )
            } ?: MESSAGE
        }
    }
}