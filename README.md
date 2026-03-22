# ShopNest — Flipkart-style E-Commerce Platform

## Tech Stack
- **Java 21** + **Spring Boot 3.2** + **Spring Cloud 2023**
- **MySQL 8** · **Redis 7** · **Apache Kafka** · **Elasticsearch 8**
- **Razorpay** (Payments) · **Docker Compose** (local infra)
- **Maven Multi-Module** project structure

---

## Project Structure

```
shopnest/
├── pom.xml                          ← Parent POM (versions + shared deps)
├── docker-compose.yml               ← Start entire platform locally
├── docker/mysql/init.sql            ← Creates all 8 databases
│
├── shopnest-eureka/                 ← Service registry  (port 8761)
├── shopnest-config-server/          ← Centralized config (port 8888)
├── shopnest-gateway/                ← API Gateway        (port 8080)
│
├── shopnest-user-service/           ← Auth + JWT         (port 8081)
├── shopnest-product-service/        ← Catalog + Search   (port 8082)
├── shopnest-order-service/          ← Cart + Orders      (port 8083)
├── shopnest-payment-service/        ← Razorpay + UPI     (port 8084)
├── shopnest-inventory-service/      ← Stock management   (port 8085)
├── shopnest-notification-service/   ← Email/SMS/Push     (port 8086)
├── shopnest-shipping-service/       ← Logistics          (port 8087)
└── shopnest-review-service/         ← Ratings + Reviews  (port 8088)
```

---

## Service Startup Order

```
Zookeeper → Kafka → MySQL → Redis → Elasticsearch
    ↓
Eureka (8761)
    ↓
Config Server (8888)
    ↓
All Microservices (8081–8088) [in parallel]
    ↓
API Gateway (8080)
```

---

## Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| Java | 21 LTS  | https://adoptium.net |
| Maven | 3.9+  | https://maven.apache.org |
| Docker Desktop | Latest | https://docker.com |
| IntelliJ IDEA | Any | Recommended IDE |

---

## Step-by-Step Setup

### Step 1 — Clone and build

```bash
git clone https://github.com/your-org/shopnest.git
cd shopnest

# Build all modules (downloads all dependencies — takes ~3 min first time)
mvn clean install -DskipTests
```

### Step 2 — Set Razorpay credentials

Create a `.env` file in the project root (never commit this!):

```bash
# .env — copy from .env.example
RAZORPAY_KEY_ID=rzp_test_xxxxxxxxxxxx
RAZORPAY_SECRET_KEY=your_secret_key_here
RAZORPAY_WEBHOOK_SECRET=your_webhook_secret

JWT_SECRET=shopnest-super-secret-key-at-least-256-bits-long

SMTP_HOST=smtp.gmail.com
SMTP_USERNAME=your-email@gmail.com
SMTP_PASSWORD=your-app-password
```

**Get Razorpay test keys:**
1. Sign up at https://dashboard.razorpay.com
2. Go to Settings → API Keys
3. Generate test keys (start with `rzp_test_`)

### Step 3 — Start infrastructure with Docker Compose

```bash
# Start all infrastructure + services
docker compose up -d

# Watch logs (optional)
docker compose logs -f

# Check all services are UP
docker compose ps
```

### Step 4 — Verify everything started

| Dashboard | URL | Purpose |
|-----------|-----|---------|
| Eureka | http://localhost:8761 | See all registered services |
| API Gateway | http://localhost:8080 | Entry point |
| Kafka UI | http://localhost:9090 | Browse topics/messages |
| Payment Swagger | http://localhost:8084/swagger-ui.html | Test payment APIs |
| User Swagger | http://localhost:8081/swagger-ui.html | Test auth APIs |

---

## Running Services in IntelliJ (Dev Mode)

For faster development, run services directly in IntelliJ instead of Docker:

1. Start infra only:
   ```bash
   docker compose up -d mysql redis kafka zookeeper elasticsearch
   ```

2. In IntelliJ, open each service's main class and run it:
   - `EurekaServerApplication` → start first
   - `PaymentServiceApplication` → run with env vars:
     ```
     RAZORPAY_KEY_ID=rzp_test_xxx;RAZORPAY_SECRET_KEY=xxx
     ```

3. Use IntelliJ's "Run Configuration" → Environment Variables section

---

## Payment Flow (End-to-End)

```
User clicks "Pay Now"
      │
      ▼
POST /api/v1/payments/initiate
      │  Gateway validates JWT
      │  Payment Service creates Razorpay order
      │  Returns: { razorpayOrderId, razorpayKeyId, amount }
      ▼
Frontend opens Razorpay modal (UPI / Card / Net Banking)
      │
      ▼ (User completes payment)
      │
Razorpay calls your webhook:
POST /api/v1/payments/webhook
      │  Verify X-Razorpay-Signature header
      │  Update payment status → SUCCESS
      │  Publish to Kafka: payment.success
      ▼
Frontend sends verification:
POST /api/v1/payments/verify
      │  { razorpayOrderId, razorpayPaymentId, razorpaySignature }
      │  Server verifies HMAC-SHA256 signature
      │  Returns: { status: SUCCESS }
      ▼
Kafka Consumers react:
  order-service        → status: PAYMENT_SUCCESS → CONFIRMED
  notification-service → sends email + SMS confirmation
  inventory-service    → confirms stock deduction
```

