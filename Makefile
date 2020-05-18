start_kafka:
	docker-compose -f containers/docker-compose.deps.yml up kafka

start_zookeeper:
	docker-compose -f containers/docker-compose.deps.yml up zookeeper

start_zk_kafka:
	docker-compose -f containers/docker-compose.deps.yml up zookeeper kafka

start_redis:
	docker-compose -f containers/docker-compose.deps.yml up redis

stop_all:
	$(COMPOSE_CMD) -f containers/docker-compose.deps.yml down -v
	docker container prune -f
	docker volume prune -f
	docker network prune -f
