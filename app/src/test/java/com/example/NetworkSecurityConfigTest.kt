package com.example

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.XmlResourceParser
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class NetworkSecurityConfigTest {

    @Test
    fun `bundled sectigo root r46 certificate is valid root ca matching official sha256 fingerprint`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val cert = context.resources.openRawResource(R.raw.sectigo_root_r46).use { input ->
            CertificateFactory.getInstance("X.509").generateCertificate(input) as X509Certificate
        }

        // Must be a self-signed Root CA (basicConstraints >= 0), never a leaf server cert
        assertTrue("Expected CA basicConstraints >= 0", cert.basicConstraints >= 0)
        assertEquals(cert.subjectX500Principal, cert.issuerX500Principal)
        assertTrue(
            "Expected Sectigo Public Server Authentication Root R46 subject, was ${cert.subjectX500Principal.name}",
            cert.subjectX500Principal.name.contains("Sectigo Public Server Authentication Root R46")
        )

        val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        val fingerprint = digest.joinToString(":") { "%02X".format(it) }
        assertEquals(
            "7B:B6:47:A6:2A:EE:AC:88:BF:25:7A:A5:22:D0:1F:FE:A3:95:E0:AB:45:C7:3F:93:F6:56:54:EC:38:F2:5A:06",
            fingerprint
        )
    }

    @Test
    fun `network security config preserves system trust globally and scopes root r46 strictly to vu edu pk`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val appInfo: ApplicationInfo = context.applicationInfo
        assertTrue(
            "Cleartext traffic must remain disabled in manifest",
            (appInfo.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC) == 0
        )

        val parser: XmlResourceParser = context.resources.getXml(R.xml.network_security_config)
        var inBaseConfig = false
        var inDomainConfig = false
        var sawBaseConfig = false
        var baseCleartextAllowed = true
        val baseCertSources = mutableListOf<String>()
        val domainCleartextValues = mutableListOf<String>()
        val configuredDomains = mutableListOf<Pair<String, Boolean>>()
        val domainCertSources = mutableListOf<String>()
        var sawPinSet = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "base-config" -> {
                            inBaseConfig = true
                            sawBaseConfig = true
                            baseCleartextAllowed =
                                parser.getAttributeBooleanValue(null, "cleartextTrafficPermitted", true)
                        }
                        "domain-config" -> {
                            inDomainConfig = true
                            val cleartext = parser.getAttributeValue(null, "cleartextTrafficPermitted") ?: ""
                            domainCleartextValues += cleartext
                        }
                        "domain" -> {
                            val includeSub =
                                parser.getAttributeBooleanValue(null, "includeSubdomains", false)
                            val domainName = parser.nextText().trim()
                            configuredDomains += domainName to includeSub
                        }
                        "certificates" -> {
                            val resId = parser.getAttributeResourceValue(null, "src", 0)
                            val rawSrc = if (resId == R.raw.sectigo_root_r46) {
                                "@raw/sectigo_root_r46"
                            } else {
                                parser.getAttributeValue(null, "src").orEmpty()
                            }
                            if (inBaseConfig) baseCertSources += rawSrc
                            if (inDomainConfig) domainCertSources += rawSrc
                        }
                        "pin-set", "pin" -> {
                            sawPinSet = true
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "base-config" -> inBaseConfig = false
                        "domain-config" -> inDomainConfig = false
                    }
                }
            }
            event = parser.next()
        }
        parser.close()

        assertTrue("Expected <base-config> in network_security_config.xml", sawBaseConfig)
        assertFalse("Global base-config must disable cleartext traffic", baseCleartextAllowed)
        assertEquals("Global base-config must trust system CAs only", listOf("system"), baseCertSources)

        assertEquals(listOf("vu.edu.pk" to true), configuredDomains)
        assertEquals(listOf("false"), domainCleartextValues)
        assertTrue("vu.edu.pk domain-config must preserve system CAs", domainCertSources.contains("system"))
        assertTrue(
            "vu.edu.pk domain-config must include @raw/sectigo_root_r46",
            domainCertSources.contains("@raw/sectigo_root_r46")
        )
        assertFalse("Do not pin leaf or intermediate certificates", sawPinSet)
    }
}
