import sys, os.path, string
path = os.path.dirname(sys.argv[0])
if len(path) > 0:
    path = path + "/"
path = path + "../harness/python/lib"
sys.path.insert(0, path)

try:
    import json
except ImportError:
    import simplejson as json

import amqplib.client_0_8 as amqp
import couchdb.client as couch

connection = amqp.Connection(host="localhost:5672", userid="feedshub_admin", password="feedshub_admin")
channel = connection.channel()
exchange = "feedshub/config"

feedId = sys.argv[1]
feedStatus = False
if string.lower(sys.argv[2]) in ["true", "on", "activate", "yes", "indubitably", "positive", "1"]:
    feedStatus = True

db = couch.Database('http://localhost:5984/feedshub_status/')
statusDoc = db.get(feedId + "_status")

if statusDoc == None:
    print "Unable to find the status for feed id " + feedId
    sys.exit(1)

routingkey=feedId
if statusDoc['type']=='terminal-status':
    configDoc = db.get(feedId)
    routingkey = '%s.%s' % (configDoc['server'], feedId)

oldActive = statusDoc['active']
statusDoc['active'] = feedStatus
db.update([statusDoc])

channel.basic_publish(amqp.Message(body="status change", children=None), exchange=exchange, routing_key=routingkey)
