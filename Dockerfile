FROM python:3.11-slim

WORKDIR /app

# Copy dashboard static files and server into /app
COPY dashboard/ /app/

# Cloud platforms like Render supply $PORT; fallback to 8081 for local runs
ENV PORT=8081
EXPOSE 8081

CMD ["python3", "server.py"]
