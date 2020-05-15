ifndef BRANCH_NAME
	BRANCH_NAME = $(shell git rev-parse --abbrev-ref HEAD)
endif

ifndef UID
	UID = $(shell id -u)
endif
export UID

ifndef GID
	GID = $(shell id -g)
endif
export GID

COMPOSE_CMD = docker-compose -p $(BRANCH_NAME)
SBT_MEM = 2048
SBT_RUN_OPTS = -mem $(SBT_MEM)
SBT_CMD = $(COMPOSE_CMD) -f containers/docker-compose.dev.yml run --rm sbt $(SBT_RUN_OPTS)

sbt:
	$(SBT_CMD) $(CMD)

clean:
	$(SBT_CMD) clean

coverage:
	$(SBT_CMD) coverage

coverageReport:
	$(SBT_CMD) coverage test coverageReport

compile:
	$(SBT_CMD) compile test:compile it:compile

run:
	$(COMPOSE_CMD) -f cicd/docker-compose.run.yml up -d -V

pipelines:
	$(COMPOSE_CMD) -f cicd/docker-compose.pipelines.yml up

stop_run:
	$(COMPOSE_CMD) -f cicd/docker-compose.run.yml down -v

start_dependencies:
	$(COMPOSE_CMD) -f cicd/docker-compose.deps.yml up -d -V

stop_dependencies:
	$(COMPOSE_CMD) -f cicd/docker-compose.deps.yml down -v

start_jdbc_dependencies:
	$(COMPOSE_CMD) -f cicd/docker-compose.jdbc.yml up -d -V
	$(COMPOSE_CMD) -f cicd/docker-compose.jdbc.yml exec hive-server sh -c "/root/wait-for-it.sh hive-server:10000 -t 180 -- /opt/hive/bin/beeline -u jdbc:hive2://localhost:10000 -f /opt/initdb/01-database-1.hql"

stop_jdbc_dependencies:
	$(COMPOSE_CMD) -f cicd/docker-compose.jdbc.yml down -v

start_fs_dependencies:
	$(COMPOSE_CMD) -f cicd/docker-compose.fs.yml up -d -V
	$(COMPOSE_CMD) -f cicd/docker-compose.fs.yml exec localstack sh -c "/root/wait-for-it.sh -h localhost -p 4572 -t 180 -- /root/wait-for-it.sh -h localhost -p 4593 -t 180 -- /root/init-s3.sh"
	$(COMPOSE_CMD) -f cicd/docker-compose.fs.yml exec hdfs bash -c "/root/wait-for-it.sh -h hdfs -p 50010 -- /root/wait-for-it.sh -h hdfs -p 8020 -- hdfs dfs -copyFromLocal /home/data/* /"

stop_fs_dependencies:
	$(COMPOSE_CMD) -f cicd/docker-compose.fs.yml down -v

test:
	$(COMPOSE_CMD) -f cicd/docker-compose.dev.yml run --rm --entrypoint "sbt coverage test coverageReport $(SBT_RUN_OPTS)" sbt

test_it:
	$(COMPOSE_CMD) -f cicd/docker-compose.deps.yml up -d -V
	$(COMPOSE_CMD) -f cicd/docker-compose.jdbc.yml up -d -V
	$(COMPOSE_CMD) -f cicd/docker-compose.it.yml up -d -V
	$(COMPOSE_CMD) -f cicd/docker-compose.jdbc.yml exec hive-server sh -c "/root/wait-for-it.sh hive-server:10000 -t 180 -- /opt/hive/bin/beeline -u jdbc:hive2://localhost:10000 -f /opt/initdb/01-database-1.hql"
	$(COMPOSE_CMD) -f cicd/docker-compose.dev.yml run --rm --entrypoint "${PWD}/cicd/wait-for-it.sh hive-server:10000 -t 180 -- sbt it:test $(SBT_RUN_OPTS)" sbt

test_jdbc_module:
	$(COMPOSE_CMD) -f cicd/docker-compose.dev.yml run --rm --entrypoint "${PWD}/cicd/wait-for-it.sh hive-server:10000 -t 180 -- sbt 'project jdbc' it:test $(SBT_RUN_OPTS)" sbt

test_connection_exploration_module_it:
	$(COMPOSE_CMD) -f cicd/docker-compose.dev.yml run --rm --entrypoint "${PWD}/cicd/wait-for-it.sh hive-server:10000 -t 180 -- sbt 'project connection-exploration' it:test $(SBT_RUN_OPTS)" sbt

test-only_connection_exploration_module_it:
	$(SBT_CMD) "connection-exploration/it:testOnly $(TEST_CLASS)"

test-only_module_it:
	$(SBT_CMD) "$(MODULE)/it:testOnly $(TEST_CLASS)"

test_argo_module_it:
	$(COMPOSE_CMD) -f cicd/docker-compose.dev.yml run --rm --entrypoint "${PWD}/cicd/wait-for-it.sh hive-server:10000 -t 180 -- sbt 'project argo' it:test $(SBT_RUN_OPTS)" sbt

test_fs_module:
	$(COMPOSE_CMD) -f cicd/docker-compose.dev.yml run --rm --entrypoint "sbt 'project fs' it:test $(SBT_RUN_OPTS)" sbt

test-only:
	$(SBT_CMD) "testOnly $(TEST_CLASS)"

checkFormat:
	$(SBT_CMD) scalafmtCheckAll scalafmtSbtCheck

format:
	$(SBT_CMD) scalafmt test:scalafmt scalafmtSbt

stop_all:
	$(COMPOSE_CMD) -f cicd/docker-compose.deps.yml -f cicd/docker-compose.jdbc.yml -f cicd/docker-compose.fs.yml down -v
	docker container prune -f
	docker volume prune -f
	docker network prune -f

publish_local:
	sbt docker:publishLocal
