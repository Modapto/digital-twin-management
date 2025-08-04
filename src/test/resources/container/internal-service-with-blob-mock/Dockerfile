FROM python:3.8-slim

RUN apt-get update -y && \
    apt-get install -y python3-pip python3-dev

WORKDIR /app

RUN pip install flask==2.3.0 Werkzeug==2.3.0 flask_api

COPY . /app

ENTRYPOINT [ "python" ]

CMD [ "server.py" ]

EXPOSE 5001