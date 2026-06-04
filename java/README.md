# cd2026-curd

Spring Boot + MyBatis XML CRUD scaffold.

## Environment

- JDK: 21
- Database: MySQL `127.0.0.1:3306/cd2026`
- Maven local repository: `D:/maven/repository`

## Run

```powershell
mvn spring-boot:run
```

The application creates table `test` automatically with `schema.sql` when it starts.

## APIs

- `GET /api/test`
- `GET /api/test/{id}`
- `POST /api/test`
- `PUT /api/test/{id}`
- `DELETE /api/test/{id}`

Example request body:

```json
{
  "name": "demo",
  "remark": "hello"
}
```
