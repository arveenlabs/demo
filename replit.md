# LMS Student API — Spring Boot + MongoDB

A full LMS (Learning Management System) backend replicating the FastAPI/Python original, built with Spring Boot 4.1 and Spring Data MongoDB.

## Stack
- **Language:** Java 21
- **Framework:** Spring Boot 4.1 (Spring Web MVC + Spring Data MongoDB + Spring Security)
- **Auth:** JWT (HS256) via jjwt 0.12.x
- **Build tool:** Maven (`mvn spring-boot:run`)

## Running on Replit
The "Start application" workflow runs:
```
cd demo && JAVA_HOME=... mvn spring-boot:run
```
The app listens on **port 5000**.

## Configuration (Replit Secrets / Env Vars)

| Secret / Env Var | Purpose | Default |
|---|---|---|
| `SPRING_DATA_MONGODB_URI` | MongoDB connection string | `mongodb://localhost:27017/lms_app` |
| `JWT_SECRET` | HS256 signing key (≥32 chars) | `dev-secret-change-me-32chars-min-12345` |
| `RAZORPAY_KEY_ID` | Razorpay key ID | `rzp_test_placeholder` |
| `RAZORPAY_KEY_SECRET` | Razorpay key secret | `placeholder_secret` |
| `RAZORPAY_WEBHOOK_SECRET` | Razorpay webhook secret | `placeholder_webhook_secret` |
| `EMERGENT_PUSH_KEY` | Emergent push API key | `placeholder` |

Set `SPRING_DATA_MONGODB_URI` in the Secrets panel to connect a real MongoDB instance.

## API Endpoints (all match the Python original)

### Auth — `/api/auth/**`
- `POST /signup` · `POST /login` · `POST /refresh`
- `POST /forgot-password` · `POST /reset-password`
- `POST /logout` · `GET /me` · `PATCH /me`

### Courses — `/api/**`
- `GET /categories` · `GET /courses` · `GET /courses/recommended` · `GET /courses/trending`
- `GET /courses/{id}` · `POST /courses/{id}/enroll` · `GET /my/courses`

### Wishlist — `GET/POST/DELETE /api/wishlist/**`
### Lectures & Progress — `GET /api/lectures/{id}` · `POST /api/progress`
### Quizzes — `GET /api/courses/{id}/quizzes` · `GET /api/quizzes/{id}` · `POST /api/quizzes/submit` · `GET /api/my/quiz-results`
### Notifications — `GET /api/notifications` · `POST /api/notifications/{id}/read`
### Dashboard — `GET /api/dashboard`
### Payments — `GET /api/payments/config` · `POST /api/payments/create-order` · `POST /api/payments/verify` · `GET /api/payments/orders`
### Live Classes — `GET/GET/{id}/POST /{id}/attend /api/live-classes/**`
### Downloads — `GET/POST/DELETE /api/downloads/**`
### Admin — `GET/POST/PUT/DELETE /api/admin/**` (requires admin role)

## Seed Data
On startup the app seeds:
- 6 categories, 6 courses with 5 lectures each, 2 quizzes, 3 notifications, 3 live classes
- Admin account: `admin@lumina.com` / `Admin1234`

## User preferences
<!-- Add any remembered preferences here -->
