# Building
1. Run: `ant -f update_dependencies.xml` (append `jb_update` if you have access to internal JetBrains server (could be faster)). (todo: use ant from docker).
2. Open project in IntelliJ IDEA and make (Build -> Make Project).
3. Run: `cd web-client && npm install && gulp compile` to install the web app dependencies. (todo: use gulp from docker).

# Running from sources
1. Run: `docker-compose -f mqAndDb.yml up` to start a messaging broker and a database.
1. Run: `docker-compose -f nodeAppAndWeb.yml up` to start a web server.
1. Run IntelliJ IDEA (use run configuration "Idea").
1. Open your browser to `http://<dockerd host ip>`

We have to split docker-compose.yml due to [IDEA-137765 Support docker-compose](https://youtrack.jetbrains.com/issue/IDEA-137765).

# Setting up development environment
1. Install [Docker and Docker Compose](https://docs.docker.com/compose/install/).
(If you have Parallels Desktop, consider to use [Vagrant](https://github.com/Parallels/vagrant-parallels/issues/115) ([sample Vagrantfile](https://dl.dropboxusercontent.com/u/43511007/Vagrantfile))).
1. Install IntelliJ IDEA plugins:
  * YAML to edit .yml files.
  * Markdown to edit .md files.
  * nginx Support to edit .conf files.
  * .ignore to edit .md files.

To maintain dependencies of web client, use [npm-check-updates](https://www.npmjs.com/package/npm-check-updates).

If you have strange mystic errors after editing volumes configuration in the docker-compose files — it is caused, probably, by ghosted volumes.
Execute ```docker rm -v `docker ps --no-trunc -aq``` (be aware — all container volumes data will be lost, don't use it if you don't understand what does it mean).

# Building docker images
We use [Tutum](https://www.tutum.co) to build, deploy and manage, so, app images are publishing to [Tutum's private Docker image registry](https://support.tutum.co/support/articles/5000012183-using-tutum-s-private-docker-image-registry).

`$USER` used instead of real username, so, you can run command as is, without modification (assume that your OS username equals to tutum username).
## mq-auth
`docker build -f mq-auth/Dockerfile -t tutum.co/$USER/mq-auth .`