# LAVS: Loan Application and Verification System

SOFTENG 306, Project 2, Team 13. LAVS is a JavaFX desktop application for Countrywide
Banking Corporation. It lets bank staff search customers, view and record their details, and
create loans with a calculated repayment schedule. Customer and loan data is kept on the
course mainframe, so nothing is stored locally.

## Team

| Name | GitHub |
| --- | --- |
| Alyza So | [aso060](https://github.com/aso060) |
| Conor Sheridan | [Conor-She](https://github.com/Conor-She) |
| Olivia Kusuma | [olivia2450](https://github.com/olivia2450) |
| Taiki Yorifuji | [Yorifuji-T](https://github.com/Yorifuji-T) |

## Course deliverables

The project specification is [on Canvas](https://canvas.auckland.ac.nz/courses/142477/pages/project-2-overview?module_item_id=3007942).

- [Proposal](https://canvas.auckland.ac.nz/courses/142477/assignments/514280)
- [Presentation](https://canvas.auckland.ac.nz/courses/142477/assignments/514279)
- [Code](https://canvas.auckland.ac.nz/courses/142477/assignments/514277)
- [Documentation](https://canvas.auckland.ac.nz/courses/142477/assignments/514278)

## Requirements

- JDK 17 or newer, with 'JAVA_HOME' pointing to it.
- An internet connection. The Maven wrapper downloads Maven and the dependencies on its first
  run, and the app talks to the course mainframe.
- The course mainframe's URL and a mainframe account (see **How to run the app**).

## Configuration

The app reads two settings. 

| Setting | Environment variable | Needed for |
| --- | --- | --- |
| Mainframe URL | 'LAVS_MAINFRAME_URL' | Signing in (required). Must be HTTPS; plain HTTP is accepted only for localhost. |
| Organisation ID | 'LAVS_ORGANISATION_ID' | Creating customers. Team 13's organisation ID is '13'. |

If you run the app from an IDE instead, add the same variables to the run configuration's
environment variables.

## How to run the app

'''powershell
$env:LAVS_MAINFRAME_URL = "https://animating-eraser-arrogance.ngrok-free.dev/"
$env:LAVS_ORGANISATION_ID = "13"
.\mvnw.cmd javafx:run
'''

On macOS or Linux, use 'export' and 'sh ./mvnw javafx:run'. Set the variables in the same
terminal you start the app from, because they last only for that window.

Then sign in with the team account 'Group_13'. The password is in our Canvas submission notes.

### If sign-in fails

- "Sign in is not configured" means 'LAVS_MAINFRAME_URL' isn't set in the terminal you started
  the app from.
- "Use HTTPS for the mainframe URL" means the URL doesn't start with 'https://'.
- "The sign-in service is unavailable" means the URL is wrong or the mainframe can't be reached.
  Check the URL against Canvas and your connection.

## Trying the app

1. **Sign in.** The Home screen and sidebar show the signed-in username. Logging out clears the
   session.
2. **Customers.** Type a name or customer ID to search. The mainframe doesn't list customers
   for a blank query. Search runs in the background, and failures can be retried. Select a
   result to see personal details and every address, phone, email and employer ("View all"
   shows extra contact entries).
3. **Add a customer** from the Home or Customers screen (needs the organisation ID above).
4. **Create a loan** from a customer's page ("+ Add Loan") or from the Loans screen. The wizard
   has three steps:
   1. Choose the borrower and enter the loan details (type, principal, interest rate, payment
      and rate frequency, term, start and renewal dates). The borrower must have a mailing
      address, a phone number or email, and either an employer or a guarantor. If they don't,
      the wizard says which requirement is missing and won't continue.
   2. Add up to three co-borrowers.
   3. Review the loan and its repayment schedule (with total interest and total repayable), then
      press Finish. The loan is saved on the mainframe. If the mainframe can't be reached, the
      wizard says no loan was saved and nothing is added.
5. **Find loans.** The Loans screen searches the loans held on the mainframe by loan number or
   borrower name. Select a loan to open its details: type, principal, interest rate, payment
   and rate frequency, term, start and renewal dates and status, plus its co-borrowers,
   repayment schedule and the payments recorded against it. Note:
   - The mainframe has no native field for table and reducing-balance loan types, so LAVS stores
  the selected type as loan metadata. New loans therefore retain their type after reloading.
  Older loans without that metadata still show "Not recorded". The mainframe also does not store
  a loan's term. For reducing-balance and interest-only loans, the schedule the mainframe holds
  can differ from the one shown when the loan was created.
6. **Customer records.** Selecting a customer shows their personal details, contact and
   employment records, loans, notes, documents and guarantors. Personal details, notes and
   guarantors can be edited and are saved to the mainframe. Existing loans can't be removed. A
   loan's co-borrowers are on its Co-borrowers tab.

## Running the tests

'''powershell
.\mvnw.cmd test "-Dtest=*Test"
'''

This runs our own unit tests, which use an in-memory 'FakeMainframeGateway' and need no
mainframe. To run everything, including the supplied client tests, use '.\mvnw.cmd test'.
Those supplied tests ('src/test/java/mainframe/client/', named '*Tests') call the shared
course server with a shared test account, so some can fail for reasons outside our code.
After a test run, the coverage report is at 'target/site/jacoco/index.html'.

## How the code is organised

| Package | Role |
| --- | --- |
| 'com.lavs.ui' | JavaFX controllers and FXML screens |
| 'com.lavs.business' | Business logic: loan, customer and payment services, repayment calculators |
| 'com.lavs.data' | 'MainframeGateway' interface, the real 'MainframeGatewayImpl', and the in-memory 'FakeMainframeGateway' used in tests |
| 'com.lavs.model' | Domain objects (customers, loans, repayments, payments and so on) |
| 'mainframe.client' | The supplied client for sending requests to the mainframe |

The user interface only talks to the business layer, and the business layer only talks to the
'MainframeGateway' interface, so everything except the real gateway can be tested without a
server.

## Known limitations

- The mainframe has no native field for table and reducing-balance loan types. LAVS preserves the
  selected type as loan metadata for newly created or edited loans, while older loans without
  metadata still show "Not recorded". The mainframe also does not store the term. For
  reducing-balance and interest-only loans, the schedule the mainframe holds can differ from the
  one shown when the loan was created.
- The mainframe can't delete a loan. If a loan is created but a co-borrower can't be added, the
  loan is cancelled instead.
