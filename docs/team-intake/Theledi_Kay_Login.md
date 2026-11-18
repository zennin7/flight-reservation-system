# Team Intake Specification: Login & Authentication
**Assigned Members:** Theledi & Kay  
**Module Focus:** User Authentication, Role Access & Login UI

---

## 1. Mapping to Shadow Repository Files

| Shadow Component | File Path |
|---|---|
| **FXML View** | `desktop/src/main/resources/com/group2/flightres/fxml/login.fxml` |
| **JavaFX Controller** | `desktop/src/main/java/com/group2/flightres/controller/LoginController.java` |
| **Auth Service** | `core/src/main/java/com/group2/flightres/service/AuthService.java` |
| **User DAO** | `core/src/main/java/com/group2/flightres/dao/impl/UserDaoImpl.java` |
| **Password Utility** | `core/src/main/java/com/group2/flightres/util/PasswordUtil.java` |
| **Web Equivalent** | `web/src/main/resources/templates/login.html` |

---

## 2. What the Teammate Must Provide

1. **Clean FXML (`login.fxml`)**:
   - `usernameField`, `passwordField`, `loginButton`, and `errorLabel`.
   - Free of inline hardcoded color styles (uses `-sv-` CSS tokens).
2. **Controller Wiring (`LoginController.java`)**:
   - Implements `WizardStepController`.
   - Invokes `AuthService.authenticate(username, password)`.
   - Hides error label until a failed login attempt occurs (no mockup placeholder labels).
   - On success, sets `BookingContext.setCurrentUser(user)` and navigates appropriately.
3. **Security Standards**:
   - Salted BCrypt password verification (no plaintext comparisons).
   - PreparedStatement queries only (no SQL injection vulnerabilities).
   - Graceful account deactivation handling (`is_active` check).

---

## 3. Comparison Criteria & Evaluation Rubric

- **Security Verification**: Passwords verified via `jBCrypt`? No passwords logged to console or SLF4J logs?
- **Role Redirection**: Does logging in as an `ADMIN` redirect to the Admin Dashboard and enable the navbar admin pill? Does logging in as `CUSTOMER` return to the booking flow?
- **Visual Presentation**: Does the layout align with the Elevate spec (split-screen layout with dark photographic left hero and clean right card)?
- **Theme Support**: Does the form adapt smoothly between Light Mode and Dark Mode?

---

## 4. Shadow Baseline (What We Keep If Incoming Is Weaker)

- If incoming code contains direct SQL in the controller, we keep the shadow's **`AuthService`** delegation.
- If incoming UI has broken alignment or hardcoded colors, we retain the shadow's **split-pane Elevate styling** (`login.fxml` and `login.html`).
- If error messages reveal stack traces or database errors, we keep the shadow's sanitized **`AuthenticationException`** alert flow.
