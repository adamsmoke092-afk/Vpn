package com.unitytunnel.app.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ServerEndpoint(
    val id: String,
    val name: String,
    val country: String,
    val flagEmoji: String,
    val protocol: String, // "VLESS", "Trojan"
    val transport: String, // "ws", "grpc", "http/2", "xhttp"
    val host: String,
    val port: Int,
    val pingMs: Int
) {
    companion object {
        val DEFAULT_SERVERS = listOf(
            ServerEndpoint(
                id = "sa-joburg-01",
                name = "Johannesburg Premium (MTN Route)",
                country = "South Africa",
                flagEmoji = "🇿🇦",
                protocol = "VLESS",
                transport = "ws",
                host = "za-jbp1.unitytunnel.com",
                port = 443,
                pingMs = 18
            ),
            ServerEndpoint(
                id = "sa-ct-02",
                name = "Cape Town Low-Latency (Vodacom Route)",
                country = "South Africa",
                flagEmoji = "🇿🇦",
                protocol = "VLESS",
                transport = "grpc",
                host = "za-ct1.unitytunnel.com",
                port = 443,
                pingMs = 24
            ),
            ServerEndpoint(
                id = "sa-dbn-03",
                name = "Durban High-Speed (Telkom Route)",
                country = "South Africa",
                flagEmoji = "🇿🇦",
                protocol = "Trojan",
                transport = "xhttp",
                host = "za-db1.unitytunnel.com",
                port = 8443,
                pingMs = 32
            ),
            ServerEndpoint(
                id = "uk-london-04",
                name = "London International Fallback",
                country = "United Kingdom",
                flagEmoji = "🇬🇧",
                protocol = "VLESS",
                transport = "http/2",
                host = "uk-lon1.unitytunnel.com",
                port = 443,
                pingMs = 165
            ),
            ServerEndpoint(
                id = "us-ny-05",
                name = "New York Global Hub",
                country = "United States",
                flagEmoji = "🇺🇸",
                protocol = "Trojan",
                transport = "ws",
                host = "us-ny1.unitytunnel.com",
                port = 443,
                pingMs = 240
            )
        )
    }
}
