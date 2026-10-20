# Library Connect

A small library management website.
**Front end:** HTML, CSS, JavaScript  |  **Back end:** Java 17 + Spring Boot  |  **Database:** PostgreSQL  |  **Deployment:** Docker

Librarians add books and issue / return them. Students browse books, borrow them for a chosen number of days and request returns.
Everyone logs in with a secret code (no email or password).

---

## 1. What is Docker? (plain English)

Normally, to run this project you would have to install Java, Maven and PostgreSQL, set them up, and hope it works the same on every computer.
**Docker puts the program and everything it needs into a "container"**, a sealed box that runs the same way on any computer or server.

* **Image**: the recipe/template of the box (made from the `Dockerfile`).
* **Container**: a running copy of an image.
* **Docker Compose**: a tool that starts several containers together (`docker-compose.yml`). Here: one for the Java app, one for the database.
* **Volume**: a folder that lives outside the container so the data (database, uploaded images) is not lost when the container restarts.

## 2. How to run it

1. Install **Docker Desktop** (https://www.docker.com/products/docker-desktop) and start it.
2. Open a terminal in this folder and run:

```
docker compose up --build
```

3. Wait until you see `Started LibraryApplication` (the first build takes a few minutes), then open **http://localhost:8080**.
4. Stop with `Ctrl + C`. Start again later with `docker compose up`. Delete everything including data with `docker compose down -v`.

## 3. Architecture (how to explain it)

```
 Browser (HTML + CSS + JavaScript)
        |  HTTP requests with JSON  (fetch → /api/...)
        v
 Spring Boot back end (Java)          <- container "app"
   controller → service → repository
        |  SQL via JPA / Hibernate
        v
 PostgreSQL database                  <- container "db"
```

1. The browser loads `index.html`, `style.css` and `app.js` (served by Spring Boot from `src/main/resources/static`).
2. `app.js` calls the REST API, for example `GET /api/books`.
3. A **controller** receives the request, checks who is calling, and asks a **repository** to read or write the database.
4. The answer goes back as JSON and `app.js` draws it on the page.

### Folder map

| Path | What it does |
|---|---|
| `src/main/resources/static/` | **Front end**: `index.html`, `style.css`, `app.js` |
| `src/main/java/com/library/controller/` | **REST API** (what the front end can call) |
| `src/main/java/com/library/service/` | Business logic: login/sessions (`AuthService`), statistics (`StatsService`) |
| `src/main/java/com/library/model/` | **Entities** = one Java class per database table |
| `src/main/java/com/library/repository/` | Database access (Spring Data JPA, no hand-written SQL) |
| `src/main/resources/application.properties` | Settings (database address, upload limit) |
| `pom.xml` | Maven file: lists the libraries the project needs |
| `Dockerfile` | Recipe that builds the Java app into an image |
| `docker-compose.yml` | Starts the app + database together |

### Database tables (created automatically by Hibernate)

| Table | Main columns |
|---|---|
| `accounts` | id, role (`lib`/`stu`), code_hash, name, school, library_name, address, hours, genres, about, photo, saved |
| `books` | id, title, author, genre, description, copies, cover |
| `loans` | id, book_id, book_title, student_id, status, days, requested_at, due_at, returned_at, return_requested |
| `user_sessions` | token, account_id, created_at |
| `settings` | name, val (stores the hashed librarian staff key) |

Loan life cycle: `REQUESTED → ISSUED → RETURNED` (or `DECLINED`). Number of available copies = `copies − active loans`.

### REST API

| Method + URL | Who | Purpose |
|---|---|---|
| `POST /api/auth/create` | anyone | Create account, returns the secret code |
| `POST /api/auth/login` | anyone | Log in with the code |
| `GET /api/books` | logged in | List books with availability |
| `POST/PUT/DELETE /api/books` | librarian | Add / edit / remove a book |
| `POST /api/loans` | student | Request a book for N days |
| `POST /api/loans/{id}/issue` `/decline` `/return` `/extend` | librarian | Handle requests and returns |
| `POST /api/loans/{id}/return-request` | student | "I returned it" (librarian must confirm) |
| `GET /api/loans`, `GET /api/loans/mine` | librarian / student | List loans |
| `GET/PUT /api/profile`, `/api/me`, `/api/me/stats` | logged in | Own profile and statistics |
| `GET /api/students/{id}` | librarian | Student profile and behaviour record |
| `GET /api/library` | logged in | Library name, address, hours, numbers |
| `POST /api/upload` | logged in | Upload a cover / photo image |

### Security (what to say if asked)

* There are no passwords. A random 16-character **secret code** is shown once; only its **SHA-256 hash** is stored.
* After login the server creates a random **session token**. The browser sends it in the `X-Token` header, and the server checks the account type (librarian or student) on every request.
* Creating a librarian account needs a **staff key** (set by the first librarian, stored hashed).
* Limits (good to mention honestly): no HTTPS inside the project (put it behind a hosting provider's HTTPS), no rate limiting, a lost code cannot be recovered.

## 4. Using the website

1. Open the site → **Profile** → choose **Librarian** → **Create account**. Set a staff key. Save the code that appears.
2. Fill in the library name, address, desk hours. Add a few books (with cover images).
3. In a private window, choose **Student** → **Create account** → borrow a book.
4. Back as librarian: **Requests** → click the student's name to see their record → **Issue**.
5. As student: **My books → Return book**. As librarian: **Issued → Confirm return**.

## 5. Putting it on the internet (hosting)

The same Docker files work on any host that runs containers.

* **Easiest, with a managed service** (for example Render, Railway or Fly.io): create a new project from this code (GitHub repo), choose "Docker", and add a PostgreSQL database from the same service.
  Set these environment variables on the app: `DB_URL` (format `jdbc:postgresql://HOST:5432/DBNAME`), `DB_USER`, `DB_PASSWORD`. For uploaded images, attach a persistent disk/volume mounted at `/data/uploads` and set `UPLOAD_DIR=/data/uploads`.
* **Own server (VPS)**: install Docker, copy this folder, run `docker compose up -d --build`, and put a reverse proxy (Caddy or Nginx) in front for HTTPS.
* Before going public, change the database password in `docker-compose.yml`.

Free plans and exact menus change over time, so check the provider's current docs.

## 6. Run without Docker (for development)

Needs Java 17, Maven and a PostgreSQL database named `library` (user/password `library`):

```
mvn spring-boot:run
```

## 7. Hosting on Vercel (beta container support)

Vercel can run a Dockerfile named `Dockerfile.vercel` (already included, same content as `Dockerfile`).

1. Put the project on GitHub (`git init`, commit, push).
2. Create a free PostgreSQL database (Vercel dashboard → Storage / Marketplace, for example Neon or Supabase). Note its host, database name, user and password.
3. On vercel.com choose **Add New → Project**, import the GitHub repo, and before deploying add these Environment Variables:
   * `DB_URL` = `jdbc:postgresql://HOST:5432/DBNAME?sslmode=require`
   * `DB_USER` = database user, `DB_PASSWORD` = database password
   * `PORT` = `8080` (Vercel expects port 80 unless you set this; the app listens on `PORT`)
   * `UPLOAD_DIR` = `/tmp/uploads`
4. Deploy and open the `.vercel.app` link.

Limits to know: containers on Vercel have no permanent disk yet, so uploaded images in `/tmp/uploads` can disappear; the database keeps everything else. After ~5 minutes without visitors the app sleeps, and the next visit takes a few seconds while Java starts. Container Images are in beta.
