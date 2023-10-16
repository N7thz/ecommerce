package br.com.serratec.ecommerce.service;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import br.com.serratec.ecommerce.model.email.Email;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender javaMailSender;

    public void enviar(Email email) throws MessagingException { // checked em um checked

        MimeMessage mimeMessage = javaMailSender.createMimeMessage();

        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "utf-8");

        helper.setFrom(email.getRemetente());
        helper.setSubject(email.getAssunto());
        helper.setText(email.getMensagem(), true); // true para html
        helper.setTo(email.getDestinatarios()
                .toArray(new String[email.getDestinatarios().size()]));

        javaMailSender.send(mimeMessage);
    }
}
