# Course database verification

Run compilation and the foundation tests:

```sh
mvn compile -Dtest=CourseMappingTest,CoursePostgresTest test
```

`CourseMappingTest` checks Hibernate metadata and derived repository property
paths without a database. It does not validate SQL against PostgreSQL.

To enable `CoursePostgresTest`, set these environment variables to a **dedicated
PostgreSQL test database**, not `acadence_db`:

- `ACADENCE_TEST_DATABASE_URL` (JDBC PostgreSQL URL)
- `ACADENCE_TEST_DATABASE_USERNAME`
- `ACADENCE_TEST_DATABASE_PASSWORD`

The test creates a unique `acadence_test_<random>` schema, runs V1 and V2 there,
starts Hibernate with `validate`, and checks persistence, timestamps, lazy loading,
repository lookups, and SQL constraints. The test account needs permission to create
schemas. The temporary schema is removed afterward; existing schemas, data, and
migration history are untouched. Without test configuration the test is skipped.

The application keeps Flyway migrations and Hibernate validation as configured.
V2 will be applied on normal startup against the application's configured database.

## Membership foundation

Run `mvn compile -Dtest=CourseMembershipMappingTest,CourseMembershipPostgresTest test`.
The same dedicated test database environment variables enable the PostgreSQL test.
It applies V1–V3 in an isolated schema, validates all four entity mappings, and
tests per-offering roles, repository lookups, timestamps, lazy relationships,
duplicate membership rejection, foreign keys, role checks, and restrictive deletion.
Without these settings the PostgreSQL test is explicitly skipped; metadata tests
are not a substitute for database constraint validation.
