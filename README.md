# TASK AWAY (Sopra Group 07)

## Introduction

TASK AWAY is a chore management app designed for shared living spaces. It helps teams assign, track, and complete household tasks through a shared interactive pinboard. The task assignment system is flexible, supporting various distribution methods: First-Come-First-Serve (default), Karma’s Hand (based on progress), and Lucky Draw (randomized allocation). To boost motivation, the app incorporates gamification—users earn XP and level up by completing tasks. Calendar integration (via Google Calendar API) keeps everyone on track, while promoting teamwork and transparency.

## Technologies

The back-end of the application is built with Java 17 using the Spring Boot framework. For data persistence, we use PostgreSQL hosted on Supabase. The server is deployed on Google Cloud, while the client is hosted on Vercel. Communication between the server and client is managed through both REST APIs and WebSockets.

## High-level components

All REST calls are handled by the [controller classes](./src/main/java/ch/uzh/ifi/hase/soprafs24/controller), which delegate the corresponding logic to the service layer. The core functionality for creating and joining teams is implemented in the [TeamService](./src/main/java/ch/uzh/ifi/hase/soprafs24/service/TeamService), while all logic related to task creation and distribution resides in the [TaskService](./src/main/java/ch/uzh/ifi/hase/soprafs24/service/TaskService). Integration with external Google Calendar API is managed through the [CalendarService](./src/main/java/ch/uzh/ifi/hase/soprafs24/service/CalendarService) and [CalendarController](./src/main/java/ch/uzh/ifi/hase/soprafs24/controller/CalendarController), which handle all related communication.

## Launch & Deployment


### Build

```
./gradlew build
```
In case of problems or tests not running during the build process, you can run the following command:

```bash
./gradlew clean build
```

### Run with production DB

```
./gradlew bootRunDev
```
Use bootRunDev to run the application locally with an in-memory H2 database. This mode is ideal for development and testing, as no changes are written to the actual PostgreSQL database.

### Run with persitant DB

```
./gradlew bootRun
```
Use bootRun to start the application with the PostgreSQL database. This mode should be used when you want to persist data to the database.

### Test
```
./gradlew test
```
#### Development Mode

You can start the backend in development mode, this will automatically trigger a new build and reload the application once the content of a file has been changed.

Start two terminal windows and run:
```
./gradlew build --continuous
```

and in the other one:
```
./gradlew bootRunDev
```
If you want to avoid running all tests with every change, use the following command instead:
```
./gradlew build --continuous -xtest
```
#### API Endpoint Testing with Postman

We recommend using [Postman](https://www.getpostman.com) to test your API Endpoints.


## Roadmap

Further version of TASK AWAY could include these functionalities:

* New game modes to enhance task assignment variety 
* Global leaderboard showcasing all teams ranked by total team XP  
* Task browsing with advanced filtering and sorting options:
  * Filter by user color  
  * Sort tasks alphabetically by name or numerically by XP  
* Multi-team participation allowing users to join and contribute to more than one team  
* Statistics and History of:
  * Task claiming  
  * Task completion  


## Authors and acknowledgment

The SoPra FS25 Group consists of:

* [Lunoastro](https://github.com/Lunoastro) *Back-end*
* [ppossler](https://github.com/ppossler) *Back-end*

* [soluth29](https://github.com/soluth29) *Back-end*

* [Eni1a](https://github.com/Eni1a) *Front-end*

* [peng-liu98](https://github.com/peng-liu98) *Front-end*


We would like to thank our teaching assistant [Grizzlytron](https://github.com/Grizzlytron) for his helpful guidance throughout this project.

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](./LICENSE) file for details.
