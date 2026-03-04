package com.twohearts.services

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.config.*
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import java.util.*

private val logger = KotlinLogging.logger {}

class MailService(config: ApplicationConfig) {

    private val host    = config.property("mail.host").getString()
    private val port    = config.property("mail.port").getString().toInt()
    private val from    = config.property("mail.from").getString()
    private val useTls  = runCatching { config.property("mail.tls").getString().toBoolean() }.getOrDefault(false)
    private val baseUrl = config.property("app.baseUrl").getString()

    private val session: Session by lazy {
        val props = Properties().apply {
            put("mail.smtp.host", host)
            put("mail.smtp.port", port.toString())
            put("mail.smtp.auth", "false")
            if (useTls) put("mail.smtp.starttls.enable", "true")
        }
        Session.getInstance(props)
    }

    fun sendMagicLink(email: String, token: String) {
        val verifyUrl = "$baseUrl/api/v1/auth/verify-web?token=$token"

        val html = """
            <!DOCTYPE html><html><body style="font-family:sans-serif;max-width:480px;margin:auto;padding:32px">
              <h2 style="color:#E85D75">TwoHearts 💕</h2>
              <p>Click below to sign in. This link expires in <strong>15 minutes</strong>.</p>
              <a href="$verifyUrl"
                 style="display:inline-block;background:#E85D75;color:white;padding:14px 28px;
                        border-radius:8px;text-decoration:none;font-weight:bold;margin:16px 0">
                Sign In to TwoHearts
              </a>
              <p style="font-size:12px;color:#888">
                Or paste this token in the app:<br>
                <code style="background:#f5f5f5;padding:4px 8px;border-radius:4px">$token</code>
              </p>
            </body></html>
        """.trimIndent()

        try {
            // FIX: sin apply{} — llamadas directas sobre msg evitan ambigüedad de overloads en Kotlin
            val msg = MimeMessage(session)
            msg.setFrom(InternetAddress(from))
            msg.addRecipient(Message.RecipientType.TO, InternetAddress(email))
            msg.subject = "Your TwoHearts sign-in link"
            msg.setContent(html, "text/html; charset=utf-8")
            msg.sentDate = Date()
            Transport.send(msg)
            logger.info { "Magic link sent to $email" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to send email to $email" }
            // No relanzar — siempre retornar éxito (previene enumeración de emails)
        }
    }
}
