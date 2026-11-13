package com.mortgee.al_mobile.data.model

/**
 * Represents supported backend roles in the AL-Core backend system.
 * Supported roles are strictly: borrower, broker, underwriter.
 * Owner and Provider are product terminology with no approved backend mapping.
 */
enum class BackendRole(val value: String) {
    BORROWER("borrower"),
    BROKER("broker"),
    UNDERWRITER("underwriter");

    companion object {
        /**
        * Maps exact backend string values to [BackendRole].
        * Returns null for any unknown value, unsupported role, or null input.
        * Does not default unknown values to BORROWER.
        */
        fun fromValue(value: String?): BackendRole? {
            return entries.find { it.value == value }
        }
    }
}
