
# rate-limited-allow-list

This service is intended to reduce the amount of code service teams need to write, maintain and later remove for managing a gradual rollout of a service or feature to a group of users (for example, as part of a private beta rollout). It is responsible for storing a list of the user's identifiers and the rollout limits, and allow the service can check against that  list.

## How does it work

The service allows you to specify the number of users you want to allow, i.e. the rate, by setting that limit. When a user's identifier is not on already saved and we are within limits for that allow list, the user will be saved to the list, and a positive response will be returned. If we are at the limit of the number of new users and the user is not on the allow list, a negative response is returned that can be used by the calling service in determining where to route the user. 

You can manage the numbers of users to onboard over time - add, remove, to control the rate at which users are onboarded via the admin frontend [rate-limited-allow-list-admin-frontend](https://github.com/hmrc/rate-limited-allow-list-admin-frontend). 

## What it is not

- It is not a replacement for other forms of authentication or authorisation. You should still make use of other predicates to ensure a user is allowed to use your service.
- It is not for storing values that need to be retrieved - it only allows you to check if a value is present. You cannot retrieve the original values back.
- It is not for long-term storage. The production TTL is set to 30 days since last update.
- It is not intended for "feature flagging" functionality in your service.
- It does not allow multiple identifiers to be checked in a single request. It is intended for single lookups.
- It does not allow "partial matches". Identifiers must match exactly.
- It cannot manage lists that are shared across multiple services. Each service can only access its own identifier list.

## Integrating with user-allow-list

### 1. Decide on the identifier and feature name

Before starting any implementation you should decide upon a stable identifier you will use to identify users. For example, a national insurance number or other identifier retrieved from auth. _This cannot be a value provided by a user themselves_.

You should also decide upon the name for the allow list. A service can have multiple allow lists and each will be treated as independent of the other. For example for use in a service named `fake-frontend` for a private beta, you can use the name `fake-frontend-private-beta-2026`.

### 2. Create the config for the allow list

Using the admin service, [rate-limited-allow-list-admin-frontend](https://github.com/hmrc/rate-limited-allow-list-admin-frontend) create the allow list.

### 3. Add a connector to your service

The service exposes a single endpoint to consuming services - `/rate-limited-allow-list/services/:service/features/:feature` - where service is the name of your service and feature is a name you chose previously. You must add appropriate tests to ensure this functionality works as you expect.

### 4. Managing the number of users for an allow list

The number of users can be managed in the admin service [rate-limited-allow-list-admin-frontend](https://github.com/hmrc/rate-limited-allow-list-admin-frontend).

## License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").