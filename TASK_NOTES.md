# Notes

## Repository cleanup

At the very start, I removed irrelevant code and hoisted up Java template to the root repository. This is to keep the project simpler
and effectively reduce the token spend and improve results.

## Basic setup

I chose to perform this task with Claude Code (Opus 5.5). At this moment I see no need to complicate the environment
and use other AI tools. Opus 5.5 seems like an excellent compromise between performance and token spend.

I am set up with a dedicated Linux VM on my MacOS laptop. All tools (Claude Code, Docker, IntelliJ IDEA) are running
within the VM. The VM is isolated from outside environment. This way I can tightly control what the model has access to
which may not be super important in case of this simple project but would be an important concern in a less trivial case.

I opted to use a full blown desktop environment VM (Parallels Desktop) for the convenience of not having to deal
with additional complication of dev containers. Also, this makes it easy for me to use Docker and Docker Compose inside
the development environment.

This is not exactly repeatable development environment that dev containers promise, but if it ever becomes a problem
I can use tools like Vagrant to create a recipe to spawn environments at will.

## Initial reading of the code and instructions

### Dubious choices for data types

#### Floating point amounts

Floating point has no place when representing any amounts in accounting context (TimeDeposit.balance). While there
is an attempt to use BigDecimal type within updateBalance(), it unfortunately is not correct and does not prevent
all of the possible artifacts of floating point. I decided to honor the instructions for the sake of this task,
but in any real life situation I would instantly flag this as an unacceptable practice.

#### 31 bit ids

Likewise, int only has about 2B possible positive values. I would not have used a 4 byte wide integer type to store
an identifier that I can reasonably expect to grow beyond billions. Even if practical number of legitimate deposits
will never exceed 2B, there are other reasons like possible system failures causing retry loops or maybe
legitimate changes in business practices in the future. Experience shows that this practice of saving couple bytes is not worth
any potential performance improvements as it may result in outages and sometimes costly migrations. Again, I will honor
task constraints and keep it as it is but in real life situation I would also flag this as unacceptable.

### Hexagonal Architecture

I have little experience with Hexagonal Architecture. I know it in theory but when I learned about it, but I decided
against using it for two basic reasons:

#### Increase in complexity

In my experience in most cases, the simplest concrete solution to the problem tends to also
be the best one. Many times I observed myself or other people add complexity in the hope that it will improve
future maintainability only to find that the result was actually the opposite.

Therefore, my principle is to *NEVER* add complexity speculatively, without actual knowledge that
a particular problem will have to be solved in the near future. For example, if adding a new payment system is already
scheduled for later in the project, it is fine to create an abstraction around it. On the other hand if nothing is known about it,
creating abstractions is simply paying guaranteed upfront and maintenance costs for an unknown
return and possibility that the investment will actually make it more difficult to implement the new payment system in the future.

#### New joiners

Nowadays people join and leave companies quickly. It is more and more important to keep the application simple in terms
of amount of code but also in terms of the types of patterns that are being useful. Getting a new developer to quickly
become productive and be able to quickly start reading and understanding code has never been more important.

AI agents are no exception. They still need to learn and comprehend that code, so the more code they need to read
the larger the cost of entire operation.

Again, I decided to follow the instruction but with understanding I have not much experience actually using it (for
the reasons stated above).

### Unclear requirements on end of interest payment

Student deposit description says "no interest after 1 year". Based on the initial implementation one can see this is one
year from the deposit date. But what happens at the end if the interest is not calculated on the last day? Technically,
the interest has been accruing for some number of days an the customer would be entiteld to receive that interest, but
there is currently no logic to handle it.

## AI Usage

I used AI to write all code. As a general rule, decisions about technological stack, project structure
and major design decisions are mine. I let AI make smaller design decisons and pick specific versions of components
and add ancillary dependencies. I review all code that is being created and use AI to discuss some of the choices
it has made.

# Design choices

## OpenAPI contracts and Swagger

In my experience, the best way to keep API contract in sync with both implementation and Swagger UI is to derive
everything from the contract document itself. OpenAPI Generator allows generating code for the presentation
layer with only an adapter implementation needed to provide glue between presentation and the services.

For simplicity, I put Swagger on the backend application itself. I could have put it as a separate container
but that creates complexity for no concrete benefit (Rule #1, only add complexity if it solves a problem). Having
the Swagger local on the service simplifies finding it esp. when there is a lot of services.
In a regular application I would create a configuration option and only enable it explicitly in development environment/profile.

## Batch processing stack

In a real life application, expecting to fetch or update all of the deposits at once is naive at best. An operation like that
needs to be executed over time and be able to gracefully interrupt and resume operations.

I could have used Spring Batch (which I used on my last project), but in my experience the benefits on the structure
and performance of the project are dubious. I decided for a simple case like this to simply roll simple batching logic
which also makes it relatively easy to improve it. If I needed to coordinate a lot more processess I would probably
opt to use Spring Batch for this.

## Accounting Date

In real life, processing does not always happen on exact calendar day. Sometimes processess can shift around midnight,
be executed in a different timezone or be postponed by an outage, even multiple days.

Therefore I added accounting date to the interest calculation endpoint so that the process can execute in correct context
with business rules based on actual intended date on the process, not the current calendar date.

## Batch processing integrity

There are multiple problems when trying to ensure integrity of this kind of batch processing.

### Running multiple interest calculations concurrently

In simple words, there is no scenario in which it makes sense to start interest calculation before the previous interest
calculation operation finishes successfully.

For this reason I added a mechanism that will take a lock on the process such that it can only succeed if the previous
calculation finished successfully.

### Possible retries or skips when running interest calculation

In an accounting system like this, it is not correct to allow interest to be applied multiple times per period or to skip
periods accidentally.

For this reason I added a mechanism to track accounting date and ensure that the process can only start when it would
advance accounting date by one date. This prevents possibility of retrying the same process multiple times for a given day
and it also prevents possibility that a day of calculations is accidentally skipped.

## Overall structure of batch processing for interest calculation

In a real bank, there would exist an end of day process with roughly following structure:

* run batch processess that need to be executed in order to close the balance (essentially, figure out the current state of each account)
* run batch processess that need to be executed on the balance (including interest calculation)
* verify all conditions and advance accounting day
* run batch processess that need to be executed at the start of the day

Therefore, a real service wouldn't just perform interest calculation, it would probably be orchestrated from an external
service with the current accounting date received from an external service and switched somewhere else.

That said, I replicated this tiny bit of functionality just to ensure consistency of the process.

## Fetch consistency tradeoffs

I decided not to guarantee a consistent result of queries while interest calculation is pending. This would require
either a substantial redesign of database structure or creating a very large transaction which would most likely cause
severe database issues in real life. In practice it is easier to commit progress of task like interest calculation in
batches and then handle any problems result from it than deal with large transactions that update all records in a large
table.

What would I do in real life?
A traditional would be temporal separation. Calculations happen at different time (nightly batch) from user access.
To my knowledge this is done in most banking systems. An orchestrator ensures sequencing of processess and the sequence
is constructed such that no component queries current state while the state is being updated.

This has benefit that it is simple but a drawback is that there is no safety. I would either make sure the query fails
if there is ongoing update or that the query runs on immutable data snapshot.

## Reverting broken batches

In a real life situation I would design an application like this with an ability to revert changes in case of
a process failure (wrong interests calculated, etc.) This could be implemented for example using immutable objects
(timeDeposit stores new objects for each update) and then discarding incorrectly calculated data to revert to previous state.

Current solution does not address this problem.
