# ADR-001: Reactor Flux over Custom StreamSource

## Status
Accepted

## Context
Early versions used custom `AsyncTask<T>` and `StreamSource<T>` abstractions. We needed to decide whether to continue with custom abstractions or adopt an industry-standard reactive library.

## Decision
Adopted Reactor 3.6 `Flux<StreamEvent>` as the unified streaming abstraction throughout the entire pipeline.

## Consequences
- **Positive**: Backpressure support, composition operators, error handling, proven in production (Spring ecosystem).
- **Positive**: Eliminated custom `AsyncTask`, `StreamSource`, `StreamSubscriber` classes.
- **Negative**: Reactor learning curve for contributors unfamiliar with reactive programming.
- **Migration**: `AsyncTask`/`StreamSource` interfaces removed. `Plugin` system kept as legacy fallback only.
