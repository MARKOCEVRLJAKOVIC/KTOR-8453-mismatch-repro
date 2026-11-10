# Ktor Jetty Jakarta + Maven: Servlet API mismatch

With Ktor 3.6.0, Maven resolves `jakarta.servlet-api` 5.0.0 for `ktor-server-jetty-jakarta`,
while Jetty 12 EE10 requires 6.0.0. Gradle resolves 6.0.0, so only Maven users are affected.

Related: [KTOR-8453](https://youtrack.jetbrains.com/issue/KTOR-8453)

## Run

```bash
./run.sh
```

It runs the same app twice:

- **default**: Ktor's POM as published. A request through a Jetty EE10 servlet context fails with
  `NoClassDefFoundError: jakarta/servlet/ServletConnection`.
- **fixed** (`-Pfixed`): `jakarta.servlet-api` 6.0.0. Everything works.

The `TimeoutException` during engine shutdown appears in both runs and is unrelated.