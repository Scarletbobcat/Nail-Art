# path to where docker-compose.yaml is located; change if needed
DOCKER_COMPOSE_PATH="$HOME/Desktop/Projects/Nail-Art"

# urls to where the application is exposed
FRONTEND_URL="http://localhost:5173"

# updating images
docker pull -a scarletbobcat/nail-art

# cd and start containers
cd $DOCKER_COMPOSE_PATH || { echo "Failed to change directory"; exit 1; }
docker-compose up -d

# wait for the application to start
echo "Waiting for the application to start..."
until docker ps | grep "nail-art-client-1"; do sleep 1; done
until curl -o /dev/null --silent --fail $FRONTEND_URL; do sleep 1; done
until curl -o /dev/null -H "Content-Type: application/json" --silent --retry-delay 2 http://localhost:8080/employees; do sleep 1; done

# opening front end
open $FRONTEND_URL

exit 0