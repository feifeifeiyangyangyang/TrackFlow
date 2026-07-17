mvn test
Push-Location web
npm ci
npm run type-check
npm run test
npm run build
Pop-Location
docker compose config
