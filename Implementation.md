# RabbitMQ
Current implementation in the original Flux is not suitable — RabbitMQ [access control](https://www.rabbitmq.com/access-control.html) allows to manage access to exchanges or queues, but not to routing keys. Our authentication server uses [rabbitmq_auth_backend_http](https://github.com/simonmacmullen/rabbitmq-auth-backend-http).

A topic exchange created for a user ("t.$username") because events must be broadcasted only to the user. Authentication server checks, that a user can use only own exchange. A direct exchange created for a user ("d.$username"). User name could be prepended to a queue name to control access, but it will be complicated.

For events server-named exclusive, autodelete, non-durable queue used. Without explicit acknowledgement for performance reasons (in any case it makes little sense for topic exchange).

For RPC non-exclusive, autodelete, non-durable queue used. Not the event queue because RPC should be consumed with explicit acknowledgement. Not an exclusive queue to reduce numbers of queues and, first of all, to clarify that it is a [work queue](https://www.rabbitmq.com/tutorials/tutorial-two-python.html). As a result, RabbitMqMessageConnector requires to specify rpcQueueName, because each client can define own set of bindings. If two clients define different bindings but use the same rpcQueueName, client can receive unsupported message.

IntelliJ IDEA plugin currently uses "idea-client" as a rpcQueueName.

# Eclipse Orion
## Plugins
 * webEditingPlugin — register default editor service [orion.edit.editor](https://wiki.eclipse.org/Orion/Documentation/Developer_Guide/Plugging_into_the_editor#orion.edit.editor). So, we must use.

Eclipse Orion has very suitable and appropriate API — [AsyncStyler](https://orion.eclipse.org/jsdoc/symbols/orion.editor.AsyncStyler.html) to provide highlighting. IDE implementation could be used directly without intermediate model. Performance — yes, on each typing we ask IDE to provide style information, but in any case we must do it to annotate code.

# Tutum
Note about tutum.yml: One certs service cannot be used for both mq and web services due to https://support.tutum.co/support/tickets/1287:

> Tutum does "volumes-from" associations based on the container name suffix.
  Therefore, web-1 gets its "volumes-from" from certs-1, and mq-1 also gets its "volumes-from" from certs-1,
  that is why web-1 fails to deploy with this message.

> We will fix this in the future, but it is not between our priorities in the short term.
  For now, you could split the "certs" service in two services, "web-certs" and "mq-certs",
  and deploy each of them with the same tags as "web" and "mq" respectively.