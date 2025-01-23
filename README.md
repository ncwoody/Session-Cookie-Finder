# Session-Cookie-Finder
Burp Suite extension which will identify session cookies.
Written in Java with Portswigger's Montoya API.
Currently a work in progress, the algorithm to determine if a cookie is a session can still be improved.
Usage: right-click on a request and use the Session Cookie Finder option under Extensions. This will create an issue with any found session cookies. Additionally, a UI tab is available to help organize session cookies.
Building: a jar is included in build/libs, however gradle can be used to build your own jar with the "gradle build" command.
