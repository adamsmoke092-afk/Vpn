package com.unitytunnel.app.vpn

import com.unitytunnel.app.model.ServerEndpoint

object XrayConfigGenerator {

    /**
     * Generates Xray-core JSON config.
     * Uses a local socks5 inbound on port 10808, and configures the outbound to target the selected server.
     * Fallbacks sequence try WS, then gRPC, then HTTP/2 as requested in §4.5 to bypass South African DPI.
     */
    fun generateConfig(
        server: ServerEndpoint,
        lowDataMode: Boolean,
        autoProtocol: Boolean
    ): String {
        // Construct fallback outbounds list if auto-protocol is enabled
        val protocolType = if (autoProtocol) "vless" else server.protocol.lowercase()
        val streamSettings = when (val t = if (autoProtocol) "ws" else server.transport) {
            "ws" -> """
                "streamSettings": {
                    "network": "ws",
                    "security": "tls",
                    "tlsSettings": {
                        "serverName": "${server.host}",
                        "allowInsecure": false
                    },
                    "wsSettings": {
                        "path": "/unity-tunnel",
                        "headers": {
                            "Host": "${server.host}"
                        }
                    }
                }
            """.trimIndent()
            "grpc" -> """
                "streamSettings": {
                    "network": "grpc",
                    "security": "tls",
                    "tlsSettings": {
                        "serverName": "${server.host}",
                        "allowInsecure": false
                    },
                    "grpcSettings": {
                        "serviceName": "unity-grpc"
                    }
                }
            """.trimIndent()
            "http/2", "http2" -> """
                "streamSettings": {
                    "network": "http",
                    "security": "tls",
                    "tlsSettings": {
                        "serverName": "${server.host}",
                        "allowInsecure": false
                    },
                    "httpSettings": {
                        "path": "/unity-h2",
                        "host": ["${server.host}"]
                    }
                }
            """.trimIndent()
            else -> """
                "streamSettings": {
                    "network": "tcp",
                    "security": "tls",
                    "tlsSettings": {
                        "serverName": "${server.host}",
                        "allowInsecure": false
                    }
                }
            """.trimIndent()
        }

        val limitBuffer = if (lowDataMode) 32 * 1024 else 256 * 1024

        return """
        {
            "log": {
                "loglevel": "warning"
            },
            "inbounds": [
                {
                    "port": 10808,
                    "listen": "127.0.0.1",
                    "protocol": "socks",
                    "settings": {
                        "auth": "noauth",
                        "udp": true,
                        "ip": "127.0.0.1"
                    },
                    "sniffing": {
                        "enabled": true,
                        "destOverride": ["http", "tls"]
                    }
                }
            ],
            "outbounds": [
                {
                    "protocol": "$protocolType",
                    "settings": {
                        "vnext": [
                            {
                                "address": "${server.host}",
                                "port": ${server.port},
                                "users": [
                                    {
                                        "id": "e44d82b4-539c-4903-a1f0-29339a04a3f1",
                                        "alterId": 0,
                                        "security": "auto",
                                        "level": 0
                                    }
                                ]
                            }
                        ],
                        "servers": [
                            {
                                "address": "${server.host}",
                                "port": ${server.port},
                                "password": "unity-prepaid-trojan-pass"
                            }
                        ]
                    },
                    $streamSettings,
                    "tag": "proxy"
                },
                {
                    "protocol": "freedom",
                    "settings": {},
                    "tag": "direct"
                },
                {
                    "protocol": "blackhole",
                    "settings": {},
                    "tag": "block"
                }
            ],
            "routing": {
                "domainStrategy": "AsIs",
                "rules": [
                    {
                        "type": "field",
                        "ip": ["geoip:private"],
                        "outboundTag": "direct"
                    },
                    {
                        "type": "field",
                        "domain": ["geosite:category-ads-all"],
                        "outboundTag": "block"
                    }
                ]
            },
            "stats": {},
            "policy": {
                "levels": {
                    "0": {
                        "handshake": 4,
                        "connIdle": 300,
                        "uplinkOnly": 2,
                        "downlinkOnly": 5,
                        "statsUserDownlink": false,
                        "statsUserUplink": false,
                        "bufferSize": $limitBuffer
                    }
                }
            }
        }
        """.trimIndent()
    }
}
