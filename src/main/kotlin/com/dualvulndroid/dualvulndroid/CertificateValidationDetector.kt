package com.dualvulndroid.dualvulndroid

class CertificateValidationDetector {

    fun detect(code: String): Boolean {

        return code.contains("HostnameVerifier") &&
                code.contains("true")

    }
}