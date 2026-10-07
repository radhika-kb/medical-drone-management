FROM python:3.11-slim
WORKDIR /app/ai-service
COPY requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r /app/requirements.txt
COPY ai-service /app/ai-service
EXPOSE 8000
CMD ["python", "server.py"]
