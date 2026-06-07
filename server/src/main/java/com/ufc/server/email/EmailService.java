// package com.ufc.server.email;
// import org.springframework.beans.factory.annotation.Value;
// import org.springframework.mail.SimpleMailMessage;
// import org.springframework.mail.javamail.JavaMailSender;
// import org.springframework.stereotype.Service;
// @Service
// public class EmailService {
//     @Value("${spring.mail.username}")
//     private String fromEmail;
//     private final JavaMailSender mailSender;
//     public EmailService(JavaMailSender mailSender) {
//         this.mailSender = mailSender;
//     }
//     public void sendEmail(String to, String subject, String body) {
//         SimpleMailMessage message = new SimpleMailMessage();
//         message.setFrom(fromEmail);
//         message.setTo(to);
//         message.setSubject(subject);
//         message.setText(body);
//         mailSender.send(message);
//     }
// }
