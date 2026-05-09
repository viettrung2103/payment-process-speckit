# Scaling Efficiency Analysis

## Summary

The quick performance tests show a single-instance throughput of ~384 RPS while the scaled three-instance setup delivers ~245 RPS. This is lower than expected for horizontal scaling, and it indicates the system is limited by shared backend resources or proxy overhead rather than by the payment-bridge replicas themselves.

## Key findings

- The scaled test is not delivering 3x throughput because the application replicas share the same PostgreSQL, RabbitMQ, and mock-payment-api services.
- Nginx load balancing introduces additional latency and connection handling overhead that does not exist in the single-instance path.
- The JMeter profile used for the quick test may not be producing enough concurrent demand to fully utilize all three replicas.
- The measured efficiency is still valuable: it shows the system can handle 245 RPS under the current combined architecture.

## Local resource starvation observation

The current Mac-based Docker test appears to be limited by host resources, not application logic.

- Total CPU usage across containers can exceed the CPU allocation configured for Docker Desktop / OrbStack on Mac. When total usage is above 400-600%, the VM is saturated.
- The three `payment-bridge` containers alone can consume around 350% CPU, while JMeter can consume another 180% and Postgres/RabbitMQ around 110%.
- That means local scaling on a single laptop is primarily constrained by VM CPU and memory capacity.
- In an enterprise environment, bridges would usually be on separate nodes and JMeter would run on a separate load-generation machine. Local tests are therefore a limited approximation of cloud-scale behavior.

### Local test recommendations

1. Increase Docker CPU allocation if your Mac can support more cores.
2. Run JMeter separately if possible, so the load generator does not compete with the application containers.
3. Lower the local JVM footprint with `-Xmx512m` and fewer Tomcat threads for each bridge.
4. Use this local result as a resource-constrained baseline rather than a production scaling metric.

## How to diagnose the bottleneck

- Use `docker stats` for the scaled stack:
  - `docker stats payment-bridge-1 payment-bridge-2 payment-bridge-3 payment-system-postgres payment-system-rabbitmq payment-system-nginx`
  - If PostgreSQL or RabbitMQ is close to 100% while the app replicas are not, the shared services are the limiting factor.
- Inspect response time and error metrics:
  - A high average or P95 latency with low error rate points to a backend, database, or proxy constraint.
  - Frequent 429 responses or connection resets suggest load balancer or rate limiting is interfering.
- Validate application health and logs:
  - `docker logs payment-system-nginx`
  - `docker logs payment-system-postgres`
  - `curl http://localhost:8080/actuator/health`
- Run an isolated app-layer test:
  - Replace the backend with a lightweight stub or bypass the database to see whether app replicas can scale linearly.
- Increase the JMeter concurrency if all services are underutilized:
  - A low overall CPU utilization with a lower-than-expected RPS means the test load is not high enough.

## Recommendations for deeper analysis

- Measure resource saturation across all containers.
- Increase JMeter users and duration to see if throughput improves on the three-instance setup.
- Separate the app layer from the shared state layer in scaling tests.
- Add application and infrastructure metrics (CPU, memory, DB queries, queue depth, connection counts).
- Re-run the test with a higher concurrency plan to validate whether throughput curves rise closer to expected scaling.

## Practical conclusion

A drop from 384 RPS to 245 RPS for a three-instance deployment is not necessarily a failure. It is an expected sign that the shared services and load balancer are the current performance constraints. The next step is to identify which shared component is limiting overall throughput and address that before assuming linear scaling is possible.
