FROM citusdata/citus:14.2.0-pg17

COPY docker/citus/020-register-workers.sh /docker-entrypoint-initdb.d/020-register-workers.sh
RUN chmod +x /docker-entrypoint-initdb.d/020-register-workers.sh

EXPOSE 5432