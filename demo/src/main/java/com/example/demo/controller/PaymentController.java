package com.example.demo.controller;

import com.example.demo.dto.CreateOrderRequest;
import com.example.demo.dto.VerifyPaymentRequest;
import com.example.demo.model.Enrollment;
import com.example.demo.model.Order;
import com.example.demo.repository.CourseRepository;
import com.example.demo.repository.EnrollmentRepository;
import com.example.demo.repository.OrderRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret}")
    private String razorpayKeySecret;

    private final CourseRepository courseRepo;
    private final EnrollmentRepository enrollRepo;
    private final OrderRepository orderRepo;
    private final MongoTemplate mongoTemplate;

    public PaymentController(CourseRepository courseRepo, EnrollmentRepository enrollRepo,
                             OrderRepository orderRepo, MongoTemplate mongoTemplate) {
        this.courseRepo = courseRepo;
        this.enrollRepo = enrollRepo;
        this.orderRepo = orderRepo;
        this.mongoTemplate = mongoTemplate;
    }

    private boolean isPlaceholder() {
        return razorpayKeyId.startsWith("rzp_test_placeholder");
    }

    @GetMapping("/config")
    public Map<String, String> paymentConfig() {
        return Map.of("key_id", razorpayKeyId, "currency", "INR");
    }

    @PostMapping("/create-order")
    public Map<String, Object> createOrder(@Valid @RequestBody CreateOrderRequest body, Authentication auth) {
        String userId = auth.getName();
        var course = courseRepo.findById(body.getCourseId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
        if (enrollRepo.findByUserIdAndCourseId(userId, body.getCourseId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Already enrolled");
        }
        double price = course.getDiscountPrice() != null ? course.getDiscountPrice() : course.getPrice();
        if (price <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course is free — use enroll endpoint");
        }
        long amountPaise = Math.round(price * 100);
        String receipt = ("lum-" + body.getCourseId().substring(0, Math.min(12, body.getCourseId().length()))
                + "-" + Instant.now().getEpochSecond());
        if (receipt.length() > 40) receipt = receipt.substring(0, 40);

        // Demo mode when keys are placeholders
        String orderId = "order_demo_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        boolean demo = isPlaceholder();

        Order order = new Order();
        order.setId(orderId);
        order.setUserId(userId);
        order.setCourseId(body.getCourseId());
        order.setAmount(amountPaise);
        order.setCurrency("INR");
        order.setReceipt(receipt);
        order.setStatus("created");
        order.setCreatedAt(Instant.now().toString());
        order.setDemo(demo);
        orderRepo.save(order);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("order_id", orderId);
        result.put("amount", amountPaise);
        result.put("currency", "INR");
        result.put("receipt", receipt);
        result.put("key_id", razorpayKeyId);
        result.put("course_title", course.getTitle());
        result.put("demo", demo);
        return result;
    }

    @PostMapping("/verify")
    public Map<String, Object> verifyPayment(@Valid @RequestBody VerifyPaymentRequest body, Authentication auth) {
        String userId = auth.getName();
        Order order = orderRepo.findByIdAndUserId(body.getRazorpayOrderId(), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        boolean signatureOk;
        if (order.isDemo()) {
            signatureOk = "demo_signature".equals(body.getRazorpaySignature());
        } else {
            signatureOk = verifyRazorpaySignature(
                    body.getRazorpayOrderId(), body.getRazorpayPaymentId(), body.getRazorpaySignature());
        }

        if (!signatureOk) {
            order.setStatus("failed");
            orderRepo.save(order);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Signature verification failed");
        }

        order.setStatus("paid");
        order.setPaymentId(body.getRazorpayPaymentId());
        order.setPaidAt(Instant.now().toString());
        orderRepo.save(order);

        if (enrollRepo.findByUserIdAndCourseId(userId, body.getCourseId()).isEmpty()) {
            Enrollment e = new Enrollment();
            e.setId(UUID.randomUUID().toString());
            e.setUserId(userId);
            e.setCourseId(body.getCourseId());
            e.setEnrolledAt(Instant.now().toString());
            e.setPaid(true);
            e.setOrderId(body.getRazorpayOrderId());
            e.setAmount(order.getAmount());
            enrollRepo.save(e);
            courseRepo.findById(body.getCourseId()).ifPresent(c -> {
                c.setStudents(c.getStudents() + 1);
                courseRepo.save(c);
            });
        }

        return Map.of("message", "Payment verified & course unlocked", "course_id", body.getCourseId());
    }

    @GetMapping("/orders")
    public List<Map<String, Object>> myOrders(Authentication auth) {
        List<Order> orders = orderRepo.findByUserId(auth.getName(),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Order o : orders) {
            Map<String, Object> m = orderToMap(o);
            courseRepo.findById(o.getCourseId()).ifPresent(c -> {
                Map<String, Object> cs = new LinkedHashMap<>();
                cs.put("id", c.getId());
                cs.put("title", c.getTitle());
                cs.put("thumbnail", c.getThumbnail());
                m.put("course", cs);
            });
            result.add(m);
        }
        return result;
    }

    private boolean verifyRazorpaySignature(String orderId, String paymentId, String signature) {
        try {
            String msg = orderId + "|" + paymentId;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(razorpayKeySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(msg.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString().equals(signature);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            return false;
        }
    }

    static Map<String, Object> orderToMap(Order o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId());
        m.put("user_id", o.getUserId());
        m.put("course_id", o.getCourseId());
        m.put("amount", o.getAmount());
        m.put("currency", o.getCurrency());
        m.put("receipt", o.getReceipt());
        m.put("status", o.getStatus());
        m.put("created_at", o.getCreatedAt());
        m.put("payment_id", o.getPaymentId());
        m.put("paid_at", o.getPaidAt());
        m.put("demo", o.isDemo());
        return m;
    }
}
