# RabbitMQ Streaming Scala Client

My attempt to learn Scala by implementing the RabbitMQ Streams protocol from scratch.

This is a learning project - I don't know Scala well so the code might be off, but I'll keep working on it until it becomes good(?).

## Status

Currently works for basic publish and subscribe operations. All protocol commands are implemented but mostly untested.

## Running

```bash
# Publisher (default)
sbt run

# Consumer
sbt "run subscribe"
```

## Environment Variables

```bash
export RABBITMQ_HOST=localhost
export RABBITMQ_PORT=5552
export RABBITMQ_USERNAME=guest
export RABBITMQ_PASSWORD=guest
```