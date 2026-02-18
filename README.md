# 📔 Journal App - Spring Boot REST API

A full-featured RESTful API built with Spring Boot for managing personal journal entries with user authentication, authorization, and MongoDB integration.

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.18-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-8-orange.svg)](https://www.oracle.com/java/)
[![MongoDB](https://img.shields.io/badge/MongoDB-Atlas-green.svg)](https://www.mongodb.com/)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

## 🚀 Features

- **User Management** - User registration, authentication, and profile management
- **Journal Entries** - Create, read, update, and delete personal journal entries
- **Secure Authentication** - JWT-based authentication and authorization
- **Role-Based Access Control** - Admin and User roles with different permissions
- **MongoDB Integration** - Cloud-based MongoDB Atlas for data persistence
- **RESTful API Design** - Well-structured endpoints following REST principles
- **Input Validation** - Server-side validation for data integrity
- **Exception Handling** - Global exception handling with meaningful error messages
- **API Documentation** - Comprehensive API documentation for easy integration

## 🛠️ Technologies Used

### Backend
- **Spring Boot 2.7.18** - Application framework
- **Spring Data MongoDB** - MongoDB integration
- **Spring Security** - Authentication and authorization
- **JWT (JSON Web Tokens)** - Secure token-based authentication
- **Hibernate Validator** - Input validation
- **Lombok** - Reducing boilerplate code
- **Maven** - Dependency management and build tool

### Database
- **MongoDB Atlas** - Cloud-hosted NoSQL database

### Tools
- **Postman** - API testing
- **IntelliJ IDEA** - IDE
- **Git** - Version control

## 📋 Prerequisites

- Java 8 or higher
- Maven 3.6+
- MongoDB Atlas account (or local MongoDB instance)
- Postman (for API testing)

## ⚙️ Installation & Setup

### 1. Clone the repository
```bash
git clone https://github.com/yourusername/journal-app.git
cd journal-app
```

### 2. Configure MongoDB
Update `src/main/resources/application.properties`:
```properties
spring.data.mongodb.uri=mongodb+srv://your_username:your_password@cluster0.xxxxx.mongodb.net/journaldb?retryWrites=true&w=majority
spring.data.mongodb.auto-index-creation=true
server.port=8080
```

### 3. Build the project
```bash
mvn clean install
```

### 4. Run the application
```bash
mvn spring-boot:run
```

The application will start on `http://localhost:8080`

## 🔌 API Endpoints

### Authentication
| Method | Endpoint | Description | Access |
|--------|----------|-------------|--------|
| POST | `/user` | Register new user | Public |
| POST | `/auth/login` | User login | Public |

### User Management
| Method | Endpoint | Description | Access |
|--------|----------|-------------|--------|
| GET | `/user` | Get all users | Admin |
| PUT | `/user/{username}` | Update user | Authenticated |
| DELETE | `/user/{username}` | Delete user | Admin |

### Journal Entries
| Method | Endpoint | Description | Access |
|--------|----------|-------------|--------|
| GET | `/journalapp/{username}` | Get all entries for user | Owner/Admin |
| POST | `/journalapp/{username}` | Create new entry | Owner |
| GET | `/journalapp/id/{id}` | Get entry by ID | Owner/Admin |
| PUT | `/journalapp/id/{username}/{id}` | Update entry | Owner |
| DELETE | `/journalapp/id/{username}/{id}` | Delete entry | Owner |

## 📝 Sample API Requests

### Register a new user
```bash
POST http://localhost:8080/user
Content-Type: application/json

{
  "userName": "john_doe",
  "password": "securePassword123"
}
```

### Create a journal entry
```bash
POST http://localhost:8080/journalapp/john_doe
Content-Type: application/json
Authorization: Bearer <your_jwt_token>

{
  "title": "My First Entry",
  "content": "Today was a productive day learning Spring Boot!"
}
```

### Get all entries for a user
```bash
GET http://localhost:8080/journalapp/john_doe
Authorization: Bearer <your_jwt_token>
```

## 🏗️ Project Structure

```
journal-app/
├── src/
│   ├── main/
│   │   ├── java/com/devtaha/journalapp/
│   │   │   ├── controller/          # REST Controllers
│   │   │   ├── entity/              # MongoDB Entity Models
│   │   │   ├── repository/          # Data Access Layer
│   │   │   ├── service/             # Business Logic Layer
│   │   │   ├── config/              # Security & App Configuration
│   │   │   ├── exception/           # Custom Exceptions
│   │   │   └── dto/                 # Data Transfer Objects
│   │   └── resources/
│   │       └── application.properties
│   └── test/                        # Unit & Integration Tests
├── pom.xml
└── README.md
```

## 🔐 Security

- Passwords are encrypted using BCrypt
- JWT tokens expire after 24 hours
- Role-based authorization (USER, ADMIN)
- Protected endpoints require valid JWT token
- CORS configured for secure cross-origin requests

## 🧪 Testing

Run tests using:
```bash
mvn test
```

Use Postman collection (included in `/postman` directory) for manual API testing.

## 📚 Key Concepts Implemented

- **Dependency Injection** - Using @Autowired for loose coupling
- **Layered Architecture** - Controller → Service → Repository pattern
- **DTO Pattern** - Data Transfer Objects for API responses
- **Exception Handling** - @ControllerAdvice for global exception handling
- **Validation** - @Valid annotation with Hibernate Validator
- **Logging** - SLF4J for application logging
- **Pagination & Sorting** - For efficient data retrieval
- **Transactional Management** - @Transactional for data consistency

## 🐛 Troubleshooting

### MongoDB Connection Issues
- Verify MongoDB Atlas connection string
- Check network access settings (IP whitelist)
- Ensure database user has proper permissions

### Authentication Errors
- Verify JWT token is included in Authorization header
- Check token expiration
- Ensure user has appropriate role for the endpoint

## 📈 Future Enhancements

- [ ] Email verification for new users
- [ ] Password reset functionality
- [ ] Rich text editor support for journal entries
- [ ] Tags and categories for entries
- [ ] Search functionality
- [ ] File attachments (images, documents)
- [ ] Export journal as PDF
- [ ] Mobile app integration

## 👨‍💻 Author

**Taha Farooqi**
- LinkedIn: https://www.linkedin.com/in/taha-farooqi-b84119244/

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments
- Engineering Digest, Vipul tyagi
- Spring Boot Documentation
- MongoDB Atlas Documentation
- Baeldung Spring Security Tutorials
- Stack Overflow Community

---

⭐ If you found this project helpful, please give it a star!
