# ADR 0007: No resolution cache in the prototype

**Status:** Accepted. This deviates on purpose from the design doc's "in-memory hash" performance note.

## Problem
The design doc suggests an in-memory hash for low-latency redirects.

## Alternatives
1. `ConcurrentHashMap`, or Spring Cache with `ConcurrentMapCacheManager`. These grow without bound (a memory DoS vector), and each instance has its own copy.
2. Caffeine with a size limit. Adds a dependency.
3. Redis. That's distributed infrastructure, explicitly out of scope.
4. **No cache.** Use the unique index on `short_code`.

## Decision
Option 4. The redirect lookup is a single-row unique-index lookup. At prototype scale, there is no measured need for a cache. A cache would add:
- invalidation work as soon as deactivate, expire or suspend features arrive
- unbounded-memory risk
- per-instance inconsistency

None of that has a measured benefit at prototype scale.

## Tradeoffs
- (+) Less code, no invalidation bugs, no memory risk.
- (−) Every redirect hits the database.
- (+) Deactivation (ADR 0009) takes effect for redirects that start after it commits, because the database is the only source of truth and there's no cache to invalidate. Instances sharing the database are designed to behave the same way; multi-instance deployment wasn't exercised in this prototype.
- **When to revisit:** once there's measured latency or DB load under real traffic. The cache belongs inside `UrlService`'s lookup, bounded (Caffeine) or shared (Redis). It must be evicted on every lifecycle change (`UrlLifecycleService`), or deactivated URLs would keep redirecting. Callers don't change.
