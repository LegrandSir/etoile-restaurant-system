# L'Étoile Bistro — Table-Side Self-Ordering & Kitchen Display System (KDS)

An enterprise client-server hospitality platform built with **Java 21**, **JavaFX**, **Spring Boot 3**, **Spring Data JPA**, and **WebSockets**. Designed to eliminate manual order-relay bottlenecks during peak dining hours by linking permanent table-mounted ordering tablets directly to a real-time Kitchen Display System.

## Key Features

### Customer Table-Side Ordering Interface (JavaFX)
- **Full-Screen Touch-First UX:** Responsive grid/list menu layout with tactile category filters, instant search, and an interactive prep-time & price distribution histogram.
- **Contextual Slide-Over Customization:** Animated customization drawer for cook temperatures, accompaniments, portion options, and zero-typing kitchen preferences with live price calculation in KSh.
- **Persistent Table Cart & Live Tracking:** Real-time order status tracker (`Received → In Prep → Serving`) pushed directly from the kitchen via WebSocket.

### Kitchen Display System — KDS (JavaFX)
- **Zero-Latency Ticket Streaming:** Incoming table orders appear instantaneously with live ticking `MM:SS` timers (color-escalating at 10m and 15m) and a live prep-time distribution histogram.
- **Station Routing & Prioritization:** Filter tickets by status (`All`, `New`, `In Prep`, `Ready`) or kitchen station (`Grill`, `Fryer`, `Pass`, `Bar`), pin urgent tickets with **Rush** priority, and toggle between Card and Compact board layouts.
- **Item-Level Prep Checklists:** Checking off individual dishes syncs across clients and automatically transitions order states.

### Enterprise Backend (Spring Boot + JPA + WebSocket)
- **Layered Architecture:** Clean separation across `shared` DTOs, JPA `@Entity` models (`MenuItem`, `RestaurantOrder`, `OrderItem`), Spring Data `JpaRepository` persistence (embedded H2), transactional service logic, REST controllers, and a thread-safe `TextWebSocketHandler` broadcaster.

## Tech Stack
- **Language & Build:** Java 21, Gradle
- **Desktop Clients:** JavaFX 21 (custom CSS design system, cubic ease-out transitions, `java.net.http` async REST & WebSocket client)
- **Backend & Persistence:** Spring Boot 3.4, Spring Data JPA (Hibernate), H2 Database, Spring WebSocket

## System Architecture

```text
[ Customer Tablet (JavaFX) ] ── REST + WebSocket ──► [ Spring Boot Server ] ── JPA ──► [ H2 Database ]
                                                              │
                                                     Real-Time Broadcast
                                                              ▼
                                                  [ Kitchen Display (JavaFX) ]
Running LocallyPrerequisitesJDK 21+Launch All Three ApplicationsOpen three terminals in the project root:Bash# 1. Start the Spring Boot + WebSocket Server (port 8080)
./gradlew :app:runServer

# 2. Launch the Kitchen Display System (KDS)
./gradlew :app:runKds

# 3. Launch the Customer Table-Side Tablet
./gradlew :app:runTablet
Multi-Machine LAN ModePoint the Tablet or KDS to a server running on another machine over the same network:Bash./gradlew :app:runTablet -PserverHost=<SERVER_IP>:8080
API EndpointsMethodEndpointDescriptionGET/api/menuFetch full or category-filtered menuGET/api/ordersFetch all active (non-completed) kitchen ticketsGET/api/orders/table/{tableNumber}Fetch order history & live status for a tablePOST/api/ordersSubmit a new customized order and broadcast to KDSPATCH/api/orders/{id}/status?status=Advance order lifecycle (NEW → IN_PREP → READY → COMPLETED)PATCH/api/orders/{orderId}/items/{itemId}/toggleToggle item prepared state in real timeWS/ws/ordersReal-time JSON order broadcast channel