---

## Key API Endpoints (via Gateway — port 8080)

### Auth
```
POST   /api/v1/auth/register         Register new user
POST   /api/v1/auth/login            Login → get JWT
POST   /api/v1/auth/refresh          Refresh JWT
POST   /api/v1/auth/forgot-password  Send OTP
POST   /api/v1/auth/reset-password   Reset with OTP
```

### Products
```
GET    /api/v1/products              List all (paginated)
GET    /api/v1/products/{id}         Get product
GET    /api/v1/products/search?q=    Search (Elasticsearch)
POST   /api/v1/products              Create (admin/seller)
PUT    /api/v1/products/{id}         Update
```

### Cart & Orders
```
POST   /api/v1/cart/items            Add to cart
GET    /api/v1/cart                  View cart
POST   /api/v1/orders                Place order
GET    /api/v1/orders                Order history
GET    /api/v1/orders/{id}           Order details
POST   /api/v1/orders/{id}/cancel    Cancel order
```

### Payments
```
POST   /api/v1/payments/initiate     Start payment (returns Razorpay details)
POST   /api/v1/payments/verify       Confirm payment signature
POST   /api/v1/payments/webhook      Razorpay webhook (no auth)
GET    /api/v1/payments/{id}/status  Payment status
POST   /api/v1/payments/{id}/refund  Initiate refund
```

---

## Testing Payments in Razorpay Test Mode

Use these test card/UPI details on the Razorpay modal:

**Test Card:**
- Number: `4111 1111 1111 1111`
- Expiry: Any future date
- CVV: Any 3 digits

**Test UPI:**
- UPI ID: `success@razorpay` → simulates successful payment
- UPI ID: `failure@razorpay` → simulates failed payment

**Test Net Banking:**
- Select any bank → use test credentials provided by Razorpay

---

## Environment Variables Reference

| Variable | Used By | Description |
|----------|---------|-------------|
| `RAZORPAY_KEY_ID` | payment-service | Public Razorpay key |
| `RAZORPAY_SECRET_KEY` | payment-service | Private Razorpay key |
| `RAZORPAY_WEBHOOK_SECRET` | payment-service | Webhook signing secret |
| `JWT_SECRET` | user-service, gateway | JWT signing key (min 256-bit) |
| `DB_HOST` | all services | MySQL hostname |
| `REDIS_HOST` | all services | Redis hostname |
| `KAFKA_SERVERS` | all services | Kafka bootstrap servers |
| `EUREKA_HOST` | all services | Eureka hostname |

---

## Common Issues & Fixes

**Services not appearing in Eureka dashboard:**
- Wait 30–60 seconds after startup (Eureka has a heartbeat delay)
- Check service logs: `docker compose logs payment-service`

**Payment webhook not received locally:**
- Razorpay webhooks need a public URL (not localhost)
- Use ngrok: `ngrok http 8080`
- Set webhook URL in Razorpay dashboard: `https://xxxx.ngrok.io/api/v1/payments/webhook`

**MySQL connection refused:**
- Wait for MySQL health check to pass: `docker compose ps`
- Check logs: `docker compose logs mysql`

**Kafka consumer lag:**
- Open Kafka UI at http://localhost:9090
- Check consumer groups and lag per partition

---

## Next Steps — React Frontend

Once the backend is complete, the React frontend will:
1. Call `/api/v1/auth/login` → store JWT in memory (not localStorage!)
2. Attach `Authorization: Bearer <token>` to every API request
3. Use Razorpay.js to open payment modal
4. Send verify request after payment completes

> The next module we build together: **User Service** (JWT + OAuth2 Google login)

---

## Module Build Status

| Module | Status | Port |
|--------|--------|------|
| shopnest-eureka | ✅ Complete | 8761 |
| shopnest-config-server | ✅ Complete | 8888 |
| shopnest-gateway | ✅ Complete | 8080 |
| shopnest-payment-service | ✅ Complete | 8084 |
| shopnest-user-service | 🔲 Next | 8081 |
| shopnest-product-service | 🔲 Upcoming | 8082 |
| shopnest-order-service | 🔲 Upcoming | 8083 |
| shopnest-inventory-service | 🔲 Upcoming | 8085 |
| shopnest-notification-service | 🔲 Upcoming | 8086 |
| shopnest-shipping-service | 🔲 Upcoming | 8087 |
| shopnest-review-service | 🔲 Upcoming | 8088 |
