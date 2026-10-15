# Mainframe Interface: Project 2 Starter

Welcome to the second project for SOFTENG 306.

The project specification is available [on Canvas](https://canvas.auckland.ac.nz/courses/142477/pages/project-2-overview?module_item_id=3007942). The four deliverables for this project are:

- [Proposal](https://canvas.auckland.ac.nz/courses/142477/assignments/514280)
- [Presentation](https://canvas.auckland.ac.nz/courses/142477/assignments/514279)
- [Code](https://canvas.auckland.ac.nz/courses/142477/assignments/514277)
- [Documentation](https://canvas.auckland.ac.nz/courses/142477/assignments/514278)

## Important

Update this readme file to contain the names of each team member and your GitHub login.

- _Replace this text: Alyza So - aso060_
- _Replace this text: Conor Sheridan - Conor-She_
- _Replace this text: Olivia Kusuma - olivia2450_
- _Replace this text: Taiki Yorifuji - Yorifuji-T_

You must also update this file to contain either the instructions for running your code or a link to the instructions.

## Running the app

Install JDK 17 or newer and make sure `JAVA_HOME` points to it. The Maven wrapper downloads Maven automatically on its first run, which requires an internet connection.

From the project root on Windows (PowerShell):

```powershell
.\mvnw.cmd javafx:run
```

On macOS or Linux:

```sh
sh ./mvnw javafx:run
```

To run the tests, replace `javafx:run` with `test`.

### Dashboard and customer search

Set `LAVS_MAINFRAME_URL` to the HTTPS base URL of the course mainframe before
starting the app (plain HTTP is allowed for localhost development). The login
form sends the entered credentials to that server, and only a successful response
opens the dashboard. For example, in PowerShell:

```powershell
$env:LAVS_MAINFRAME_URL = "https://your-mainframe-url/"
.\mvnw.cmd javafx:run
```

You can also set the Java system property `lavs.mainframe.url`. Login errors are
shown on the form, and logging out clears the local session token before returning
to login. `LAVS_ORGANISATION_ID` (or the `lavs.organisation.id` system
property) can be set for customer creation, which uses the configured organisation.

The Home screen and sidebar profile show the signed-in username. Home, Customers
(search UI), and Loans share a persistent navigation sidebar. The Customers screen searches the signed-in mainframe as you type and displays
customer ID, name and date-of-birth previews. Enter a customer name or ID to search; the mainframe does not list customers for a blank query. Select
a result to retrieve personal details and every address, phone, email and employer.
Use the "View all" links for additional contact entries. Search and retrieval run
in the background; failures remain on the search screen and can be retried.

To manually check this flow, sign in, open Customers, search for a known customer,
then select their row. Try a query with no matches, then search for the known customer again. Customer records contain no built-in sample data. Retrieved records are
read-only; editing existing records is not connected yet. The Loans, Notes,
Documents and Guarantors tabs do not retrieve existing records in this flow.
Customer creation requires the numeric organisation ID setting above.

## Initial Repository Code

This repository initially contains the code for the mainframe interface and the unit tests. The following code is supplied:

- [mainframe](src/main/java/mainframe/client/): the interfaces for sending requests to and receiving responses from the mainframe. You will need to use the [Connection](src/main/java/mainframe/client/Connection.java) interface in your code. We have provided an implementation that connects to a web server in [HttpConnection](src/main/java/mainframe/client/HttpConnection.java).
- [tests](src/test/java/mainframe/client/): the unit tests. Most of these connect to the web server, but there is an [example unit test](src/test/java/mainframe/client/SimulateListCustomers.java) for connecting to a stub.
