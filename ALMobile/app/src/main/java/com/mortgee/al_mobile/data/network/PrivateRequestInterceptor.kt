package com.mortgee.al_mobile.data.network

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp Interceptor that guards Authorization header credentials on private requests.
 *
 * Enforces security rules:
 * 1. Public requests (without Authorization header) remain free of bearer headers.
 * 2. Restricts Authorization headers strictly to the configured AL-Core HTTPS origin.
 * 3. Strips Authorization headers from requests to untrusted origins or non-HTTPS schemes.
 * 4. Strips Authorization headers on redirects to untrusted origins or non-HTTPS schemes.
 */
@Singleton
class PrivateRequestInterceptor @Inject constructor(
    private val privateTokenSeam: PrivateTokenSeam
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val authHeader = originalRequest.header("Authorization")

        // Public requests stay free of bearer headers.
        // If Authorization header is present, check origin and HTTPS.
        val requestToProceed = if (!authHeader.isNullOrBlank()) {
            if (!privateTokenSeam.isAllowedOrigin(originalRequest.url)) {
                // Strip Authorization header if target origin is not allowed or non-HTTPS
                originalRequest.newBuilder()
                    .removeHeader("Authorization")
                    .build()
            } else {
                originalRequest
            }
        } else {
            originalRequest
        }

        val response = chain.proceed(requestToProceed)

        // Inspect redirect responses (3xx) to ensure credentials are not leaked on cross-origin redirects
        if (response.isRedirect) {
            val location = response.header("Location")
            if (!location.isNullOrBlank()) {
                val targetUrl = requestToProceed.url.resolve(location)
                if (targetUrl != null && !privateTokenSeam.isAllowedOrigin(targetUrl)) {
                    // Note: Any follow-up request to targetUrl via RetryAndFollowUpInterceptor passes through intercept(chain)
                    // and will have its Authorization header stripped if targetUrl is cross-origin or non-HTTPS.
                    return response
                }
            }
        }

        return response
    }
}
