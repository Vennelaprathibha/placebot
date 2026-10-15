# PlaceBot — conversational AI, progress and mock interviews

PlaceBot is a simple student registration portal with three areas: **Chat**, **My Progress**, and **Mock Interviews**.

## Features
- Registration profile stored in MySQL. The same browser automatically resumes the saved profile and opens Chat; returning users can sign in with their registered email and password on another browser/device.
- Chat history saved and included as context for follow-up questions.
- General-purpose conversational responses through Google's Gemini API when `GEMINI_API_KEY` is configured.
- Attach a PDF, DOC/DOCX, TXT, RTF, PPT/PPTX or common image (up to 8 MB), type a question in the same composer, and send both together. Document text is added as context; images are sent to Gemini with their image data when an API key is present.
- **My Progress** shows saved chat activity by subject, including DSA, Java, OS, DBMS/SQL, Networks, aptitude/reasoning, web/software engineering and some branch-specific subjects. The percentages indicate activity, not a verified proficiency score.
- **Mock Interviews** starts an interactive, one-question-at-a-time practice session in chat for aptitude/reasoning, coding/DSA, core CS theory or a mixed placement interview. It asks the AI to wait for answers, evaluate them and explain solutions. Use the microphone button to dictate an answer (browser microphone permission required).
- **Settings** lets a student review and update profile details or log out. Password sign-in is available for returning users.

## Configure
1. A starter `.env` file is included, so you can build immediately.
2. For general-purpose AI answers, put your valid Google Gemini API key in `GEMINI_API_KEY` in `.env` (create one at https://aistudio.google.com/apikey). Without a key, the app can start, calculate simple arithmetic and provide a few basic built-in replies, but it cannot provide general-purpose AI answers. This is expected; an AI API key is required for broad question answering, coding explanations, document reasoning and image understanding.
3. You can change `DB_PASSWORD` before the first run. If the `dbdata` volume already exists from an earlier run, its stored MySQL password may differ; see the note below.
4. Run `docker compose up --build -d` from this folder.
5. Open `http://localhost:3000`. New users select **Create account**; returning users sign in. If you are already signed in on the same browser, PlaceBot restores the saved profile and opens Chat directly.

If a MySQL volume was initialized with a previous password, changing `DB_PASSWORD` in `.env` does not automatically change the existing database user's password. Do not remove the `dbdata` volume unless you intentionally want to delete stored data.

## Backend build (Maven)
Requires Java 17+ and Maven 3.9+.
```
cd backend
mvn clean verify          # compile + run tests
mvn spring-boot:run       # run locally (needs MySQL on localhost:3306, see application.properties)
```
The packaged jar is `backend/target/placement-assistant.jar`. Health check: `http://localhost:8080/actuator/health`.

## Selenium job scraper
Runs every 6 hours. Trigger it manually: `curl -X POST http://localhost:8080/api/jobs/refresh`; list jobs: `curl http://localhost:8080/api/jobs`.
In Docker/Kubernetes it uses the `selenium` container through `SELENIUM_URL`; with no `SELENIUM_URL` it uses local Chrome.

## Docker (MySQL + Selenium + backend + frontend)
```
cp .env.example .env      # then edit DB_PASSWORD and GEMINI_API_KEY
docker compose up --build -d
docker compose ps         # all services should become healthy
```
App: http://localhost:3000, API: http://localhost:8080.

## Kubernetes
Works with Docker Desktop Kubernetes, minikube or kind. Manifests are in `k8s/` (namespace, config, MySQL StatefulSet with a 5Gi volume, Selenium, backend, frontend).
```
scripts/k8s-deploy.sh     # builds images, creates the secret from .env, applies k8s/
kubectl -n placebot get pods
```
App: http://localhost:30080 (minikube: `minikube service frontend -n placebot`).
Remove everything: `kubectl delete namespace placebot`.

## Git
```
scripts/git-init.sh https://github.com/<you>/<repo>.git
```
`.env` and `k8s/secret.yaml` are git-ignored. A GitHub Actions workflow (`.github/workflows/ci.yml`) builds with Maven and builds both Docker images on every push.
