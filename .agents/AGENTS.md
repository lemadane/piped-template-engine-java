# Project Guidelines

## Access Modifier Rules
- Do NOT use `private` access modifiers on fields, methods, constructors, or inner classes/records/enums.
- Use package-private (default visibility without `private`) for all internal methods, fields, constructors, and classes so that unit tests and integration tests within the same package can access them directly.
