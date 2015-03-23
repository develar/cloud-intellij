RabbitMQ Authentication Server Backend that [rabbitmq_auth_backend_http](https://github.com/simonmacmullen/rabbitmq-auth-backend-http) can authenticate against.

Consider to use docker image [develar/rabbitmq-auth](https://registry.hub.docker.com/u/develar/rabbitmq-auth/).

HTTPS is not supported and will not be supported — [private overlay network](http://blog.tutum.co/2015/03/03/introducing-overlay-networking-for-containers-and-dynamic-links-in-tutum/) for containers should be used.