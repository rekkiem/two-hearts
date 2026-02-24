package com.twohearts.services

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.config.*
import jakarta.mail.*
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import java.util.*

private val logger = KotlinLogging.logger {}

class MailService(config: ApplicationConfig) {

    private val host     = config.property("mail.host").getString()
    private val port     = config.property("mail.port").getString().toInt()
    private val from     = config.property("mail.from").getString()
    private val useTls   = try { config.property("mail.tls").getString().toBoolean() } catch (_: Exception) { false }
    private val baseUrl  = config.property("app.baseUrl").getString()

    private val session: Session by lazy {
        val props = Properties().apply {
            put("mail.smtp.host", host)
            put("mail.smtp.port", port.toString())
            put("mail.smtp.auth", "false")
            if (useTls) {
                put("mail.smtp.starttls.enable", "true")
            }
        }
        Session.getInstance(props)
    }

    fun sendMagicLink(email: String, token: String) {
        val verifyUrl = "$baseUrl/api/v1/auth/verify-web?token=$token"
        val deepLink  = "twohearts://auth?token=$token"

        val html = """
            <!DOCTYPE html>
            <html>
            <body style="font-family:sans-serif;max-width:480px;margin:auto;padding:32px">
              <h2 style="color:#E85D75">TwoHearts 💕</h2>
              <p>Tap the button below to sign in. This link expires in <strong>15 minutes</strong>.</p>
              <a href="$verifyUrl"
                 style="display:inline-block;background:#E85D75;color:white;padding:14px 28px;
                        border-radius:8px;text-decoration:none;font-weight:bold;margin:16px 0">
                Sign In to TwoHearts
              </a>
              <p style="font-size:12px;color:#888">
                Or copy this token into the app:<br>
                <code style="background:#f5f5f5;padding:4px 8px;border-radius:4px">$token</code>
              </p>
              <p style="font-size:11px;color:#aaa">
                If you didn't request this, you can safely ignore this email.
              </p>
            </body>
            </html>
        """.trimIndent()

        try {
            val msg = MimeMessage(session).apply {
                setFrom(InternetAddress(from))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(email))
                subject = "Your TwoHearts sign-in link"
                setContent(html, "text/html; charset=utf-8")
                sentDate = Date()
            }
            Transport.send(msg)
            logger.info { "Magic link email sent to $email" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to send magic link email to $email" }
            // Don't throw — always return success to prevent enumeration
        }
    }
}
