# Demo — Spring Boot + MongoDB

A Spring Boot 4.1 web application backed by MongoDB.

## Stack
- **Language:** Java 21
- **Framework:** Spring Boot 4.1 (Spring Web MVC + Spring Data MongoDB)
- **Build tool:** Maven (via `demo/mvnw`)

## Running locally on Replit
The workflow command is:
```
cd demo && JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java)))) ./mvnw spring-boot:run
```
The app listens on **port 5000**.

## Configuration
| Secret / Env Var | Purpose | Default |
|---|---|---|
| `SPRING_DATA_MONGODB_URI` | MongoDB connection string | `mongodb://localhost:27017/demo` |

Set `SPRING_DATA_MONGODB_URI` in the Replit Secrets panel before starting the app if you need a real MongoDB database.

## User preferences
<!-- Add any remembered preferences here -->
