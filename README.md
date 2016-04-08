# DB Migrate Example

Ever wonder how to properly use a bunch of Clojure technology together:

* [core.async](https://github.com/clojure/core.async)
* [component](https://github.com/stuartsierra/component)
* [io.aviso/config](https://github.com/AvisoNovate/config)
* [walmartlabs/active-status](https://github.com/walmartlabs/active-status)
* [walmartlabs/system-viz](https://github.com/walmartlabs/system-viz)
* and [tools.cli](https://github.com/clojure/tools.cli) for good measure?

Well, look no further. 

This project is a snapshot of code extracted from real live code in our system.
The real code performs an update across a huge number of rows stored
in a Cassandra database.

This version has had all the actual database work replaced with sleeps,
so it's a bit of a toy. 
But what we've kept is:

* Parsing of command line options
* Creation of a component system
* Visualization of the system components
* Execution of the "migration", using asynchronous processing jobs
* Dynamic console output of the status of all asynchronous jobs

Here's what is looks like in action:

![example execution](db-migrate.gif)

