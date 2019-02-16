package none.nintha.vtraceapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.FileSystemResource
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import java.io.File

@Service
class MailService {
    private val logger: Logger = LoggerFactory.getLogger("MailService")
    @Autowired
    lateinit var mailSender: JavaMailSender
    @Value("@{spring.mail.username}")
    lateinit var from: String

    fun sendAttachmentsMail(target: String, text: String, subject: String = "Vtracer Message", attachments: Map<String, String> = mapOf()) {
        try {
            val mimeMessage = mailSender.createMimeMessage()

            val helper = MimeMessageHelper(mimeMessage, true)
            helper.setFrom(from)
            helper.setTo(target)
            helper.setSubject(subject)
            helper.setText(text)

            attachments.forEach { name, filePath -> helper.addAttachment(name, FileSystemResource(File(filePath))) }

            mailSender.send(mimeMessage)
        } catch (e: Exception) {
            logger.error("Failed to send mail, target=$target, text=$text, subject=$subject, attachments=$attachments", e)
        }
    }
}