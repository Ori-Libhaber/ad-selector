# Ad-Selector
Undertone coding task

## Preamble
* The Service was designed according to specs provided in Home Exam document.
* Both service design and implementation follow the "clean architecture" coding style guidelines.
* Terminology and structure are inspired by *Alistair Cockburn's* definition of the "Hexagonal architecture" a.k.a "Ports and Adapters Pattern".
  * Ports - Essentially interfaces and other abstractions intended to be implemented by external modules, thus promoting
    system extensibility without requiring to push code changes to core business logic.
  * Adaptors - Those are the actual implementations for the so called Ports.
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
  *  By default eager parsing is used.
* Test coverage is around 85% - 100% for essential flows.
* Test containers were used to test RedisBackedAdDistributionStore

