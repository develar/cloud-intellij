# Building
1. Run: `ant -f update_dependencies.xml` (append ` jb_update` if you have access to internal JetBrains server (could be faster)).
2. Open project in IntelliJ IDEA and make (Build -> Make Project).
  3. Run: `docker run --rm -v $PWD/flux/node.server/flux.orion.integration:/data develar/nodejs-bower` to install the web app dependencies.

# Running
1. Run: `docker-compose -f mqAndDb.yml up` to start a messaging broker and a database.
2. Run: `docker-compose -f nodeAppAndWeb.yml up` to start the node server.
3. Open your browser to `http://<dockerd host ip>`

We have to split docker-compose.yml due to [IDEA-137765 Support docker-compose](https://youtrack.jetbrains.com/issue/IDEA-137765).

# Running IDEA to develop plugin
2. Use run configuration "Idea".

# Setting up development environment
1. Checkout flux — run getFlux.sh (Linux/Mac) or getFlux.bat (Windows).
2. Install [Docker and Docker Compose](https://docs.docker.com/compose/install/). 
(If you have Parallels Desktop, consider to use [Vagrant](https://github.com/Parallels/vagrant-parallels/issues/115) ([sample Vagrantfile](https://dl.dropboxusercontent.com/u/43511007/Vagrantfile))).
3. Install IntelliJ IDEA plugins: YAML, Markdown and .ignore.

To maintain dependencies of the node.server, use [npm-check-updates](https://www.npmjs.com/package/npm-check-updates).

If you have strange mystic errors after editing volumes configuration in the docker-compose files — it is caused, probably, by ghosted volumes.
Execute ```docker rm -v `docker ps --no-trunc -aq``` (be aware — all container volumes data will be lost, don't use it if you don't understand what does it mean).

# RabbitMQ
Current implementation in the original Flux is not suitable for us — RabbitMQ [access control](https://www.rabbitmq.com/access-control.html) allows to manage access to exchanges or queues, but not to routing keys.
Our authentication server uses [rabbitmq_auth_backend_http](https://github.com/simonmacmullen/rabbitmq-auth-backend-http).

We create a topic exchange for a user ("t.$username") because we must broadcast events only to the user. Authentication server checks, that a user can use only own exchange.

We create a direct exchange for a user ("d.$username"). We can prepend user name to queue name to control access, but it will be complicated.

# Building docker images
We use [Tutum](https://www.tutum.co) to build, deploy and manage, so, app images are publishing to [Tutum's private Docker image registry](https://support.tutum.co/support/articles/5000012183-using-tutum-s-private-docker-image-registry).

`$USER` used instead of real username, so, you can run command as is, without modification (assume that your OS username equals to tutum username).
## mq-auth
`docker build -f mq-auth/Dockerfile -t tutum.co/$USER/mq-auth .`