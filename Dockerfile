FROM postgres:17-alpine

COPY src/main/resources/schema.sql /docker-entrypoint-initdb.d/01-create-schema.sql

EXPOSE 5432