FROM python:3.11-slim AS build
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app.py train.py summarize_dataset.py ./
COPY training-data ./training-data
RUN if [ -f training-data/transactions.csv ] && [ -f training-data/accounts.csv ] && [ -f training-data/customers.csv ] && [ -f training-data/loans.csv ]; then \
      python train.py --input training-data/transactions.csv && python summarize_dataset.py; \
    else python train.py; fi

FROM python:3.11-slim
WORKDIR /app
COPY --from=build /usr/local/lib/python3.11/site-packages /usr/local/lib/python3.11/site-packages
COPY app.py ./app.py
COPY --from=build /app/models ./models
EXPOSE 8090
CMD ["python", "-m", "uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8090"]
