package com.shopnest.shipping;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Shipping Service — logistics partner integration and shipment tracking.
 *
 * Integrates with Indian logistics APIs:
 *   - Delhivery  (enterprise, best for B2C volume)
 *   - Shiprocket (aggregator, multiple courier options)
 *   - DTDC / Blue Dart (premium delivery)
 *
 * Key responsibilities:
 *   1. Pincode serviceability check before order placement
 *   2. Create shipment AWB (AirWayBill) after order confirmed
 *   3. Webhook from logistics partner → update tracking status
 *   4. Reverse logistics (pickup for returns)
 *   5. Estimated delivery date calculation based on pincode
 *
 * @EnableScheduling powers a polling job that:
 *   Every 30 minutes → fetch tracking updates for IN-TRANSIT shipments
 *   Updates DB → publishes order.tracking.updated Kafka event
 *   Notification service sends push notification to user
 */
@SpringBootApplication
@EnableEurekaClient
@EnableJpaAuditing
@EnableKafka
@EnableScheduling   // Tracking poll job: every 30 min fetch updates from logistics API
public class ShippingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShippingServiceApplication.class, args);
    }
}
