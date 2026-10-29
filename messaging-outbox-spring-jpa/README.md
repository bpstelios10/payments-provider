# messaging-outbox-spring-jpa

The Spring Boot + JPA integration for [`messaging-outbox-core`](../messaging-outbox-core).

## Contents
| Type | Role |
|---|---|
| `OutboxEvent` | JPA entity for the `outbox_event` table. Converts to and from `OutboxRecord`. |
| `OutboxEventRepository` | `SELECT … FOR UPDATE SKIP LOCKED LIMIT 100` polling query (**PostgreSQL**) |
| `OutboxEventProcessorRunner` | `@Transactional`: lock batch → `OutboxProcessor` → sync state back |
| `OutboxScheduler` | `@Scheduled` trigger, enabled by property |

## Integrating into a service
1. Provide an `OutboxEventSender` bean (for example a Kafka sender).
2. Make the library's beans and entity visible:
   ```java
   @SpringBootApplication(scanBasePackageClasses = {MyApp.class, OutboxEventProcessorRunner.class})
   @EntityScan(basePackages = {"my.app", "org.learnings.payments.messaging.outbox.jpa"})
   @EnableJpaRepositories(basePackages = {"my.app", "org.learnings.payments.messaging.outbox.jpa"})
   ```
3. Create the `outbox_event` table via your migrations. See `payments-service/src/main/resources/db/changelog/v1-002-create_outbox_event.yml`.
4. Write events with `outboxEventRepository.save(OutboxEvent.fromRecord(record))` **inside the same transaction** as the business change.
5. Configure it:
   ```yaml
   outbox:
     spring.scheduling.enabled: true
     schedule:
       delay: 5000          # ms between runs
       initialDelay: 5000   # ms before first run
   ```

## Caveats
- The batch transaction stays open while records are sent. Keep sends fast and bounded by a timeout.
- The polling query is PostgreSQL-specific (`SKIP LOCKED`).
- Planned: Spring Boot auto-configuration instead of component scanning. See [CODE_REVIEW §25](../docs/CODE_REVIEW.md#25-messaging-library-design).
