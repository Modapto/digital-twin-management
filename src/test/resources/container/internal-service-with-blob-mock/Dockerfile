FROM python:3.8-slim-buster

RUN apt-get update -y && \
    apt-get install -y python-pip python-dev

WORKDIR /app

RUN pip install flask==2.3.0 Werkzeug==2.3.0 flask_api

COPY . /app

ENTRYPOINT [ "python" ]

CMD [ "server.py" ]

EXPOSE 5001