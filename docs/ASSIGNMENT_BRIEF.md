# Academic Project Brief: Flight Reservation System

## 1. Project Overview & Scope
The objective of this project is to design, implement, and test a robust, multi-channel Flight Reservation System that demonstrates enterprise software development principles, database integrity, and modern user experience standards.

## 2. Core Functional Requirements
- **Authentication & Authorization**: Role-based access control (Admin, Agent, Customer) with secure password hashing (BCrypt) and session management.
- **Flight Discovery & Search**: Query scheduled flights by departure city, arrival city, and date across major domestic corridors.
- **Interactive Seat Selection**: Visual 2D cabin map representing aircraft fuselage layouts with First Class and Economy classes.
- **Concurrency & Availability**: Optimistic locking preventing double-booking race conditions during seat selection.
- **Transactional Booking & Payment**: Atomic ACID transactions recording booking confirmation, passenger details, seat assignments, and issuing a unique 6-character PNR reference.
- **Boarding Pass & Confirmation**: Instant e-ticket voucher generation and downloadable PDF boarding pass.
- **Administrative Intelligence**: Real-time KPI reporting, revenue metrics, seat-load factor calculations, and daily booking volume trends.

## 3. Architecture & Standards
- Multi-tier MVC / DAO architecture with clean separation of concerns.
- Shared business logic and database access layer (`core`) utilized by both desktop (`desktop`) and web (`web`) clients.
- Automated unit and integration testing validating concurrency holds, transactional booking, and user journeys.
