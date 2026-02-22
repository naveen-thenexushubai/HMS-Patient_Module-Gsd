#!/bin/bash
# Start Spring Boot application in background for verification

cd /Users/Naveen-Ainexus/Projects/Hospital_Gsd

echo "Starting Spring Boot application..."
nohup mvn spring-boot:run > spring-boot.log 2>&1 &
SERVER_PID=$!

echo "Server starting (PID: $SERVER_PID)..."
echo "Waiting for application to be ready..."

# Wait for health endpoint to respond (max 60 seconds)
for i in {1..30}; do
    sleep 2
    if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo "Server is ready!"
        echo "Health check: $(curl -s http://localhost:8080/actuator/health)"
        echo ""
        echo "Server is running. Logs: spring-boot.log"
        echo "To stop: pkill -f spring-boot:run"
        exit 0
    fi
    echo -n "."
done

echo ""
echo "Server may still be starting. Check spring-boot.log for details."
echo "To stop: pkill -f spring-boot:run"
