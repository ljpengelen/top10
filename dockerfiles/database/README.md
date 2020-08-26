# PostgreSQL in Docker Container

To run PostgreSQL in a Docker container, first build an image by executing the following command.

```
docker build -t top10-postgres .
```

Afterwards, start a container by executing the following command.

```
docker run --rm -p 5432:5432 -v top10-postgres:/var/lib/postgresql/data top10-postgres
```

The script `init-db.sh` will create two databases (`top10-dev` and `top10-test`) when you start the container for the first time.
