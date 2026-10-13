FROM python:3.11-slim

WORKDIR /app

COPY . .

# Install dependencies only if requirements.txt exists
RUN if [ -f requirements.txt ]; then \
        pip install --no-cache-dir -r requirements.txt; \
    fi

EXPOSE 5000

CMD ["python", "app.py"]
