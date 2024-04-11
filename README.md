### Welcome to Isolation Chamber

### testing
for now you have to run the docker-compose.yml file before running the
test suite.

### api
call PostgresqlFactory.preparePostgresDB with an optional schema to get an isolated database with that schema applied.

Currently this repo contains only the implementation that uses integresql via the integresql client that is also contained in this repo. 
Call PostgresqlFactory.cleanUp at the end of your suite to drop all the databases. 
