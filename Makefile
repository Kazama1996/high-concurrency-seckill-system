.PHONY: infra-up infra-down app-up app-down down-all logs logs-infra ps restart-app clean

# Start infra (postgres / redis / kafka / pgadmin / kafka-ui)
infra-up:
	docker compose --env-file .env.infra up -d

# Stop infra (does not affect app)
infra-down:
	docker compose --env-file .env.infra down

# Start app (infra will be started too if not already running, depends_on handles ordering)
app-up:
	docker compose --env-file .env.infra --profile app up -d --build

# Stop app only, leave infra running
app-down:
	docker compose --env-file .env.infra stop app
	docker compose --env-file .env.infra rm -f app

# Stop everything (infra + app)
down-all:
	docker compose --env-file .env.infra --profile app down

# Tail app logs
logs:
	docker compose --env-file .env.infra logs -f app

# Tail infra logs (optionally filter by service, e.g. make logs-infra s=postgres)
logs-infra:
	docker compose --env-file .env.infra logs -f $(s)

# Show status of all containers
ps:
	docker compose --env-file .env.infra --profile app ps

# Rebuild and restart app only (useful after code changes)
restart-app:
	docker compose --env-file .env.infra --profile app up -d --build app

# Destructive: tear down everything including volumes (wipes DB data)
clean:
	docker compose --env-file .env.infra --profile app down -v