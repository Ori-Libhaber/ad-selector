# Ad-Selector
Undertone coding task

## Preamble
* The Service was designed according to specs provided in Home Exam document.
* Both service design and implementation follow the "clean architecture" coding style guidelines.
* Terminology and structure are inspired by *Alistair Cockburn's* definition of the "Hexagonal architecture" a.k.a "Ports and Adapters Pattern".
  * Ports - Essentially interfaces and other abstractions intended to be implemented by external modules, thus promoting
    system extensibility without requiring to push code changes to core business logic.
  * Adaptors - Those are the actual implementations for the so-called Ports.
  * Read more about it [here](https://alistair.cockburn.us/hexagonal-architecture/)

## High-Level Class Diagram

<img src="E:\Dev\Others\ad-selector\docs\ad-selector-class-diagram.svg" alt="class-diagram" style="zoom:200%;" />

##### Color codes

* Blue: Interfaces
* Green: Abstract classes
* Purple: Concrete implementations
* Orange: Exceptions

##### Structure

Diagram is divided into block sections, each corresponds to a separated maven module.
Each block section is internally divided into groups that correlate to Java package hierarchy.

## Implementation Highlights

* WebFlux was used to facilitate the reactive capabilities of the system.
* *FileBackedAdBudgetPlanStore* 
  * Implementation is using a WatchService to keep track of changes to backing plan file and reloads it if the latter changes on disk.
    The watcher could be turned off by setting the value of either 
    * application.properties variable: plan.file.watcher.enabled=false
    * environment variable: PLAN_FILE_WATCHER_ENABLED=false
  * By default, file watcher is active.
  * By default, plan file is expected to be located under: /plan/plan.json , but this could be set to any path using either
    * application.properties variable: plan.file=/plan/plan.json
    * environment variable: PLAN_FILE=/plan/plan.json
  * Implementation optionally supports lazy parsing of plan contents (AdBudget instances), toggleable by either
    * application.properties value: plan.file.lazy.loading.enabled=true
    * environment variable: PLAN_FILE_LAZY_LOADING_ENABLED=true
  *  By default, eager parsing is used.
* Test coverage is around 85% - 100% for essential flows.
* ![](E:\Dev\Others\ad-selector\docs\coverage.PNG)
* Test containers library was used to test *RedisBackedAdDistributionStore* which utilizes Spring's reactive Redis template to persist and synchronize quota  spending between service instances.

## Endpoints

There's just a single RESTful enpoint to fetch selections under `/api/v1/selectAd` as can be seen in the following example:

POST http://localhost:8080/api/v1/selectAd
*Content-Type*: application/json

{
  "q": [
    "test0",
    "test1",
    "test9997",
    "test9996",
    "test9995",
    "test9994",
    "test9993",
    "test9992",
    "test9991",
    "test9990",
    "test9989",
    "test9988",
    "test9987",
    "test12",
    "test15"
  ]
}

## How to run?

First thing to do is to compile and package the executable jar using:

`mvn clean install -T1C`

Once build is complete, it's recommended to use the attached `docker-compose.yml` to spin-up a Redis container alongside
the application container.

To do that (assuming docker desktop or equivalent is installed) under the root project folder, run:

`docker-compose up -d`

### Please note

Project root folder contains `./ad-selector/plan/plan.json`
This is a running example for a plan file, you my replace it with any plan file of your own either when creating the image 
(it is being copied in Dockerfile to container) or by directly uploading it to a running container under `/plan`
