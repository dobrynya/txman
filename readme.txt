Txman is just a sample on using Akka Persistence as an account and transaction management system.

It provides abilities to

1) create an account
PUT at /account

{
  "request": "123",
  "owner": "Frodo Bagging",
  "initialBalance": 0
}

results with

{
  "id": "created account id",
  "owner": "Frodo Baggins",
  "initialBalance": 0
}

2) get account information
GET at /account/{accountId}

results with

{
  "id": "account id",
  "owner": "Frodo Baggins",
  "initialBalance": 0
}

3) initiate a money transfer between accounts

PUT at /transaction


{
  "request": "request identifier",
  "source": "account id to be debited for amount of money",
  "destination": "account id to be credited for amount of money",
  "value": "amount of money to be transferred",
}

results with

{
  "request": "request identifier",
  "source": "account id to be debited for amount of money",
  "destination": "account id to be credited for amount of money",
  "value": "amount of money to be transferred",
  "status": "CLOSED" or "FAILED",
  reason: "Text description of failure"
}

All data aware components (account manager, accounts, transaction manager) are made as persistent actors to preserve
its state over its whole lifecycle.

Currently the account manager has a companion made as Persistent View which persists "account created" events to a JDBC
store using Lightbend Slick. Actually the account manager view can do any aggregations on event stream and calculate any
meaningful statistics and so on.

To run tests write in the console
./gradlew clean test

To build an executable fat Jar write as follows
./gradlew clean buildDist

To run the application write as follows
cd build/libs
java -jar txman.jar

To stop the app press Enter in the console

The application binds "0.0.0.0" at 8080. So endpoint is available at http://localhost:8080

txman-soapui-project.xml contains a SOAPUI project with appropriate requests.

Thanks for your attention. In case of any question please do not hesitate to ask me for an answer.