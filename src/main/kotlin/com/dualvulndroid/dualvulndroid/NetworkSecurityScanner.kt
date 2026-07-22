package com.dualvulndroid.dualvulndroid

import java.io.File

data class NetworkIssue(
    val file: String,
    val message: String,
    val severity: String
)

object NetworkSecurityScanner {

    fun scan(projectPath: String): List<NetworkIssue> {

        val issues = mutableListOf<NetworkIssue>()

        // AndroidManifest.xml
        val manifest = File(projectPath, "app/src/main/AndroidManifest.xml")

        if (manifest.exists()) {

            val text = manifest.readText()

            if (text.contains("android:usesCleartextTraffic=\"true\"")) {

                issues.add(
                    NetworkIssue(
                        "AndroidManifest.xml",
                        "Cleartext HTTP traffic is enabled.",
                        "HIGH"
                    )
                )
            }

            if (text.contains("android:networkSecurityConfig")) {

                issues.add(
                    NetworkIssue(
                        "AndroidManifest.xml",
                        "Custom Network Security Configuration detected.",
                        "INFO"
                    )
                )
            }
        }

        // network_security_config.xml
        val networkConfig =
            File(projectPath,
                "app/src/main/res/xml/network_security_config.xml")

        if (networkConfig.exists()) {

            val xml = networkConfig.readText()

            if (xml.contains("cleartextTrafficPermitted=\"true\"")) {

                issues.add(
                    NetworkIssue(
                        "network_security_config.xml",
                        "Cleartext traffic permitted.",
                        "HIGH"
                    )
                )
            }

            if (xml.contains("<debug-overrides")) {

                issues.add(
                    NetworkIssue(
                        "network_security_config.xml",
                        "Debug overrides found.",
                        "MEDIUM"
                    )
                )
            }

            if (xml.contains("src=\"user\"")) {

                issues.add(
                    NetworkIssue(
                        "network_security_config.xml",
                        "User certificates are trusted.",
                        "HIGH"
                    )
                )
            }

            if (xml.contains("@raw")) {

                issues.add(
                    NetworkIssue(
                        "network_security_config.xml",
                        "Custom CA certificate detected.",
                        "INFO"
                    )
                )
            }

        }

        return issues
    }

}