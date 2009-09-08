# What is this?

"RabbitMQ Streams" is our name for the open source project developed
with the BBC.  It is a **data streams management system**.  Have a
look at [Whence
Streams](http://wiki.github.com/squaremo/rabbitmq-streams/whencestreams)
for more of an explanation and some background, and [the
roadmap](http://wiki.github.com/squaremo/rabbitmq-streams/roadmap) for
where it's at and where it's going.

The basic logical building blocks are *Sources* and *Destinations* of
data and *Pipelines*.  The latter are composed of *PipelineComponents*
which can route (e.g. based on regexp matches on Atom feed entries),
merge and transform the data in arbitrary ways.

Data arrive at sources, and leave from destinations, via *Gateways*,
which talk various protocols to the outside world.

Gateways as well as pipeline components (jointly referred to as
*Plugins*) can currently be written in Java and Python, and require little
 boilerplate (see
e.g. [regexp_replace.py](plugins/regexp_replace/regexp_replace.py
"regexp_replace.py") Support for other languages can be added
straightforwardly by creating a *Harness*; plugins are essentially just
programs following a simple protocol, with the harness taking care of
much of the detail.

There's an API (over HTTP) for listing pipelines and starting and
stopping them.  It'll be expanded to include defining pipelines, as
well as declaring sources and destinations, and subscribing pipelines
to them (and them to pipelines).

See the [wiki](http://wiki.github.com/squaremo/rabbitmq-streams) for
information about using and hacking on Streams.
