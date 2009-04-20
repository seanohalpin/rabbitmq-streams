"""
Interfaces for plugin components to use.
"""

import couchdb.client as couch
import amqplib.client_0_8 as amqp

try:
    import json
except:
    import simplejson as json

def ensure_resource(resource):
    try:
        resource.head()
    except:
        resource.put(content={})

def db_name(db):
    """Give the name of the database.
    >>> db = db_from_config(dict(server='http://localhost:5984', database='pythontest'))
    >>> db_name(db)
    "pythontests"
    """
    return db._name

def db_server(db):
    """Give the server part of a database.
    >>> db = db_from_config(dict(server='http://localhost:5984', database='pythontest'))
    >>> db_server(db)
    "http://localhost:5984"
    """
    return db.resource.uri[:-len(db._name)]

def config_of_db(db):
    """Give the configuration stanza of a store
    >>> db = db_from_config(dict(server='http://localhost:5984', database='pythontests'))
    >>> config = config_of_db(db)
    >>> config['server']
    "http://localhost:5984"
    >>> config['database']
    "pythontests"
    """
    return dict(database=db_name(db), server=db_server(db))

def db_from_config(config):
    """Make a store given a config.
    >>> db = db_from_config(dict(server='http://localhost:5984', database='pythontests'))
    >>> db_name(db)
    "pythontests"
    >>> db_server(db)
    "http://localhost:5984"
    """
    server = couch.Server(config['server'])
    return server[config['database']]

def amqp_connection_from_config(hostspec):
    hostname = hostspec['host']
    port = str(hostspec['port'])
    host = ":".join([hostname, port])
    virt = hostspec['virtual_host']
    userid, password = hostspec['username'], hostspec['password']
    connection = amqp.Connection(host=host,
                                 userid=userid,
                                 password=password,
                                 virtual_host=virt)
    return connection

def publish_to_exchange(channel, exchange):
    def p(msg, **headers):
        # TODO: treat application_headers specially, and expect a content type
        channel.basic_publish(amqp.Message(body=msg, children=None, **headers), exchange=exchange)
    return p

def subscribe_to_queue(channel, queue, method):
    channel.basic_consume(queue=queue, callback=lambda msg: method(msg))

class Component(object):

    INPUTS = {}
    OUTPUTS = {}

    def __init__(self, config):
        msghostspec = config['messageserver']
        self.__conn = amqp_connection_from_config(msghostspec)
        self.__channel = self.__conn.channel()
        
        # Inputs and outputs are matched by position
        inputs = [(desc['name'], q) for (q, desc) in
                     zip(config['inputs'], config['plugin_type']['inputs'])]

        for name, queue in inputs:
            method = getattr(self, self.INPUTS[name])
            subscribe_to_queue(self.__channel, queue, method)
            
        outputs = [(desc['name'], ex) for (ex, desc) in
                   zip(config['outputs'], config['plugin_type']['outputs'])]
        for name, exchange in outputs:
            setattr(self, self.OUTPUTS[name],
                    publish_to_exchange(self.__channel, exchange))

        self.__stateresource = couch.Resource(None, config['state'])
        ensure_resource(self.__stateresource)

        if 'database' in config and config['database'] is not None:
            self.__db = couch.Database(config['database'])
        else:
            self.__db = None
        self.__config = config['config']['configuration']

    def ack(self, msg):
        self.__channel.basic_ack(msg.delivery_info['delivery_tag'])

    def putState(self, state):
        """Record the state of the component"""
        #print "Putting state: "
        #print json.dumps(state)
        resp, data = self.__stateresource.put(content = state)
        return self.getState()

    def getState(self, defaultState = None):
        try:
            resp, data = self.__stateresource.get()
            return couch.Document(data)
        except couch.ResourceNotFound:
            return defaultState

    def privateDatabase(self):
        return self.__db

    def setting(self, name, defaultValue = None):
        """Get a configuration setting."""
        return self.__config.get(name, defaultValue)

    def run(self):
        while True:
            self.__channel.wait() # let the callbacks process

    def start(self):
#        try:
        self.run()
        #finally:
        #    self.__channel.close()
        #    self.__conn.close()
