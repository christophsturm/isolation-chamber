### Welcome to Isolation Chamber

The fastest way to get isolated databases for your tests. Currently supports creating postgresql dbs. 


This project is currently moving from into this repository from a monorepo. It should be perfectly usable even if the build is pretty new. Docs are also missing. 

### testing
please run the docker-compose.yml file before running the
test suite. 

### api
call PostgresqlFactory.preparePostgresDB with an optional schema to get an isolated database with that schema applied.

Currently, this repo contains only the implementation that uses integresql via the integresql client that is also contained in this repo. 
Call PostgresqlFactory.cleanUp at the end of your suite to drop all the databases. 
