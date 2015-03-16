# Running
1. Run: `cd flux && docker-compose up`
2. Open your browser to `http://<dockerd host ip>`

# Setting up development environment
1. Checkout flux — run getFlux.sh (Linux/Mac) or getFlux.bat (Windows).
2. Install [Docker and Docker Compose](https://docs.docker.com/compose/install/). (If you have Parallels Desktop, consider to use [Vagrant](https://github.com/Parallels/vagrant-parallels/issues/115) ([sample Vagrantfile](https://dl.dropboxusercontent.com/u/43511007/Vagrantfile))).
3. Install IntelliJ IDEA plugins: YAML, Markdown and .ignore.

To maintain dependencies of the node.server, use [npm-check-updates](https://www.npmjs.com/package/npm-check-updates).

