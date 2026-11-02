package com.example.securescan.utils

import android.util.Patterns
import java.net.URL

enum class QrContentType {
    URL, EMAIL, PHONE, CONTACT, WIFI, TEXT
}

enum class SecurityLevel {
    SAFE, SUSPICIOUS, DANGEROUS, CHECKING, UNKNOWN
}

data class SecurityResult(
    val level          : SecurityLevel,
    val reasons        : List<String>  = emptyList(),
    val expandedUrl    : String?       = null,
    val safeBrowsing   : String?       = null
)

object SecurityChecker {

    private val SHORTENER_DOMAINS = setOf(
        "bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly",
        "short.link", "buff.ly", "rb.gy", "cutt.ly", "is.gd",
        "tiny.cc", "lnkd.in", "dlvr.it", "su.pr", "twit.ac",
        "snipurl.com", "bl.ink", "tiny.one", "short.io"
    )

    private val SUSPICIOUS_KEYWORDS = setOf(
        "login", "signin", "sign-in", "verify", "verification",
        "account", "secure", "update", "confirm", "banking",
        "paypal", "apple", "microsoft", "google", "amazon",
        "free", "winner", "prize", "click", "urgent", "alert",
        "suspended", "unusual", "activity", "password", "credential",
        "wallet", "crypto", "bitcoin", "investment", "earn"
    )

    private val SUSPICIOUS_TLDS = setOf(
        ".xyz", ".top", ".club", ".online", ".site",
        ".web", ".info", ".biz", ".pw", ".cc"
    )

    private val TRUSTED_DOMAINS = setOf(
        "google.com", "youtube.com", "github.com", "stackoverflow.com",
        "wikipedia.org", "apple.com", "microsoft.com", "amazon.com",
        "facebook.com", "instagram.com", "twitter.com", "linkedin.com",
        "reddit.com", "medium.com", "npmjs.com", "android.com"
    )

    fun detectContentType(raw: String): QrContentType {
        return when {
            raw.startsWith("http://") || raw.startsWith("https://") ||
                    raw.startsWith("www.")    -> QrContentType.URL
            raw.startsWith("mailto:") || raw.contains("@") &&
                    Patterns.EMAIL_ADDRESS.matcher(raw).matches() -> QrContentType.EMAIL
            raw.startsWith("tel:")   -> QrContentType.PHONE
            raw.startsWith("BEGIN:VCARD") -> QrContentType.CONTACT
            raw.startsWith("WIFI:")  -> QrContentType.WIFI
            else                     -> QrContentType.TEXT
        }
    }

    fun performLocalCheck(url: String): SecurityResult {
        val reasons  = mutableListOf<String>()
        val lowerUrl = url.lowercase()

        try {
            val parsedUrl  = URL(if (url.startsWith("http")) url else "https://$url")
            val host       = parsedUrl.host.lowercase().removePrefix("www.")
            val path       = parsedUrl.path.lowercase()
            val fullLower  = "$host$path"

            // Check shortened link
            if (SHORTENER_DOMAINS.any { host == it || host.endsWith(".$it") }) {
                reasons.add("⚠️ Shortened URL — destination unknown")
            }

            // Check trusted domain first
            val isTrusted = TRUSTED_DOMAINS.any {
                host == it || host.endsWith(".$it")
            }
            if (isTrusted && reasons.isEmpty()) {
                return SecurityResult(SecurityLevel.SAFE)
            }

            // Check suspicious keywords
            val foundKeywords = SUSPICIOUS_KEYWORDS.filter { fullLower.contains(it) }
            if (foundKeywords.isNotEmpty()) {
                reasons.add("⚠️ Suspicious keywords: ${foundKeywords.take(3).joinToString(", ")}")
            }

            // Check suspicious TLD
            if (SUSPICIOUS_TLDS.any { host.endsWith(it) }) {
                reasons.add("⚠️ Unusual domain extension")
            }

            // Check IP address instead of domain
            if (host.matches(Regex("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"))) {
                reasons.add("🚨 IP address used instead of domain name")
            }

            // HTTP (not HTTPS)
            if (url.startsWith("http://")) {
                reasons.add("⚠️ Not using secure HTTPS connection")
            }

            // Excessive subdomains (phishing trick)
            val subdomainCount = host.split(".").size
            if (subdomainCount > 4) {
                reasons.add("⚠️ Excessive subdomains — possible phishing")
            }

            // Homograph/lookalike chars
            if (host.contains("paypa1") || host.contains("go0gle") ||
                host.contains("arnazon") || host.contains("micros0ft")) {
                reasons.add("🚨 Domain looks like a known brand (possible typosquatting)")
            }

        } catch (e: Exception) {
            reasons.add("⚠️ Malformed URL — cannot parse")
        }

        val level = when {
            reasons.any { it.startsWith("🚨") } -> SecurityLevel.DANGEROUS
            reasons.isNotEmpty()                -> SecurityLevel.SUSPICIOUS
            else                                -> SecurityLevel.SAFE
        }

        return SecurityResult(level = level, reasons = reasons)
    }
}