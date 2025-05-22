# TASK AWAY (Sopra Group 07)

## Introduction

TASK AWAY is a chore management app designed for shared living spaces. It helps teams assign, track, and complete household tasks through a shared interactive pinboard. The task assignment system is flexible, supporting various distribution methods: First-Come-First-Serve (default), Karma’s Hand (based on progress), and Lucky Draw (randomized allocation). To boost motivation, the app incorporates gamification—users earn XP and level up by completing tasks. Calendar integration (via Google Calendar API) keeps everyone on track, while promoting teamwork and transparency.

## Technologies

The back end of the application is built with Java using the Spring Boot framework. PostgreSQL, hosted on Supabase, is used for data persistence. The server is deployed on Google Cloud (App Engine), while the client is hosted on Vercel. We integrate the Google Calendar API as an external service. Communication between the server and client is handled via both REST APIs and WebSockets.

## High-level components

All REST calls are handled by the [controller classes](./src/main/java/ch/uzh/ifi/hase/soprafs24/controller), which delegate the corresponding logic to the service layer. The core functionality for creating and joining teams is implemented in the [TeamService](./src/main/java/ch/uzh/ifi/hase/soprafs24/service/TeamService), while all logic related to task creation and distribution resides in the [TaskService](./src/main/java/ch/uzh/ifi/hase/soprafs24/service/TaskService). All user-related business logic is encapsulated in the [UserService](./src/main/java/ch/uzh/ifi/hase/soprafs24/service/UserService). Integration with external Google Calendar API is managed through the [CalendarService](./src/main/java/ch/uzh/ifi/hase/soprafs24/service/CalendarService) and [CalendarController](./src/main/java/ch/uzh/ifi/hase/soprafs24/controller/CalendarController), which handle all related communication.

## Launch & Deployment

### Google Calendar API

#### Google Calendar Integration:

Our application uses the Google Calendar API to let users view and sync events with their personal calendars. Integration is optional — users can still use the task calendar without linking their Google account.

Documentation on Google Calendar API can be found [here](https://developers.google.com/calendar).

#### Setup

* Enable API & Create Credentials
* Go to the Google Cloud API Library
* Enable the Google Calendar API for your project.
* Create OAuth 2.0 credentials (type: Web Application) and set your redirect URI (e.g., localhost:8080/api/calendar/callback) for local and deployed URL.
* A setup guide can be found [here](https://developers.google.com/workspace/guides/create-credentials)

#### Download & Configure Credentials

* Download credentials.json and place it in your project (e.g., src/main/resources/) for local use.

* Set an environment variable pointing to it:
export GOOGLE_APPLICATION_CREDENTIALS=src/main/resources/credentials.json


* For deployment, use GitHub secrets to store credentials and configure the callback URI in production.

#### Add Dependencies in build.gradle with:

```
implementation 'com.google.api-client:google-api-client:2.0.0'
```
```
implementation 'com.google.oauth-client:google-oauth-client-jetty:1.34.1'
```
```
implementation 'com.google.apis:google-api-services-calendar:v3-rev20230807-2.0.0''
```

#### OAuth Flow
The user is redirected to Google’s consent screen. After authorization, the access tokens are handled in [CalendarService](./src/main/java/ch/uzh/ifi/hase/soprafs24/service/CalendarService) for fetching and adding events to the Google Calendar.

### Build

```
./gradlew build
```

To resolve build issues or missing tests, run the following command:

```bash
./gradlew clean build
```

### Run with production DB

```
./gradlew bootRunDev
```
Use bootRunDev to run the application locally with an in-memory H2 database. This mode is ideal for development and testing, as no changes are written to the PostgreSQL database.

### Run with persistent DB

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

We recommend using [Postman](https://www.getpostman.com) to test the API Endpoints. Most endpoints can be tested this way; however, due to OAuth flow requirements, endpoints in the [CalendarController](./src/main/java/ch/uzh/ifi/hase/soprafs24/controller/CalendarController) cannot be fully tested via Postman. In the [CalendarAuthController](./src/main/java/ch/uzh/ifi/hase/soprafs24/controller/CalendarAuthController), only the GET /calendar/auth-url endpoint can be tested to generate the Google authorization URL. Subsequent steps in the OAuth process must be performed through the browser and cannot be completed directly within Postman.


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
