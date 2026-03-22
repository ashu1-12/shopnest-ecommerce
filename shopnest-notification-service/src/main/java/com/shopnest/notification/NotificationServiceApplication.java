package com.shopnest.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Notification Service — all outbound communication.
 *
 * Purely event-driven: listens to Kafka, never called directly.
 * No REST endpoints exposed (except actuator/health).
 *
 * Kafka topics consumed:
 *   user.registered       → Welcome email with account details
 *   payment.success       → Order confirmation email + SMS
 *   payment.failed        → Retry payment email
 *   order.confirmed       → Order confirmed email
 *   order.shipped         → Shipped email with AWB tracking link
 *   order.out_for_delivery → Out for delivery SMS push
 *   order.delivered       → Delivery confirmation + review request email
 *   payment.refund.*      → Refund status email
 *
 * @EnableAsync activates @Async annotation.
 * Email sending is done on a separate thread pool so Kafka consumer
 * thread is never blocked waiting for SMTP response.
 * If email fails, the Kafka offset is still committed (email is best-effort).
 * For critical notifications, use retry queues.
 */
@SpringBootApplication
@EnableEurekaClient
@EnableJpaAuditing
@EnableKafka
@EnableAsync   // Email/SMS sent on async thread pool — doesn't block Kafka consumer
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
