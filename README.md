# Building
1. Run: `ant -f update_dependencies.xml` (append `jb_update` if you have access to internal JetBrains server (could be faster)). (todo: use ant from docker).
2. Open project in IntelliJ IDEA and make (Build -> Make Project).
3. Run: `cd web-client && npm run build` to build web app. (todo: use webpack from docker).

# Running from sources
1. Run: `docker-compose up`.
1. Run IntelliJ IDEA (use run configuration "Idea").
1. Open your browser to `https://flux.dev`.

After project update, you should execute `docker-compose pull && docker-compose -f web.yml pull` (because `up` doesn't check updates).

# Setting up development environment
1. Clone https://github.com/develar/orion.client.git to web-client: `git clone https://github.com/develar/orion.client.git web-client/orion.client`
1. Install [Docker and Docker Compose](https://docs.docker.com/compose/install/). Docker 1.6+ and Docker Compose 1.2+ required. 
(If you have Parallels Desktop, consider to use [Vagrant](https://github.com/Parallels/boot2docker-vagrant-box) ([sample Vagrantfile](https://dl.dropboxusercontent.com/u/43511007/Vagrantfile))).
1. Trust the self signed development certificate `certs/cert.pem`.
1. Configure flux.dev and hub.dev domains (point to `DOCKER_HOST` ip) to avoid HTTPS warning and using IP address. Use dnsmasq to resolve all `*.dev` domains ([OS X](https://gist.github.com/develar/8c3a9430fd6682960c83)) or just `/etc/hosts` (on OS X is not recommended due to possible slow DNS lookup).

Useful IntelliJ IDEA plugins:
  * YAML to edit .yml files.
  * Markdown to edit .md files.
  * nginx Support to edit .conf files.
  * .ignore to edit .md files.

To maintain dependencies of web client, use [npm-check-updates](https://www.npmjs.com/package/npm-check-updates).

If you have strange mystic errors after editing volumes configuration in the docker-compose files — it is caused, probably, by ghosted volumes.
Execute ```docker rm -v `docker ps --no-trunc -aq``` (be aware — all container volumes data will be lost, don't use it if you don't understand what does it mean).

# Building docker images
Docker hub is used, see [cloudintellij](https://registry.hub.docker.com/repos/cloudintellij/) organization.

[Tutum](https://www.tutum.co/) is used to deploy and manage, so, private app images (e.g. SSL certificates) are publishing to [Tutum's private image registry](https://support.tutum.co/support/articles/5000012183-using-tutum-s-private-docker-image-registry).
`$USER` used instead of real username, so, you can run command as is, without modification (assume that your OS username equals to tutum username).

## mq-auth
`docker build -f mq-auth/Dockerfile -t cloudintellij/mq-auth .`
`docker push cloudintellij/mq-auth`

## flux-web
Build and push: `(cd web-client && exec npm run dist && exec npm run push)`. 

Service will be redeployed automatically after push (Tutum [web hook](https://support.tutum.co/support/solutions/articles/5000513815-webhook-handlers) configured). 

## SSL cert/key data container
Copy cert.pem and key.pem to certs/production.
`docker build -t tutum.co/$USER/intellij-io-certs certs`