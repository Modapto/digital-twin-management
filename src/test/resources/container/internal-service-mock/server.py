#!/usr/bin/env python
# encoding: utf-8
import json
from flask import Flask, request
from flask_api import status
app = Flask(__name__)


@app.route('/add', methods = ['POST'])
def add():
    global value
    input1 = float(request.json['data']['input1'])
    input2 = float(request.json['input2'])
    print(str(input1) + "+" + str(input2))
    return  json.dumps({'result': str(input1 + input2)}), status.HTTP_200_OK

if __name__ == "__main__":
    app.run('0.0.0.0', port=5001)
