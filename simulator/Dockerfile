FROM python:3.12-alpine
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple
COPY sand_ship_simulator.py .
ENTRYPOINT ["python", "sand_ship_simulator.py"]
