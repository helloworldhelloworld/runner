#!/bin/bash

# Load environment variables from .env file
set -a
source .env
set +a

# Start Spring Boot application
mvn spring-boot:run
