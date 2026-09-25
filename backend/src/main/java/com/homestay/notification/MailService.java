package com.homestay.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.homestay.config.AppProperties;

/** Gửi email trực tiếp (dùng cho email bảo mật như mật khẩu tạm, liên kết đặt lại mật khẩu). */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender sender;
    private final AppProperties props;

    public MailService(JavaMailSender sender, AppProperties props) {
        this.sender = sender;
        this.props = props;
    }

    /** Gửi và ném lỗi nếu thất bại — để hàng đợi email biết mà thử lại. */
    public void sendOrThrow(String to, String subject, String body) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(props.mailFrom());
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(body);
        sender.send(msg);
    }

    /** Gửi, lỗi thì chỉ ghi log (không làm hỏng thao tác chính). */
    public boolean send(String to, String subject, String body) {
        try {
            sendOrThrow(to, subject, body);
            return true;
        } catch (RuntimeException e) {
            log.warn("Gửi email tới {} thất bại: {}", to, e.getMessage());
            return false;
        }
    }
}
