#!/usr/bin/env python
# encoding: utf-8
import base64
import json
import logging
from flask import Flask, request
from flask_api import status

app = Flask(__name__)
logging.basicConfig(level=logging.INFO)

@app.route('/add', methods = ['POST'])
def add():
    global value
    payload = request.data.decode('utf-8')
    logging.info("Received payload: %s", payload)
    input1 = extractValue(request.json['input1'])
    input2 = extractValue(request.json['input2'])
    
    logging.info("Calculating %s + %s...", input1, input2)
    return json.dumps({'result': str(input1 + input2)}), status.HTTP_200_OK	

def extractValue(json_data):
    return float(json.loads(base64.b64decode(json_data['value']).decode('utf-8'))['data'])

if __name__ == "__main__":
    app.run('0.0.0.0', port=5001)
