package com.mortgee.al_mobile.data.repository

import com.mortgee.al_mobile.data.model.LoanField
import com.squareup.moshi.Moshi

private const val ROOT_DEPOSIT_ERROR_TYPE = "value_error"
private const val ROOT_DEPOSIT_ERROR_MSG = "Value error, Deposit must be less than the property asking price"
private const val NO_BROKERS_DETAIL = "No brokers are currently available"

private val LOAN_FIELD_BY_PATH: Map<String, LoanField> = LoanField.entries.associateBy { it.path }

internal fun classifyLoanRejection(code: Int, body: String?, moshi: Moshi): LoanSubmitResult = when (code) {
    401 -> LoanSubmitResult.SignInRequired
    403 -> LoanSubmitResult.Forbidden
    422 -> decodeLoanValidation(moshi, body)
    503 -> if (isNoBrokersAvailable(moshi, body)) {
        LoanSubmitResult.NoBroker
    } else {
        LoanSubmitResult.Unconfirmed
    }
    else -> LoanSubmitResult.Unconfirmed
}

private fun isNoBrokersAvailable(moshi: Moshi, body: String?): Boolean {
    if (body.isNullOrBlank()) return false
    val root = try {
        moshi.adapter(Any::class.java).fromJson(body)
    } catch (_: Exception) {
        return false
    }
    val rootMap = root as? Map<*, *> ?: return false
    val detail = rootMap["detail"] as? String ?: return false
    return detail == NO_BROKERS_DETAIL
}

internal fun decodeLoanValidation(moshi: Moshi, body: String?): LoanSubmitResult.Invalid {
    if (body.isNullOrBlank()) {
        return LoanSubmitResult.Invalid(fields = emptySet(), other = true)
    }

    val root = try {
        moshi.adapter(Any::class.java).fromJson(body)
    } catch (_: Exception) {
        return LoanSubmitResult.Invalid(fields = emptySet(), other = true)
    }

    val rootMap = root as? Map<*, *>
        ?: return LoanSubmitResult.Invalid(fields = emptySet(), other = true)

    val detailList = rootMap["detail"] as? List<*>
        ?: return LoanSubmitResult.Invalid(fields = emptySet(), other = true)

    if (detailList.isEmpty()) {
        return LoanSubmitResult.Invalid(fields = emptySet(), other = true)
    }

    val fields = linkedSetOf<LoanField>()
    var other = false

    for (entry in detailList) {
        val entryMap = entry as? Map<*, *>
        if (entryMap == null) {
            other = true
            continue
        }

        val loc = entryMap["loc"] as? List<*>
        if (loc == null) {
            other = true
            continue
        }

        val matchedField: LoanField? = when (loc.size) {
            2 -> {
                val scope = loc[0] as? String
                val topKey = loc[1] as? String
                if (scope == "body" && topKey != null && !topKey.contains('.')) {
                    LOAN_FIELD_BY_PATH[topKey]
                } else {
                    null
                }
            }
            3 -> {
                val scope = loc[0] as? String
                val parent = loc[1] as? String
                val leaf = loc[2] as? String
                if (scope == "body" && parent == "property" && leaf != null) {
                    LOAN_FIELD_BY_PATH["property.$leaf"]
                } else {
                    null
                }
            }
            1 -> {
                val scope = loc[0] as? String
                val type = entryMap["type"] as? String
                val msg = entryMap["msg"] as? String
                if (scope == "body" && type == ROOT_DEPOSIT_ERROR_TYPE && msg == ROOT_DEPOSIT_ERROR_MSG) {
                    LoanField.DEPOSIT
                } else {
                    null
                }
            }
            else -> null
        }

        if (matchedField != null) {
            fields.add(matchedField)
        } else {
            other = true
        }
    }

    return LoanSubmitResult.Invalid(fields = fields, other = other)
}
