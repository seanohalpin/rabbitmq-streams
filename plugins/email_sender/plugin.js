{
    "name": "Email sender",
    "author": {"name": "James Kearney", "email": "james@lshift.net"},

    "type": "plugin-specification",
    "harness": "java",
    "subtype": "server",
    
    "global_configuration_specification": [],
    "configuration_specification": [
                                    {"label": "Transport protocol", "type": "protocol (e.g. smtp)", "name": "transportProtocol"},
                                    {"label": "Host", "type": "URL - the smtp host", "name": "host"},
                                    {"label": "Username", "type": "String - username", "name": "username"},
                                    {"label": "Password", "type": "String - password", "name": "password"},
                                    ],

    "destination_specification": [{"label": "To", "type": "List of emails", "name": "to"}
                                 ]
}