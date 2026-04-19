web:
  cd client && npm run dev

api:
  cd api && ./mvnw spring-boot:run

[parallel]
dev:
  #!/bin/bash
  just web & \
  just api

web-install:
  cd client && npm install
