package com.unitytunnel.app.data.api

import com.unitytunnel.app.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class SignatureInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val requestBody = originalRequest.body
        
        var bodyString = ""
        if (requestBody != null) {
            val buffer = Buffer()
            requestBody.writeTo(buffer)
            bodyString = buffer.readUtf8()
        }

        // Get the API secret from BuildConfig
        val secret = BuildConfig.API_SECRET
        
        // Compute HMAC-SHA256
        val mac = Mac.getInstance("HmacSHA256")
        val secretKeySpec = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(secretKeySpec)
        val hmacBytes = mac.doFinal(bodyString.toByteArray(Charsets.UTF_8))
        
        // Hex encode
        val signature = hmacBytes.joinToString("") { "%02x".format(it) }

        // Attach header
        val newRequest = originalRequest.newBuilder()
            .header("x-signature", signature)
            .build()
            
        return chain.proceed(newRequest)
    }
}
