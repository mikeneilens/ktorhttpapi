### ktor http API

This is an example of using ktor to create a simple API, including unit tests.
The API is based on the ktor example which includes basic auth and auth using JWT token.
The basic auth passes the credentials in the body (thats how the Ktor demo code did it) but I've left test code in to take credentials from the blob passed with the authorization header.

All the routing is in Application.main() although this is built to use application.conf which specifies the port. 
For some reason on my Linux I've found it will only run using ./gradlew run but works OK on a mac. The tests run ok on the IDE.