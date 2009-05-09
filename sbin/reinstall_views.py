import sys, os.path, string
sys.path.append("../harness/python/lib")

try:
    import json
except ImportError:
    import simplejson as json

import amqplib.client_0_8 as amqp

connection = amqp.Connection(host="localhost:5672", userid="feedshub_admin", password="feedshub_admin")
channel = connection.channel()
exchange = "feedshub/config"

channel.basic_publish(amqp.Message(body="install views", children=None), exchange=exchange)